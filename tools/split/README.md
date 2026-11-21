<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Split tools

How the compiler was moved here with its history (split step A10, [SPLIT_PLAN](../../docs/design/SPLIT_PLAN.md) section 0). The
host's repository is never touched: everything runs on a fresh clone in a scratch folder.

```text
git clone https://github.com/<owner>/drishti.git fresh && cd fresh
git filter-repo --paths-from-file <this folder>/a10-paths.txt \
    --path-rename drishti-sutra/:java/rachana-core/ --path-rename drishti-rachana/:java/rachana-core/ \
    --path-rename compute/drishti-rachana/:java/rachana-core/
git for-each-ref --format='%(refname:short)' refs/heads | grep -vx main | xargs -r git branch -D; git tag -l | xargs -r git tag -d
# scan every blob of the result for secrets and private data, and for size, before anything is merged
cd <drishti-rachana> && git fetch <fresh> main && git merge --allow-unrelated-histories FETCH_HEAD
# the rename commit: move com/ash/drishti/rachana to com/ash/rachana (git mv), then
python3 tools/split/rename_packages.py java/rachana-core/src
```

| File | What |
|---|---|
| `a10-paths.txt` | the reviewed path allow-list (main code, the signatures catalogue, the unit tests that need only the compiler) |
| `a13-paths.txt` | the binder's allow-list (step A13) |
| `rename_packages.py` | headers and the package rename of the rename commit |
| `compare_results.py` | the proof of each step: results over this code against the reference bridge's, byte for byte but for `known-differences.txt` |

The binder moved the same way at step A13 with its own allow-list (`a13-paths.txt`: engine `bind`, `view`, `control`, `wire` and the
live `Frame` and `Patch`, over the two paths the engine had; 84 commits), the rename commit with `rename_packages.py --map`.

From A14 the reference results file of a step is the compiler-only bridge run for every kind but `sutra`, plus the engine class path
run of `--kinds sutra` (reference-bridge README).
