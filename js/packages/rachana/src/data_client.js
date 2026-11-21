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
   * @param {Object} [o.bridge] the in-process bridge instead of HTTP (PROTOCOL 12; a desktop host's web view channel):
   *   {post(message), listen(handler) => stop}; every request is a bridge message answered by `res` (or `res.arrow`), changes are
   *   `sub` / `unsub` with `msg` pushed back. Only messages from this channel are read (never from page content or another frame).
   * @param {Object} [o.filter] (follow) a filter tree for every subscription: only matching rows (the table must declare changes.filter)
   * @param {number} [o.everyMs] (follow) ask the host to conflate a table's changes to at most one message per everyMs (PROTOCOL 8.4)
   */
  constructor(o) {
    this.form = o.form || 'json';
    this.arrow = o.arrow || null;
    this.everyMs = o.everyMs || 0;
    if (o.bridge) { this.bridge = new BridgeChannel(o.bridge); this.base = null; return; }
    const origin = o.origin || (typeof location !== 'undefined' ? location.origin : null);
    const base = new URL(o.base, origin || undefined);
    const allowed = [origin, ...(o.allow || [])].filter(Boolean);
    if (!allowed.includes(base.origin)) {
      throw new DataError(0, { code: 'rdp.forbidden', message: `the data address ${base.origin} is not on the host's allow-list` });
    }
    this.base = base.href.replace(/\/$/, '');
    this.fetch = o.fetch || ((u, init) => globalThis.fetch(u, init));
    this.subscribeHook = o.subscribe || null;
  }

  /** One page of a query in a row form other than JSON, read into the JSON page. */
  async rowsPage(table, doc, form) {
    if (this.bridge) {
      if (form !== 'arrow') return this.bridge.request('POST', `/tables/${encodeURIComponent(table)}/query`, doc);
      return arrowPage(this.arrow || globalThis.Arrow, await this.bridge.request('POST', `/tables/${encodeURIComponent(table)}/query`, doc, 'arrow'));
    }
    const init = { method: 'POST', headers: { Accept: form === 'arrow' ? ARROW : NDJSON, 'Content-Type': 'application/json' }, body: JSON.stringify(doc) };
    const r = await this.fetch(`${this.base}/tables/${encodeURIComponent(table)}/query`, init);
    if (r.status !== 200) { const t = await r.text(); throw new DataError(r.status, t ? JSON.parse(t).error : null); }
    return form === 'arrow' ? arrowPage(this.arrow || globalThis.Arrow, new Uint8Array(await r.arrayBuffer())) : ndjsonPage(await r.text());
  }

  async json(method, path, body) {
    if (this.bridge) return this.bridge.request(method, path, body);
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
   * One page of a query, never the next ones (a live panel's window, SPEC 2.3): its rows, version, total (when the host counts) and next.
   * @returns {Promise<{rows: Object[], version: ?{epoch: string, seq: number}, total: ?number, next: ?string}>}
   */
  async page(table, query) {
    const page = await this.json('POST', `/tables/${encodeURIComponent(table)}/query`, Object.assign({}, query || {}));
    return { rows: pageRows(page), version: page.version || null, total: typeof page.total === 'number' ? page.total : null, next: page.next || null };
  }

  /**
   * Follows a table's changes after a position. onFrame receives each `msg` or `gap` frame parsed; the rules of PROTOCOL 8.3 (drop a
   * seq not above the last, resync on a gap or another epoch) are the caller's. Returns a function that stops.
   * opts.window ({keys: [[...key values]], top?}, a table whose can has changes.window; PROTOCOL 16): only the patches of the keys the
   * reader's page shows, plus its new `total` when other rows change; a `subd` frame (the first over HTTP) names the subscription
   * for `window(sub, keys, top)`. A window falling behind the host's capacity ends with a `gap` (read the page again).
   */
  follow(table, after, onFrame, onError, opts) {
    const filter = (opts && opts.filter) || null, everyMs = (opts && opts.everyMs) || this.everyMs, win = (opts && opts.window) || null;
    const windowed = win ? { keys: win.keys || [], top: !!win.top } : undefined;
    if (this.subscribeHook) return this.subscribeHook(table, after, onFrame, onError, { filter, everyMs, window: windowed });
    if (this.bridge) return this.bridge.subscribe(table, after, filter, everyMs, onFrame, onError, windowed);
    const url = `${this.base}/changes?table=${encodeURIComponent(table)}&after=${encodeURIComponent(`${after.epoch}:${after.seq}`)}`;
    // a filtered, conflated or windowed stream is asked with POST (the minimal SSE form's body); EventSource cannot POST, so fetch reads it
    const post = filter || everyMs || windowed ? { table, after: `${after.epoch}:${after.seq}`, filter: filter || undefined, conflate: everyMs ? { everyMs } : undefined, window: windowed } : null;
    if (typeof EventSource !== 'undefined' && !post) {
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
      const init = { headers: { Accept: 'text/event-stream' }, signal: ctl && ctl.signal };
      if (post) Object.assign(init, { method: 'POST', body: JSON.stringify(post) }, { headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json' } });
      const r = await this.fetch(post ? `${this.base}/changes` : url, init);
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

  /**
   * Changes the window of a windowed subscription (`sub` from its `subd` frame; PROTOCOL 16): the keys the page now shows and whether it
   * starts at the top of the table's own order. Over HTTP `POST {base}/changes/{sub}/window`, over the bridge a `win` message. Only the
   * subscription's own caller may; `rdp.table.unknown` otherwise.
   */
  async window(sub, keys, top) {
    if (this.bridge) return this.bridge.window(sub, keys, top);
    const r = await this.fetch(`${this.base}/changes/${encodeURIComponent(sub)}/window`, { method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify({ keys: keys || [], top: !!top }) });
    if (r.status !== 204 && r.status !== 200) { const t = await r.text(); throw new DataError(r.status, t ? JSON.parse(t).error : null); }
  }
}

/** Base64 (a bridge res.arrow buffer in JSON) as bytes; a platform's transferable buffer is used as it is. */
function bytesOf(buffer) {
  if (typeof buffer !== 'string') return buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (typeof atob === 'function') { const b = atob(buffer), u = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); return u; }
  return new Uint8Array(Buffer.from(buffer, 'base64'));
}

/**
 * The view's end of the bridge (PROTOCOL 12): requests as bridge messages, each answered exactly once by `res` (or `res.arrow`);
 * subscriptions as `sub`/`unsub`, the host's `msg` frames routed by their `sub` id. Request ids are unique per view.
 */
export class BridgeChannel {
  constructor(ch) {
    this.ch = ch; this.n = 0; this.waiting = new Map(); this.subs = new Map();
    this.stop = ch.listen((m) => this.receive(typeof m === 'string' ? JSON.parse(m) : m));
    ch.post({ rdp: 1, t: 'hello', client: 'drishti-rachana', v: 1 });
  }

  receive(m) {
    if (!m || m.rdp !== 1) return;
    if (m.t === 'res' || m.t === 'res.arrow') {
      const w = this.waiting.get(m.re);
      if (!w) return;
      this.waiting.delete(m.re);
      if (m.error) w.fail(new DataError(0, m.error)); else w.ok(m.t === 'res.arrow' ? bytesOf(m.buffer) : m.body);
    } else if ((m.t === 'msg' || m.t === 'gap') && this.subs.has(m.sub)) {
      this.subs.get(m.sub)(m);
    }
  }

  send(message) {
    const id = `r${++this.n}`;
    return new Promise((ok, fail) => { this.waiting.set(id, { ok, fail }); this.ch.post(Object.assign({ rdp: 1, id }, message(id))); });
  }

  /** An HTTP-shaped request of the client as its bridge message. */
  request(method, path, body, accept) {
    const m = /^\/tables(?:\/([^/]+)(?:\/(rows|query))?)?$/.exec(path);
    if (!m) return Promise.reject(new DataError(0, { code: 'rdp.query.invalid', message: `no bridge request for ${path}` }));
    const table = m[1] ? decodeURIComponent(m[1]) : undefined, t = !m[1] ? 'tables' : m[2] || 'schema';
    return this.send(() => ({ t, table, body, accept }));
  }

  subscribe(table, after, filter, everyMs, onFrame, onError, window) {
    let id = null, live = true;
    this.send((rid) => { id = rid; this.subs.set(rid, (msg) => onFrame(msg.t === 'gap' ? { t: 'gap', epoch: msg.epoch, seq: msg.seq, recoverable: false }
      : { t: 'msg', epoch: msg.epoch, seq: msg.seq, first: msg.first, data: msg.data }));
      return { t: 'sub', table, after, filter: filter || undefined, conflate: everyMs ? { everyMs } : undefined, window }; })
      .then((body) => {
        if (!live) return;
        if (body && body.recovered === false) onFrame({ t: 'gap', recoverable: false });
        else if (window) onFrame({ t: 'subd', sub: id, table });
      }, (e) => { if (onError) onError(e); });
    return () => { live = false; if (id) { this.subs.delete(id); this.ch.post({ rdp: 1, t: 'unsub', id }); } };
  }

  /** A windowed subscription's window changed (PROTOCOL 16 `win`). */
  window(sub, keys, top) {
    return this.send(() => ({ t: 'win', sub, keys: keys || [], top: !!top })).then(() => undefined);
  }
}

/**
 * The live tables of a view for Drishti Elements' painter (its options.tables; contract 1.2, panel kind live): for a dataset's live
 * reference {table, window, top, filter}, page(query) reads one page and follow(after, keys, top, onPatch, onGap) subscribes with that
 * window (PROTOCOL 16), passes each patch on in seq order and calls onGap on a gap, another epoch or a skipped seq (the painter reads
 * its page again). The answer's window(keys, top) moves the window; stop() ends it.
 * @param {DataClient} client
 * @returns {function(Object): {page: function(Object): Promise<Object>, follow: function(*, Array, boolean, function, function): Object}}
 */
export function liveTables(client) {
  return (ref) => ({
    page: (query) => client.page(ref.table, query),
    follow(after, keys, top, onPatch, onGap) {
      let sub = null, last = after, ended = false;
      const gap = (e) => { if (!ended) { ended = true; stop(); onGap(e); } };
      const stop = client.follow(ref.table, after, (f) => {
        if (f.t === 'subd') { sub = f.sub; return; }
        if (f.t === 'msg' && f.epoch === last.epoch && (f.first || f.seq) === last.seq + 1) { last = { epoch: f.epoch, seq: f.seq }; onPatch(f.data); return; }
        if (f.t === 'msg' && f.epoch === last.epoch && f.seq <= last.seq) return;
        gap(f);
      }, gap, { window: { keys, top: !!top } });
      return {
        window(k, t) { return sub ? client.window(sub, k, !!t) : Promise.resolve(); },
        stop() { ended = true; stop(); }
      };
    }
  });
}
