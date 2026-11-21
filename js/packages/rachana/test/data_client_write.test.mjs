// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// Writes to a live table from JavaScript (PROTOCOL 16): DataClient.write over HTTP against the live test adapter (java/rachana-live's
// LiveReferenceServer, built by ./mvnw package): an applied change, a conflict naming the version, an invalid one naming the column, a
// repeated id answering its first answer; the live panel's write hook (liveTables(client, onWrite)); and the bridge's write message.
// Run: node --test js/packages/rachana/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, delimiter } from 'node:path';
import { DataClient, liveTables, ulid } from '../src/index.js';

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
live-table: trades
snapshot: trades
stream: trades.live
key: id
version: v
changed-by: by
columns: [ { name: id }, { name: v, type: integer }, { name: qty, type: integer }, { name: by } ]
edit:
  fields:
    - { id: qty, bind: qty, type: number, min: 0 }
`;

test('a ULID is 26 Crockford characters, time first', () => {
  const a = ulid(0), b = ulid(1);
  assert.match(a, /^[0-9A-HJKMNP-TV-Z]{26}$/);
  assert.ok(a.slice(0, 10) < b.slice(0, 10));
});

test('write over HTTP: applied, conflict, invalid, a repeat, the live panel hook', { skip: !ready && 'build java/rachana-live first' }, async () => {
  const { p, base } = await startServer();
  try {
    assert.equal((await post(base, '/_test/live', { name: 'trades', declaration: DECL, edit: { sink: 'applied' },
      rows: [{ id: 'a', v: 1, qty: 1, by: 'system' }], readers: { bob: { editable: ['qty'] } } })).status, 204);
    assert.equal((await post(base, '/_test/as', { reader: 'bob' })).status, 204);
    const sent = [];
    const c = new DataClient({ base, origin: new URL(base).origin, csrf: 't0k', fetch: (u, i) => { sent.push(i && i.headers); return fetch(u, i); } });
    const a = await c.write('trades', { id: 'c1', key: ['a'], basedOn: 1, set: { qty: 5 } });
    assert.deepEqual([a.change, a.state, a.version], ['c1', 'applied', 2]);
    assert.equal(sent[0]['X-Rachana-CSRF'], 't0k');
    assert.deepEqual(await c.write('trades', { id: 'c1', key: ['a'], basedOn: 1, set: { qty: 5 } }), a);
    await assert.rejects(c.write('trades', { key: ['a'], basedOn: 1, set: { qty: 6 } }),
      (e) => e.status === 409 && e.code === 'rdp.write.conflict' && e.error.current.version === 2 && e.error.current.by === 'bob');
    await assert.rejects(c.write('trades', { key: ['a'], basedOn: 2, set: { qty: -1 } }),
      (e) => e.status === 422 && e.error.fields[0].column === 'qty' && e.error.fields[0].rule === 'value');
    const seen = [];
    const panel = liveTables(c, (e) => { seen.push(e.phase + ':' + (e.answer ? e.answer.state : e.error ? e.error.code : '')); return e.change.set.qty !== 99; })({ table: 'trades' });
    const b = await panel.write({ key: ['a'], basedOn: 2, set: { qty: 7 } });
    assert.equal(b.state, 'applied');
    assert.match(b.change, /^[0-9A-Z]{26}$/);
    await assert.rejects(panel.write({ key: ['a'], basedOn: 3, set: { qty: 99 } }), (e) => e.code === 'rdp.write.forbidden');
    assert.deepEqual(seen, ['before:', 'after:applied', 'before:']);
    const rows = (await c.read('trades', {})).rows;
    assert.deepEqual(rows.map((r) => [r.id, r.v, r.qty]), [['a', 3, 7]]);
  } finally {
    p.kill();
  }
});

test('write over the bridge: a write message answered by res', async () => {
  const posted = [];
  let listener = null;
  const ch = { post: (m) => { posted.push(m); if (m.t === 'write') queueMicrotask(() => listener({ rdp: 1, t: 'res', re: m.id, body: { change: m.body.id, state: 'queued', message: 'queued' } })); },
    listen: (h) => { listener = h; return () => {}; } };
  const c = new DataClient({ bridge: ch });
  const a = await c.write('trades', { id: 'c9', key: ['a'], basedOn: 1, set: { qty: 2 } });
  assert.deepEqual(a, { change: 'c9', state: 'queued', message: 'queued' });
  const w = posted.find((m) => m.t === 'write');
  assert.deepEqual([w.table, w.body.op, w.body.set.qty], ['trades', 'update', 2]);
});
