// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
// Runs the Node tests: node tools/js/node_tests.mjs <pattern>...   from the repository root.
// A pattern is a path whose segments may be * (any folder name); the last segment names the folder whose *.test.mjs files run, e.g.
// js/packages/*/test. It exists so that no shell glob is needed (Windows, Node 20): the same command runs everywhere.
//
// It fails, loudly, instead of passing quietly: when the runtime bundles are not built (js/packages/rachana/dist is git-ignored: the reactor builds
// them, java/rachana-js, or python3 tools/js/build_js.py) and when any test was SKIPPED (a skip is a missing prerequisite; the reactor has them all).
// The reactor's -Drachana.node.skip=true leaves the Node run out altogether.
import { readdirSync, existsSync, statSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

function folders(pattern) {
  let found = ["."];
  for (const seg of pattern.split("/").filter(Boolean)) {
    found = found.flatMap((base) => seg === "*"
      ? (existsSync(base) ? readdirSync(base).map((n) => join(base, n)).filter((p) => statSync(p).isDirectory()) : [])
      : [join(base, seg)]);
  }
  return found.filter((p) => existsSync(p) && statSync(p).isDirectory());
}

const files = process.argv.slice(2).flatMap(folders).flatMap((d) => readdirSync(d).filter((n) => n.endsWith(".test.mjs")).sort().map((n) => join(d, n)));
if (files.length === 0) { console.error("no *.test.mjs files found for: " + process.argv.slice(2).join(" ")); process.exit(2); }
const dist = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "js", "packages", "rachana", "dist");
const missing = ["rachana.runtime.js", "rachana.tools.js"].filter((b) => !existsSync(join(dist, b)));
if (missing.length) {
  console.error("node_tests: the runtime bundle is not built (" + missing.join(", ") + " missing in " + dist + "): its tests would be skipped, so the Node run fails.\n"
    + "  Build it: python3 tools/js/build_js.py, or the reactor (cd java && ./mvnw verify builds java/rachana-js first). Leave the Node tests out on purpose: -Drachana.node.skip=true.");
  process.exit(1);
}
const run = spawnSync(process.execPath, ["--test", "--test-reporter=tap", ...files], { encoding: "utf8", maxBuffer: 256 * 1024 * 1024 });
process.stdout.write(run.stdout || "");
process.stderr.write(run.stderr || "");
const skipped = Number(((run.stdout || "").match(/^# skipped (\d+)/m) || [0, 0])[1]);
if (run.status === 0 && skipped > 0) {
  console.error("node_tests: " + skipped + " test(s) were SKIPPED (a prerequisite is missing, see their reasons above: 'build java/rachana-... first'): the Node run fails. Build the reactor first (cd java && ./mvnw verify).");
  process.exit(1);
}
process.exit(run.status ?? 1);
