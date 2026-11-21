// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
// Runs the Node tests: node tools/js/node_tests.mjs <pattern>...   from the repository root.
// A pattern is a path whose segments may be * (any folder name); the last segment names the folder whose *.test.mjs files run, e.g.
// js/packages/*/test. It exists so that no shell glob is needed (Windows, Node 20): the same command runs everywhere.
import { readdirSync, existsSync, statSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join } from "node:path";

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
process.exit(spawnSync(process.execPath, ["--test", ...files], { stdio: "inherit" }).status ?? 1);
