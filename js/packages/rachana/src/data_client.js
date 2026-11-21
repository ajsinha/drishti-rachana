// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The Data Protocol client of the JavaScript runtime (spec/1/data-protocol, B2): transport only. It reads a table's schema, reads the
// rows a plan's query document selects (POST /query, following `next` cursors to the end), and follows the table's change stream (the
// minimal SSE form of PROTOCOL 8.2, or a host's drishti-events client through `subscribe`). Every meaning (the plan, the filter tree,
// decoding cells, keyed patches, binding, frames) is Core's, in the bundle; this file has no parser, operator or formatter.
//
// Addresses: the client calls only its `base` and the origins on `allow` (default: the page's own origin; PROTOCOL 9). The host's
// `fetch` carries the host's cookies and headers; the client adds none and stores none.

/** A Data Protocol error: code (rdp.*), HTTP status and the error object's fields. */
export class DataError extends Error {
  constructor(status, error) {
    super((error && error.message) || `the host answered ${status}`);
    this.status = status;
    this.code = (error && error.code) || 'rdp.internal';
    this.error = error || {};
  }
}

/** The rows of a page as objects, from either JSON form (PROTOCOL 5.1). */
export function pageRows(page) {
  if (Array.isArray(page.rows)) return page.rows;
  const cols = page.columns || [], out = [];
  for (const v of page.values || []) {
    const r = {};
    for (let i = 0; i < cols.length; i++) r[cols[i]] = v[i];
    out.push(r);
  }
  return out;
}

const ARROW = 'application/vnd.apache.arrow.stream', NDJSON = 'application/x-ndjson';
const pad2 = (n) => String(n).padStart(2, '0');

/** One Arrow cell in its JSON form (PROTOCOL 3.1), by the field's rachana.type; the same rules as the JVM's ArrowRows. */
function arrowCell(A, field, x) {
  if (x === null || x === undefined) return null;
  const type = field.metadata.get('rachana.type');
  switch (field.typeId) {
    case A.Type.Int: { const b = BigInt(x); const big = b > 9007199254740992n || b < -9007199254740992n; return big ? b.toString() : Number(b); }
    case A.Type.Decimal: {
      const u = A.util.bigNumToBigInt(x), neg = u < 0n, digits = (neg ? -u : u).toString(), sc = field.type.scale;
      if (type === 'integer') return (u > 9007199254740992n || u < -9007199254740992n) ? u.toString() : Number(u);
      if (sc <= 0) return (neg ? '-' : '') + digits;
      const d = digits.padStart(sc + 1, '0');
      return `${neg ? '-' : ''}${d.slice(0, d.length - sc)}.${d.slice(d.length - sc)}`;
    }
    case A.Type.Date: return new Date(x).toISOString().slice(0, 10);
    case A.Type.Timestamp: return new Date(x).toISOString();
    case A.Type.Time: {
      const ms = Number(x), s = Math.floor(ms / 1000), f = ms % 1000;
      const t = `${pad2(Math.floor(s / 3600))}:${pad2(Math.floor(s / 60) % 60)}:${pad2(s % 60)}`;
      return f ? `${t}.${String(f).padStart(3, '0')}` : t;
    }
    case A.Type.Utf8: return type === 'list' || type === 'object' ? JSON.parse(x) : x;
    default: return typeof x === 'bigint' ? Number(x) : x;
  }
}

/**
 * An Arrow IPC stream (PROTOCOL 5.2) as the columnar-by-row JSON page the host would have answered, read with the vendored Apache Arrow
 * JS (A: its module object, js/vendor/apache-arrow). Masked cells come back as {$masked: true} from their <name>$masked column.
 */
export function arrowPage(A, bytes) {
  const t = A.tableFromIPC(bytes), meta = t.schema.metadata, page = { table: meta.get('rachana.table') || '' };
  if (meta.has('rachana.epoch')) page.version = { epoch: meta.get('rachana.epoch'), seq: Number(meta.get('rachana.seq') || 0) };
  const fields = t.schema.fields.filter((f) => !f.metadata.has('rachana.masked'));
  const masks = new Map(t.schema.fields.filter((f) => f.metadata.has('rachana.masked')).map((f) => [f.metadata.get('rachana.masked'), t.getChild(f.name)]));
  const vecs = fields.map((f) => t.getChild(f.name));
  page.columns = fields.map((f) => f.name);
  page.values = [];
  for (let i = 0; i < t.numRows; i++) {
    page.values.push(fields.map((f, c) => {
      const m = masks.get(f.name);
      return m && m.get(i) === true ? { $masked: true } : arrowCell(A, f, vecs[c].get(i));
    }));
  }
  if (meta.has('rachana.total')) page.total = Number(meta.get('rachana.total'));
  if (meta.has('rachana.next')) page.next = meta.get('rachana.next');
  if (meta.has('rachana.totals')) page.totals = JSON.parse(meta.get('rachana.totals'));
  if (meta.has('rachana.schema')) page.schema = JSON.parse(meta.get('rachana.schema'));
  return page;
}

/** NDJSON text (PROTOCOL 5.3: a header object, then one array of cells per line) as the columnar-by-row JSON page. */
export function ndjsonPage(text) {
  const lines = text.split('\n').filter((l) => l.trim());
  const head = lines.length ? JSON.parse(lines[0]) : {}, page = {};
  for (const [k, v] of Object.entries(head)) { page[k] = v; if (k === 'columns') page.values = []; }
  if (!page.values) page.values = [];
  for (const l of lines.slice(1)) page.values.push(JSON.parse(l));
  return page;
}

export class DataClient {
  /**
   * @param {Object} o
   * @param {string} o.base the host's base address; resolved to an absolute address against `origin`
   * @param {function} [o.fetch] the host's fetch (default the global one)
   * @param {string[]} [o.allow] other origins the base may be on (default none: the page's own origin only)
   * @param {string} [o.origin] the page's origin (default location.origin; required outside a page)
   * @param {function} [o.subscribe] a host's change stream instead of the minimal SSE form: (table, {epoch, seq}, onFrame) => unsubscribe
   * @param {string} [o.form] the row form asked: 'json' (default), 'arrow' (only of a table whose can has arrow; JSON otherwise) or 'ndjson'
   * @param {Object} [o.arrow] the Apache Arrow JS module object (default globalThis.Arrow, set by js/vendor/apache-arrow's script)
   */
  constructor(o) {
    const origin = o.origin || (typeof location !== 'undefined' ? location.origin : null);
    const base = new URL(o.base, origin || undefined);
    const allowed = [origin, ...(o.allow || [])].filter(Boolean);
    if (!allowed.includes(base.origin)) {
      throw new DataError(0, { code: 'rdp.forbidden', message: `the data address ${base.origin} is not on the host's allow-list` });
    }
    this.base = base.href.replace(/\/$/, '');
    this.fetch = o.fetch || ((u, init) => globalThis.fetch(u, init));
    this.subscribeHook = o.subscribe || null;
    this.form = o.form || 'json';
    this.arrow = o.arrow || null;
  }

  /** One page of a query in a row form other than JSON, read into the JSON page. */
  async rowsPage(table, doc, form) {
    const init = { method: 'POST', headers: { Accept: form === 'arrow' ? ARROW : NDJSON, 'Content-Type': 'application/json' }, body: JSON.stringify(doc) };
    const r = await this.fetch(`${this.base}/tables/${encodeURIComponent(table)}/query`, init);
    if (r.status !== 200) { const t = await r.text(); throw new DataError(r.status, t ? JSON.parse(t).error : null); }
    return form === 'arrow' ? arrowPage(this.arrow || globalThis.Arrow, new Uint8Array(await r.arrayBuffer())) : ndjsonPage(await r.text());
  }

  async json(method, path, body) {
    const init = { method, headers: { Accept: 'application/json' } };
    if (body !== undefined) { init.headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(body); }
    const r = await this.fetch(this.base + path, init);
    const text = await r.text();
    const doc = text ? JSON.parse(text) : null;
    if (r.status !== 200) throw new DataError(r.status, doc && doc.error);
    return doc;
  }

  /** The schema document of a table (PROTOCOL 2). */
  schema(table) { return this.json('GET', `/tables/${encodeURIComponent(table)}`); }

  /**
   * Every row a query document selects, page by page; the version is the first page's.
   * @returns {Promise<{rows: Object[], version: ?{epoch: string, seq: number}}>}
   */
  async read(table, query, schema) {
    const doc = Object.assign({}, query || {});
    let form = this.form;
    if (form === 'arrow' && !((schema && schema.can) || []).includes('arrow')) form = 'json';
    if (form === 'arrow' && !(this.arrow || globalThis.Arrow)) throw new DataError(0, { code: 'rdp.query.unsupported', message: 'Apache Arrow JS is not loaded' });
    const rows = [];
    let version = null;
    for (;;) {
      const page = form === 'json' ? await this.json('POST', `/tables/${encodeURIComponent(table)}/query`, doc) : await this.rowsPage(table, doc, form);
      if (!version && page.version) version = page.version;
      for (const r of pageRows(page)) rows.push(r);
      if (typeof page.next !== 'string') break;
      doc.cursor = page.next;
    }
    return { rows, version };
  }

  /**
   * Follows a table's changes after a position. onFrame receives each `msg` or `gap` frame parsed; the rules of PROTOCOL 8.3 (drop a
   * seq not above the last, resync on a gap or another epoch) are the caller's. Returns a function that stops.
   */
  follow(table, after, onFrame, onError) {
    if (this.subscribeHook) return this.subscribeHook(table, after, onFrame, onError);
    const url = `${this.base}/changes?table=${encodeURIComponent(table)}&after=${encodeURIComponent(`${after.epoch}:${after.seq}`)}`;
    if (typeof EventSource !== 'undefined') {
      const es = new EventSource(url);
      const on = (e) => { try { onFrame(JSON.parse(e.data)); } catch (x) { if (onError) onError(x); } };
      es.addEventListener('msg', on);
      es.addEventListener('gap', on);
      es.onerror = () => { if (es.readyState === 2 && onError) onError(new DataError(0, { code: 'rdp.unavailable', message: 'the change stream closed' })); };
      return () => es.close();
    }
    // Node and workers without EventSource: read the stream with fetch
    const ctl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    (async () => {
      const r = await this.fetch(url, { headers: { Accept: 'text/event-stream' }, signal: ctl && ctl.signal });
      if (r.status !== 200) { const t = await r.text(); throw new DataError(r.status, t ? JSON.parse(t).error : null); }
      const reader = r.body.getReader(), dec = new TextDecoder();
      let buf = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) {
          const ev = buf.slice(0, i); buf = buf.slice(i + 2);
          const data = ev.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('\n');
          if (data) onFrame(JSON.parse(data));
        }
      }
    })().catch((x) => { if (!(ctl && ctl.signal.aborted) && onError) onError(x); });
    return () => { if (ctl) ctl.abort(); };
  }
}
