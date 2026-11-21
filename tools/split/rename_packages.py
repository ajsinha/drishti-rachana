# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Split steps A10 and A13, the rename commit: each file under the folders given gets this repository's header and
com.ash.drishti.rachana -> com.ash.rachana, plus each --map old=new package mapping (A13: the engine's binder packages)."""
import re, sys, pathlib
args = sys.argv[1:]
maps = [a[6:].split("=", 1) for a in args if a.startswith("--map=")]
roots = [pathlib.Path(a) for a in args if not a.startswith("--map=")]
JH = "/*\n * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.\n * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.\n */\n"
YH = "# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.\n# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.\n"
n = 0
for f in (g for root in roots for g in root.rglob("*")):
    if not f.is_file(): continue
    t = f.read_text(encoding="utf-8")
    o = t
    if f.suffix == ".java":
        m = re.match(r"\s*/\*.*?\*/\n", t, re.S)
        if m and "Copyright" in m.group(0):
            t = JH + t[m.end():]
    elif f.suffix in (".yaml", ".properties"):
        lines = t.split("\n"); i = 0
        while i < len(lines) and lines[i].startswith("#"): i += 1
        if any("Copyright" in l for l in lines[:i]):
            t = YH + "\n".join(lines[i:])
    t = t.replace("com.ash.drishti.rachana", "com.ash.rachana")
    for old, new in maps:
        t = re.sub(re.escape(old) + r"\b", new, t)
    if t != o:
        f.write_text(t, encoding="utf-8"); n += 1
print(n, "files rewritten")
