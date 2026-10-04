#!/usr/bin/env python3
"""Compare a Helm render with the validated raw manifests (requires PyYAML).
Usage: python3 scripts/helm-check.py /tmp/stockflow-rendered.yaml
"""
import sys
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
rendered = [r for r in yaml.safe_load_all(Path(sys.argv[1]).read_text()) if r]
index = {(r['kind'], r['metadata']['name']): r for r in rendered}
assert not any(r['kind'] in ('Secret', 'Namespace', 'ClusterRole', 'ClusterRoleBinding') for r in rendered)
assert len({r['metadata']['namespace'] for r in rendered}) == 1
for resource in rendered:
    labels = resource['metadata']['labels']
    assert all(key in labels for key in ('app.kubernetes.io/name', 'app.kubernetes.io/instance',
        'app.kubernetes.io/version', 'app.kubernetes.io/component', 'app.kubernetes.io/managed-by'))

for path in (root / 'k8s').glob('*/*.yaml'):
    for raw in yaml.safe_load_all(path.read_text()):
        current = index[(raw['kind'], raw['metadata']['name'])]
        # Compare the functional spec, ignoring only intentional Helm additions.
        spec = yaml.safe_load(yaml.safe_dump(current.get('spec', {})))
        if raw['kind'] in ('Deployment', 'StatefulSet'):
            spec['selector']['matchLabels'].pop('app.kubernetes.io/instance')
            spec['template']['metadata'] = raw['spec']['template']['metadata']
            container = spec['template']['spec']['containers'][0]
            original = raw['spec']['template']['spec']['containers'][0]
            if 'imagePullPolicy' not in original:
                assert container.pop('imagePullPolicy') == 'IfNotPresent'
            if raw['metadata']['name'] == 'backend':
                assert container.pop('env') == [{'name':'PORT','value':'8080'},
                    {'name':'MANAGEMENT_SERVER_PORT','value':'9091'}]
            if raw['kind'] == 'StatefulSet':
                assert spec.pop('persistentVolumeClaimRetentionPolicy') == {'whenDeleted':'Retain','whenScaled':'Retain'}
        elif raw['kind'] == 'Service':
            spec['selector'].pop('app.kubernetes.io/instance')
        elif raw['kind'] == 'PodDisruptionBudget':
            spec['selector']['matchLabels'].pop('app.kubernetes.io/instance')
        assert spec == raw.get('spec', {}), f'Spec changed: {path} / {raw["kind"]}'
        if raw['kind'] == 'Role':
            assert current['rules'] == raw['rules']
        if raw['kind'] == 'RoleBinding':
            assert current['roleRef'] == raw['roleRef']
            subjects = [dict(s, namespace=current['metadata']['namespace']) for s in raw['subjects']]
            assert current['subjects'] == subjects
assert index[('ConfigMap','stockflow-config')]['data'] == yaml.safe_load((root/'k8s/config.yaml').read_text())['data']
for name, directory in [('prometheus-rules','prometheus/rules'),('alertmanager-config','alertmanager'),
    ('grafana-datasources','grafana/provisioning/datasources'),('grafana-providers','grafana/provisioning/dashboards'),
    ('grafana-dashboards','grafana/dashboards')]:
    expected = {p.name:p.read_text() for p in (root/'helm/stockflow/files/monitoring'/directory).iterdir() if p.is_file()}
    assert index[('ConfigMap',name)]['data'] == expected, name
prom = index[('ConfigMap','prometheus-config')]
discovery = yaml.safe_load(prom['data']['prometheus.yml'])
original = yaml.safe_load((root/'k8s/observability/prometheus.yml').read_text())
original['scrape_configs'][0]['kubernetes_sd_configs'][0]['namespaces']['names'] = [prom['metadata']['namespace']]
release_filter = discovery['scrape_configs'][0]['relabel_configs'].pop(0)
assert release_filter['source_labels'] == ['__meta_kubernetes_pod_label_app_kubernetes_io_instance']
assert release_filter['action'] == 'keep'
assert release_filter['regex'] == prom['metadata']['labels']['app.kubernetes.io/instance']
assert discovery == original, 'Kubernetes discovery behavior changed'
rules = index[('ConfigMap','prometheus-rules')]['data']
counts = [sum(len(g['rules']) for g in yaml.safe_load(rules[f'stockflow-{kind}.yml'])['groups']) for kind in ('recording','alerts')]
assert counts == [50,6], counts
print(f'PASS: {len(rendered)} resources; raw specs equivalent; canonical configs byte-identical; 50 recording / 6 alert rules.')
