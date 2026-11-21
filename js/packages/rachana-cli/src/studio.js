/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { existsSync, statSync } from "node:fs";
import { UsageError } from "./errors.js";
import { createStudio, listen } from "../../rachana-studio/server/studio_server.js";

/** rachana studio [folder] [--libs <path>[,<path>...]] [--data <dir>] [--port <n>]: Rachana Studio over a folder of Sutras (the
 * editor in the browser; saves back into the folder). Libraries: `<folder>/rachana_libs`, each --libs path (an unpacked library, a
 * .rlib, or a folder of them) RACHANA_LIBS and the repository's own `libraries/`. Listens on 127.0.0.1 only. */
export async function studio(target, flags, io) {
  if (!existsSync(target) || !statSync(target).isDirectory()) throw new UsageError(`no such folder: ${target}`);
  if (flags.data && !existsSync(flags.data)) throw new UsageError(`no such data folder: ${flags.data}`);
  const s = createStudio(target, { libs: flags.libs ? String(flags.libs).split(",").filter(Boolean) : [], dataDir: flags.data ?? null });
  const { server, url } = await listen(s, Number(flags.port ?? 0));
  const { libraries, problems } = s.libraries();
  io.out(`studio ${url}  (${libraries.map((l) => `${l.name} ${l.version}`).join(", ") || "no libraries"}; Ctrl+C to stop)`);
  for (const p of problems) io.err(`rachana studio: ${p}`);
  if (flags["test-once"]) { server.close(); return 0; }
  await new Promise((ok) => server.on("close", ok));
  return 0;
}
