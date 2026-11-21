// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// <rachana-view>: a Sutra compiled and bound in the page and painted by Drishti Elements' painter (<elements-view>, Elements 3.2.0-dev+824d83b,
// vendored under js/vendor/drishti-elements/3.2.0-dev+824d83b: load drishti-elements.js before this module). The element holds no meaning of its own:
// the bundle compiles, plans, binds and diffs; the DataClient fetches; the painter paints.
//
//   <script src="/vendor/drishti-elements/3.2.0-dev+824d83b/drishti-elements.js"></script>
//   <script type="module" src="/rachana/src/rachana_view.js"></script>
//   <rachana-view src="/sutras/sales.sutra.yaml" data="/rdp/v1" runtime="/rachana/dist/rachana.runtime.js"
//                 formats="/spec/1/formats.yaml" live></rachana-view>
//
// Attributes: src (the Sutra's address), data (the Data Protocol base), runtime (the runtime bundle), formats (format tables, comma
// separated, Core's first), level (language level, default 1.1), today (the as-of date), allow (other data origins, space separated),
// live (follow the live tables), form (rows as json, arrow or ndjson; arrow needs js/vendor/apache-arrow's script loaded first and is
// asked only of tables that declare it). Properties: sutra (the text instead of src), record (the host's record), controls (values),
// host ({notify(effect, where), run(effect, where)}: the page's own handling of a Sutra's notify and host effects; the painter runs none), linkResolver (link => absolute address or null), fetch (the host's fetch), files (the libraries a Sutra uses: {'rachana_libs/<library>/<path>': text}).
//
// Portable-view principles: every address the element is given is resolved once, against the page, to an absolute address; a link of
// the view gets its address only from the host's linkResolver and only when that address is absolute (a relative one would mean
// something else in every page that embeds the view): otherwise the link is a button that raises elements-view:link for the host.
// Events: rachana-view:ready {viewModel}, rachana-view:change {frames | viewModel}, rachana-view:error {error}.
import { DataClient } from './data_client.js';
import { Screen, loadRuntime } from './screen.js';

export const RACHANA_VIEW = 'rachana-view';
const ABSOLUTE = /^[a-z][a-z0-9+.-]*:/i;

/** Registers <rachana-view> (or another tag); nothing when there is no window or the tag is taken. */
export function defineRachanaView(o) {
  o = o || {};
  const w = o.window || (typeof window !== 'undefined' ? window : null), tag = o.tag || RACHANA_VIEW;
  if (!w || !w.customElements || w.customElements.get(tag)) return false;

  class RachanaView extends w.HTMLElement {
    static get observedAttributes() { return ['src', 'data', 'runtime', 'formats', 'level', 'today', 'allow', 'live', 'form', 'libraries']; }

    constructor() { super(); this._sutra = null; this.record = null; this.controls = {}; this.linkResolver = null; this.fetch = null; this.files = null; this.host = null; this.screen = null; this._gen = 0; }

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
      const client = this.getAttribute('data') ? new DataClient({ base: this.abs(this.getAttribute('data')), fetch: f, origin: w.location.origin,
        allow: (this.getAttribute('allow') || '').split(/\s+/).filter(Boolean), form: this.getAttribute('form') || 'json', arrow: w.Arrow || null }) : null;
      const screen = new Screen(rt, { sutra: text, level: this.getAttribute('level') || '1.1', today: this.getAttribute('today') || undefined,
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
      const host = { bind: (req) => (this.screen ? this.screen.setState(req.state) : null),
        notify: (e, where) => { if (hooks().notify) hooks().notify(e, where); }, run: (e, where) => { if (hooks().run) hooks().run(e, where); } };
      painter.options = { complete: this.hasAttribute('complete'), host, links: { href: (link) => { const h = resolver ? resolver(link) : null; return h && ABSOLUTE.test(h) ? h : null; } } };
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

    fail(error) { this.emit('error', { error }); }

    emit(name, detail) { this.dispatchEvent(new w.CustomEvent(`rachana-view:${name}`, { detail, bubbles: true, composed: true })); }
  }
  w.customElements.define(tag, RachanaView);
  return true;
}

defineRachanaView();
