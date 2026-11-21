/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.ReaderView;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The server's rights ({@code access:}), fail-closed: a request without a signed-in principal reads nothing; a table or Sutra with an entry
 * is read only by the roles it names (none named: every signed-in caller); one without an entry is open to every signed-in caller under
 * {@code default: signed-in} and to nobody under {@code default: closed}. A refused table is answered as unknown, so its existence does not
 * leak. Columns listed under {@code unmask} are masked for callers without one of their roles; the reader's audience is the set of masked
 * columns, so callers who see the same share the engine's indexes and nobody else does. Edits ({@code access.tables.<name>.edit}): a
 * caller with one of its roles may edit its columns (none listed: every column the declaration edits); everyone else reads only, and
 * {@link #mayEdit} refuses them again on every write (a forged request). The caller's name is the principal's.
 */
final class Access implements Authoriser {

    private final ServerConfig.Access access;
    private final java.util.Map<String, ServerConfig.TableEdit> edits;
    private final java.util.function.Function<String, List<String>> declared;

    /**
     * @param declared a live table's columns its declaration edits (empty: none)
     */
    Access(ServerConfig.Access access, java.util.Map<String, ServerConfig.TableEdit> edits, java.util.function.Function<String, List<String>> declared) {
        this.access = access;
        this.edits = edits == null ? java.util.Map.of() : edits;
        this.declared = declared == null ? t -> List.of() : declared;
    }

    Access(ServerConfig.Access access) {
        this(access, null, null);
    }

    /** The columns a caller may edit in a table (empty: none). */
    Set<String> editable(Users.Principal p, String table) {
        ServerConfig.TableEdit e = edits.get(table);
        if (e == null || !allowed(p, e.roles()) || e.roles().isEmpty()) {
            return Set.of();
        }
        Set<String> cols = new TreeSet<>(declared.apply(table));
        if (!e.columns().isEmpty()) {
            cols.retainAll(e.columns());
        }
        return cols;
    }

    @Override
    public String who(Object caller) {
        return caller instanceof Users.Principal p ? p.name() : null;
    }

    @Override
    public Decision mayEdit(Object caller, String table, java.util.Map<String, Object> change, java.util.Map<String, Object> row) {
        if (!(caller instanceof Users.Principal p)) {
            return new Decision.Refuse("not signed in");
        }
        Set<String> may = editable(p, table);
        Object set = change.get("set");
        if (may.isEmpty() || set instanceof java.util.Map<?, ?> m && !may.containsAll(m.keySet())) {
            return new Decision.Refuse("none of the roles that may edit " + table);
        }
        return new Decision.Allow();
    }

    @Override
    public ReaderView view(Object caller, String table) throws Refusal {
        if (!(caller instanceof Users.Principal p)) {
            throw new Refusal("not signed in", true);
        }
        ServerConfig.TableAccess a = access.tables().get(table);
        if (a == null) {
            if (!access.openDefault()) {
                throw new Refusal("no access entry and access.default is closed", true);
            }
            Set<String> ed = editable(p, table);
            return new ReaderView("", null, Set.of(), null, null, ed, ed.isEmpty() ? "you may not edit " + table : null);
        }
        if (!allowed(p, a.read())) {
            throw new Refusal("none of the roles " + a.read(), true);
        }
        Set<String> masked = new TreeSet<>();
        a.unmask().forEach((col, roles) -> {
            if (!allowed(p, roles)) {
                masked.add(col);
            }
        });
        Set<String> ed = new TreeSet<>(editable(p, table));
        ed.removeAll(masked);                                         // a masked value is never edited
        return new ReaderView(masked.isEmpty() ? "" : "masked:" + String.join(",", masked), null, masked, null, null, ed,
                ed.isEmpty() ? "you may not edit " + table : null);
    }

    /** Whether a caller may open a Sutra (its page, its text, its library manifest). */
    boolean sutra(Object caller, String name) {
        if (!(caller instanceof Users.Principal p)) {
            return false;
        }
        List<String> roles = access.sutras().get(name);
        return roles == null ? access.openDefault() : allowed(p, roles);
    }

    private static boolean allowed(Users.Principal p, Collection<String> roles) {
        if (roles.isEmpty()) {
            return true;
        }
        for (String r : roles) {
            if (p.roles().contains(r)) {
                return true;
            }
        }
        return false;
    }
}
