// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The Data Protocol's static-files mode (docs/MODES.md 1), in the page: a site with no server beside it holds data/index.json and
// data/<table>.csv|json|jsonl; this module answers the protocol's GET /tables/<t> and POST /tables/<t>/query for the DataClient from
// those files (filter tree, sort, columns, offset, limit, cursor; no changes stream). It wraps fetch for URLs under the data folder
// only; everything else passes through. Import it before rachana_view.js:
//   <script type="module">import '/rachana/src/static_files_host.js'; import '/rachana/src/rachana_view.js';</script>
// The page's <rachana-view data="data"> then reads the files as a protocol host.

const PAGE_ROWS = 10000;

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

const num = (x) => (x !== null && x !== '' && !isNaN(Number(x)) ? Number(x) : x);
function evalNode(n, row) {
  if ('col' in n) { let v = row; for (const p of n.col.split('.')) v = v && typeof v === 'object' ? v[p] : null; return v === undefined ? null : v; }
  if ('lit' in n) return n.lit;
  if ('list' in n) return n.list.map((x) => evalNode(x, row));
  const a = (n.args || []).map((x) => evalNode(x, row));
  const cmp = (f) => { if (a[0] == null || a[1] == null) return false; const both = typeof a[0] === 'string' && typeof a[1] === 'string'; return f(both ? a[0] : num(a[0]), both ? a[1] : num(a[1])); };
  const ops = {
    '==': () => cmp((x, y) => x === y), '!=': () => !cmp((x, y) => x === y), '<': () => cmp((x, y) => x < y), '<=': () => cmp((x, y) => x <= y),
    '>': () => cmp((x, y) => x > y), '>=': () => cmp((x, y) => x >= y), '&&': () => a.every(Boolean), '||': () => a.some(Boolean), '!': () => !a[0],
    'in': () => a[1].includes(a[0]), 'not-in': () => !a[1].includes(a[0]), 'is-null': () => a[0] == null,
    'contains': () => a[0] != null && String(a[0]).toLowerCase().includes(String(a[1]).toLowerCase()),
    'starts_with': () => String(a[0]).startsWith(String(a[1])), 'ends_with': () => String(a[0]).endsWith(String(a[1])),
    'lower': () => (a[0] == null ? null : String(a[0]).toLowerCase()), 'upper': () => (a[0] == null ? null : String(a[0]).toUpperCase()),
  };
  if (!ops[n.op]) throw perr(422, 'rdp.query.unsupported', `the operation '${n.op}' is not evaluated by this host`);
  return ops[n.op]();
}

const perr = (status, code, message, extra) => Object.assign(new Error(message), { status, doc: { error: { code, message, ...extra } } });
const sortKey = (v) => (v == null ? [1, 0, ''] : typeof v === 'number' ? [0, 0, v] : [0, 1, String(v)]);
const cmpKey = (x, y) => { for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] < y[i] ? -1 : 1; return 0; };

/** An in-memory protocol host over {name: rows[]}: handle(method, path, bodyText) -> {status, doc}. */
export class StaticHost {
  constructor(tables, keys = {}) {
    this.tables = tables; this.keys = keys; this.epoch = 'st' + Math.floor(Math.random() * 0xffff).toString(16);
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
    const sch = this.schema(name), known = sch.columns.map((c) => c.name);
    let rows = this.tables[name].slice();
    for (const c of q.columns || []) if (!known.includes(c)) throw perr(422, 'rdp.column.unknown', `${name} has no column '${c}'`, { table: name, column: c, expected: known });
    if (q.group) throw perr(422, 'rdp.query.unsupported', 'grouping is not evaluated by this host', { table: name });
    if (q.filter) rows = rows.filter((r) => evalNode(q.filter, r));
    for (const s of [...(q.sort || [])].reverse()) {
      if (!known.includes(s.col)) throw perr(422, 'rdp.column.unknown', `${name} has no column '${s.col}'`, { table: name, column: s.col, expected: known });
      rows.sort((x, y) => (s.dir === 'desc' ? -1 : 1) * cmpKey(sortKey(x[s.col]), sortKey(y[s.col])));
    }
    const sig = JSON.stringify([q.filter, q.sort, q.columns]);
    let start = Number(q.offset || 0);
    if (q.cursor) {
      const [tag, off] = String(q.cursor).split(':');
      if (tag !== String(sig.length) + '.' + hash(sig) || !/^\d+$/.test(off || '')) throw perr(410, 'rdp.cursor.invalid', 'the cursor is not from this query', { table: name });
      start = Number(off);
    }
    const limit = Math.min(Number(q.limit || PAGE_ROWS), PAGE_ROWS), page = rows.slice(start, start + limit), cols = q.columns || known;
    const out = { table: name, version: sch.version };
    if (q.shape === 'objects') out.rows = page.map((r) => Object.fromEntries(cols.map((c) => [c, r[c] ?? null])));
    else { out.columns = cols; out.values = page.map((r) => cols.map((c) => r[c] ?? null)); }
    out.total = rows.length;
    if (start + limit < rows.length) out.next = `${sig.length}.${hash(sig)}:${start + limit}`;
    if (q.schema) out.schema = sch;
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

function hash(s) { let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return (h >>> 0).toString(36); }

/**
 * Wraps fetch (of `w`, default the page) so the protocol under `base` is answered from <base>/index.json and its table files.
 * @param {string} [base] default: the page's data/ folder.
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
    return new StaticHost(tables, keys);
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
