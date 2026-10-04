#!/usr/bin/env python3
"""Record/verify OCI artifacts and publish the exact tested index (no rebuild)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile


def inspect_archive(path):
    with tarfile.open(path) as archive:
        def read_json(name):
            return json.load(archive.extractfile(name))

        def blob(descriptor):
            digest = descriptor["digest"]
            if not re.fullmatch(r"sha256:[a-f0-9]{64}", digest):
                raise ValueError("Invalid OCI digest")
            name = "blobs/sha256/" + digest.split(":")[1]
            with archive.extractfile(name) as stream:
                if "sha256:" + hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                    raise ValueError("OCI blob digest mismatch")
            return name

        root = read_json("index.json")["manifests"]
        # Buildx emits one reference per tag, all pointing at the same index.
        if not root or len({entry["digest"] for entry in root}) != 1:
            raise ValueError("Expected references to a single OCI image index")
        for entry in root:
            blob(entry)
        index = read_json(blob(root[0]))
        attestations = {}
        runnable = []
        for descriptor in index["manifests"]:
            manifest = read_json(blob(descriptor))
            blob(manifest["config"])
            for layer in manifest["layers"]:
                name = blob(layer)
                if layer["mediaType"] == "application/vnd.in-toto+json":
                    statement = read_json(name)
                    kind = statement["predicateType"]
                    attestations[kind] = statement
            if descriptor.get("platform", {}).get("os") != "unknown":
                runnable.append(descriptor)
        if len(runnable) != 1 or runnable[0]["platform"]["architecture"] != "amd64" or runnable[0]["platform"]["os"] != "linux":
            raise ValueError("Expected exactly one linux/amd64 image")
        for kind in ["https://spdx.dev/Document", "https://slsa.dev/provenance/v1"]:
            if kind not in attestations:
                raise ValueError("Required SBOM/provenance attestation missing")
            if not any(s["digest"].get("sha256") == runnable[0]["digest"].split(":")[1]
                       for s in attestations[kind]["subject"]):
                raise ValueError("Attestation subject does not match runnable manifest")
        return root[0]["digest"], attestations


def command(*args):
    return subprocess.check_output(args, text=True, timeout=300).strip()


def verify(directory):
    record = json.loads((directory / "image.json").read_text())
    digest, _ = inspect_archive(directory / "image.tar")
    if digest != record["digest"]:
        raise ValueError("Archive no longer matches recorded digest")
    if not re.fullmatch(r"ghcr\.io/[a-z0-9_-]+/stockflow-(backend|frontend)", record["repository"]):
        raise ValueError("Unexpected publication repository")
    if not re.fullmatch(r"[a-f0-9]{40}", record["commit"]):
        raise ValueError("Invalid source commit")
    expected = {record["repository"] + ":sha-" + record["commit"]}
    if record.get("version"):
        if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", record["version"]):
            raise ValueError("Only stable X.Y.Z versions are published")
        expected.add(record["repository"] + ":" + record["version"])
    if set(record["tags"]) != expected:
        raise ValueError("Tags do not follow SHA/stable-version policy")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["record", "load", "publish"])
    parser.add_argument("directory", type=Path)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--digest")
    parser.add_argument("--repository")
    parser.add_argument("--commit")
    parser.add_argument("--version", default="")
    args = parser.parse_args()
    if args.mode == "record":
        digest, attestations = inspect_archive(args.directory / "image.tar")
        if digest != args.digest:
            raise ValueError("Buildx output digest differs from OCI index")
        metadata = json.loads(args.metadata.read_text())
        record = {"repository": args.repository, "tags": metadata["tags"], "digest": digest,
                  "commit": args.commit, "version": args.version,
                  "run_url": os.environ.get("GITHUB_SERVER_URL", "https://github.com") + "/" +
                      os.environ.get("GITHUB_REPOSITORY", "local") + "/actions/runs/" +
                      os.environ.get("GITHUB_RUN_ID", "local"), "platform": "linux/amd64",
                  "published": False}
        (args.directory / "image.json").write_text(json.dumps(record, indent=2) + "\n")
        for kind, name in [("https://spdx.dev/Document", "sbom.spdx.json"),
                           ("https://slsa.dev/provenance/v1", "provenance.json")]:
            (args.directory / name).write_text(json.dumps(attestations[kind]["predicate"] if name == "sbom.spdx.json" else attestations[kind], indent=2) + "\n")
        verify(args.directory)
    else:
        record = verify(args.directory)
        command("docker", "load", "--input", str(args.directory / "image.tar"))
        reference = record["tags"][0]
        info = json.loads(command("docker", "image", "inspect", reference))[0]
        if info.get("Descriptor", {}).get("digest") != record["digest"]:
            raise ValueError("Docker must preserve the OCI index; enable containerd image store")
        if args.mode == "publish":
            # GitHub gates the job; also reject local/manual or wrong-commit invocations.
            if os.environ.get("GITHUB_EVENT_NAME") != "push" or os.environ.get("GITHUB_SHA") != record["commit"]:
                raise ValueError("Publication requires the matching GitHub push event")
            ref = os.environ.get("GITHUB_REF", "")
            expected_ref = "refs/tags/v" + record["version"] if record["version"] else "refs/heads/main"
            if ref != expected_ref:
                raise ValueError("Publication ref does not match artifact policy")
            for tag in record["tags"]:
                command("docker", "push", tag)
                manifest = json.loads(command("docker", "buildx", "imagetools", "inspect", tag,
                                              "--format", "{{json .Manifest}}"))
                if manifest["digest"] != record["digest"]:
                    raise ValueError("Registry digest differs from tested OCI artifact")
            record["published"] = True
            (args.directory / "image.json").write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write("digest=" + record["digest"] + "\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write(f"### {record['repository']}\n\nPublished: {record['published']}\n\n"
                          f"Commit: `{record['commit']}`\n\nDigest: `{record['digest']}`\n\n")
            summary.write("\n".join("- `" + tag + "`" for tag in record["tags"]) + "\n")


if __name__ == "__main__":
    main()
