// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// A Sutra bound to its tables, in the JavaScript runtime: the bundle's screen functions (Core's LiveScreen: the plan, decoding, keyed
// patches, binding, frames) driven by a DataClient (transport). One per view.

/** Loads the runtime bundle (an ES module built by tools/js/build_js.py) and initialises it once per bundle address. */
const loaded = new Map();
export function loadRuntime(bundleUrl, formatsTexts, profiles) {
  const key = bundleUrl;
  if (!loaded.has(key)) {
    loaded.set(key, import(bundleUrl).then((m) => { m.init(profiles || 'core', JSON.stringify(formatsTexts || [])); return m; }));
  }
  return loaded.get(key);
}

function call(rt, op, req) {
  const r = JSON.parse(rt.screen(op, JSON.stringify(req)));
  if (r && r.error) {
    const e = new Error(r.error.message || (r.error.problems || []).map((p) => `${p.code} ${p.message}`).join('; ') || r.error.code);
    e.code = r.error.code; e.problems = r.error.problems;
    throw e;
  }
  return r;
}

export class Screen {
  /**
   * @param {Object} rt the initialised runtime bundle
   * @param {Object} o {sutra: text, level: '1.1', today?: 'yyyy-mm-dd', ctl?: {}, record?: {}, client?: DataClient}
   */
  constructor(rt, o) {
    this.rt = rt;
    this.client = o.client || null;
    this.ctl = Object.assign({}, o.ctl || {});
    const given = { ctl: this.ctl };
    if (o.today) given.today = o.today;
    if (o.record) given.record = o.record;
    const opened = call(rt, 'open', { sutra: o.sutra, since: o.level || '1.1', profile: 'core', given });
    this.id = opened.id;
    this.tables = opened.tables;
    this.reads = new Map();     // table -> {version, live}
    this.stops = [];
    this.plan = null;
  }

  /** Reads every table through the plan (push-down where the table can); tables the host lacks read as no rows. */
  async load() {
    const schemas = {};
    if (this.client) {
      for (const t of this.tables) {
        try { schemas[t] = await this.client.schema(t); } catch (e) { if (e.code !== 'rdp.table.unknown') throw e; }
      }
    }
    this.plan = call(this.rt, 'plan', { id: this.id, schemas: this.client ? schemas : undefined, ctl: this.ctl });
    for (const t of this.plan.tables) {
      if (!schemas[t.name] || this.reads.has(t.name)) continue;
      const { rows, version } = await this.client.read(t.name, t.query);
      call(this.rt, 'table', { id: this.id, name: t.name, schema: schemas[t.name], rows });
      this.reads.set(t.name, { version, live: !!t.live });
    }
    return this.plan;
  }

  /** The ViewModel now. */
  view() { return call(this.rt, 'view', { id: this.id, ctl: this.ctl }).viewModel; }

  /** Applies a keyed patch to a table: {frames} (maybe empty) or, when the screen's shape changed, {viewModel}. */
  patch(table, patch) { return call(this.rt, 'patch', { id: this.id, table, patch }); }

  /**
   * Follows every live table (PROTOCOL 8.3): messages applied in seq order, older ones dropped; a gap, another epoch or a skipped seq
   * reads the table again and continues from the new version (onChange receives {viewModel} then). onChange gets each result.
   */
  live(onChange, onError) {
    for (const [table, read] of this.reads) {
      if (!read.live || !read.version) continue;
      let last = read.version;
      const start = () => {
        const stop = this.client.follow(table, last, (f) => {
          if (f.t === 'msg' && f.epoch === last.epoch && f.seq === last.seq + 1) {
            last = { epoch: f.epoch, seq: f.seq };
            onChange(this.patch(table, f.data));
          } else if (f.t === 'msg' && f.epoch === last.epoch && f.seq <= last.seq) {
            // already applied
          } else {
            stop();
            this.resync(table).then((v) => { last = v; onChange({ viewModel: this.view() }); start(); }, onError);
          }
        }, onError);
        this.stops.push(stop);
      };
      start();
    }
  }

  async resync(table) {
    const t = this.plan.tables.find((x) => x.name === table);
    const { rows, version } = await this.client.read(table, t.query);
    call(this.rt, 'table', { id: this.id, name: table, schema: await this.client.schema(table), rows });
    return version;
  }

  /** New control values: the plan may change (pushed-down filters read them), so the tables are read again. */
  async controls(next) {
    Object.assign(this.ctl, next);
    this.stop();
    this.reads.clear();
    await this.load();
    return this.view();
  }

  stop() { this.stops.splice(0).forEach((s) => s()); }

  close() { this.stop(); call(this.rt, 'close', { id: this.id }); }
}
