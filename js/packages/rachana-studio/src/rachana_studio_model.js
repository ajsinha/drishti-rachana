// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
//
// Rachana Studio's model: plain functions, no DOM, run in the page and in Node (the unit tests). It reads the typed parameters of a
// library's fragments (the YAML reader is handed in: js-yaml in the page), makes the form schema of a fragment's parameters (Drishti
// Elements' Form takes it), turns the form's values into the `use:` block, and puts that block into the Sutra's text. The meaning of
// a Sutra stays the compiler bundle's: nothing here parses a Sutra beyond finding its `data:` names, and the result is checked by
// the bundle like any text.

const FRAGMENT = /(?:^|\/)fragments\/([\w-]+)\.v(\d+)\.fragment\.yaml$/;
const VALUE_TYPES = new Set(["text", "number", "integer", "decimal", "boolean", "date", "datetime", "duration", "money"]);

/** The typed fragments of one library (as the server sends it: {name, version, files}): the highest major of each name. */
export function fragmentsOf(lib, yaml) {
  const best = new Map(), skipped = [];
  for (const [path, text] of Object.entries(lib.files)) {
    const m = path.match(FRAGMENT);
    if (!m) continue;
    let doc;
    try { doc = yaml.load(text); } catch (e) { skipped.push(`${path}: ${e.message.split("\n")[0]}`); continue; }
    const params = doc && doc.params;
    if (!params || typeof params !== "object" || !Object.values(params).every((v) => v && typeof v === "object" && v.type)) { skipped.push(`${path}: not in the typed form`); continue; }
    const major = Number(m[2]);
    if (!best.has(m[1]) || best.get(m[1]).major < major)
      best.set(m[1], { library: lib.name, version: lib.version, name: m[1], major, summary: (doc.doc && doc.doc.summary) || "", description: (doc.doc && doc.doc.description) || "",
        category: (doc.doc && doc.doc.category) || "", tags: (doc.doc && doc.doc.tags) || [], params });
  }
  return { fragments: [...best.values()].sort((a, b) => a.category.localeCompare(b.category) || a.name.localeCompare(b.name)), skipped };
}

/** The tables a Sutra can hand a fragment: its `data:` entries (columns from the project's data file or the rows written inline) and
 * the project's other data files (declared when the fragment is inserted). [{name, columns:[{name,type}], declared}] */
export function tablesOf(text, project, yaml) {
  const out = [], byName = new Map(project.map((t) => [t.name, t]));
  let doc = null;
  try { doc = yaml.load(text); } catch { doc = null; }
  const data = doc && typeof doc === "object" && doc.data && typeof doc.data === "object" ? doc.data : {};
  for (const [name, e] of Object.entries(data)) {
    let columns = [];
    if (e && e.table && byName.has(e.table)) columns = byName.get(e.table).columns;
    else if (e && Array.isArray(e.rows) && e.rows[0] && typeof e.rows[0] === "object")
      columns = Object.keys(e.rows[0]).map((n) => ({ name: n, type: e.rows.every((r) => typeof r[n] === "number") ? "number" : "text" }));
    out.push({ name, columns, declared: true });
  }
  const used = new Set(Object.values(data).map((e) => e && e.table));
  for (const t of project) if (!out.some((o) => o.name === t.name) && !used.has(t.name)) out.push({ name: t.name, columns: t.columns, declared: false });
  return out;
}

const words = (s) => String(s).replace(/[-_]/g, " ").replace(/^./, (c) => c.toUpperCase());
const sameName = (a, b) => a.toLowerCase().replace(/[^a-z0-9]/g, "") === b.toLowerCase().replace(/[^a-z0-9]/g, "");

/** A proposed mapping of a table parameter's columns onto a table's: the same name, else the first unused column of the type. */
export function proposeMapping(want, columns) {
  const used = new Set(), out = {};
  for (const [key, spec] of Object.entries(want)) { const c = columns.find((x) => sameName(x.name, key)); if (c) { out[key] = c.name; used.add(c.name); } }
  for (const [key, spec] of Object.entries(want)) {
    if (out[key] || spec.optional) continue;
    const c = columns.find((x) => !used.has(x.name) && (spec.type === "number" ? x.type === "number" : x.type !== "number"));
    if (c) { out[key] = c.name; used.add(c.name); }
  }
  return out;
}

/** The first table-typed parameter's chosen table name in the values, if any (the `id` parameters name columns of it). */
function chosenTable(frag, values) {
  for (const [name, p] of Object.entries(frag.params)) if (p.type === "table" && values[name] && values[name].from) return values[name].from;
  return null;
}

/** The form of a fragment's parameters: {schema, values}. `values` are the form's current values (or none: the proposals). */
export function formSpec(frag, tables, values = null) {
  const names = tables.map((t) => t.name), colsOf = (n) => (tables.find((t) => t.name === n) || { columns: [] }).columns;
  const props = {}, required = [], init = {};
  const tableName = values ? chosenTable(frag, values) : (names[0] ?? null);
  for (const [name, p] of Object.entries(frag.params)) {
    const base = { title: words(name), description: p.doc || "" };
    if (p.required) required.push(name);
    if (p.type === "table") {
      const want = p.columns || {}, from = values && values[name] ? values[name].from : (names[0] ?? ""), cols = colsOf(from);
      // the column mapping is Drishti Elements' ColumnMap field: the fragment's own columns (targets) on the table's (text is its "string")
      const ty = (t) => (t === "text" ? "string" : t || ""), targets = Object.entries(want).map(([k, spec]) => ({ name: k, type: ty(spec.type), required: !spec.optional, description: spec.doc || "" }));
      props[name] = { ...base, type: "object", properties: { from: { type: "string", title: "Table", enum: names, widget: "select" }, ...(targets.length ? { columns: { type: "columnmap", title: "Column mapping", options: { targets, columns: cols.map((c) => ({ name: c.name, type: ty(c.type) })) } } } : {}) }, required: ["from"] };
      init[name] = values && values[name] ? values[name] : { from, columns: proposeMapping(want, cols) };
    } else if (p.type === "id") {
      const cols = colsOf(tableName);
      props[name] = { ...base, type: "string", ...(cols.length ? { enum: cols.map((c) => c.name), widget: "select" } : {}) };
      if (!values && cols.length) { const c = cols.find((x) => sameName(x.name, name)) || cols[0]; init[name] = c.name; }
    } else if (p.type === "columns") {
      const cols = colsOf(tableName);
      props[name] = { ...base, type: "array", items: { type: "object", properties: { label: { type: "string", title: "Label" }, bind: { type: "string", title: "Column", ...(cols.length ? { enum: cols.map((c) => c.name), widget: "select" } : {}) },
        fmt: { type: "string", title: "Format" }, total: { type: "boolean", title: "Total" } }, required: ["label", "bind"] }, ...(p.required ? { minItems: 1 } : {}) };
      if (!values) init[name] = cols.slice(0, 4).map((c) => ({ label: words(c.name), bind: c.name, ...(c.type === "number" ? { fmt: "amount0", total: true } : {}) }));
    } else if (p.type === "enum") {
      props[name] = { ...base, type: "string", enum: p.options || [], widget: "select" };
      if (p.default !== undefined) init[name] = p.default;
    } else if (p.type === "boolean") {
      props[name] = { ...base, type: "boolean" };
      if (p.default !== undefined) init[name] = p.default;
    } else {
      // value and name classes: text typed as it is meant (a number or date is an expression or a literal); the compiler checks it
      const hint = VALUE_TYPES.has(p.type) && p.type !== "text" ? `${p.type}: a value or an expression` : p.type === "format" ? "a format name" : "";
      props[name] = { ...base, type: "string", description: [p.doc, hint].filter(Boolean).join(" - ") };
      if (p.default !== undefined) init[name] = String(p.default);
    }
  }
  return { schema: { type: "object", title: frag.name, properties: props, required }, values: values ?? init };
}

/** The `with:` mapping of a use, from the form's values: blank values left out (the fragment's default applies). */
export function withOf(frag, values) {
  const out = {};
  for (const [name, p] of Object.entries(frag.params)) {
    const v = values[name];
    if (v === undefined || v === null || v === "" || (Array.isArray(v) && !v.length)) continue;
    if (p.type === "table") {
      if (!v.from) continue;
      const map = {};
      for (const [k, c] of Object.entries(v.columns || {})) if (c && c !== k) map[k] = c;
      out[name] = Object.keys(map).length ? { from: `$.${v.from}`, columns: map } : `$.${v.from}`;
    } else if (p.type === "columns") {
      out[name] = v.map((c) => { const o = { label: c.label, bind: `@.${c.bind}` }; if (c.fmt) o.fmt = c.fmt; if (c.total) o.total = true; return o; });
    } else if (p.type === "boolean") { if (v !== (p.default === undefined ? false : p.default) || p.required) out[name] = Boolean(v); }
    else if ((p.type === "number" || p.type === "integer") && /^-?\d+(\.\d+)?$/.test(String(v))) out[name] = Number(v);
    else if (p.default !== undefined && String(v) === String(p.default) && !p.required) continue;
    else out[name] = String(v);
  }
  return out;
}

/** One value as a single line of YAML in the style of the examples: `{ a: 1, b: [ x, y ] }`; each scalar is quoted by the YAML library. */
function line(yaml, v) {
  if (Array.isArray(v)) return `[ ${v.map((x) => line(yaml, x)).join(", ")} ]`;
  if (v && typeof v === "object") return `{ ${Object.entries(v).map(([k, x]) => `${line(yaml, k)}: ${line(yaml, x)}`).join(", ")} }`;
  return yaml.dump(v, { lineWidth: -1 }).trim();
}

/** The block of a use, ready to sit under `panels:` at `indent`: `- use: lib/fragment@N` and its `with:`. */
export function useText(frag, values, yaml, indent = "  ") {
  const w = withOf(frag, values), out = [`${indent}- use: ${frag.library}/${frag.name}@${frag.major}`];
  const keys = Object.keys(w);
  if (keys.length) { out.push(`${indent}  with:`); for (const k of keys) out.push(`${indent}    ${k}: ${line(yaml, w[k])}`); }
  return out.join("\n") + "\n";
}

const blank = (l) => /^\s*$/.test(l);

/** The end (exclusive, trailing blank lines left out) of the top-level block that starts at line i. */
function blockEnd(lines, i) {
  let e = i + 1;
  while (e < lines.length && (blank(lines[e]) || /^\s/.test(lines[e]) || /^#/.test(lines[e]))) e++;
  while (e > i + 1 && (blank(lines[e - 1]) || /^#/.test(lines[e - 1]))) e--;
  return e;
}

/** The Sutra text with the use inserted at the end of `panels:` (or a new `panels:`), the table declared when it is only a project
 * data file, and the library added to the Sutra's `libraries:` (a written list, or a new line after `version:`). {text} or {error}. */
export function insertUse(text, frag, values, tables, yaml) {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  if (lines[lines.length - 1] === "") lines.pop();
  const pi = lines.findIndex((l) => /^panels:/.test(l));
  if (pi >= 0 && !/^panels:\s*(#.*)?$/.test(lines[pi])) return { error: "panels: is written inline; Studio adds to a block list (panels: then - items)" };
  let indent = "  ";
  if (pi >= 0) { const first = lines.slice(pi + 1).find((l) => /^\s*- /.test(l)); if (first) indent = first.match(/^(\s*)- /)[1]; }
  const item = useText(frag, values, yaml, indent).replace(/\n$/, "").split("\n");
  const wanted = Object.entries(frag.params).filter(([, p]) => p.type === "table").map(([n]) => values[n] && values[n].from).filter(Boolean);
  const declare = [...new Set(wanted)].filter((n) => (tables.find((t) => t.name === n) || {}).declared === false);
  if (pi >= 0) lines.splice(blockEnd(lines, pi), 0, ...item); else lines.push("panels:", ...item);
  if (declare.length) {
    const di = lines.findIndex((l) => /^data:/.test(l)), entries = declare.map((n) => `{ table: ${n} }`);
    if (di >= 0 && !/^data:\s*(#.*)?$/.test(lines[di])) return { error: "data: is written inline; Studio adds to a block of entries" };
    if (di >= 0) {
      const first = lines.slice(di + 1).find((l) => /^\s+\S/.test(l)), ind = first ? first.match(/^(\s*)/)[1] : "  ";
      lines.splice(blockEnd(lines, di), 0, ...declare.map((n, k) => `${ind}${n}: ${entries[k]}`));
    } else {
      const at = lines.findIndex((l) => /^panels:/.test(l));
      lines.splice(at, 0, "data:", ...declare.map((n, k) => `  ${n}: ${entries[k]}`));
    }
  }
  const li = lines.findIndex((l) => /^libraries:/.test(l)), entry = `${frag.library}@${frag.major}`;
  if (li < 0) {
    const vi = lines.findIndex((l) => /^version:/.test(l)), si = lines.findIndex((l) => /^sutra:/.test(l));
    lines.splice(Math.max(vi, si) + 1, 0, `libraries: [${entry}]`);
  } else if (/^libraries:\s*\[/.test(lines[li])) {
    const m = lines[li].match(/^libraries:\s*\[(.*)\](.*)$/), have = m[1].split(",").map((x) => x.trim()).filter(Boolean);
    if (!have.some((x) => x.split("@")[0] === frag.library)) lines[li] = `libraries: [${[...have, entry].join(", ")}]${m[2]}`;
  } else if (/^libraries:\s*(#.*)?$/.test(lines[li])) {
    const e = blockEnd(lines, li), first = lines.slice(li + 1, e).find((l) => /^\s*- /.test(l)), ind = first ? first.match(/^(\s*)- /)[1] : "  ";
    if (!lines.slice(li + 1, e).some((l) => l.replace(/^\s*- /, "").trim().split("@")[0] === frag.library)) lines.splice(e, 0, `${ind}- ${entry}`);
  }
  return { text: lines.join("\n") + "\n" };
}

/** The bundle's problems as the code editor's: {line, from?, severity, message}. */
export function editorProblems(result) {
  return (result.problems || []).map((p) => ({ line: Math.max(1, (p.range && p.range.start && p.range.start.line) || 1),
    ...(p.range && p.range.start && p.range.start.column ? { from: Math.max(0, p.range.start.column - 1) } : {}), severity: p.severity === "error" ? "error" : "warning", message: `${p.code}: ${String(p.message).split("\n")[0]}` }));
}
