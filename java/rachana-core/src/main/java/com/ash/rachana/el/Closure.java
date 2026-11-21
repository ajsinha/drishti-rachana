/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.List;

/**
 * A lambda closed over the context it was written in (EL_EXPANSION X13, DECISIONS PY3). Second class: the parser lets it be passed to a
 * form or bound by {@code let} and called, never stored, compared or printed. Inside, {@code @}, {@code ^} and {@code #index} keep their
 * outer meaning; the parameters are names, as {@code let}'s.
 */
record Closure(Expr.Lambda fn, EvalContext at) {

    Object call(List<Object> args) {
        EvalContext c = at;
        for (int i = 0; i < fn.params().size(); i++) {
            c = c.withLocal(fn.params().get(i), i < args.size() ? args.get(i) : null);
        }
        return fn.body().eval(c);
    }

    /** The function {@code e} stands for in a form's argument (a lambda, or a {@code let} name bound to one), else null. */
    static Closure of(Expr e, EvalContext c) {
        if (e instanceof Expr.Lambda l) {
            return new Closure(l, c);
        }
        if (e instanceof Expr.FnRef r && c.local(r.name()) instanceof Closure f) {
            return f;
        }
        return null;
    }

    /** {@code e} for one element: the function called with it, or {@code e} evaluated with {@code @} the element. */
    static Object each(Expr e, EvalContext c, Object element, int index) {
        Closure f = of(e, c);
        return f == null ? e.eval(c.withRow(element, index)) : f.call(List.of(element == null ? com.ash.rachana.api.Node.missing() : element));
    }
}
