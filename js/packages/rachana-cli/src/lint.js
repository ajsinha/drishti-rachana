/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, relative } from "node:path";
import { tools } from "./bundle.js";
import { UsageError } from "./errors.js";

export const SUFFIX = ".sutra.yaml";

/** Every *.sutra.yaml under the paths (files as given; folders searched, dot folders and node_modules skipped). */
export function findSutras(paths) {
  const out = [];
  const walk = (dir) => {
    for (const n of readdirSync(dir).sort()) {
      if (n.startsWith(".") || n === "node_modules") continue;
      const p = join(dir, n);
      if (statSync(p).isDirectory()) walk(p); else if (n.endsWith(SUFFIX)) out.push(p);
    }
  };
  for (const p of paths) {
    if (!existsSync(p)) throw new UsageError(`no such file or folder: ${p}`);
    if (statSync(p).isDirectory()) walk(p); else out.push(p);
  }
  return out;
}

/** Per file: { file, ok, problems } from the bundle's check (the problem objects of SPEC section 7, identical to the JVM and Python). */
export async function lintFiles(files, profiles = "core") {
  const r = await tools(profiles);
  return files.map((file) => {
    const res = JSON.parse(r.check(JSON.stringify({ sutra: readFileSync(file, "utf8"), file })));
    return { file, ok: res.ok, problems: res.problems };
  });
}

export function sarif(results) {
  return {
    version: "2.1.0", $schema: "https://json.schemastore.org/sarif-2.1.0.json",
    runs: [{ tool: { driver: { name: "rachana", informationUri: "https://github.com/ajsinha/drishti-rachana" } },
      results: results.flatMap((r) => r.problems.map((p) => ({
        ruleId: p.code, level: p.severity === "error" ? "error" : p.severity === "warning" ? "warning" : "note",
        message: { text: p.message },
        locations: [{ physicalLocation: { artifactLocation: { uri: r.file },
          region: { startLine: p.range?.start?.line ?? 1, startColumn: p.range?.start?.column ?? 1 } } }] }))) }],
  };
}

export async function lint(paths, flags, io) {
  const fmt = flags.format ?? "text";
  if (!["text", "json", "sarif"].includes(fmt)) throw new UsageError("--format is text, json or sarif");
  const results = await lintFiles(findSutras(paths), flags.profiles ?? "core");
  const failing = results.flatMap((r) => r.problems).filter((p) => p.severity === "error" || (flags.strict && p.severity === "warning")).length;
  if (fmt === "json") io.out(JSON.stringify(results, null, 2));
  else if (fmt === "sarif") io.out(JSON.stringify(sarif(results), null, 2));
  else {
    for (const r of results) for (const p of r.problems) {
      const s = p.range?.start ?? {};
      io.out(`${relative(".", r.file)}:${s.line ?? 1}:${s.column ?? 1}: ${p.severity} ${p.code}: ${(p.message ?? "").split("\n")[0]}`);
    }
    io.out(`${results.length} file(s), ${failing} failing problem(s)`);
  }
  return failing ? 1 : 0;
}
