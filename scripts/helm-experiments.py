#!/usr/bin/env python3
"""Helm lifecycle experiments on the dedicated stockflow-helm-lab cluster.
Requires a fresh installed release, loaded stage5-v1/v2 images and external Secrets.
Evidence stays outside the repository. Uninstall is explicitly opt-in.
"""
import argparse
import base64
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--namespace', default='stockflow-helm')
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--resume-reinstall-check', action='store_true', help='Resume only the final verification from a completed uninstall/reinstall checkpoint')
parser.add_argument('--skip-e2e', action='store_true', help='Resume lifecycle after the commercial E2E already passed in this evidence directory')
parser.add_argument('--uninstall', action='store_true', help='Uninstall then reinstall using preserved PVCs')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
assert not args.output.resolve().is_relative_to(root), 'Evidence must stay outside the repository'
args.output.mkdir(parents=True, exist_ok=True)
K = ['kubectl', '--context', 'kind-stockflow-helm-lab', '-n', args.namespace]
H = ['helm', '--kube-context', 'kind-stockflow-helm-lab', '--namespace', args.namespace]
chart = str(root/'helm/stockflow')
values = str(root/'helm/stockflow/values-local.yaml')

def run(command, timeout=400):
    return subprocess.check_output(command, text=True, timeout=timeout)

def obj(*command):
    return json.loads(run(K+list(command)+['-o','json']))

def wait(predicate, timeout=150):
    deadline = time.monotonic()+timeout
    while time.monotonic()<deadline:
        value = predicate()
        if value:
            return value
        threading.Event().wait(1)
    raise TimeoutError('Experiment condition did not become true')

def call(base, path, data=None, headers=None):
    req = urllib.request.Request(base+path, headers=headers or {}, data=json.dumps(data).encode() if data is not None else None)
    with urllib.request.urlopen(req, timeout=20) as response:
        return json.load(response)

forwards = []
def forward(service, port, target):
    process = subprocess.Popen(K+['port-forward','--address','127.0.0.1','service/'+service,f'{port}:{target}'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    forwards.append(process)
    return f'http://127.0.0.1:{port}'

def sql():
    return run(K+['exec','database-0','--','psql','-U','stockflow','-d','stockflow','-Atc',
        'SELECT (SELECT count(*) FROM categories),(SELECT count(*) FROM products),(SELECT count(*) FROM sales),(SELECT sum(stock) FROM products),(SELECT count(*) FROM application_users),(SELECT count(*) FROM flyway_schema_history WHERE success);']).strip()

result = json.loads((args.output/'helm-lifecycle.json').read_text()) if args.resume_reinstall_check else {}
try:
    frontend = forward('frontend',58273,5173)
    prometheus = forward('prometheus',59290,9090)
    grafana = forward('grafana',53200,3000)
    password = base64.b64decode(obj('get','secret','stockflow-secrets')['data']['APP_ADMIN_PASSWORD']).decode()
    gp = base64.b64decode(obj('get','secret','grafana-secret')['data']['password']).decode()
    def login():
        try:
            return call(frontend,'/api/auth/login',{'email':'admin@stockflow.local','password':password},{'Content-Type':'application/json'})['accessToken']
        except (OSError, ValueError):
            return None
    token = wait(login)
    headers = {'Authorization':'Bearer '+token}
    gh = {'Authorization':'Basic '+base64.b64encode(('admin:'+gp).encode()).decode(),'Content-Type':'application/json'}
    # Reuse the existing commercial browser scenario against the real Helm installation.
    env = dict(os.environ, E2E_ADMIN_EMAIL='admin@stockflow.local', E2E_ADMIN_PASSWORD=password,
        E2E_K8S_URL=frontend)
    if args.skip_e2e or args.resume_reinstall_check:
        assert '1 passed' in (args.output/'playwright.log').read_text(), 'Require prior successful E2E evidence'
    else:
        with (args.output/'playwright.log').open('w') as log:
            subprocess.run(['npx','playwright','test','--config','playwright.k8s.config.ts','--output',str(args.output/'playwright-artifacts')], cwd=root/'frontend',env=env,stdout=log,stderr=subprocess.STDOUT,timeout=160,check=True)
    result['e2e']='passed'
    def ready(count):
        deployment = obj('get','deployment','backend')
        return deployment['status'].get('readyReplicas')==count and deployment['status'].get('updatedReplicas')==count
    def discovered(count):
        try:
            data = call(prometheus,'/api/v1/targets')['data']['activeTargets']
            return len(data)==count and all(t['health']=='up' for t in data)
        except OSError:
            return False
    def dashboards(count):
        output = {}
        for uid, titles in [('stockflow-overview',{'Scrape UP','Uptime'}),('stockflow-sre',{'Readiness','Scraping UP','Readiness sample age'})]:
            dashboard = call(grafana,'/api/dashboards/uid/'+uid,headers=gh)['dashboard']
            queries=[];gauges=[]
            for panel in dashboard['panels']:
                for target in panel.get('targets',[]):
                    ref = str(len(queries))
                    queries.append({'refId':ref,'expr':target['expr'].replace('$__rate_interval','1m'),'datasource':{'uid':'stockflow-prometheus','type':'prometheus'},'intervalMs':15000,'maxDataPoints':100,'instant':True})
                    if panel['title'] in titles:
                        gauges.append(ref)
            response = call(grafana,'/api/ds/query',{'from':'now-15m','to':'now','queries':queries},gh)
            assert not any(r.get('error') for r in response['results'].values())
            for ref in gauges:
                labels=[field['labels'] for frame in response['results'][ref]['frames'] for field in frame['schema']['fields'] if field.get('labels',{}).get('instance')]
                if len(labels)!=count:
                    return None  # Readiness sampling and scrape can lag target discovery.
            output[dashboard['title']]=len(queries)
        return output
    def verify(count):
        wait(lambda:ready(count));wait(lambda:discovered(count))
        assert call(frontend,'/api/dashboard',headers=headers)
        return {'ready':count,'targets_up':count}
    def upgrade(*extra):
        return run(H+['upgrade','stockflow',chart,'-f',values,*extra,'--wait','--timeout','6m'])
    if args.resume_reinstall_check:
        assert result.get('uninstall') and result.get('reinstall'), 'Require completed uninstall/reinstall checkpoint'
        assert sql()==result['uninstall']['data_before']
        result['reinstall']=verify(2)
        result['dashboards_reinstall']=wait(lambda:dashboards(2), timeout=360)
    else:
        result['install']=verify(2)
        result['dashboards_initial']=wait(lambda:dashboards(2))
        upgrade('--set','backend.replicaCount=3')
        result['scale_up']=verify(3)
        result['dashboards_three']=wait(lambda:dashboards(3))
        upgrade('--set','backend.replicaCount=2')
        result['scale_down']=verify(2)
        revision = json.loads(run(H+['history','stockflow','-o','json']))[-1]['revision']
        def traffic(stop, statuses):
            while not stop.is_set():
                try:
                    call(frontend,'/api/dashboard',headers=headers)
                    statuses.append(200)
                except Exception as error:
                    statuses.append(type(error).__name__)
                stop.wait(0.2)
        def rolling(action):
            stop=threading.Event();statuses=[]
            with concurrent.futures.ThreadPoolExecutor() as executor:
                future=executor.submit(traffic,stop,statuses)
                try:
                    action()
                    run(K+['rollout','status','deployment/backend','--timeout=240s'])
                finally:
                    stop.set();future.result(timeout=30)
            assert statuses and set(statuses)=={200},statuses
            return {'requests':len(statuses),'statuses':sorted(set(statuses))}
        result['image_upgrade']=rolling(lambda:upgrade('--set','backend.image.tag=stage5-v2'))
        assert obj('get','deployment','backend')['spec']['template']['spec']['containers'][0]['image']=='stockflow-backend:stage5-v2'
        result['image_upgrade'].update(verify(2))
        result['history_before_rollback']=json.loads(run(H+['history','stockflow','-o','json']))
        result['rollback']=rolling(lambda:run(H+['rollback','stockflow',str(revision),'--wait','--timeout','6m']))
        assert obj('get','deployment','backend')['spec']['template']['spec']['containers'][0]['image']=='stockflow-backend:stage5-v1'
        result['rollback'].update(verify(2))
        result['history_after_rollback']=json.loads(run(H+['history','stockflow','-o','json']))
        groups=call(prometheus,'/api/v1/rules')['data']['groups']
        assert sum(len(g['rules']) for g in groups)==56
        assert all(r['health']=='ok' for g in groups for r in g['rules'])
        result['rules']={'recording':50,'alerts':6,'health':'ok'}
        if args.uninstall:
            before=sql()
            claims={p['metadata']['name']:p['metadata']['uid'] for p in obj('get','pvc')['items']}
            run(H+['uninstall','stockflow','--wait','--timeout','4m'])
            assert not obj('get','deployments,statefulsets,services,configmaps,roles,rolebindings,serviceaccounts,poddisruptionbudgets','-l','app.kubernetes.io/instance=stockflow')['items']
            assert claims=={p['metadata']['name']:p['metadata']['uid'] for p in obj('get','pvc')['items']}
            assert len(obj('get','secrets')['items'])==2
            # Stop stale port-forwards; reconnect after reinstall.
            for process in forwards:
                process.terminate();process.wait(timeout=10)
            forwards.clear()
            run(H+['upgrade','--install','stockflow',chart,'-f',values,
                '--set','prometheus.storage.existingClaim=prometheus-data',
                '--set','grafana.storage.existingClaim=grafana-data',
                '--set','alertmanager.storage.existingClaim=alertmanager-data','--wait','--timeout','6m'])
            frontend=forward('frontend',58273,5173);prometheus=forward('prometheus',59290,9090);grafana=forward('grafana',53200,3000)
            token=wait(login);headers={'Authorization':'Bearer '+token}
            assert sql()==before
            result['uninstall']={'pvc_uids_preserved':claims,'data_before':before,'data_after':sql(),'external_secrets_preserved':True}
            result['reinstall']=verify(2)
            result['dashboards_reinstall']=wait(lambda:dashboards(2), timeout=360)
    print(json.dumps(result,indent=2))
finally:
    for process in forwards:
        process.terminate();process.wait(timeout=10)
    (args.output/'helm-lifecycle.json').write_text(json.dumps(result,indent=2))
