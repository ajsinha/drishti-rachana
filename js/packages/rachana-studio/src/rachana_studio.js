// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// Rachana Studio (decision MK-1: mock-up A, the three panes, with B's command bar). Left: the fragments of the project's libraries;
// middle: the live <rachana-view>; right: the Sutra source with its problems, the parameter form of the chosen fragment, and the
// unsaved changes. Every control is a Drishti Elements widget (Select, NavList, SplitPane, CodeEditor, Form with its columnmap field, CodeDiff, toast); this file only
// arranges them and moves text between them. The compiler bundle (on the server) checks and the runtime (in the page) paints.
import { fragmentsOf, tablesOf, formSpec, insertUse, proposeMapping, editorProblems } from "./rachana_studio_model.js";

const HEADER = { "content-type": "application/json", "x-rachana-studio": "1" };

async function call(path, body) {
  const r = await fetch(path, body === undefined ? undefined : { method: "POST", headers: HEADER, body: JSON.stringify(body) });
  if (!r.ok) throw new Error(`${path}: ${r.status} ${(await r.text()).slice(0, 200)}`);
  return r.json();
}

export async function startStudio(root) {
  const E = window.Drishti.elements, core = E.core, inputs = E.inputs, yaml = window.jsyaml, doc = root.ownerDocument;
  const h = (tag, p, kids) => core.h(doc, tag, p, kids);
  const project = await call("/api/project");
  const libs = project.libraries.map((l) => ({ ...l, ...fragmentsOf(l, yaml) }));
  const fragments = libs.flatMap((l) => l.fragments);
  const libFiles = {};
  for (const l of project.libraries) for (const [n, t] of Object.entries(l.files)) libFiles[`rachana_libs/${l.name}/${n}`] = t;

  const st = { path: null, saved: "", text: "", problems: [], frag: null, timer: null, checks: 0 };

  // ---- the command bar (B): files, the AI prompt (a placeholder), save
  const fileHost = h("div", { cls: "rs-file" });
  const aiInput = h("input", { cls: "di-field", attrs: { type: "text", disabled: "", placeholder: "Describe a screen...", "aria-label": "Describe a screen (AI)" }, data: { rs: "ai" } });
  const aiBtn = h("button", { cls: "di-btn", text: "Generate", attrs: { type: "button", disabled: "" } });
  const status = h("span", { cls: "rs-status", attrs: { role: "status" }, data: { rs: "status" } });
  const saveBtn = h("button", { cls: "di-btn di-primary", text: "Save", attrs: { type: "button", disabled: "" }, data: { rs: "save" } });
  const bar = h("header", { cls: "rs-bar" }, [h("span", { cls: "rs-brand", text: "Rachana Studio" }), fileHost,
    h("div", { cls: "rs-ai" }, [aiInput, aiBtn, h("span", { cls: "rs-note", text: "AI authoring arrives with the GenAI release." })]),
    h("span", { cls: "rs-grow" }), status, saveBtn]);

  // ---- left pane: the palette, a NavList (search, headings by library, arrows and Enter are the widget's)
  const items = fragments.map((f) => ({ id: `${f.library}/${f.name}`, label: f.name, detail: [f.category, f.summary].filter(Boolean).join(" - "), group: f.library, frag: f }));
  const byId = new Map(items.map((i) => [i.id, i.frag]));
  const count = h("div", { cls: "rs-count", text: `${items.length} from ${libs.map((l) => `${l.name} ${l.version}`).join(", ") || "no library"}` });
  const palBody = h("div", { cls: "rs-body", data: { rs: "palette" } });
  const nav = new inputs.NavList(palBody, { items, label: "Fragments", placeholder: "Search fragments...", empty: "No fragment matches" });
  nav.on("change", ({ value }) => { if (byId.has(value) && byId.get(value) !== st.frag) chooseFragment(byId.get(value)); });
  const palette = h("section", { cls: "rs-pane rs-palette" }, [h("h2", { text: "Fragments" }), count, palBody]);
  const drawPalette = () => { if (st.frag) nav.setValue(`${st.frag.library}/${st.frag.name}`); };

  // ---- middle pane: the live preview
  const view = doc.createElement("rachana-view");
  view.setAttribute("data", "/data");
  view.setAttribute("runtime", "/rachana/rachana.runtime.js");
  view.setAttribute("complete", "");
  view.files = libFiles;
  const previewNote = h("div", { cls: "rs-note", data: { rs: "preview-note" } });
  const preview = h("section", { cls: "rs-pane rs-preview" }, [h("h2", { text: "Live preview" }), h("div", { cls: "rs-body" }, [previewNote, view])]);
  view.addEventListener("rachana-view:ready", () => { previewNote.textContent = ""; root.dataset.painted = String(Number(root.dataset.painted || 0) + 1); });
  view.addEventListener("rachana-view:error", (e) => { previewNote.textContent = `The preview cannot show this text: ${e.detail.error.message}`; root.dataset.previewError = e.detail.error.message; });

  // ---- right column: source, fragment form, changes
  const srcTitle = h("h2", { text: "Sutra" });
  const srcHost = h("div", { cls: "rs-code", data: { rs: "source" } });
  const srcBody = h("div", { cls: "rs-body" }, [srcHost]);
  const formTitle = h("h2", { text: "Fragment properties" });
  const formHost = h("div", { data: { rs: "form" } }, [h("p", { cls: "rs-note", text: "Choose a fragment on the left to set its parameters and insert it." })]);
  const diffTitle = h("h2", { text: "Changes" });
  const diffHost = h("div", { data: { rs: "diff" } });
  const srcPane = h("section", { cls: "rs-pane" }, [srcTitle, srcBody]), formPane = h("section", { cls: "rs-pane" }, [formTitle, h("div", { cls: "rs-body" }, [formHost])]);
  const diffPane = h("section", { cls: "rs-pane" }, [diffTitle, h("div", { cls: "rs-body" }, [diffHost])]);

  // ---- the page frame: SplitPane (palette | preview | the right column, itself a vertical SplitPane)
  const mainHost = h("main", { cls: "rs-main" }), rightHost = h("div", { cls: "rs-split-right" });
  const rightSplit = new inputs.SplitPane(rightHost, { direction: "vertical", label: "Source, properties and changes", persistKey: "rachana-studio-right",
    panes: [{ id: "source", content: srcPane, size: 1.3, min: 120 }, { id: "form", content: formPane, size: 1, min: 90 }, { id: "diff", content: diffPane, size: 0.8, min: 70 }] });
  const mainSplit = new inputs.SplitPane(mainHost, { direction: "horizontal", label: "Fragments, preview and editing", persistKey: "rachana-studio-main",
    panes: [{ id: "palette", content: palette, size: 1, min: 200 }, { id: "preview", content: preview, size: 2, min: 260 }, { id: "right", content: rightHost, size: 2, min: 300 }] });
  root.append(bar, mainHost);

  // ---- the editor, with the bundle's check as its live lint
  const check = async (text) => {
    const mine = ++st.checks;
    const res = await call("/api/check", { sutra: text, file: st.path || "case.sutra.yaml" });
    if (mine === st.checks) { st.problems = editorProblems(res); root.dataset.problems = String(st.problems.length); }
    return editorProblems(res);
  };
  const editor = new inputs.CodeEditor(srcHost, { mode: "yaml", value: "", height: 240, lint: check, lintDelay: 300, label: "Sutra source", keys: { "Ctrl-S": () => save(), "Cmd-S": () => save() } });
  editor.on("lint", ({ problems }) => { srcTitle.textContent = `Sutra - ${problems.length} problem${problems.length === 1 ? "" : "s"}`; });
  const fit = () => { if (editor.cm) editor.cm.setSize(null, Math.max(90, srcBody.clientHeight - 16 - (editor.list.hidden ? 0 : Math.min(editor.list.offsetHeight, 120)))); };
  new ResizeObserver(fit).observe(srcBody);
  editor.on("lint", () => setTimeout(fit, 0));
  editor.on("ready", fit);

  const diff = new inputs.CodeDiff(diffHost, { original: "", value: "", mode: "yaml", layout: "unified", labels: ["Saved", "Editing"] });

  const setStatus = (state, text) => { status.dataset.state = state; status.textContent = text; };
  const dirty = () => st.text !== st.saved;
  const refreshState = () => {
    saveBtn.toggleAttribute("disabled", !st.path || !dirty());
    setStatus(!st.path ? "" : dirty() ? "dirty" : "saved", !st.path ? "" : dirty() ? "Unsaved changes" : "Saved");
    diffTitle.textContent = dirty() ? "Changes (unsaved)" : "Changes";
    diff.setTexts(st.saved, st.text);
  };
  editor.on("change", ({ value }) => {
    st.text = value;
    refreshState();
    clearTimeout(st.timer);
    st.timer = setTimeout(() => { view.sutra = st.text; }, 350);
  });

  async function openSutra(path) {
    if (st.path && dirty() && !window.confirm("Discard the unsaved changes of " + st.path + "?")) { fileSelect.setValue(st.path); return; }
    const text = await (await fetch(`/api/file?path=${encodeURIComponent(path)}`)).text();
    st.path = path; st.saved = text; st.text = text;
    editor.setValue(text);
    refreshState();
    view.sutra = text;
    if (st.frag) chooseFragment(st.frag);
  }
  const fileSelect = new inputs.Select(fileHost, { options: project.sutras.map((p) => ({ value: p, label: p })), label: "Sutra", search: "auto", placeholder: "Open a Sutra" });
  fileSelect.on("change", ({ value }) => { if (value && value !== st.path) openSutra(value).catch(fail); });

  async function save() {
    if (!st.path || !dirty()) return;
    try {
      await call("/api/save", { path: st.path, text: st.text });
      st.saved = st.text;
      refreshState();
      core.toast({ text: `Saved ${st.path}` });
    } catch (e) { fail(e); }
  }
  saveBtn.addEventListener("click", save);
  doc.addEventListener("keydown", (e) => { if ((e.ctrlKey || e.metaKey) && e.key === "s") { e.preventDefault(); save(); } });
  const fail = (e) => { setStatus("bad", String(e.message || e)); core.toast({ text: String(e.message || e) }); };

  // ---- the fragment's form
  let form = null;
  function chooseFragment(frag, values = null) {
    st.frag = frag;
    drawPalette();
    const tables = tablesOf(st.text, project.tables, yaml), spec = formSpec(frag, tables, values);
    formTitle.textContent = `Fragment properties - ${frag.library}/${frag.name}@${frag.major}`;
    formHost.textContent = "";
    formHost.append(h("p", { cls: "rs-note", text: frag.summary }));
    const box = h("div");
    formHost.append(box);
    if (form) form.destroy();
    form = new inputs.Form(box, { schema: spec.schema, value: spec.values, submitLabel: "Insert into Sutra", resetButton: false, doneText: "", onSubmit: (v) => insert(frag, v) });
    form.on("change", ({ path, values: v }) => {
      const m = path.match(/^([\w-]+)\.from$/);
      if (!m) return;
      const t = tablesOf(st.text, project.tables, yaml).find((x) => x.name === v[m[1]].from);
      v[m[1]].columns = proposeMapping(frag.params[m[1]].columns || {}, t ? t.columns : []);
      chooseFragment(frag, v);
    });
    root.dataset.form = `${frag.library}/${frag.name}`;
  }
  function insert(frag, values) {
    const r = insertUse(st.text, frag, values, tablesOf(st.text, project.tables, yaml), yaml);
    if (r.error) { fail(new Error(r.error)); return; }
    editor.setValue(r.text);
    if (editor.cm) editor.cm.setCursor({ line: editor.cm.lineCount() - 1, ch: 0 });
    core.toast({ text: `Inserted ${frag.name}` });
  }

  drawPalette();
  if (project.problems.length) previewNote.textContent = `Libraries not loaded: ${project.problems.join("; ")}`;
  if (project.sutras.length) { fileSelect.setValue(project.sutras[0]); await openSutra(project.sutras[0]); }
  root.dataset.ready = "yes";
  window.__studio = { state: st, nav, mainSplit, rightSplit, editor, view, openSutra, chooseFragment };
}
