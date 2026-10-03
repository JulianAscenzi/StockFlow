#!/usr/bin/env python3
"""Controlled experiments only against kind-stockflow-lab. Requires a frontend port-forward.
Uses existing application APIs; no artificial production endpoints or dependencies.
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
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('experiment', choices=['self-healing', 'db-down', 'persistence', 'graceful', 'liveness', 'rollout'])
parser.add_argument('--url', default='http://127.0.0.1:58173')
parser.add_argument('--output', required=True, type=Path, help='Private evidence directory outside the repository')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
K = ['kubectl', '--context', 'kind-stockflow-lab', '-n', 'stockflow']

def k(*commands, timeout=60):
    return subprocess.check_output(K + list(commands), text=True, timeout=timeout)

def obj(*commands):
    return json.loads(k(*commands, '-o', 'json'))

def pods():
    return obj('get', 'pods', '-l', 'app=backend')['items']

def ready(pod):
    return not pod['metadata'].get('deletionTimestamp') and any(c['type'] == 'Ready' and c['status'] == 'True' for c in pod['status'].get('conditions', []))

def wait(predicate, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = predicate()
        if result:
            return result
        # Polling delay belongs to this test harness, never production code.
        threading.Event().wait(0.5)
    raise TimeoutError('Experiment condition did not become true')

def stable():
    return wait(lambda: len(pods()) == 2 and all(ready(p) for p in pods()))

def sql(query):
    return k('exec', 'database-0', '--', 'psql', '-U', 'stockflow', '-d', 'stockflow', '-Atc', query).strip()

def http(path, data=None, base=None, token=None, timeout=12):
    request = urllib.request.Request((base or args.url) + path, data=json.dumps(data).encode() if data is not None else None,
        headers={'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        try: body = json.loads(body)
        except json.JSONDecodeError: pass  # Vite can return an empty 503 with no ready endpoints.
        return error.code, body

secret = obj('get', 'secret', 'stockflow-secrets')['data']
password = base64.b64decode(secret['APP_ADMIN_PASSWORD']).decode()
email = obj('get', 'configmap', 'stockflow-config')['data']['APP_ADMIN_EMAIL']
status, login = http('/api/auth/login', {'email': email, 'password': password})
assert status == 200
TOKEN = login['accessToken']
def request(path='/api/dashboard', data=None, base=None):
    return http(path, data, base, TOKEN)

def restarts():
    return {p['metadata']['name']: p['status']['containerStatuses'][0]['restartCount'] for p in pods()}

def endpoints():
    return [e for s in obj('get', 'endpointslices', '-l', 'kubernetes.io/service-name=backend')['items'] for e in s.get('endpoints', [])]

class Traffic:
    def __enter__(self):
        self.stop = threading.Event()
        self.results = []
        def run():
            while not self.stop.is_set():
                try:
                    self.results.append(request()[0])
                except Exception as error:
                    self.results.append(type(error).__name__)
                self.stop.wait(0.2)
        self.thread = threading.Thread(target=run)
        self.thread.start()
        return self
    def __exit__(self, *unused):
        self.stop.set()
        self.thread.join(timeout=15)
        assert not self.thread.is_alive()
        assert self.results and all(s == 200 for s in self.results), self.results

stable()
result = {'experiment': args.experiment, 'before_restarts': restarts()}
start = time.monotonic()
if args.experiment == 'self-healing':
    victim = pods()[0]['metadata']['name']
    with Traffic() as traffic:
        k('delete', 'pod', victim, '--wait=false')
        wait(lambda: all(p['metadata']['name'] != victim for p in pods()))
        stable()
    result.update(replacement_seconds=round(time.monotonic()-start, 2), requests=len(traffic.results), statuses=sorted(set(traffic.results)))
elif args.experiment == 'db-down':
    before = restarts()
    try:
        k('scale', 'statefulset/database', '--replicas=0')
        k('wait', '--for=delete', 'pod/database-0', '--timeout=90s', timeout=100)
        wait(lambda: all(not ready(p) for p in pods()))
        result['endpoints_down'] = endpoints()
        assert not any(e['conditions'].get('ready') for e in endpoints())
        result['probes'] = {}
        for p in pods():
            name = p['metadata']['name']
            live = k('exec', name, '--', 'curl', '-s', '--max-time', '5', '-w', '%{http_code}', 'http://127.0.0.1:9091/actuator/health/liveness')
            readiness = k('exec', name, '--', 'curl', '-s', '--max-time', '5', '-w', '%{http_code}', 'http://127.0.0.1:9091/actuator/health/readiness')
            assert live.endswith('200') and readiness.endswith('503'), (live, readiness)
            assert p['status']['phase'] == 'Running'
            result['probes'][name] = {'liveness': live, 'readiness': readiness}
        # Span multiple liveness cycles while continuously checking restart counts.
        until = time.monotonic() + 40
        while time.monotonic() < until:
            assert restarts() == before
            threading.Event().wait(2)
        result['restart_counts_during_outage'] = restarts()
        args.output.joinpath('db-down-pods.json').write_text(json.dumps(pods(), indent=2))
        args.output.joinpath('db-down-events.txt').write_text(k('get', 'events', '--sort-by=.lastTimestamp'))
    finally:
        k('scale', 'statefulset/database', '--replicas=1')
        k('rollout', 'status', 'statefulset/database', '--timeout=180s', timeout=190)
        stable()
    assert restarts() == before
    assert request()[0] == 200
    result['recovered_endpoints'] = endpoints()
elif args.experiment == 'persistence':
    query = 'SELECT (SELECT count(*) FROM categories),(SELECT count(*) FROM products),(SELECT count(*) FROM sales),(SELECT sum(stock) FROM products),(SELECT count(*) FROM application_users),(SELECT count(*) FROM flyway_schema_history WHERE success);'
    before = sql(query)
    assert int(before.split('|')[2]) > 0, 'Run the commercial E2E first'
    uid = obj('get', 'pod', 'database-0')['metadata']['uid']
    k('delete', 'pod', 'database-0', '--wait=true', timeout=90)
    wait(lambda: obj('get', 'pods', '-l', 'app=database')['items'] and obj('get', 'pods', '-l', 'app=database')['items'][0]['metadata']['uid'] != uid)
    k('rollout', 'status', 'statefulset/database', '--timeout=180s', timeout=190)
    stable()
    after = sql(query)
    assert before == after
    wait(lambda: request()[0] == 200, timeout=60)
    stable()
    result.update(before=before, after=after, pvc=k('get', 'pvc', 'data-database-0'))
elif args.experiment == 'liveness':
    victim = pods()[0]['metadata']['name']
    before = restarts()[victim]
    container_id = obj('get', 'pod', victim)['status']['containerStatuses'][0]['containerID'].split('://', 1)[1]
    runtime = json.loads(subprocess.check_output(['docker', 'exec', 'stockflow-lab-control-plane', 'crictl', 'inspect', container_id], text=True))
    process_id = str(runtime['info']['pid'])
    def signal_process(signal):
        # PID 1 ignores unhandled signals from its own namespace. Signal from
        # the ancestor (kind node) namespace, without changing pod privileges.
        return subprocess.run(['docker', 'exec', 'stockflow-lab-control-plane', 'kill', signal, process_id], timeout=10, check=False)
    try:
        signal_process('-STOP').check_returncode()
        def killing():
            events = obj('get', 'events', '--field-selector', 'involvedObject.name=' + victim)['items']
            return any(e['reason'] == 'Killing' and 'liveness' in e.get('message', '').lower() for e in events)
        wait(killing, timeout=70)
        result['events'] = k('get', 'events', '--field-selector', 'involvedObject.name=' + victim)
    finally:
        # Resume the frozen JVM so kubelet's pending SIGTERM can run its shutdown hook.
        signal_process('-CONT')
    wait(lambda: restarts().get(victim, 0) == before + 1, timeout=90)
    stable()
    result['previous_termination'] = obj('get', 'pod', victim)['status']['containerStatuses'][0]['lastState']
elif args.experiment == 'rollout':
    # v2 must already be built/loaded, differing only in image metadata.
    result['history_before'] = k('rollout', 'history', 'deployment/backend')
    with Traffic() as traffic:
        k('set', 'image', 'deployment/backend', 'backend=stockflow-backend:stage5-v2')
        result['rollout'] = k('rollout', 'status', 'deployment/backend', '--timeout=240s', timeout=250)
    result['rollout_traffic'] = {'requests': len(traffic.results), 'statuses': sorted(set(traffic.results))}
    result['history_v2'] = k('rollout', 'history', 'deployment/backend')
    with Traffic() as traffic:
        result['undo'] = k('rollout', 'undo', 'deployment/backend')
        result['rollback'] = k('rollout', 'status', 'deployment/backend', '--timeout=240s', timeout=250)
    result['rollback_traffic'] = {'requests': len(traffic.results), 'statuses': sorted(set(traffic.results))}
    stable()
    assert obj('get', 'deployment', 'backend')['spec']['template']['spec']['containers'][0]['image'] == 'stockflow-backend:stage5-v1'
elif args.experiment == 'graceful':
    product = int(sql('SELECT id FROM products ORDER BY id LIMIT 1'))
    before_stock = int(sql(f'SELECT stock FROM products WHERE id={product}'))
    victim = pods()[0]['metadata']['name']
    log_path = args.output / 'graceful.log'
    lifecycle_path = args.output / 'graceful-lifecycle.jsonstream'
    with log_path.open('w') as log_file, lifecycle_path.open('w') as lifecycle_file:
        watch = subprocess.Popen(K + ['get', 'pod', victim, '--watch', '--output-watch-events', '-o', 'json'], stdout=lifecycle_file, stderr=subprocess.DEVNULL)
        log = subprocess.Popen(K + ['logs', '-f', victim], stdout=log_file, stderr=subprocess.STDOUT)
        pf = subprocess.Popen(K + ['port-forward', '--address', '127.0.0.1', 'pod/'+victim, '58080:8080'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        locker = subprocess.Popen(K + ['exec', '-i', 'database-0', '--', 'psql', '-U', 'stockflow', '-d', 'stockflow', '-At'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            def forwarded():
                try: return request(base='http://127.0.0.1:58080')[0] == 200
                except Exception: return False
            wait(forwarded, timeout=20)
            locker.stdin.write(f"SET application_name='stockflow-k8s-lock'; BEGIN; SELECT id FROM products WHERE id={product} FOR UPDATE;\n")
            locker.stdin.flush()
            wait(lambda: sql("SELECT count(*) FROM pg_stat_activity WHERE application_name='stockflow-k8s-lock' AND state='idle in transaction';") == '1')
            with concurrent.futures.ThreadPoolExecutor() as executor:
                inflight = executor.submit(lambda: http(f'/api/products/{product}/stock/in', {'quantity':1,'reason':'Kubernetes graceful laboratory'}, 'http://127.0.0.1:58080', TOKEN, timeout=40))
                wait(lambda: int(sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND application_name<>'stockflow-k8s-lock';")) > 0)
                with Traffic() as traffic:
                    k('delete', 'pod', victim, '--wait=false')
                    wait(lambda: any(e.get('targetRef', {}).get('name') == victim and not e['conditions'].get('ready') and e['conditions'].get('terminating') for e in endpoints()), timeout=15)
                    result['terminating_endpoints'] = endpoints()
                    wait(lambda: 'Commencing graceful shutdown' in log_path.read_text(), timeout=15)
                    assert not inflight.done(), 'Request must still be blocked while graceful shutdown starts'
                    locker.stdin.write('COMMIT;\n\\q\n')
                    locker.stdin.flush()
                    result['inflight_status'] = inflight.result(timeout=20)[0]
                    assert result['inflight_status'] == 200
                    wait(lambda: 'Shutdown completed' in log_path.read_text(), timeout=20)
                    stable()
                result['service_requests'] = len(traffic.results)
            assert int(sql(f'SELECT stock FROM products WHERE id={product}')) == before_stock + 1
            result['graceful_logs'] = str(log_path)
        finally:
            if locker.poll() is None:
                try: locker.stdin.write('ROLLBACK;\n\\q\n'); locker.stdin.flush()
                except BrokenPipeError: pass
                locker.communicate(timeout=10)
            pf.terminate(); pf.wait(timeout=10)
            log.terminate(); log.wait(timeout=10)
            watch.terminate(); watch.wait(timeout=10)
    decoder = json.JSONDecoder()
    stream = lifecycle_path.read_text().lstrip()
    terminations = []
    while stream:
        event, length = decoder.raw_decode(stream)
        for container in event['object']['status'].get('containerStatuses', []):
            if 'terminated' in container.get('state', {}):
                terminations.append(container['state']['terminated'])
        stream = stream[length:].lstrip()
    result['terminations'] = terminations
    assert terminations and all(t['exitCode'] == 143 for t in terminations), terminations
result['after_restarts'] = restarts()
result['elapsed_seconds'] = round(time.monotonic()-start, 2)
args.output.joinpath(args.experiment+'.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result, indent=2))
