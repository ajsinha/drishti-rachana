// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The Data Protocol's static-files mode (docs/IN_YOUR_APP.md), in the page: a site with no server beside it holds data/index.json and
// data/<table>.csv|json|jsonl; this module answers the protocol's GET /tables/<t> and POST /tables/<t>/query for the DataClient from
// those files (filter tree, sort, columns, offset, limit, cursor; no changes stream). It evaluates nothing itself: the query is answered by the runtime bundle's
// `query` (Core's RowQuery, the one evaluator of the filter tree and the sort order), so a static site and a server answer alike. It wraps fetch for URLs under the data folder
// only; everything else passes through. Import it before rachana_view.js:
//   <script type="module">import '/rachana/src/static_files_host.js'; import '/rachana/src/rachana_view.js';</script>
// The page's <rachana-view data="data"> then reads the files as a protocol host.

import { initialisedRuntime } from './screen.js';

const PAGE_ROWS = 10000;
const runtimeFor = async (r) => (r && typeof r.query === 'function' ? r : await initialisedRuntime(r));

/** Rows of CSV text (RFC 4180: quotes, doubled quotes, embedded line breaks); the first line names the columns. */
export function parseCsv(text) {
  const rows = []; let row = [], cell = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) { if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else q = false; } else cell += c; }
    else if (c === '"') q = true;
    else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); cell = ''; if (row.length > 1 || row[0] !== '') rows.push(row); row = []; }
    else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  const head = rows.shift() || [];
  const cols = head.map((_, j) => rows.length && rows.every((r) => r[j] === '' || (r[j] !== undefined && r[j].trim() !== '' && !isNaN(Number(r[j]))) ));
  return rows.map((r) => Object.fromEntries(head.map((h, j) => [h, r[j] === undefined || r[j] === '' ? null : cols[j] ? Number(r[j]) : r[j]])));
}

export function parseRows(name, text) {
  if (/\.csv$/.test(name)) return parseCsv(text);
  if (/\.jsonl$/.test(name)) return text.split('\n').filter((l) => l.trim()).map((l) => JSON.parse(l));
  const v = JSON.parse(text);
  return Array.isArray(v) ? v : v.rows || [];
}

const typeOf = (vals) => {
  let t = 'string';
  for (const v of vals) {
    if (v == null) continue;
    if (typeof v === 'boolean') return 'boolean';
    if (typeof v === 'number') { if (!Number.isInteger(v)) return 'number'; t = 'integer'; }
    else if (Array.isArray(v)) return 'list';
    else if (typeof v === 'object') return 'object';
    else return 'string';
  }
  return t;
};

const perr = (status, code, message, extra) => Object.assign(new Error(message), { status, doc: { error: { code, message, ...extra } } });

/** An in-memory protocol host over {name: rows[]}: handle(method, path, bodyText) -> {status, doc}. `runtime` is the initialised runtime bundle (its `query`). */
export class StaticHost {
  constructor(tables, keys = {}, runtime = null) {
    if (!runtime || typeof runtime.query !== 'function') throw new Error('StaticHost needs the runtime bundle (its query): the host evaluates nothing itself');
    this.tables = tables; this.keys = keys; this.runtime = runtime; this.epoch = 'st' + Math.floor(Math.random() * 0xffff).toString(16);
  }
  schema(name) {
    const rows = this.tables[name];
    if (!rows) throw perr(404, 'rdp.table.unknown', `no table '${name}'`, { table: name });
    const names = []; for (const r of rows.slice(0, 200)) for (const k of Object.keys(r)) if (!names.includes(k)) names.push(k);
    const d = { name };
    if (this.keys[name]) d.key = this.keys[name];
    return Object.assign(d, { columns: names.map((n) => ({ name: n, type: typeOf(rows.slice(0, 200).map((r) => r[n])) })), rows: rows.length,
      can: ['page', 'sort', 'filter', 'offset', 'count'], version: { epoch: this.epoch, seq: 0 }, limits: { pageRows: PAGE_ROWS } });
  }
  query(name, q) {
    const sch = this.schema(name), { shape, schema, ...doc } = q;
    const r = JSON.parse(this.runtime.query(JSON.stringify({ schema: sch, rows: this.tables[name], query: doc, epoch: this.epoch, version: sch.version })));
    if (r.error) throw perr(500, 'rdp.host.error', r.error);
    if (r.status !== 200) throw Object.assign(new Error(r.doc.error.message), { status: r.status, doc: r.doc });
    const out = r.doc;
    if (shape === 'objects' && out.values) { out.rows = out.values.map((v) => Object.fromEntries(out.columns.map((c, i) => [c, v[i]]))); delete out.columns; delete out.values; }
    if (schema) out.schema = sch;
    return out;
  }
  handle(method, path, bodyText) {
    try {
      const parts = path.split('/').filter(Boolean);
      if (!parts.length && method === 'GET') return { status: 200, doc: { protocol: 'rdp', v: 1, tables: 'tables', limits: { pageRows: PAGE_ROWS } } };
      if (parts[0] === 'tables' && parts.length === 1 && method === 'GET') return { status: 200, doc: { tables: Object.keys(this.tables).map((n) => ({ name: n, can: this.schema(n).can })) } };
      if (parts[0] === 'tables' && parts[1]) {
        const name = decodeURIComponent(parts[1]);
        if (parts.length === 2 && method === 'GET') return { status: 200, doc: this.schema(name) };
        if (parts[2] === 'query' && method === 'POST') {
          let q; try { q = JSON.parse(bodyText || '{}'); } catch (e) { throw perr(422, 'rdp.query.invalid', 'the query is not JSON'); }
          return { status: 200, doc: this.query(name, q) };
        }
      }
      return { status: 404, doc: { error: { code: 'rdp.table.unknown', message: 'no such resource' } } };
    } catch (e) {
      if (e.doc) return { status: e.status, doc: e.doc };
      throw e;
    }
  }
}

/**
 * Wraps fetch (of `w`, default the page) so the protocol under `base` is answered from <base>/index.json and its table files.
 * @param {string} [base] default: the page's data/ folder.
 * @param {Object|string} [runtime] the initialised runtime bundle, or the address the page's <rachana-view> loaded it from (default: the one it loaded).
 */
export function installStaticHost(o) {
  o = o || {};
  const w = o.window || globalThis, orig = w.fetch.bind(w);
  const base = new URL(o.base || 'data', (w.document && w.document.baseURI) || w.location.href).href.replace(/\/$/, '');
  let host = null;
  const load = async () => {
    const idx = await (await orig(base + '/index.json')).json();
    const tables = {}, keys = {};
    for (const t of idx.tables || []) { tables[t.name] = parseRows(t.file, await (await orig(`${base}/${t.file}`)).text()); if (t.key) keys[t.name] = t.key; }
    return new StaticHost(tables, keys, await runtimeFor(o.runtime));
  };
  w.fetch = async (url, init) => {
    const u = typeof url === 'string' ? url : url.url;
    if (!u.startsWith(base + '/tables') && u !== base + '/') return orig(url, init);
    host = host || load();
    const r = (await host).handle((init && init.method) || 'GET', u.slice(base.length), init && init.body);
    return new Response(JSON.stringify(r.doc), { status: r.status, headers: { 'Content-Type': 'application/json' } });
  };
  return base;
}

if (typeof document !== 'undefined' && typeof window !== 'undefined') installStaticHost();
