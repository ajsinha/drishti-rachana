// Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
// T3 (start, parse + check), T4 (twenty expressions against a hand-written evaluator) and T5 (live change) on one JavaScript
// engine. Shared by bench-node.mjs and the browser page; `m` is the imported proof bundle.

import { compile } from './mini-el.mjs';

export const TWENTY = [
  ['@.amount', r => r.amount],
  ['@.amount * 2', r => r.amount * 2],
  ['@.price * @.qty - @.amount', r => r.price * r.qty - r.amount],
  ['@.amount > 1000000', r => r.amount > 1000000],
  ["@.ccy == 'USD'", r => r.ccy === 'USD'],
  ["@.ccy in ['USD', 'EUR', 'GBP']", r => r.ccy === 'USD' || r.ccy === 'EUR' || r.ccy === 'GBP'],
  ["@.amount > 0 && @.status != 'CANCELLED'", r => r.amount > 0 && r.status !== 'CANCELLED'],
  ["@.type == 'FX' ? @.amount : -@.amount", r => (r.type === 'FX' ? r.amount : -r.amount)],
  ["'Leg ' + #index", (r, i) => 'Leg ' + i],
  ['T:${@.book} / ${@.ccy}', r => `${r.book} / ${r.ccy}`],
  ['round(@.price, 2)', r => Math.sign(r.price) * Math.round(Math.abs(r.price) * 100) / 100],
  ['abs(@.amount) / 1e6', r => Math.abs(r.amount) / 1e6],
  ["nonblank(@.desk, 'n/a')", r => (r.desk == null || r.desk === '' ? 'n/a' : r.desk)],
  ['upper(left(@.book, 3))', r => r.book.slice(0, 3).toUpperCase()],
  ["@.trade_date >= '2026-01-01'", r => r.trade_date >= '2026-01-01'],
  ["days_between(@.trade_date, '2026-11-17')", r => (Date.UTC(2026, 10, 17) - Date.parse(r.trade_date)) / 86400000],
  // 17: sum() has no provider in the P1 cut (aggregates come from Drishti's pivot engine until A6), so its stand-in is reduce()
  //     and reduce() refuses addition by design, so the stand-in folds with greatest(): the same walk over the ten legs
  ["reduce($.legs, 0, greatest(@acc, @.notional))", (r, i, d) => { let s = 0; for (const l of d.legs) s = Math.max(s, l.notional); return s; }],
  ["size($.legs[?@.type == 'FLOAT'])", (r, i, d) => { let n = 0; for (const l of d.legs) if (l.type === 'FLOAT') n++; return n; }],
  ['map($.legs, @.notional / 1e6)', (r, i, d) => d.legs.map(l => l.notional / 1e6)],
  ["matches(@.book, '^BOOK-[0-9]+$')", r => /^BOOK-[0-9]+$/.test(r.book)],
];

const now = () => performance.now();
const median = a => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };
const pct = (a, p) => { const s = [...a].sort((x, y) => x - y); return s[Math.min(s.length - 1, Math.ceil(p * s.length) - 1)]; };

export function t3(m, typical, large) {
  const first = now(); const p0 = m.compileSutra(typical); const firstMs = now() - first;
  const runs = (doc) => { const t = []; for (let k = 0; k < 30; k++) { const a = now(); m.compileSutra(doc); t.push(now() - a); } return t; };
  for (let k = 0; k < 10; k++) { m.compileSutra(typical); m.compileSutra(large); }
  const ty = runs(typical), lg = runs(large);
  return { firstCompileMs: firstMs, problems: p0, typicalP50: median(ty), typicalP95: pct(ty, 0.95), largeP50: median(lg), largeP95: pct(lg, 0.95),
           largeProblems: m.compileSutra(large) };
}

export function t4(m, rowsJson, plain) {
  const n = m.load(rowsJson);
  const out = [];
  for (const [expr, fn] of TWENTY) {
    for (let k = 0; k < 3; k++) m.evalLoop(expr, 1);
    const tc = [];
    for (let k = 0; k < 10; k++) { const a = now(); m.evalLoop(expr, 1); tc.push(now() - a); }
    let sink = 0;
    const th = [];
    for (let k = 0; k < 13; k++) {
      const a = now();
      for (let i = 0; i < plain.rows.length; i++) { const v = fn(plain.rows[i], i, plain.record); sink += v == null ? 0 : 1; }
      if (k >= 3) th.push(now() - a);
    }
    const ev = compile(expr); const ti = [];
    for (let k = 0; k < 13; k++) {
      const a = now();
      for (let i = 0; i < plain.rows.length; i++) { const v = ev(plain.rows[i], i, plain.record); sink += v == null ? 0 : 1; }
      if (k >= 3) ti.push(now() - a);
    }
    const interpNs = median(ti) * 1e6 / n;
    const compiledNs = median(tc) * 1e6 / n, handNs = Math.max(median(th) * 1e6 / n, 0.5);
    out.push({ expr, compiledNs: +compiledNs.toFixed(1), interpNs: +interpNs.toFixed(1), ratioInterp: +(compiledNs / interpNs).toFixed(1),
               handNs: +(median(th) * 1e6 / n).toFixed(2), ratioHand: +(compiledNs / handNs).toFixed(1),
               sample: m.evalOne(expr, 3), interpSample: String(ev(plain.rows[3], 3, plain.record)), handSample: String(fn(plain.rows[3], 3, plain.record)), sink });
  }
  return out;
}

export function t5(m, rowsJson, expr) {
  m.load(rowsJson);
  const out = {};
  for (const [name, f] of [['withTableCopy', m.liveChange], ['rowsOnly', m.liveChangeRowsOnly]]) {
    for (let k = 0; k < 20; k++) f(expr, 100, k);
    const t = [];
    for (let k = 0; k < 50; k++) { const a = now(); f(expr, 100, k); t.push(now() - a); }
    out[name] = { p50ms: +median(t).toFixed(3), p95ms: +pct(t, 0.95).toFixed(3) };
  }
  return out;
}
