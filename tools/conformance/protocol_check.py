#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Runs the `protocol` cases (conformance/1/protocol, FORMAT 5.12) against a Data Protocol host with the test adapter of PROTOCOL 15:
for each case it loads the fixture (POST {base}/_test/fixture with {name, schema, rows}), sends each step's request, reads the minimal
SSE stream of /changes until it is idle (the step's body is the array of its frames), and compares the status exactly and the body as a
subset (FORMAT 7). Rows in the other forms are compared as the JSON page they stand for: an NDJSON answer (PROTOCOL 5.3) as its header
with `values` (the lines), an Arrow IPC answer (5.2) and a bridge `res.arrow` buffer decoded with pyarrow (the official Apache Arrow
library; run with `uv run --with pyarrow`, otherwise those steps are errors) by the rules of 3.1 and 3.2. Writes one results line per case
and a summary, as rachana_conformance.py does.

Usage: python3 tools/conformance/protocol_check.py --reference          (starts java/rachana-data's ReferenceServer; build it first:
                                                                          cd java && ./mvnw clean package -DskipTests)
       python3 tools/conformance/protocol_check.py --python           (starts the Python DataHost with the same test adapter; needs
                                                                          the python/ package's dependencies, which are the standard library's)
       python3 tools/conformance/protocol_check.py --base http://127.0.0.1:8080/rdp/v1   (a host of your own)"""
import argparse, base64, fnmatch, http.client, json, os, socket, subprocess, sys, time, urllib.parse
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
            if c.sock:
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
        kind = r.getheader("Content-Type") or ""
        if r.status == 200 and kind.startswith("application/x-ndjson"):
            return r.status, ndjson_page(raw.decode())
        if r.status == 200 and kind.startswith("application/vnd.apache.arrow.stream"):
            return r.status, arrow_page(raw)
        body = json.loads(raw) if raw else None
        if isinstance(body, dict) and body.get("t") == "res.arrow" and isinstance(body.get("buffer"), str):
            body["buffer"] = arrow_page(base64.b64decode(body["buffer"]))
        return r.status, body


def ndjson_page(text):
    """NDJSON (PROTOCOL 5.3) as the JSON page: the header's fields, `values` after `columns`."""
    lines = [json.loads(ln) for ln in text.split("\n") if ln.strip()]
    page = {}
    for k, v in (lines[0] if lines else {}).items():
        page[k] = v
        if k == "columns":
            page["values"] = lines[1:]
    page.setdefault("values", lines[1:])
    return page


def arrow_page(data):
    """An Arrow IPC stream (PROTOCOL 5.2) as the JSON page, by the cell rules of 3.1 (and 3.2 for <name>$masked columns)."""
    import datetime
    import decimal
    try:
        import pyarrow.ipc as ipc
    except ImportError as e:
        raise ValueError("Arrow answers need pyarrow: uv run --with pyarrow python3 tools/conformance/protocol_check.py") from e
    t = ipc.open_stream(data).read_all()
    meta = {k.decode(): v.decode() for k, v in (t.schema.metadata or {}).items()}
    fields = [f for f in t.schema if b"rachana.masked" not in (f.metadata or {})]
    masks = {f.metadata[b"rachana.masked"].decode(): t.column(f.name).to_pylist() for f in t.schema if b"rachana.masked" in (f.metadata or {})}

    def cell(f, x):
        rtype = (f.metadata or {}).get(b"rachana.type", b"").decode()
        if x is None:
            return None
        if isinstance(x, bool) or isinstance(x, float):
            return x
        if isinstance(x, int) or rtype == "integer":
            x = int(x)
            return x if abs(x) <= 2 ** 53 else str(x)
        if isinstance(x, decimal.Decimal):
            return format(x, "f")
        if isinstance(x, datetime.datetime):
            return x.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") + f"{x.microsecond // 1000:03d}Z"
        if isinstance(x, datetime.date):
            return x.isoformat()
        if isinstance(x, datetime.time):
            return x.strftime("%H:%M:%S") + (f".{x.microsecond // 1000:03d}" if x.microsecond else "")
        return json.loads(x) if rtype in ("list", "object") else x

    page = {"table": meta.get("rachana.table", "")}
    if "rachana.epoch" in meta:
        page["version"] = {"epoch": meta["rachana.epoch"], "seq": int(meta.get("rachana.seq", "0"))}
    cols = {f.name: t.column(f.name).to_pylist() for f in fields}
    page["columns"] = [f.name for f in fields]
    page["values"] = [[{"$masked": True} if masks.get(f.name, [False] * t.num_rows)[i] else cell(f, cols[f.name][i]) for f in fields]
                      for i in range(t.num_rows)]
    for k, conv in (("total", int), ("next", str), ("totals", json.loads), ("schema", json.loads)):
        if "rachana." + k in meta:
            page[k] = conv(meta["rachana." + k])
    return page


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
    p = subprocess.Popen(["java", "--add-opens=java.base/java.nio=org.apache.arrow.memory.core", "-p", mp, "-m", "com.ash.rachana.data/com.ash.rachana.data.ReferenceServer", "--port", "0"],
                         stdout=subprocess.PIPE, text=True)
    line = p.stdout.readline()
    if "listening on" not in line:
        p.kill()
        sys.exit(f"the reference server did not start: {line!r}")
    return p, line.split("listening on", 1)[1].strip()


# What the Python host does not offer (it answers JSON only, with no bridge subscriptions, filtered or conflated streams): its own
# declaration, not a failure. A host of your own can pass --skip too.
PYTHON_SKIPS = ("protocol/forms#*", "protocol/live#*")


def start_python():
    p = subprocess.Popen([sys.executable, "-m", "drishti_rachana.testhost", "--port", "0"], stdout=subprocess.PIPE, text=True,
                         env={**os.environ, "PYTHONPATH": str(ROOT / "python" / "src") + os.pathsep + os.environ.get("PYTHONPATH", "")})
    line = p.stdout.readline()
    if "listening on" not in line:
        p.kill()
        sys.exit(f"the Python host did not start: {line!r}")
    return p, line.split("listening on", 1)[1].strip()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--base")
    ap.add_argument("--reference", action="store_true")
    ap.add_argument("--python", action="store_true", help="start python/src/drishti_rachana/testhost.py (DataHost) and run the same cases")
    ap.add_argument("--cases", default=str(ROOT / "conformance" / "1"))
    ap.add_argument("--only")
    ap.add_argument("--skip", help="comma list of case globs not run (--python skips what the Python host does not offer: " + ", ".join(PYTHON_SKIPS) + ")")
    ap.add_argument("--out", default=str(ROOT / "results" / "protocol.jsonl"))
    a = ap.parse_args(argv)
    proc = None
    if a.reference:
        proc, a.base = start_reference()
    elif a.python:
        proc, a.base = start_python()
    if not a.base:
        ap.error("give --base, --reference or --python")
    host = Host(a.base)
    only = [g for g in (a.only or "").split(",") if g]
    skip = [g for g in (a.skip or "").split(",") if g] + (list(PYTHON_SKIPS) if a.python else [])
    counts, failures, lines = {"pass": 0, "fail": 0, "error": 0}, [], []
    t0 = time.time()
    try:
        for p, suite, doc in load_files(Path(a.cases)):
            for c in doc["cases"]:
                c = merged(doc, c)
                cid = f"{suite}#{c['id']}"
                if c["kind"] != "protocol" or (only and not any(fnmatch.fnmatchcase(cid, g) for g in only)):
                    continue
                if any(fnmatch.fnmatchcase(cid, g) for g in skip):
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
