/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { existsSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));

/** The dist folder of the compiler bundles: RACHANA_DIST, or ../rachana/dist (built by tools/js/build_js.py), or, when that is
 *  not built, the committed copies in python/src/drishti_rachana/bundle (a zip of the platform runs without a build). */
export function distDir() {
  if (process.env.RACHANA_DIST) return process.env.RACHANA_DIST;
  const dist = join(HERE, "..", "..", "rachana", "dist");
  const committed = join(HERE, "..", "..", "..", "..", "python", "src", "drishti_rachana", "bundle");
  return !existsSync(join(dist, "rachana.tools.js")) && existsSync(join(committed, "rachana.tools.js")) ? committed : dist;
}

let cached;
/** The tools bundle, initialised once. The facade takes (profiles, formats JSON). */
export async function tools(profiles = "core") {
  if (cached) return cached;
  const f = join(distDir(), "rachana.tools.js");
  if (!existsSync(f)) throw new Error(`${f} is missing: build it with python3 tools/js/build_js.py (or set RACHANA_DIST)`);
  const m = await import(pathToFileURL(f).href);
  m.init(profiles, "[]");
  return (cached = m);
}
