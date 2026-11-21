/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { existsSync, mkdirSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { UsageError } from "./errors.js";
import { languageLevels } from "./lint.js";

const TEMPLATES = {
  basic: {
    "rachana.yaml": "# Rachana project\nmode: static          # static | embedded | python | server\nlevel: %LEVEL%          # the language level this project runs at: raise it on purpose\nsutras: sutras/\ndata: data/\n",
    "sutras/overview.sutra.yaml": `rachana: 1
sutra: overview
version: 1
title: { id: "'Overview'" }
description: A starter Sutra. Edit it, then run rachana lint and rachana preview.
panels:
  - { id: hello, kind: metric, label: Revenue, value: 1250, fmt: amount0, span: 4 }
  - { id: units, kind: metric, label: Units, value: 42, fmt: amount0, span: 4 }
  - { id: note, kind: markdown, text: "Replace these tiles with your own panels.", span: 4 }
`,
    "data/sales.csv": "region,month,units,revenue\nNorth,2026-01-01,10,120\nNorth,2026-02-01,12,135\nEast,2026-01-01,8,75\n",
    "README.md": "# Starter\n\n    rachana lint\n    rachana preview sutras/overview.sutra.yaml --data data\n    rachana build --out site\n",
  },
};

/** rachana new <dir>: writes a starter folder; refuses a folder that already has files. */
export async function scaffold(dir, template = "basic", io) {
  if (!dir) throw new UsageError("rachana new <dir>");
  const t = TEMPLATES[template];
  if (!t) throw new UsageError(`unknown template '${template}' (known: ${Object.keys(TEMPLATES).join(", ")})`);
  if (existsSync(dir) && readdirSync(dir).length) throw new UsageError(`${dir} exists and is not empty`);
  const level = (await languageLevels()).default;      // the newest built level, written into the project (SPEC 7.2 rule 6)
  for (const [name, text0] of Object.entries(t)) {
    const text = text0.replace("%LEVEL%", level);
    const f = join(dir, name);
    mkdirSync(join(f, ".."), { recursive: true });
    writeFileSync(f, text);
  }
  io.out(`created ${dir} (${Object.keys(t).length} files). Next: cd ${dir} && rachana lint`);
  return 0;
}
