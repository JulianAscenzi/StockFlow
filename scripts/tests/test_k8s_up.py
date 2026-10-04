"""Regression checks for external Secret overrides; never contacts Docker or Kubernetes."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
HELM = shutil.which("helm")


@unittest.skipUnless(HELM, "Helm is required to render effective values")
class ExternalSecretOverridesTest(unittest.TestCase):
    def run_script(self, available):
        with tempfile.TemporaryDirectory(prefix="stockflow-secret-test-") as directory:
            base = Path(directory)
            commands = {
                "kind": "#!/bin/sh\necho stockflow-lab\n",
                "docker": "#!/bin/sh\necho docker-reached >> \"$TEST_COMMAND_LOG\"\nexit 72\n",
                "kubectl": r"""#!/usr/bin/env python3
import json, os, re, sys
args = sys.argv[1:]
with open(os.environ['TEST_COMMAND_LOG'], 'a') as log:
    log.write(json.dumps(args) + '\n')
if 'get' in args and 'secret' in args:
    sys.exit(0 if args[-1] in json.loads(os.environ['TEST_AVAILABLE_SECRETS']) else 1)
if '--dry-run=client' in args and '-f' in args:
    data = sys.stdin.read()
    field = 'secretKeyRef' if 'secretKeyRef' in args[-1] else 'secretRef'
    match = re.search(field + r':\s*\n\s*name:\s*"([^"\n]+)"', data)
    if not match:
        sys.exit(1)
    print(match.group(1), end='')
""",
            }
            for name, content in commands.items():
                path = base / name
                path.write_text(content)
                path.chmod(0o755)
            log = base / "commands.log"
            env = dict(os.environ, PATH=str(base) + os.pathsep + os.environ["PATH"],
                       KUBECONFIG=str(base / "kubeconfig"), STOCKFLOW_KIND_CLUSTER="stockflow-lab",
                       STOCKFLOW_NAMESPACE="isolated-test", TEST_COMMAND_LOG=str(log),
                       TEST_AVAILABLE_SECRETS=json.dumps(available))
            result = subprocess.run([
                "bash", str(ROOT / "scripts/k8s-up.sh"),
                "--set", "existingSecret=external-db",
                "--set", "grafana.existingSecret=external-grafana",
            ], env=env, capture_output=True, text=True, timeout=30)
            return result, log.read_text() if log.exists() else ""

    def test_alternate_secrets_allow_progress_without_default_secrets(self):
        result, log = self.run_script(["external-db", "external-grafana"])
        self.assertEqual(result.returncode, 72, result.stderr)
        self.assertIn('"get", "secret", "external-db"', log)
        self.assertIn('"get", "secret", "external-grafana"', log)
        self.assertIn("docker-reached", log)

    def test_missing_effective_secret_stops_before_build(self):
        result, log = self.run_script(["external-db", "stockflow-secrets", "grafana-secret"])
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn('"get", "secret", "external-grafana"', log)
        self.assertNotIn("docker-reached", log)


if __name__ == "__main__":
    unittest.main()
