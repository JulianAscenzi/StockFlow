#!/usr/bin/env python3
"""Smoke-test two already-built images on an isolated, disposable Docker network."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import threading
import time
import uuid


def docker(*args, input=None, check=True):
    return subprocess.run(["docker", *args], input=input, text=True, capture_output=True,
                          timeout=60, check=check).stdout.strip()


def wait(check, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            return
        threading.Event().wait(1)
    raise TimeoutError("Image did not become ready within its startup budget")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", required=True)
    parser.add_argument("--frontend", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.output.resolve().is_relative_to(root):
        parser.error("Write runtime evidence outside the repository")
    args.output.mkdir(parents=True, exist_ok=True)
    prefix = "stockflow-smoke-" + uuid.uuid4().hex[:12]
    containers = [prefix + "-" + name for name in ("database", "backend", "frontend")]
    database, backend, frontend = containers
    password = secrets.token_hex(32)
    report = {}
    try:
        for name, image in [("backend", args.backend), ("frontend", args.frontend)]:
            info = json.loads(docker("image", "inspect", image))[0]
            if info["Config"].get("User") in (None, "", "0", "root"):
                raise ValueError(f"{name} image must run as non-root")
            report[name] = {"image": image, "id": info["Id"], "user": info["Config"]["User"]}
        docker("network", "create", "--internal", prefix)
        with tempfile.TemporaryDirectory(prefix=prefix) as private:
            env = Path(private) / "credentials.env"
            fd = os.open(env, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "w") as f:
                f.write(f"POSTGRES_PASSWORD={password}\nAPP_ADMIN_PASSWORD={password}\n"
                        f"APP_JWT_SECRET={secrets.token_hex(32)}\n")
            docker("run", "-d", "--pull=never", "--name", database, "--network", prefix,
                   "--network-alias", "database", "--env-file", str(env),
                   "-e", "POSTGRES_DB=stockflow", "-e", "POSTGRES_USER=stockflow",
                   "--tmpfs", "/var/lib/postgresql/data", "postgres:17-alpine")
            wait(lambda: subprocess.run(["docker", "exec", database, "pg_isready", "-U",
                 "stockflow", "-d", "stockflow"], capture_output=True, timeout=10).returncode == 0)
            docker("run", "-d", "--pull=never", "--name", backend, "--network", prefix,
                   "--network-alias", "backend", "--env-file", str(env),
                   "-e", "POSTGRES_DB=stockflow", "-e", "POSTGRES_USER=stockflow",
                   "-e", "POSTGRES_HOST=database", "-e", "SPRING_PROFILES_ACTIVE=prod,observability",
                   "-e", "APP_ADMIN_EMAIL=smoke@stockflow.test", "-e", "APP_AUTH_ENABLED=true",
                   args.backend)
            wait(lambda: subprocess.run(["docker", "exec", backend, "curl", "-fsS", "--max-time",
                 "5", "http://127.0.0.1:9091/actuator/health/readiness"],
                 capture_output=True, timeout=10).returncode == 0)
            health = json.loads(docker("exec", backend, "curl", "-fsS", "--max-time", "5",
                                      "http://127.0.0.1:9091/actuator/health/liveness"))
            if health["status"] != "UP":
                raise ValueError("Backend liveness is not UP")
            docker("run", "-d", "--pull=never", "--name", frontend, "--network", prefix,
                   "-e", "VITE_API_PROXY_TARGET=http://backend:8080", "-e", "VITE_API_BASE_URL=",
                   "-e", "VITE_AUTH_ENABLED=true", args.frontend)
            wait(lambda: subprocess.run(["docker", "exec", frontend, "node", "-e",
                 "fetch('http://127.0.0.1:5173').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"],
                 capture_output=True, timeout=10).returncode == 0)
            # Send credentials through stdin, never process arguments or public ports.
            script = """
const base='http://127.0.0.1:5173';
const html=await fetch(base).then(r=>r.text());
if (!html.includes('id="root"')) throw new Error('Missing React document');
const denied=await fetch(base+'/api/dashboard');
if (denied.status!==401) throw new Error('Expected protected dashboard');
const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify(CREDENTIALS)});
if (!login.ok) throw new Error('Proxy login failed');
const {accessToken}=await login.json();
if (!accessToken) throw new Error('Missing access token');
const dashboard=await fetch(base+'/api/dashboard',{headers:{Authorization:'Bearer '+accessToken}});
if (!dashboard.ok) throw new Error('Authenticated dashboard failed');
await dashboard.json();
console.log('Frontend HTTP and backend proxy/authenticated dashboard passed');
"""
            credentials = json.dumps({"email": "smoke@stockflow.test", "password": password})
            print(docker("exec", "-i", frontend, "node", "--input-type=module",
                         input="const CREDENTIALS=" + credentials + ";\n" + script))
        report["checks"] = ["postgres-ready", "backend-readiness", "backend-liveness",
                            "frontend-http", "unauthorized-401", "proxy-login", "dashboard-200"]
        (args.output / "smoke.json").write_text(json.dumps(report, indent=2) + "\n")
        print("PASS: backend/frontend images start and integrate; temporary resources removed")
    except Exception:
        for name in containers:
            (args.output / (name + ".log")).write_text(docker("logs", name, check=False))
        raise
    finally:
        for name in reversed(containers):
            docker("rm", "-f", "-v", name, check=False)
        docker("network", "rm", prefix, check=False)


if __name__ == "__main__":
    main()
