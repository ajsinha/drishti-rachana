// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// Windowed subscriptions of a live table from JavaScript (PROTOCOL 16): the minimal SSE form against the live test adapter
// (java/rachana-live's LiveReferenceServer, built by ./mvnw package): subd first, the window's patches and totals, window() moving it;
// and the bridge's sub with a window, win and a window's gap.
// Run: node --test js/packages/rachana/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, delimiter } from 'node:path';
import { DataClient, liveTables } from '../src/index.js';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const TARGET = resolve(ROOT, 'java/rachana-live/target');
const ready = existsSync(resolve(TARGET, 'runtime-classpath.txt'));

function startServer() {
  const jar = readdirSync(TARGET).find((f) => /^rachana-live-.*\.jar$/.test(f) && !/-(sources|tests)\.jar$/.test(f));
  const mp = [resolve(TARGET, jar), readFileSync(resolve(TARGET, 'runtime-classpath.txt'), 'utf8').trim()].join(delimiter);
  const p = spawn('java', ['--add-opens=java.base/java.nio=org.apache.arrow.memory.core', '-p', mp, '-m',
    'com.ash.rachana.live/com.ash.rachana.live.LiveReferenceServer', '--port', '0'], { stdio: ['ignore', 'pipe', 'inherit'] });
  return new Promise((ok, fail) => {
    p.stdout.once('data', (b) => { const m = /listening on (\S+)/.exec(String(b)); m ? ok({ p, base: m[1] }) : fail(new Error(String(b))); });
    p.once('exit', (c) => fail(new Error(`server exited ${c}`)));
  });
}

const post = (base, path, body) => fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const DECL = `rachana: 1
live-table: orders
snapshot: orders
stream: orders.live
key: id
version: v
columns: [ { name: id }, { name: v, type: integer }, { name: qty, type: integer } ]
`;

/** Frames as they arrive, awaited one at a time. */
function queue() {
  const items = [], waiting = [];
  return {
    push(f) { const w = waiting.shift(); if (w) w(f); else items.push(f); },
    next(ms = 5000) {
      if (items.length) return Promise.resolve(items.shift());
      return new Promise((ok, fail) => { const t = setTimeout(() => fail(new Error('no frame')), ms); waiting.push((f) => { clearTimeout(t); ok(f); }); });
    },
  };
}

test('a windowed SSE stream: subd, the window\'s patches, the total, window() moves it', { skip: !ready && 'build java/rachana-live first' }, async () => {
  const { p, base } = await startServer();
  try {
    assert.equal((await post(base, '/_test/live', { name: 'orders', declaration: DECL,
      rows: [{ id: 'a', v: 1, qty: 1 }, { id: 'b', v: 1, qty: 2 }, { id: 'c', v: 1, qty: 3 }] })).status, 204);
    const c = new DataClient({ base, origin: new URL(base).origin });
    const { rows, version } = await c.read('orders', { sort: [{ col: 'id' }] });
    assert.equal(rows.length, 3);
    const q = queue();
    const stop = c.follow('orders', version, (f) => q.push(f), (e) => q.push({ t: 'error', e }), { window: { keys: [['a']] } });
    try {
      const subd = await q.next();
      assert.equal(subd.t, 'subd');
      await post(base, '/_test/stream', { upsert: [{ id: 'b', v: 2, qty: 20 }] });
      assert.deepEqual((await q.next()).data, { table: 'orders', total: 3 });
      await post(base, '/_test/stream', { upsert: [{ id: 'a', v: 2, qty: 10 }] });
      assert.deepEqual((await q.next()).data.update, [{ id: 'a', v: 2, qty: 10 }]);
      await c.window(subd.sub, [['b']], false);
      await post(base, '/_test/stream', { upsert: [{ id: 'b', v: 3, qty: 30 }] });
      assert.deepEqual((await q.next()).data.update, [{ id: 'b', v: 3, qty: 30 }]);
      await post(base, '/_test/as', { reader: 'eve', view: {} });
      await assert.rejects(c.window(subd.sub, [['a']], false), (e) => e.code === 'rdp.table.unknown');
    } finally {
      stop();
    }
  } finally {
    p.kill();
  }
});

test('the bridge: sub carries the window, win moves it, a window\'s gap reaches the reader', async () => {
  const sent = [];
  let listener = null;
  const ch = { post: (m) => sent.push(m), listen: (h) => { listener = h; return () => {}; } };
  const c = new DataClient({ bridge: ch });
  const frames = [];
  c.follow('orders', { epoch: 'e1', seq: 0 }, (f) => frames.push(f), null, { window: { keys: [['a']], top: true } });
  const sub = sent.find((m) => m.t === 'sub');
  assert.deepEqual(sub.window, { keys: [['a']], top: true });
  listener({ rdp: 1, t: 'res', re: sub.id, body: { recovered: true, version: { epoch: 'e1', seq: 0 } } });
  await new Promise((ok) => setTimeout(ok, 0));
  assert.deepEqual(frames[0], { t: 'subd', sub: sub.id, table: 'orders' });
  const moved = c.window(sub.id, [['b']], false);
  const win = sent.find((m) => m.t === 'win');
  assert.deepEqual([win.sub, win.keys, win.top], [sub.id, [['b']], false]);
  listener({ rdp: 1, t: 'res', re: win.id, body: {} });
  await moved;
  listener({ rdp: 1, t: 'gap', sub: sub.id, table: 'orders', epoch: 'e1', seq: 3, recoverable: false });
  assert.deepEqual(frames[1], { t: 'gap', epoch: 'e1', seq: 3, recoverable: false });
});

test('liveTables (the painter\'s options.tables): one page, a windowed follow in seq order, the window moved', { skip: !ready && 'build java/rachana-live first' }, async () => {
  const { p, base } = await startServer();
  try {
    assert.equal((await post(base, '/_test/live', { name: 'orders', declaration: DECL,
      rows: [{ id: 'a', v: 1, qty: 1 }, { id: 'b', v: 1, qty: 2 }, { id: 'c', v: 1, qty: 3 }] })).status, 204);
    const live = liveTables(new DataClient({ base, origin: new URL(base).origin }))({ table: 'orders', window: 2 });
    const page = await live.page({ sort: [{ col: 'id' }], limit: 2 });
    assert.deepEqual(page.rows.map((r) => r.id), ['a', 'b']);
    assert.ok(page.version && page.version.epoch);
    const q = queue();
    const f = live.follow(page.version, [['a'], ['b']], false, (patch) => q.push(patch), (e) => q.push({ gap: e }));
    try {
      assert.equal(await f.ready, true);
      await post(base, '/_test/stream', { upsert: [{ id: 'a', v: 2, qty: 10 }] });
      assert.deepEqual((await q.next()).update, [{ id: 'a', v: 2, qty: 10 }]);
      await post(base, '/_test/stream', { upsert: [{ id: 'c', v: 2, qty: 30 }] });
      assert.deepEqual(await q.next(), { table: 'orders', total: 3 });
      await f.window([['c']], false);
      await post(base, '/_test/stream', { upsert: [{ id: 'c', v: 3, qty: 31 }] });
      assert.deepEqual((await q.next()).update, [{ id: 'c', v: 3, qty: 31 }]);
    } finally {
      f.stop();
    }
  } finally {
    p.kill();
  }
});
