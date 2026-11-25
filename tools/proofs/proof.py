# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""M0 proofs P1 (JavaScript through TeaVM), P2 (determinism probes) and W (WebAssembly GC under wasmtime from Python): one
reproducible script. Results: the Proofs paragraph of docs/DESIGN.md.

Prepare your environment (once):
  JDK 21 at $JAVA_HOME (the builds and the reference); Maven 3.9; Node 22; Python 3.12+ with a virtual environment holding
  tools/proofs/requirements.txt; Playwright's browsers installed for that Playwright version; a Drishti checkout whose
  compiler jars are built (compute/drishti-rachana, kernel/drishti-api, kernel/drishti-common target/*.jar) at --commit.

Steps (each reads what the one before wrote; `all` runs them in order; one heavy job at a time):
  source      the compiler copy: `git archive` of Drishti at --commit (read only), then cuts.py
  reference   compile the harness against Drishti's own jars; the prep step (cases, probes, bench inputs to JSON)
  jvm         the cut copy on the JVM: conformance + probes; FAITHFUL when byte-equal to the reference
  js          TeaVM builds: the full cut (FULL and ADVANCED), the runtime-shaped entry, a classic-script build
  node        conformance + probes + benches in Node
  browsers    conformance + probes in Chromium, Firefox, WebKit (strict CSP), T7 page shapes, benches
  wasm        the WebAssembly GC build (patched class library, boundary patch), run under wasmtime from Python
  pyjs        fallback C measured: the JavaScript bundle inside Python (V8 and QuickJS wheels)
  report      the difference tables (stdout)

Usage: python tools/proofs/proof.py <step|all> --drishti <path> [--commit c6bc1d1bc] [--python <venv python>]
"""
import argparse
import json
import os
import pathlib
import shutil
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent
WORK, OUT = HERE / "work", HERE / "out"
RUN = HERE / "runners"
COPY = ["compute/drishti-rachana/src/main/java/com/ash/drishti/rachana/" + p for p in ("el", "format", "model", "parse")] + \
       ["compute/drishti-rachana/src/main/java/com/ash/drishti/rachana/" + f + ".java"
        for f in ("SutraText", "SutraWiring", "SutraExpressions", "SutraProblem", "SutraException")] + \
       ["kernel/drishti-api/src/main/java/com/ash/drishti/api/" + f + ".java"
        for f in ("DataNode", "NodeType", "PathWalker", "ListValues", "IdText", "error/ErrorCode", "error/DrishtiException")] + \
       ["kernel/drishti-common/src/main/java/com/ash/drishti/common/" + f + ".java" for f in ("SafeRegex", "BusinessCalendar")]
JARS = ["compute/drishti-rachana/target/drishti-rachana-*.jar", "kernel/drishti-api/target/drishti-api-*.jar",
        "kernel/drishti-common/target/drishti-common-*.jar"]
TEAVM = "0.16.0"


def sh(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd), flush=True)
    return subprocess.run([str(c) for c in cmd], check=True, **kw)


def java(tool="java"):
    home = os.environ.get("JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64")
    return str(pathlib.Path(home) / "bin" / tool)


def mvn(*props, pom=HERE / "pom.xml"):
    sh(["mvn", "-q", "-B", "-f", pom, "clean", "process-classes", *[f"-D{p}" for p in props]], stdout=subprocess.DEVNULL)


def step_source(a):
    if WORK.exists():
        shutil.rmtree(WORK)
    (WORK / "drishti").mkdir(parents=True)
    tar = subprocess.run(["git", "-C", a.drishti, "archive", a.commit, *COPY], check=True, capture_output=True).stdout
    subprocess.run(["tar", "-x", "-C", WORK / "drishti"], input=tar, check=True)
    src = WORK / "src"
    for root in ("compute/drishti-rachana/src/main/java", "kernel/drishti-api/src/main/java", "kernel/drishti-common/src/main/java"):
        shutil.copytree(WORK / "drishti" / root, src, dirs_exist_ok=True)
    shutil.copytree(src, WORK / "pristine")
    (WORK / "SOURCE.txt").write_text(f"Drishti {a.commit}\n" + "\n".join(COPY) + "\n", encoding="utf-8")
    sh([sys.executable, HERE / "cuts.py", src, HERE / "java" / "cut"])


def ref_classpath(a):
    cp = OUT / "reference-classpath.txt"
    if not cp.exists():
        OUT.mkdir(exist_ok=True)
        sh(["mvn", "-q", "-B", "-f", HERE / "reference" / "pom.xml", "dependency:build-classpath", f"-Dmdep.outputFile={cp}"])
    jars = [str(next(pathlib.Path(a.drishti).glob(j))) for j in JARS]
    return ":".join(jars + [cp.read_text().strip()])


def step_reference(a):
    cls = OUT / "ref-classes"
    shutil.rmtree(cls, ignore_errors=True)
    cp = ref_classpath(a)
    h, s = HERE / "java" / "harness" / "com/ash/proof", HERE / "java" / "stubs" / "com/ash/proof"
    sh([java("javac"), "-nowarn", "-d", cls, "-cp", cp, *[h / f for f in ("Canon.java", "Harness.java", "JvmMain.java", "Bench.java")],
        s / "Json.java", s / "Values.java", HERE / "java/reference/com/ash/proof/ref/Prep.java"])
    run = [java(), "-cp", f"{cls}:{cp}"]
    sh([sys.executable, RUN / "gen_bench.py", OUT / "bench"])
    sh([sys.executable, RUN / "gen_bench.py", OUT / "bench50", "50000"])
    (OUT / "probes-in").mkdir(parents=True, exist_ok=True)
    sh([sys.executable, RUN / "probes.py", "draft", OUT / "probes-in" / "determinism.yaml"])
    sh(run + ["com.ash.proof.ref.Prep", REPO / "conformance" / "1", OUT / "prep"])
    sh(run + ["com.ash.proof.ref.Prep", OUT / "probes-in", OUT / "prep-probes"])
    sh(run + ["com.ash.proof.ref.Prep", OUT / "bench" / "cases", OUT / "bench" / "prep"])
    (WORK / "gen" / "com/ash/proof").mkdir(parents=True, exist_ok=True)
    sh([sys.executable, RUN / "gen_resources.py", OUT / "prep" / "resources", WORK / "gen" / "com/ash/proof/Resources.java"])
    for name, cases in (("conformance", OUT / "prep" / "cases.jsonl"), ("probes", OUT / "prep-probes" / "cases.jsonl")):
        (OUT / "results" / name).mkdir(parents=True, exist_ok=True)
        sh(run + ["com.ash.proof.JvmMain", cases, OUT / "results" / name / "jvm-drishti.jsonl", "--yaml"])
    # conformance/1/el/determinism.yaml: the probes with expect = the reference's actual result
    sh([sys.executable, RUN / "probes.py", "final", OUT / "determinism.yaml", OUT / "results" / "probes" / "jvm-drishti.jsonl"])


def step_jvm(a):
    cls, strict = OUT / "jvm-classes", OUT / "jvm-strict-classes"
    for d in (cls, strict):
        shutil.rmtree(d, ignore_errors=True)
    srcs = [p for d in (WORK / "src", WORK / "gen", HERE / "java/stubs") for p in d.rglob("*.java")]
    srcs += [HERE / "java/harness/com/ash/proof" / f for f in ("Canon.java", "Harness.java", "JvmMain.java", "Bench.java", "BenchMain.java")]
    sh([java("javac"), "-nowarn", "--release", "21", "-d", cls, *srcs])
    # the CoreMath question on the JVM: the same copy with java.lang.Math's transcendental functions as StrictMath (fdlibm)
    sdir = OUT / "strict-src"
    shutil.rmtree(sdir, ignore_errors=True)
    shutil.copytree(WORK / "src", sdir)
    import re
    for p in sdir.rglob("*.java"):
        t = p.read_text(encoding="utf-8")
        t2 = re.sub(r"(?<![A-Za-z.])Math\.(log|exp|pow|log1p|expm1|log10|tanh|sin|atan2)\(", r"StrictMath.\1(", t)
        if t2 != t:
            p.write_text(t2, encoding="utf-8")
    ssrcs = [p for d in (sdir, WORK / "gen", HERE / "java/stubs") for p in d.rglob("*.java")] + srcs[-5:]
    sh([java("javac"), "-nowarn", "--release", "21", "-d", strict, *ssrcs])
    j25 = os.environ.get("JAVA25_HOME", "/usr/lib/jvm/java-25-openjdk-amd64")
    for name, cases in (("conformance", OUT / "prep" / "cases.jsonl"), ("probes", OUT / "prep-probes" / "cases.jsonl")):
        res = OUT / "results" / name
        sh([java(), "-cp", cls, "com.ash.proof.JvmMain", cases, res / "jvm.jsonl"])
        sh([java(), "-cp", strict, "com.ash.proof.JvmMain", cases, res / "jvm-strictmath.jsonl"])
        sh([java(), "-Duser.language=de", "-Duser.country=DE", "-Duser.timezone=Asia/Kolkata", "-cp", cls, "com.ash.proof.JvmMain", cases, res / "jvm-de.jsonl"])
        sh([java(), "-Duser.language=tr", "-Duser.country=TR", "-cp", cls, "com.ash.proof.JvmMain", cases, res / "jvm-tr.jsonl"])
        if pathlib.Path(j25).exists():
            sh([pathlib.Path(j25) / "bin/java", "-cp", cls, "com.ash.proof.JvmMain", cases, res / "jvm25.jsonl"])
        same = (res / "jvm.jsonl").read_bytes() == (res / "jvm-drishti.jsonl").read_bytes()
        print(f"{name}: the cut copy on the JVM is {'FAITHFUL (byte-equal to Drishti)' if same else 'NOT FAITHFUL'}")
        if not same:
            raise SystemExit(1)
    with open(OUT / "bench-jvm.json", "w") as f:
        sh([java(), "-Xmx2g", "-cp", cls, "com.ash.proof.BenchMain", OUT / "bench", OUT / "bench50"], stdout=f)


def step_js(a):
    for opt in ("FULL", "ADVANCED"):
        mvn(f"proof.opt={opt}", f"proof.out={OUT / ('js-' + opt)}")
    mvn("proof.opt=FULL", "proof.minify=false", f"proof.out={OUT / 'js-plain'}")
    for opt in ("FULL", "ADVANCED"):
        mvn("proof.entry=rt", "proof.mainClass=JsRuntime", f"proof.opt={opt}", f"proof.out={OUT / ('js-rt-' + opt)}")
    mvn("proof.entry=rt", "proof.mainClass=JsRuntime", "proof.minify=false", f"proof.out={OUT / 'js-rt-plain'}")
    mvn("proof.module=UMD", f"proof.out={OUT / 'js-umd'}")
    for i in (1, 2):   # T9: two clean builds from the same classes
        mvn(f"proof.out={OUT / ('js-t9-' + str(i))}")


def node(*args):
    sh(["node", *args])


def step_node(a):
    b = OUT / "js-FULL" / "rachana-proof.js"
    for name, cases in (("conformance", OUT / "prep" / "cases.jsonl"), ("probes", OUT / "prep-probes" / "cases.jsonl")):
        node(RUN / "run-node.mjs", b, cases, OUT / "results" / name / "node.jsonl")
        node(RUN / "run-node.mjs", OUT / "js-t9-1/rachana-proof.js", cases, OUT / "results" / name / "node-t9-1.jsonl")
        node(RUN / "run-node.mjs", OUT / "js-t9-2/rachana-proof.js", cases, OUT / "results" / name / "node-t9-2.jsonl")
    for opt in ("FULL", "ADVANCED"):
        with open(OUT / f"bench-node-{opt}.json", "w") as f:
            sh(["node", "--expose-gc", RUN / "bench-node.mjs", OUT / f"js-{opt}/rachana-proof.js", OUT / "bench", OUT / "bench50"], stdout=f)


def step_browsers(a):
    b = OUT / "js-FULL" / "rachana-proof.js"
    for name, cases in (("conformance", OUT / "prep" / "cases.jsonl"), ("probes", OUT / "prep-probes" / "cases.jsonl")):
        sh([a.python, RUN / "run_browsers.py", b, cases, OUT / "results" / name / "browsers"])
    sh([a.python, RUN / "t7_pages.py", OUT / "js-umd/rachana-proof.js", b, OUT / "prep/cases.jsonl", OUT / "results/conformance/browsers", OUT / "t7"])
    sh([a.python, RUN / "run_browser_bench.py", b, OUT / "bench", OUT / "bench50", OUT / "bench-browsers.json"])


def step_wasm(a):
    jar = OUT / f"teavm-classlib-{TEAVM}-proofpatch.jar"
    m2 = pathlib.Path.home() / ".m2/repository/org/teavm/teavm-classlib" / TEAVM
    sources = m2 / f"teavm-classlib-{TEAVM}-sources.jar"
    if not sources.exists():
        sh(["mvn", "-q", "-B", "dependency:get", f"-Dartifact=org.teavm:teavm-classlib:{TEAVM}:jar:sources"])
    # the one TThrowable change (DESIGN.md (Proofs), W1): fillInStackTrace no longer hands the Java object to the JavaScript host
    pdir = OUT / "classlib-patch"
    shutil.rmtree(pdir, ignore_errors=True)
    (pdir / "src").mkdir(parents=True)
    import zipfile
    t = zipfile.ZipFile(sources).read("org/teavm/classlib/java/lang/TThrowable.java").decode("utf-8")
    old = "            decorateException(this);\n"
    if t.count(old) != 1:
        raise SystemExit("TThrowable: the decorateException call is not where the proof expects it")
    f = pdir / "src/org/teavm/classlib/java/lang/TThrowable.java"
    f.parent.mkdir(parents=True)
    f.write_text(t.replace(old, "            // PROOF PATCH (W1): no call to the JavaScript host with a Java object\n"), encoding="utf-8")
    cp = ":".join(str(p) for p in pathlib.Path.home().glob(f".m2/repository/org/teavm/teavm-*/{TEAVM}/teavm-*-{TEAVM}.jar"))
    sh([java("javac"), "-nowarn", "-proc:none", "-d", pdir / "classes", "-cp", cp, f])
    shutil.copyfile(m2 / f"teavm-classlib-{TEAVM}.jar", jar)
    with zipfile.ZipFile(jar, "a") as z:
        for c in (pdir / "classes").rglob("TThrowable*.class"):
            z.write(c, str(c.relative_to(pdir / "classes")))
    mvn(f"proof.classlib={jar}", "proof.entry=wasm", "proof.mainClass=WasmMain", "proof.target=WEBASSEMBLY_GC",
        "proof.file=rachana-proof.wasm", f"proof.out={OUT / 'wasm'}")
    w = OUT / "wasm"
    with open(w / "boundary.txt", "w") as fo:
        sh([sys.executable, RUN / "wasm_boundary.py", w / "rachana-proof.wasm"], stdout=fo)
    sh([sys.executable, RUN / "wasm_patch.py", w / "rachana-proof.wasm", w / "rachana-proof.patched.wasm"])
    env = dict(os.environ, RACHANA_WASM_CACHE=str(w / "rachana-proof.cwasm"))
    (w / "rachana-proof.cwasm").unlink(missing_ok=True)
    for name, cases in (("conformance", OUT / "prep" / "cases.jsonl"), ("probes", OUT / "prep-probes" / "cases.jsonl")):
        with open(w / f"run-{name}.json", "w") as fo:   # the first run compiles and caches the module (W3 cold), the next reuse it
            sh(["/usr/bin/time", "-f", "%e s wall, %M KB max resident", a.python, RUN / "run_wasm.py", "cases", w / "rachana-proof.patched.wasm",
                cases, OUT / "results" / name / "wasm.jsonl"], stdout=fo, env=env)
    with open(w / "bench.json", "w") as fo:
        sh(["/usr/bin/time", "-f", "%e s wall, %M KB max resident", a.python, RUN / "run_wasm.py", "bench", w / "rachana-proof.patched.wasm", OUT / "bench"],
           stdout=fo, env=env)


def step_pyjs(a):
    for e in ("mini_racer", "quickjs"):
        with open(OUT / f"pyjs-{e}.json", "w") as fo:
            sh([a.python, RUN / "run_pyjs.py", e, OUT / "js-umd/rachana-proof.js", OUT / "prep/cases.jsonl",
                OUT / "results/conformance" / f"py-{e}.jsonl", OUT / "bench"], stdout=fo)


def classify(ref, other):
    J = [json.loads(l) for l in open(ref, encoding="utf-8")]
    O = [json.loads(l) for l in open(other, encoding="utf-8")]
    d = [(a["case"], a["actual"], b["actual"]) for a, b in zip(J, O) if a != b]
    crash = [c for c, x, y in d if "crash" in json.dumps(y)]
    num = [c for c, x, y in d if c not in crash and x.get("type") == "number" and y.get("type") == "number"]
    return len(J), d, crash, num


def step_report(a):
    for name in ("conformance", "probes"):
        res = OUT / "results" / name
        print(f"\n{name}: reference = jvm-drishti.jsonl")
        for f in sorted(res.rglob("*.jsonl")):
            if f.name == "jvm-drishti.jsonl":
                continue
            n, d, crash, num = classify(res / "jvm-drishti.jsonl", f)
            other = [c for c, _, _ in d if c not in crash and c not in num]
            print(f"  {str(f.relative_to(res)):32s} {n} cases, {len(d):3d} differ: crash {len(crash)}, number text {len(num)}, other {len(other)} {other[:6]}")


STEPS = {"source": step_source, "reference": step_reference, "jvm": step_jvm, "js": step_js, "node": step_node,
         "browsers": step_browsers, "wasm": step_wasm, "pyjs": step_pyjs, "report": step_report}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("step", choices=[*STEPS, "all"])
    ap.add_argument("--drishti", default=str(REPO.parent / "drishti"), help="a Drishti checkout (read only)")
    ap.add_argument("--commit", default="c6bc1d1bc", help="the Drishti commit the copy is taken from")
    ap.add_argument("--python", default=sys.executable, help="the Python with tools/proofs/requirements.txt")
    a = ap.parse_args()
    for s in (STEPS if a.step == "all" else [a.step]):
        print(f"== {s}", flush=True)
        STEPS[s](a)


main()
