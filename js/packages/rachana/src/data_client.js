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

export class DataClient {
  /**
   * @param {Object} o
   * @param {string} o.base the host's base address; resolved to an absolute address against `origin`
   * @param {function} [o.fetch] the host's fetch (default the global one)
   * @param {string[]} [o.allow] other origins the base may be on (default none: the page's own origin only)
   * @param {string} [o.origin] the page's origin (default location.origin; required outside a page)
   * @param {function} [o.subscribe] a host's change stream instead of the minimal SSE form: (table, {epoch, seq}, onFrame) => unsubscribe
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
  async read(table, query) {
    const doc = Object.assign({}, query || {});
    const rows = [];
    let version = null;
    for (;;) {
      const page = await this.json('POST', `/tables/${encodeURIComponent(table)}/query`, doc);
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
