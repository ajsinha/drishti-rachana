// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The JavaScript side end to end in Node: the reference server (java/rachana-data, built by ./mvnw package), the runtime bundle
// (dist/rachana.runtime.js, built by tools/js/build_js.py), a Sutra with data: read through its plan, then kept live by the stream.
// Run: node --test js/packages/rachana/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, resolve, delimiter } from 'node:path';
import { DataClient, Screen, loadRuntime } from '../src/index.js';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const TARGET = resolve(ROOT, 'java/rachana-data/target');
const BUNDLE = resolve(ROOT, 'js/packages/rachana/dist/rachana.runtime.js');

function startServer() {
  const jar = readdirSync(TARGET).find((f) => /^rachana-data-.*\.jar$/.test(f) && !/-(sources|tests)\.jar$/.test(f));
  const mp = [resolve(TARGET, jar), readFileSync(resolve(TARGET, 'runtime-classpath.txt'), 'utf8').trim()].join(delimiter);
  const p = spawn('java', ['-p', mp, '-m', 'com.ash.rachana.data/com.ash.rachana.data.ReferenceServer', '--port', '0'], { stdio: ['ignore', 'pipe', 'inherit'] });
  return new Promise((ok, fail) => {
    p.stdout.once('data', (b) => { const m = /listening on (\S+)/.exec(String(b)); m ? ok({ p, base: m[1] }) : fail(new Error(String(b))); });
    p.once('exit', (c) => fail(new Error(`server exited ${c}`)));
  });
}

const SUTRA = `rachana: 1
sutra: live-sales
version: 1
controls:
  - { id: region, kind: dropdown, options: [North, South] }
data:
  sales: { table: sales, key: [region, desk], live: true }
panels:
  - { id: total, kind: metric, label: Revenue, value: "sum($.sales, 'revenue')" }
  - { id: list, kind: table, rows: "$.sales", where: "@.region == ctl.region", columns: [ { label: Desk, bind: desk } ] }
`;

const ready = existsSync(BUNDLE) && existsSync(TARGET);

test('a screen reads its tables through the plan and follows their changes', { skip: !ready && 'build the bundle and java/rachana-data first' }, async () => {
  const { p, base } = await startServer();
  try {
    const post = (path, body) => fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    await post('/_test/fixture', { schema: { name: 'sales', key: ['region', 'desk'], columns: [{ name: 'region', type: 'string' },
      { name: 'desk', type: 'string' }, { name: 'revenue', type: 'decimal' }], can: ['page', 'filter', 'changes', 'replay'] },
    rows: [{ region: 'North', desk: 'A', revenue: '100' }, { region: 'South', desk: 'B', revenue: '50' }] });
    const rt = await loadRuntime(pathToFileURL(BUNDLE).href, [readFileSync(resolve(ROOT, 'spec/1/formats.yaml'), 'utf8')], 'core');
    const client = new DataClient({ base, origin: new URL(base).origin });
    const screen = new Screen(rt, { sutra: SUTRA, ctl: { region: 'North' }, client });
    const plan = await screen.load();
    assert.deepEqual(plan.tables[0].readers, ['total', 'list']);
    assert.equal(plan.tables[0].local[0].panel, 'list');          // two readers: the panel's where stays local
    let vm = screen.view();
    assert.equal(vm.panels[0].data.value.text, '150');
    assert.equal(vm.panels[1].data.rows.length, 1);
    const changes = [];
    const got = new Promise((ok) => screen.live((c) => { changes.push(c); if (changes.length === 2) ok(); }, (e) => ok(e)));
    await post('/_test/patch', { table: 'sales', update: [{ region: 'North', desk: 'A', revenue: '120' }] });
    await post('/_test/patch', { table: 'sales', add: [{ region: 'North', desk: 'C', revenue: '1' }] });
    await got;
    assert.equal(changes[0].frames[0].patches[0].panel.data.value.text, '170');
    assert.equal(changes[1].frames[0].patches.length, 2);          // the total and the North list both changed
    vm = screen.view();
    assert.equal(vm.panels[1].data.rows.length, 2);
    screen.close();
  } finally {
    p.kill();
  }
});

test('the client refuses an origin that is not allowed', () => {
  assert.throws(() => new DataClient({ base: 'https://elsewhere.test/rdp', origin: 'https://page.test' }), /allow-list/);
  assert.ok(new DataClient({ base: '/rdp/v1', origin: 'https://page.test' }).base.startsWith('https://page.test/'));
});
