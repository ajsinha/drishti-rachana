// Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
// Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
// T4's hand-written JavaScript evaluator (proof only, thrown away with the proof): a tokenizer, a precedence parser and a
// tree-walking evaluator over plain JavaScript objects for exactly the twenty expressions of JS_PROOF 4.3. It is what fallback 3
// (a hand-written evaluator) would be for these twenty; null in, null out; numbers as JavaScript numbers.

const TOK = /\s*(?:(\d+(?:\.\d+)?(?:e[+-]?\d+)?)|('(?:[^'\\]|\\.)*')|(&&|\|\||==|!=|>=|<=|\*\*|[-+*/%<>?:!()\[\],.@$#^])|([A-Za-z_][A-Za-z0-9_]*))/y;

function tokens(src) {
  const out = []; TOK.lastIndex = 0;
  while (TOK.lastIndex < src.length) {
    const m = TOK.exec(src); if (!m) throw new Error('bad token at ' + TOK.lastIndex);
    if (m[1]) out.push({ t: 'num', v: Number(m[1]) }); else if (m[2]) out.push({ t: 'str', v: m[2].slice(1, -1) });
    else if (m[3]) out.push({ t: 'op', v: m[3] }); else if (m[4]) out.push({ t: 'id', v: m[4] });
  }
  return out;
}

const PREC = { '?': 1, '||': 2, '&&': 3, '==': 4, '!=': 4, in: 4, '<': 5, '>': 5, '<=': 5, '>=': 5, '+': 6, '-': 6, '*': 7, '/': 7, '%': 7 };

function parse(src) {
  const ts = tokens(src); let i = 0;
  const peek = () => ts[i], next = () => ts[i++];
  const eat = v => { const t = next(); if (!t || t.v !== v) throw new Error('expected ' + v); };
  function path(root) {
    let node = { k: 'root', root };
    for (;;) {
      const t = peek();
      if (t && t.v === '.') { next(); node = { k: 'get', of: node, name: next().v }; }
      else if (t && t.v === '[') { next(); if (peek().v === '?') { next(); const c = expr(0); eat(']'); node = { k: 'filter', of: node, c }; } else { const ix = expr(0); eat(']'); node = { k: 'idx', of: node, ix }; } }
      else return node;
    }
  }
  function primary() {
    const t = next();
    if (t.t === 'num' || t.t === 'str') return { k: 'lit', v: t.v };
    if (t.v === '(') { const e = expr(0); eat(')'); return e; }
    if (t.v === '-') return { k: 'neg', e: primary() };
    if (t.v === '!') return { k: 'not', e: primary() };
    if (t.v === '[') { const items = []; while (peek().v !== ']') { items.push(expr(0)); if (peek().v === ',') next(); } next(); return { k: 'list', items }; }
    if (t.v === '@' || t.v === '$') return path(t.v);
    if (t.v === '#') { next(); return { k: 'index' }; }
    if (t.t === 'id') {
      if (t.v === 'true' || t.v === 'false') return { k: 'lit', v: t.v === 'true' };
      if (peek() && peek().v === '(') { next(); const args = []; while (peek().v !== ')') { args.push(expr(0)); if (peek().v === ',') next(); } next(); return { k: 'call', f: t.v, args }; }
      if (t.v === 'acc' ) return { k: 'acc' };
    }
    throw new Error('unexpected ' + t.v);
  }
  function expr(min) {
    let left = primary();
    for (;;) {
      const t = peek(); if (!t) return left;
      const op = t.t === 'id' && t.v === 'in' ? 'in' : t.v; const p = PREC[op];
      if (p === undefined || p <= min) return left;
      next();
      if (op === '?') { const a = expr(0); eat(':'); const b = expr(0); left = { k: 'cond', c: left, a, b }; continue; }
      left = { k: 'bin', op, l: left, r: expr(p) };
    }
  }
  const e = expr(0); if (i !== ts.length) throw new Error('trailing'); return e;
}

const num = v => (v == null ? NaN : typeof v === 'number' ? v : typeof v === 'boolean' ? +v : Number(v));
const text = v => (v == null ? '' : String(v));

function ev(n, c) {
  switch (n.k) {
    case 'lit': return n.v;
    case 'root': return n.root === '@' ? (c.acc !== undefined && c.inAcc ? c.row : c.row) : c.doc;
    case 'get': { const o = ev(n.of, c); return o == null ? null : o[n.name] ?? null; }
    case 'idx': { const o = ev(n.of, c); return o == null ? null : o[ev(n.ix, c)] ?? null; }
    case 'filter': { const o = ev(n.of, c); if (!Array.isArray(o)) return null; const out = []; for (const x of o) if (ev(n.c, { ...c, row: x })) out.push(x); return out; }
    case 'index': return c.index;
    case 'acc': return c.acc;
    case 'neg': { const v = ev(n.e, c); return v == null ? null : -num(v); }
    case 'not': return !ev(n.e, c);
    case 'list': return n.items.map(x => ev(x, c));
    case 'cond': return ev(n.c, c) ? ev(n.a, c) : ev(n.b, c);
    case 'bin': {
      if (n.op === '&&') return !!ev(n.l, c) && !!ev(n.r, c);
      if (n.op === '||') return !!ev(n.l, c) || !!ev(n.r, c);
      const a = ev(n.l, c), b = ev(n.r, c);
      switch (n.op) {
        case '+': return a == null || b == null ? (a ?? b) : typeof a === 'string' || typeof b === 'string' ? text(a) + text(b) : num(a) + num(b);
        case '-': return a == null || b == null ? null : num(a) - num(b);
        case '*': return a == null || b == null ? null : num(a) * num(b);
        case '/': return a == null || b == null || num(b) === 0 ? null : num(a) / num(b);
        case '%': return a == null || b == null || num(b) === 0 ? null : num(a) % num(b);
        case '==': return typeof a === 'number' || typeof b === 'number' ? num(a) === num(b) : text(a) === text(b);
        case '!=': return !(typeof a === 'number' || typeof b === 'number' ? num(a) === num(b) : text(a) === text(b));
        case '>': return typeof a === 'number' || typeof b === 'number' ? num(a) > num(b) : text(a) > text(b);
        case '<': return typeof a === 'number' || typeof b === 'number' ? num(a) < num(b) : text(a) < text(b);
        case '>=': return typeof a === 'number' || typeof b === 'number' ? num(a) >= num(b) : text(a) >= text(b);
        case '<=': return typeof a === 'number' || typeof b === 'number' ? num(a) <= num(b) : text(a) <= text(b);
        case 'in': return Array.isArray(b) && b.some(x => text(x) === text(a));
      }
      throw new Error(n.op);
    }
    case 'call': return call(n, c);
  }
  throw new Error(n.k);
}

const DAY = 86400000;
function call(n, c) {
  const a = i => ev(n.args[i], c);
  switch (n.f) {
    case 'round': { const v = a(0); if (v == null) return null; const p = 10 ** num(a(1)); return Math.sign(v) * Math.round(Math.abs(num(v)) * p) / p; }
    case 'abs': { const v = a(0); return v == null ? null : Math.abs(num(v)); }
    case 'nonblank': { const v = a(0); return v == null || v === '' ? a(1) : v; }
    case 'upper': { const v = a(0); return v == null ? null : text(v).toUpperCase(); }
    case 'left': { const v = a(0); return v == null ? null : text(v).slice(0, num(a(1))); }
    case 'days_between': { const x = a(0), y = a(1); return x == null || y == null ? null : Math.round((Date.parse(y) - Date.parse(x)) / DAY); }
    case 'size': { const v = a(0); return Array.isArray(v) ? v.length : null; }
    case 'greatest': { const x = a(0), y = a(1); return x == null ? y : y == null ? x : Math.max(num(x), num(y)); }
    case 'map': { const v = a(0); return Array.isArray(v) ? v.map(x => ev(n.args[1], { ...c, row: x })) : null; }
    case 'reduce': { const v = a(0); let acc = a(1); if (!Array.isArray(v)) return null; for (const x of v) acc = ev(n.args[2], { ...c, row: x, acc }); return acc; }
    case 'matches': { const v = a(0); if (v == null) return null; const src = a(1); let re = RE.get(src); if (!re) RE.set(src, re = new RegExp(src)); return re.test(text(v)); }
  }
  throw new Error('fn ' + n.f);
}
const RE = new Map();

/** Compile an expression (or a template written T:...${...}...) to a function (row, index, doc) => value. */
export function compile(src) {
  if (src.startsWith('T:')) {
    const parts = []; const s = src.slice(2); let i = 0;
    while (i < s.length) { const j = s.indexOf('${', i); if (j < 0) { parts.push(s.slice(i)); break; } parts.push(s.slice(i, j)); const k = s.indexOf('}', j); parts.push(parse(s.slice(j + 2, k))); i = k + 1; }
    return (row, index, doc) => { let o = ''; const c = { row, index, doc }; for (const p of parts) o += typeof p === 'string' ? p : text(ev(p, c)); return o; };
  }
  const e = parse(src.replace(/@acc/g, 'acc'));
  return (row, index, doc) => ev(e, { row, index, doc });
}
