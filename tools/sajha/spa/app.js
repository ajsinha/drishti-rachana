// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// The board page of the GenUI kit: one <rachana-view> that receives {sutra, tables} from publish_ui over Server-Sent Events and paints
// every new version in place. No code arrives with a board: the Sutra is data, compiled and checked again here by the runtime bundle;
// the tables either arrive as rows (answered in the page by the runtime's own query, StaticHost) or are read from one Data Protocol host
// the kit allowed (the page's Content-Security-Policy lists it). Every asset comes from this server (the drishti-rachana wheel): no CDN.
import { StaticHost } from '/static/rachana/src/static_files_host.js';
import { initialisedRuntime } from '/static/rachana/src/screen.js';
import '/static/rachana/src/rachana_view.js';

const id = decodeURIComponent(location.pathname.split('/').filter(Boolean)[1] || '');
const view = document.getElementById('view');
const $ = (x) => document.getElementById(x);
const LOCAL = location.origin + '/board-data';      // the in-page Data Protocol base for pushed rows (never leaves the page)
const libraries = new Map();                         // name -> files, fetched once
let tables = {};
let host = null;

const show = (text) => { const p = $('problem'); p.textContent = text || ''; p.hidden = !text; };   // text only, never markup
const state = (s) => { $('state').dataset.state = s; $('state').textContent = s; };

async function filesOf(names) {
  const out = {};
  for (const n of names || []) {
    if (!libraries.has(n)) libraries.set(n, fetch(`/libraries/${encodeURIComponent(n)}.json`).then((r) => r.json()));
    Object.assign(out, await libraries.get(n));
  }
  return out;
}

async function localFetch(url, init) {
  const u = typeof url === 'string' ? url : url.url;
  if (!u.startsWith(LOCAL + '/')) return fetch(url, init);
  host = host || new StaticHost(tables, {}, await initialisedRuntime(view.getAttribute('runtime')));
  const r = host.handle((init && init.method) || 'GET', u.slice(LOCAL.length), init && init.body);
  return new Response(JSON.stringify(r.doc), { status: r.status, headers: { 'Content-Type': 'application/json' } });
}

async function draw(board) {
  document.title = board.title || 'Board';
  $('title').textContent = board.title || 'Board';
  $('meta').textContent = `version ${board.version} · ${board.updated}`;
  show('');
  view.files = await filesOf(board.libraries);
  if (board.protocol) {                              // the tables are read from the Data Protocol host itself
    view.fetch = null;
    view.setAttribute('allow', new URL(board.protocol).origin);
    view.setAttribute('data', board.protocol);
  } else {                                           // rows pushed by the kit
    tables = Object.fromEntries(Object.entries(board.tables).map(([n, t]) => [n, t.rows || []]));
    host = null;
    view.fetch = localFetch;
    view.removeAttribute('allow');
    view.setAttribute('data', LOCAL);
  }
  view.sutra = board.sutra;                          // compiled, checked and painted by the runtime; a new version redraws in place
}

view.addEventListener('rachana-view:error', (e) => show(String((e.detail && e.detail.error && e.detail.error.message) || e.detail && e.detail.error || 'error')));

function connect() {
  const es = new EventSource(`/b/${encodeURIComponent(id)}/events`);
  es.addEventListener('open', () => state('live'));
  es.addEventListener('board', (e) => { draw(JSON.parse(e.data)).catch((err) => show(String(err && err.message || err))); });
  es.addEventListener('error', () => state(es.readyState === EventSource.CLOSED ? 'closed' : 'reconnecting'));
}

if (id) connect(); else show('No board id in the address.');
