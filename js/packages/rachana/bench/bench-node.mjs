// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// A15's re-measure of P1's T3 to T5 in Node over the measuring bundle (rachana.bench.js, tools/js/build_js.py --bench), with
// P1's twenty expressions and hand-written evaluators (tools/proofs/runners/bench.mjs).
// Usage: node bench-node.mjs <rachana.bench.js> <bench dir from tools/proofs/runners/gen_bench.py> <50,000-row bench dir>
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { t3, t4, t5 } from "../../../../tools/proofs/runners/bench.mjs";

const [bundle, dir, dir50] = process.argv.slice(2);
// the two Sutras of the bench case file: the literal block under each case's "sutra: |"
const sutras = {};
let id = null, block = null;
for (const line of readFileSync(`${dir}/cases/bench.yaml`, "utf8").split("\n")) {
  const m = line.match(/^  - id: (\w+)/);
  if (m) { id = m[1]; block = null; continue; }
  if (/^    sutra: \|/.test(line)) { block = []; sutras[id] = block; continue; }
  if (block && (line.startsWith("      ") || line === "")) block.push(line.slice(6));
  else if (block) block = null;
}
const text = (k) => sutras[k].join("\n");
const a = performance.now();
const m = await import(pathToFileURL(bundle).href);
const r = { runtime: `node ${process.version}`, moduleMs: +(performance.now() - a).toFixed(1) };
r.t3 = t3(m, text("typical"), text("large"));
r.t4 = t4(m, readFileSync(`${dir}/rows.json`, "utf8"), JSON.parse(readFileSync(`${dir}/rows.plain.json`, "utf8")));
r.t5 = t5(m, readFileSync(`${dir50}/rows.json`, "utf8"), "@.price * @.qty - @.amount");
console.log(JSON.stringify(r, null, 1));
