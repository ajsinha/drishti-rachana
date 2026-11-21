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

const out = [];
const flush = () => { if (out.length) { process.stdout.write(out.join("")); out.length = 0; } };
let greeted = false;
const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
for await (const line of rl) {
  if (!greeted) { greeted = true; out.push(rachana.hello(runtime) + "\n"); flush(); continue; }
  if (!line.trim()) continue;
  out.push(rachana.answer(line) + "\n");
  if (out.length >= 64) flush();
}
flush();
