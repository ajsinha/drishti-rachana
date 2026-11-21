// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The conformance runtime program of the JavaScript build in Node (conformance/RUNNER.md section 5): case lines on standard
// input, results lines on standard output, every answer from the bundle's own harness (the JVM's code, compiled by TeaVM).
// Usage: node conformance.mjs [--bundle <rachana.tools.js>] [--runtime node] [--profiles core,drishti] <formats file>...
// Run by tools/conformance/rachana_conformance.py --node.
import { readFileSync } from "node:fs";
import { createInterface } from "node:readline";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, resolve } from "node:path";

const args = process.argv.slice(2);
let bundle = resolve(dirname(fileURLToPath(import.meta.url)), "../dist/rachana.tools.js");
let runtime = "node";
let profiles = "core";
const files = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--bundle") bundle = resolve(args[++i]);
  else if (args[i] === "--runtime") runtime = args[++i];
  else if (args[i] === "--profiles") profiles = args[++i];
  else files.push(args[i]);
}
const rachana = await import(pathToFileURL(bundle).href);
rachana.init(profiles, JSON.stringify(files.map((f) => readFileSync(f, "utf8"))));

// the runtime bundle has no harness entry: its facade answers each kind, and the line is put together here as the harness does
const BY_KIND = { "expr": "evaluate", "expr-error": "evaluateError", "format": "format", "problem": "check", "sutra": "view", "patch": "patch", "fire": "fire", "plan": "plan", "lib": "library" };
const hello = rachana.hello ? (r) => rachana.hello(r) : (r) => JSON.stringify({ runtime: r, runtimeVersion: `rachana-${rachana.version()}`,
  language: "1.0", profiles: profiles.split(","), kinds: Object.keys(BY_KIND) });
const answer = rachana.answer ? (line) => rachana.answer(line) : (line) => {
  const c = JSON.parse(line);
  const fn = BY_KIND[c.kind];
  if (!fn) return JSON.stringify({ case: c.case, error: `IllegalStateException: kind not implemented: ${c.kind}` });
  const id = c.case; delete c.case;
  const actual = rachana[fn](JSON.stringify(c));
  return actual.startsWith('{"error":"') ? `{"case":${JSON.stringify(id)},${actual.slice(1)}` : `{"case":${JSON.stringify(id)},"actual":${actual}}`;
};
const out = [];
const flush = () => { if (out.length) { process.stdout.write(out.join("")); out.length = 0; } };
let greeted = false;
const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
for await (const line of rl) {
  if (!greeted) { greeted = true; out.push(hello(runtime) + "\n"); flush(); continue; }
  if (!line.trim()) continue;
  // one case's exception is that case's error, not the end of the run (G1 R1: a bundle that throws must still be counted)
  try { out.push(answer(line) + "\n"); } catch (x) {
    const id = JSON.parse(line).case;
    out.push(JSON.stringify({ case: id, error: `JavaScript exception: ${String(x && x.message || x)}` }) + "\n");
  }
  if (out.length >= 64) flush();
}
flush();
