"""Integration checks for the Chart's local/tag/digest image selection."""
from pathlib import Path
import shutil
import subprocess
import unittest
import yaml


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which("helm"), "Helm required")
class HelmImagesTest(unittest.TestCase):
    def render(self, *overrides):
        command = ["helm", "template", "stockflow", str(ROOT / "helm/stockflow"), "-n", "test-images"]
        for value in overrides:
            command += ["--set", value]
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
        return result

    def workloads(self, result):
        self.assertEqual(result.returncode, 0, result.stderr)
        return {r["metadata"]["name"]: r["spec"]["template"]["spec"]
                for r in yaml.safe_load_all(result.stdout) if r and r["kind"] in ["Deployment", "StatefulSet"]}

    def test_defaults_keep_kind_images_and_omit_registry_credentials(self):
        workloads = self.workloads(self.render())
        for component in ["backend", "frontend"]:
            self.assertEqual(workloads[component]["containers"][0]["image"], f"stockflow-{component}:stage5-v1")
        self.assertTrue(all("imagePullSecrets" not in spec for spec in workloads.values()))

    def test_ghcr_repository_and_human_tag_are_rendered(self):
        workloads = self.workloads(self.render(
            "backend.image.repository=ghcr.io/test-owner/stockflow-backend", "backend.image.tag=1.2.3",
            "frontend.image.repository=ghcr.io/test-owner/stockflow-frontend", "frontend.image.tag=sha-" + "a" * 40))
        self.assertEqual(workloads["backend"]["containers"][0]["image"], "ghcr.io/test-owner/stockflow-backend:1.2.3")
        self.assertEqual(workloads["frontend"]["containers"][0]["image"], "ghcr.io/test-owner/stockflow-frontend:sha-" + "a" * 40)

    def test_digest_wins_and_existing_pull_secret_is_a_reference(self):
        digest = "sha256:" + "b" * 64
        workloads = self.workloads(self.render(
            "backend.image.repository=ghcr.io/test-owner/stockflow-backend", "backend.image.digest=" + digest,
            "frontend.image.repository=ghcr.io/test-owner/stockflow-frontend", "frontend.image.digest=" + digest,
            "global.imagePullSecrets[0].name=external-ghcr"))
        for component in ["backend", "frontend"]:
            self.assertEqual(workloads[component]["containers"][0]["image"], f"ghcr.io/test-owner/stockflow-{component}@{digest}")
        self.assertTrue(all(spec["imagePullSecrets"] == [{"name": "external-ghcr"}] for spec in workloads.values()))

    def test_invalid_digest_is_rejected_for_each_component(self):
        for component in ["backend", "frontend"]:
            with self.subTest(component=component):
                self.assertNotEqual(self.render(component + ".image.digest=sha256:invalid").returncode, 0)


if __name__ == "__main__":
    unittest.main()
