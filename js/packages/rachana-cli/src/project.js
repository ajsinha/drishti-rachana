/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * The language level a project names in its rachana.yaml (`level: 1.2`), looked for in the folder and the one above it; null when the
 * project names none (the tools' default then applies, SPEC 7.2 rule 6). `rachana new` writes it, so a tool upgrade never moves a project.
 */
export function projectLevel(dir) {
  for (const d of [dir, join(dir, "..")]) {
    const f = join(d, "rachana.yaml");
    if (!existsSync(f)) continue;
    const m = /^level:\s*"?(\d+\.\d+)"?\s*(#.*)?$/m.exec(readFileSync(f, "utf8"));
    if (m) return m[1];
  }
  return null;
}
