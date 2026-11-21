#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Runs the `protocol` cases (conformance/1/protocol, FORMAT 5.12) against a Data Protocol host with the test adapter of PROTOCOL 15:
for each case it loads the fixture (POST {base}/_test/fixture with {name, schema, rows}), sends each step's request, reads the minimal
SSE stream of /changes until it is idle (the step's body is the array of its frames), and compares the status exactly and the body as a
subset (FORMAT 7). Writes one results line per case and a summary, as rachana_conformance.py does.

Usage: python3 tools/conformance/protocol_check.py --reference          (starts java/rachana-data's ReferenceServer; build it first:
                                                                          cd java && ./mvnw clean package -DskipTests)
       python3 tools/conformance/protocol_check.py --base http://127.0.0.1:8080/rdp/v1   (a host of your own)"""
import argparse, fnmatch, http.client, json, os, socket, subprocess, sys, time, urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1].parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
from rachana_conformance import load_files, merged, canon  # noqa: E402  (the same case reader)


def subset(exp, act, where, why):
    """FORMAT 7 subset: an object's keys present and matching; an array of the same length, elements in order; a scalar exactly."""
    if isinstance(exp, dict):
        if not isinstance(act, dict):
            why.append(f"{where}: expected an object, actual {canon(act)}")
            return False
        return all(k in act and subset(v, act[k], f"{where}/{k}", why) or (k not in act and why.append(f"{where}/{k}: missing") and False)
                   for k, v in exp.items())
    if isinstance(exp, list):
        if not isinstance(act, list) or len(act) != len(exp):
            why.append(f"{where}: expected {len(exp)} item(s), actual {canon(act)[:200]}")
            return False
        return all(subset(e, a, f"{where}/{i}", why) for i, (e, a) in enumerate(zip(exp, act)))
    if canon(exp) != canon(act):
        why.append(f"{where}: expected {canon(exp)}, actual {canon(act)}")
        return False
    return True


class Host:
    def __init__(self, base):
        u = urllib.parse.urlsplit(base)
        self.host, self.port, self.prefix = u.hostname, u.port or 80, u.path.rstrip("/")

    def request(self, method, path, query=None, body=None, headers=None, idle=0.4):
        c = http.client.HTTPConnection(self.host, self.port, timeout=10)
        url = self.prefix + path + ("?" + urllib.parse.urlencode(query) if query else "")
        data = None if body is None else json.dumps(body).encode()
        h = {"Accept": "application/json", **(headers or {})}
        if data is not None:
            h["Content-Type"] = "application/json"
        c.request(method, url, body=data, headers=h)
        r = c.getresponse()
        if r.status == 200 and (r.getheader("Content-Type") or "").startswith("text/event-stream"):
            c.sock.settimeout(idle)
            frames, buf = [], b""
            try:
                while True:
                    chunk = r.fp.read1(65536) if hasattr(r.fp, "read1") else r.read(1)
                    if not chunk:
                        break
                    buf += chunk
                    while b"\n\n" in buf:
                        ev, buf = buf.split(b"\n\n", 1)
                        data_lines = [ln[5:].lstrip() for ln in ev.decode().split("\n") if ln.startswith("data:")]
                        if data_lines:
                            frames.append(json.loads("\n".join(data_lines)))
            except (socket.timeout, TimeoutError):
                pass
            c.close()
            return r.status, frames
        raw = r.read()
        c.close()
        return r.status, (json.loads(raw) if raw else None)


def fill(v, captured):
    if isinstance(v, str):
        for k, x in captured.items():
            v = v.replace("${" + k + "}", str(x))
        return v
    if isinstance(v, dict):
        return {k: fill(x, captured) for k, x in v.items()}
    if isinstance(v, list):
        return [fill(x, captured) for x in v]
    return v


def pointer(doc, ptr):
    cur = doc
    for raw in ptr.split("/")[1:]:
        key = raw.replace("~1", "/").replace("~0", "~")
        cur = cur[int(key)] if isinstance(cur, list) else cur[key]
    return cur


def run_case(host, c):
    why, actual = [], []
    f = c.get("fixture") or {}
    st, body = host.request("POST", "/_test/fixture", body={"name": f.get("name"), "schema": f.get("schema"), "rows": f.get("rows", [])})
    if st not in (200, 204):
        return "error", [f"fixture not loaded: {st} {body}"], actual
    captured = {}
    for i, step in enumerate(c["steps"]):
        rq = fill(step["request"], captured)
        st, body = host.request(rq.get("method", "GET"), rq.get("path", "/"), rq.get("query"), rq.get("body"), rq.get("headers"))
        actual.append({"status": st, "body": body})
        exp = step.get("expect") or {}
        if "status" in exp and exp["status"] != st:
            why.append(f"steps[{i}].status: expected {exp['status']}, actual {st} {canon(body)[:200]}")
            return "fail", why, actual
        if "body" in exp and not subset(exp["body"], body, f"steps[{i}].body", why):
            return "fail", why, actual
        for name, ptr in (step.get("capture") or {}).items():
            try:
                captured[name] = pointer(body, ptr)
            except (KeyError, IndexError, TypeError):
                why.append(f"steps[{i}].capture {name}: nothing at {ptr}")
                return "fail", why, actual
    return "pass", why, actual


def start_reference():
    target = ROOT / "java" / "rachana-data" / "target"
    cp = target / "runtime-classpath.txt"
    jars = sorted(str(j) for j in target.glob("rachana-data-*.jar") if not j.name.endswith(("-sources.jar", "-tests.jar")))
    if not cp.is_file() or not jars:
        sys.exit(f"{cp} is missing: build first (cd java && ./mvnw clean package -DskipTests)")
    mp = os.pathsep.join(jars + [cp.read_text().strip()])
    p = subprocess.Popen(["java", "-p", mp, "-m", "com.ash.rachana.data/com.ash.rachana.data.ReferenceServer", "--port", "0"],
                         stdout=subprocess.PIPE, text=True)
    line = p.stdout.readline()
    if "listening on" not in line:
        p.kill()
        sys.exit(f"the reference server did not start: {line!r}")
    return p, line.split("listening on", 1)[1].strip()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--base")
    ap.add_argument("--reference", action="store_true")
    ap.add_argument("--cases", default=str(ROOT / "conformance" / "1"))
    ap.add_argument("--only")
    ap.add_argument("--out", default=str(ROOT / "results" / "protocol.jsonl"))
    a = ap.parse_args(argv)
    proc = None
    if a.reference:
        proc, a.base = start_reference()
    if not a.base:
        ap.error("give --base or --reference")
    host = Host(a.base)
    only = [g for g in (a.only or "").split(",") if g]
    counts, failures, lines = {"pass": 0, "fail": 0, "error": 0}, [], []
    t0 = time.time()
    try:
        for p, suite, doc in load_files(Path(a.cases)):
            for c in doc["cases"]:
                c = merged(doc, c)
                cid = f"{suite}#{c['id']}"
                if c["kind"] != "protocol" or (only and not any(fnmatch.fnmatchcase(cid, g) for g in only)):
                    continue
                try:
                    status, why, actual = run_case(host, c)
                except (OSError, ValueError) as e:
                    status, why, actual = "error", [f"{type(e).__name__}: {e}"], None
                counts[status] += 1
                lines.append(canon({"case": cid, "kind": "protocol", "status": status, "actual": {"steps": actual}}))
                if status != "pass":
                    failures.append({"case": cid, "status": status, "verified": c["verified"], "detail": "; ".join(why)[:300]})
                    print(f"{status}: {cid}: {'; '.join(why)[:300]}", file=sys.stderr)
    finally:
        if proc:
            proc.kill()
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join(ln + "\n" for ln in lines), encoding="utf-8")
    verified = sum(1 for f in failures if f["verified"])
    summ = {"host": a.base, "cases": sum(counts.values()), **counts, "verifiedFailing": verified, "failures": failures,
            "millis": int((time.time() - t0) * 1000)}
    out.with_suffix(".summary.json").write_text(json.dumps(summ, indent=1) + "\n", encoding="utf-8")
    print(f"protocol: {summ['cases']} cases: pass {counts['pass']}, fail {counts['fail']}, error {counts['error']} (verified failing {verified})")
    return 1 if verified or counts["error"] else 0


if __name__ == "__main__":
    sys.exit(main())
