"""Verify artifact integrity and attestation binding without contacting a registry."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("image_artifact", Path(__file__).parents[1] / "image-artifact.py")
artifact = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(artifact)


def fixture(path, *, wrong_subject=False, provenance=True, corrupt=False, multiple_tags=False):
    files = {}

    def blob(value):
        data = json.dumps(value).encode()
        digest = "sha256:" + hashlib.sha256(data).hexdigest()
        files["blobs/sha256/" + digest.split(":")[1]] = data
        return {"digest": digest, "size": len(data), "mediaType": "application/vnd.oci.image.manifest.v1+json"}

    config = blob({})
    layer = blob({"filesystem": "test"})
    image = blob({"config": config, "layers": [layer]})
    image["platform"] = {"os": "linux", "architecture": "amd64"}
    statements = []
    for kind in ["https://spdx.dev/Document"] + (["https://slsa.dev/provenance/v1"] if provenance else []):
        statement = blob({"predicateType": kind, "predicate": {}, "subject": [
            {"digest": {"sha256": "0" * 64 if wrong_subject else image["digest"].split(":")[1]}}]})
        statement["mediaType"] = "application/vnd.in-toto+json"
        statements.append(statement)
    attest = blob({"config": config, "layers": statements})
    attest["platform"] = {"os": "unknown", "architecture": "unknown"}
    index = blob({"manifests": [image, attest]})
    files["index.json"] = json.dumps({"manifests": [index, dict(index)] if multiple_tags else [index]}).encode()
    if corrupt:
        files["blobs/sha256/" + layer["digest"].split(":")[1]] = b"tampered"
    with tarfile.open(path, "w") as archive:
        for name, data in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            archive.addfile(info, io.BytesIO(data))
    return index["digest"]


class ImageArtifactTest(unittest.TestCase):
    def test_sha_and_version_references_share_one_index(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "image.tar"
            expected = fixture(path, multiple_tags=True)
            self.assertEqual(artifact.inspect_archive(path)[0], expected)

    def test_complete_archive_is_bound_to_exact_runnable_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "image.tar"
            expected = fixture(path)
            digest, attestations = artifact.inspect_archive(path)
            self.assertEqual(digest, expected)
            self.assertEqual(len(attestations), 2)

    def test_tampered_layer_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "image.tar"
            fixture(path, corrupt=True)
            with self.assertRaisesRegex(ValueError, "blob digest mismatch"):
                artifact.inspect_archive(path)

    def test_missing_provenance_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "image.tar"
            fixture(path, provenance=False)
            with self.assertRaisesRegex(ValueError, "attestation missing"):
                artifact.inspect_archive(path)

    def test_attestation_for_another_image_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "image.tar"
            fixture(path, wrong_subject=True)
            with self.assertRaisesRegex(ValueError, "subject does not match"):
                artifact.inspect_archive(path)

    def test_latest_or_prerelease_cannot_enter_publication_record(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            digest = fixture(base / "image.tar")
            repository = "ghcr.io/test-owner/stockflow-backend"
            record = {"repository": repository, "digest": digest, "commit": "a" * 40,
                      "version": "1.2.3", "tags": [repository + ":sha-" + "a" * 40, repository + ":1.2.3"]}
            (base / "image.json").write_text(json.dumps(record))
            artifact.verify(base)
            record["tags"].append(repository + ":latest")
            (base / "image.json").write_text(json.dumps(record))
            with self.assertRaisesRegex(ValueError, "Tags do not follow"):
                artifact.verify(base)
            record["version"] = "1.2.3-rc.1"
            (base / "image.json").write_text(json.dumps(record))
            with self.assertRaisesRegex(ValueError, "Only stable"):
                artifact.verify(base)


if __name__ == "__main__":
    unittest.main()
