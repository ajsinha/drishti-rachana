// Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
// P1/P2 Node runner: node run-node.mjs <bundle.js> <cases.jsonl> <results.jsonl>
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
const [bundle, cases, out] = process.argv.slice(2);
const t0 = performance.now();
const m = await import(pathToFileURL(bundle).href);
const t1 = performance.now();
const text = readFileSync(cases, 'utf8');
const res = m.runAll(text);
const t2 = performance.now();
writeFileSync(out, res, 'utf8');
console.error(`node ${process.version}: module ${(t1 - t0).toFixed(1)} ms, ${res.split('\n').length - 1} lines in ${(t2 - t1).toFixed(0)} ms`);
