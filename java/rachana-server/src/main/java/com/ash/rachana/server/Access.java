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
 * columns, so callers who see the same share the engine's indexes and nobody else does.
 */
final class Access implements Authoriser {

    private final ServerConfig.Access access;

    Access(ServerConfig.Access access) {
        this.access = access;
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
            return new ReaderView("", null, Set.of(), null, null, Set.of(), "this server is read-only");
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
        return new ReaderView(masked.isEmpty() ? "" : "masked:" + String.join(",", masked), null, masked, null, null, Set.of(),
                "this server is read-only");
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
