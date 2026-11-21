// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The static-files host (G2 gate F6): it evaluates nothing itself. Its answers are the runtime bundle's (Core's RowQuery, the one evaluator
// of the filter tree and the sort order), so a static site and a server answer alike: the rules of PROTOCOL 6 and 7, not a second reading.
// Run: node --test js/packages/rachana/test/   (the bundle: the reactor, or python3 tools/js/build_js.py)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, resolve } from 'node:path';
import { StaticHost, parseCsv, loadRuntime } from '../src/index.js';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const rt = await loadRuntime(pathToFileURL(resolve(ROOT, 'js/packages/rachana/dist/rachana.runtime.js')).href,
  [readFileSync(resolve(ROOT, 'spec/1/formats.yaml'), 'utf8')], 'core');

const rows = parseCsv('region,desk,revenue,note\nNorth,A,100,x\nSouth,B,25.5,\nEast,C,9,"a, b"\nWest,D,1000,Y\n');
const host = new StaticHost({ sales: rows }, { sales: ['region', 'desk'] }, rt);
const query = (q, path = '/tables/sales/query') => host.handle('POST', path, JSON.stringify(q));
const names = (r) => r.doc.values.map((v) => v[0]);

test('a host without the runtime is refused: it has no evaluator of its own', () => {
  assert.throws(() => new StaticHost({ sales: rows }), /runtime bundle/);
});

test('the filter tree is Core\'s: numbers compare as numbers, not as text', () => {
  const r = query({ columns: ['region'], filter: { op: '>', args: [{ col: 'revenue' }, { lit: 20 }] }, sort: [{ col: 'revenue', dir: 'asc' }] });
  assert.equal(r.status, 200, JSON.stringify(r.doc));
  assert.deepEqual(names(r), ['South', 'North', 'West'], '9 is below 20, 1000 is above it (text order would put 1000 first)');
  assert.equal(r.doc.total, 3);
});

test('sorting, paging by cursor, and the total follow the protocol', () => {
  const a = query({ columns: ['region'], sort: [{ col: 'region', dir: 'desc' }], limit: 2 });
  assert.deepEqual(names(a), ['West', 'South']);
  assert.ok(a.doc.next, 'a cursor for the rest');
  const b = query({ columns: ['region'], sort: [{ col: 'region', dir: 'desc' }], limit: 2, cursor: a.doc.next });
  assert.deepEqual(names(b), ['North', 'East']);
  assert.equal(b.doc.next, undefined);
  const c = query({ columns: ['region'], sort: [{ col: 'region', dir: 'asc' }], offset: 3, limit: 5 });
  assert.deepEqual(names(c), ['West']);
});

test('the refusals are the protocol\'s: an unknown column, an operation the host cannot do, an unknown table', () => {
  const col = query({ columns: ['nope'] });
  assert.equal(col.status, 422);
  assert.equal(col.doc.error.code, 'rdp.column.unknown');
  const group = query({ group: { by: ['region'], measures: [{ op: 'sum', col: 'revenue' }] } });
  assert.equal(group.status, 422);
  assert.equal(group.doc.error.code, 'rdp.query.unsupported');
  assert.equal(host.handle('GET', '/tables/missing').status, 404);
  assert.equal(host.handle('POST', '/tables/sales/query', '{').status, 422);
});

test('objects shape and the schema ride along; nothing else is the host\'s own', () => {
  const r = query({ columns: ['region', 'revenue'], filter: { op: '==', args: [{ col: 'region' }, { lit: 'East' }] }, shape: 'objects', schema: true });
  assert.equal(r.status, 200, JSON.stringify(r.doc));
  assert.deepEqual(r.doc.rows, [{ region: 'East', revenue: 9 }]);
  assert.equal(r.doc.schema.name, 'sales');
});

test('the host holds no filter evaluator of its own', () => {
  const src = readFileSync(resolve(ROOT, 'js/packages/rachana/src/static_files_host.js'), 'utf8');
  for (const word of ['evalNode', "'starts_with'", 'sortKey', 'toLowerCase']) assert.ok(!src.includes(word), `static_files_host.js still has ${word}`);
});
