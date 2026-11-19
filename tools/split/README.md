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
| `rename_packages.py` | headers and the package rename of the rename commit |

The binder (step A14) moves the same way with its own allow-list.
