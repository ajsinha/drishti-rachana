# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Canonical JSON (conformance/FORMAT.md 8) and number text (FORMAT 9, pinned to Java's Double.toString)."""
from __future__ import annotations

import math
from decimal import Decimal
from typing import Any

_ESC = {0x22: '\\"', 0x5C: "\\\\", 0x08: "\\b", 0x09: "\\t", 0x0A: "\\n", 0x0C: "\\f", 0x0D: "\\r"}


def number_text(x: float | int) -> str:
    """FORMAT 9 N1 to N3, N6. NaN and infinities are not numbers here (tags / null)."""
    if isinstance(x, int):
        return str(x)
    if x == 0:
        return "0"                                     # N6
    if x == int(x) and abs(x) < 1e15:
        return str(int(x))                             # N1
    if abs(x) == 5e-324:                               # Java prints Double.MIN_VALUE as 4.9E-324 (two digits)
        return "-4.9E-324" if x < 0 else "4.9E-324"
    digits = repr(abs(x))                              # CPython: shortest round-trip digits
    d = Decimal(digits)
    sign, ds, exp = d.as_tuple()
    ds_s = "".join(map(str, ds)).rstrip("0") or "0"
    # decimal exponent of the first digit
    e10 = len("".join(map(str, ds))) + exp - 1
    neg = "-" if x < 0 else ""
    if 1e-3 <= abs(x) < 1e7:                           # N2
        s = format(d, "f")
        if "." not in s:
            s += ".0"
        else:
            s = s.rstrip("0")
            if s.endswith("."):
                s += "0"
        return neg + s
    mant = ds_s[0] + "." + (ds_s[1:] or "0")           # N3
    return f"{neg}{mant}E{e10}"


def _string(s: str) -> str:
    out = ['"']
    for ch in s:
        c = ord(ch)
        if c in _ESC:
            out.append(_ESC[c])
        elif c < 0x20 or c == 0x7F or 0xD800 <= c <= 0xDFFF:
            out.append("\\u%04x" % c)
        else:
            out.append(ch)
    out.append('"')
    return "".join(out)


def dumps(v: Any) -> str:
    """Serialise ``v`` as canonical JSON: order kept, no white space, no sorting."""
    if v is None:
        return "null"
    if v is True:
        return "true"
    if v is False:
        return "false"
    if isinstance(v, float):
        if math.isnan(v):
            return '{"$num":"NaN"}'
        if math.isinf(v):
            return '{"$num":"%sInfinity"}' % ("-" if v < 0 else "")
        return number_text(v)
    if isinstance(v, int):
        return str(v)
    if isinstance(v, str):
        return _string(v)
    if isinstance(v, (list, tuple)):
        return "[" + ",".join(dumps(x) for x in v) + "]"
    if isinstance(v, dict):
        return "{" + ",".join(_string(str(k)) + ":" + dumps(x) for k, x in v.items()) + "}"
    raise TypeError(f"not JSON: {type(v).__name__}")
