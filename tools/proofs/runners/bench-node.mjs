// Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
// node --expose-gc bench-node.mjs <bundle.js> <bench dir> <bench50k dir>  →  JSON on stdout (T3, T4, T5 with heap deltas).
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { t3, t4, t5 } from './bench.mjs';

const [bundle, dir, dir50] = process.argv.slice(2);
const a = performance.now();
const m = await import(pathToFileURL(bundle).href);
const moduleMs = performance.now() - a;
const lines = readFileSync(`${dir}/prep/cases.jsonl`, 'utf8').trim().split('\n').map(JSON.parse);
const typical = lines.find(l => l.case.endsWith('#typical')).pnode, large = lines.find(l => l.case.endsWith('#large')).pnode;
const r = { runtime: `node ${process.version}`, moduleMs };
r.t3 = t3(m, typical, large);
r.t4 = t4(m, readFileSync(`${dir}/rows.json`, 'utf8'), JSON.parse(readFileSync(`${dir}/rows.plain.json`, 'utf8')));
const rows50 = readFileSync(`${dir50}/rows.json`, 'utf8');
r.t5 = t5(m, rows50, '@.price * @.qty - @.amount');
// heap: 100 changes of 100 rows each, with and without the table copy, on 50,000 and on 5,000 rows
if (globalThis.gc) {
  const heap = (rowsJson, f) => {
    m.load(rowsJson); for (let k = 0; k < 20; k++) f('@.price * @.qty - @.amount', 100, k);
    gc(); const before = process.memoryUsage().heapUsed; let peak = before;
    for (let k = 0; k < 100; k++) { f('@.price * @.qty - @.amount', 100, k); peak = Math.max(peak, process.memoryUsage().heapUsed); }
    return Math.round((peak - before) / 1024);
  };
  const rows5 = readFileSync(`${dir}/rows.json`, 'utf8');
  r.t5.heapKBPeakOver100Changes = { copy50k: heap(rows50, m.liveChange), rowsOnly50k: heap(rows50, m.liveChangeRowsOnly),
                                    copy10k: heap(rows5, m.liveChange), rowsOnly10k: heap(rows5, m.liveChangeRowsOnly) };
}
console.log(JSON.stringify(r, null, 1));
