// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// <rachana-view>: a Sutra compiled and bound in the page and painted by Drishti Elements' painter (<elements-view>, Elements 3.3.1,
// vendored under js/vendor/drishti-elements/3.3.1: load drishti-elements.js before this module). The element holds no meaning of its own:
// the bundle compiles, plans, binds and diffs; the DataClient fetches; the painter paints.
//
//   <script src="/vendor/drishti-elements/3.3.1/drishti-elements.js"></script>
//   <script type="module" src="/rachana/src/rachana_view.js"></script>
//   <rachana-view src="/sutras/sales.sutra.yaml" data="/rdp/v1" runtime="/rachana/dist/rachana.runtime.js"
//                 formats="/spec/1/formats.yaml" live></rachana-view>
//
// Attributes: src (the Sutra's address), data (the Data Protocol base), runtime (the runtime bundle), formats (format tables, comma
// separated, Core's first), today (the as-of date), allow (other data origins, space separated),
// live (follow the live tables), form (rows as json, arrow or ndjson; arrow needs js/vendor/apache-arrow's script loaded first and is
// asked only of tables that declare it). Properties: sutra (the text instead of src), record (the host's record), controls (values),
// host ({notify(effect, where), run(effect, where)}: the page's own handling of a Sutra's notify and host effects; the painter runs none), linkResolver (link => absolute address or null), fetch (the host's fetch), files (the libraries a Sutra uses: {'rachana_libs/<library>/<path>': text}).
//
// Portable-view principles: every address the element is given is resolved once, against the page, to an absolute address; a link of
// the view gets its address only from the host's linkResolver and only when that address is absolute (a relative one would mean
// something else in every page that embeds the view): otherwise the link is a button that raises elements-view:link for the host.
// Live tables' edits (PROTOCOL 16): the painter's live panel saves a cell or record through options.tables(ref).write; the element sends
// it with its DataClient (the CSRF token from <meta name="rachana-csrf"> when the page has one), asks host.write({table, change}) first
// when the host has it (false refuses), and raises rachana-view:write {table, change, answer | error} after. element.write(table, change)
// does the same for a host's own button.
// Events: rachana-view:ready {viewModel}, rachana-view:change {frames | viewModel}, rachana-view:error {error}, rachana-view:write.
import { DataClient, liveTables } from './data_client.js';
import { Screen, loadRuntime } from './screen.js';

export const RACHANA_VIEW = 'rachana-view';
const ABSOLUTE = /^[a-z][a-z0-9+.-]*:/i;

/** Registers <rachana-view> (or another tag); nothing when there is no window or the tag is taken. */
export function defineRachanaView(o) {
  o = o || {};
  const w = o.window || (typeof window !== 'undefined' ? window : null), tag = o.tag || RACHANA_VIEW;
  if (!w || !w.customElements || w.customElements.get(tag)) return false;

  class RachanaView extends w.HTMLElement {
    static get observedAttributes() { return ['src', 'data', 'runtime', 'formats', 'today', 'allow', 'live', 'form', 'libraries']; }

    constructor() { super(); this.client = null; this._sutra = null; this.record = null; this.controls = {}; this.linkResolver = null; this.fetch = null; this.files = null; this.host = null; this.screen = null; this._gen = 0; }

    set sutra(text) { this._sutra = text; if (this.isConnected) this.refresh(); }

    get sutra() { return this._sutra; }

    connectedCallback() { this.refresh(); }

    disconnectedCallback() { this.dispose(); }

    attributeChangedCallback(n, a, b) { if (a !== b && this.isConnected) this.refresh(); }

    /** The <elements-view> child that paints. */
    get painter() { return this.querySelector('elements-view'); }

    dispose() { if (this.screen) { this.screen.close(); this.screen = null; } }

    abs(address) { return address ? new URL(address, w.document.baseURI).href : null; }

    refresh() {
      const gen = ++this._gen;
      Promise.resolve().then(() => gen === this._gen && this.draw(gen)).catch((error) => this.fail(error));
    }

    async draw(gen) {
      this.dispose();
      const f = this.fetch || ((u, i) => w.fetch(u, i));
      const text = this._sutra != null ? this._sutra : this.getAttribute('src') ? await (await f(this.abs(this.getAttribute('src')))).text() : null;
      if (text == null) return;
      const formats = [];
      for (const u of (this.getAttribute('formats') || '').split(',').map((s) => s.trim()).filter(Boolean)) formats.push(await (await f(this.abs(u))).text());
      let files = this.files || undefined;
      if (!files && this.getAttribute('libraries')) {       // a built site: the manifest lists the library files the page uses ({files: ['rachana_libs/<library>/<path>', ...]})
        const m = await (await f(this.abs(this.getAttribute('libraries')))).json(), got = {};
        await Promise.all(m.files.map(async (n) => { got[n] = await (await f(this.abs(n))).text(); }));
        files = got;
      }
      const rt = await loadRuntime(this.abs(this.getAttribute('runtime') || 'rachana.runtime.js'), formats, 'core');
      if (gen !== this._gen) return;
      const meta = w.document.querySelector && w.document.querySelector('meta[name="rachana-csrf"]');
      const client = this.getAttribute('data') ? new DataClient({ base: this.abs(this.getAttribute('data')), fetch: f, origin: w.location.origin,
        allow: (this.getAttribute('allow') || '').split(/\s+/).filter(Boolean), form: this.getAttribute('form') || 'json', arrow: w.Arrow || null,
        csrf: meta ? meta.getAttribute('content') : null }) : null;
      this.client = client;
      const screen = new Screen(rt, { sutra: text, today: this.getAttribute('today') || undefined,
        ctl: this.controls, record: this.record, client, files });
      this.screen = screen;
      await screen.load();
      if (gen !== this._gen) { screen.close(); return; }
      const vm = screen.view();
      let painter = this.painter;
      if (!painter) { painter = w.document.createElement('elements-view'); this.appendChild(painter); }
      const resolver = this.linkResolver;
      // the screen's state (level 1.2): a click that writes state sends it back here; the bundle builds the view again and the painter draws the panels that read it
      const hooks = () => this.host || {};
      const host = { bind: (req) => {
          if (!this.screen) return null;
          if (!req.controls) return this.screen.setState(req.state);
          Object.assign(this.controls, req.controls);                                  // a control's change (on.change): its new value, the state beside it
          this.screen.stateDelta = Object.assign({}, req.state);
          return this.screen.controls(req.controls);
        },
        check: (req) => (this.screen ? this.screen.check(req.panel, req.values) : []),      // a form's rules are the runtime's, never the painter's
        confirm: hooks().confirm ? (text) => hooks().confirm(text) : undefined, allowed: hooks().allowed ? (name) => hooks().allowed(name) : undefined,
        notify: (e, where) => { if (hooks().notify) hooks().notify(e, where); }, run: (e, where) => { if (hooks().run) hooks().run(e, where); } };
      painter.options = { complete: this.hasAttribute('complete'), host, tables: client ? liveTables(client, (e) => this.onWrite(e)) : undefined, links: { href: (link) => { const h = resolver ? resolver(link) : null; return h && ABSOLUTE.test(h) ? h : null; } } };
      painter.viewModel = vm;
      this.emit('ready', { viewModel: vm });
      if (this.hasAttribute('live')) {
        screen.live((change) => {
          if (change.viewModel) painter.viewModel = change.viewModel;
          else if (change.frames && change.frames.length && painter.handle) change.frames.forEach((fr) => painter.handle.patch(fr));
          this.emit('change', change);
        }, (error) => this.fail(error));
      }
    }

    /** New control values: the tables are read again through the new plan and the view painted again. */
    async setControls(values) {
      Object.assign(this.controls, values);
      if (!this.screen) return null;
      const vm = await this.screen.controls(values);
      if (this.painter) this.painter.viewModel = vm;
      return vm;
    }

    /** The edit hook of the live panels: the host's write({table, change}) may refuse first; every answer or error is raised. */
    onWrite(e) {
      if (e.phase === 'before') return this.host && this.host.write ? this.host.write({ table: e.table, change: e.change }) : true;
      this.emit('write', e.error ? { table: e.table, change: e.change, error: e.error } : { table: e.table, change: e.change, answer: e.answer });
      return true;
    }

    /** A host's own write to a live table of this view's data (PROTOCOL 16), through the same hook. */
    write(table, change) {
      if (!this.client) return Promise.reject(new Error('this view has no data address'));
      return liveTables(this.client, (e) => this.onWrite(e))({ table }).write(change);
    }

    fail(error) { this.emit('error', { error }); }

    emit(name, detail) { this.dispatchEvent(new w.CustomEvent(`rachana-view:${name}`, { detail, bubbles: true, composed: true })); }
  }
  w.customElements.define(tag, RachanaView);
  return true;
}

defineRachanaView();
