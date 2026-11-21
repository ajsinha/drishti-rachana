/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.internal.CoreMath;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The host hook for sources (split step A13): where the entities a view lists, searches, fetches and follows come from. Each call
 * has a time budget and the view's point in time; a source that fails or does not answer in time is added to {@code failures}
 * (never thrown), so the panel can say so. Core alone has no sources: {@link #NONE}.
 */
public interface TableProvider {

    /** No sources: every listing is empty and nothing is found. */
    TableProvider NONE = new TableProvider() {
        @Override
        public List<EntityHit> search(String kind, String text, int limit, Duration budget, AsOf at) {
            return List.of();
        }

        @Override
        public List<EntityHit> list(String kind, String text, int limit, Duration budget, AsOf at, SourceFailures failures) {
            return List.of();
        }

        @Override
        public List<EntityHit> listAcross(String kind, int max, Duration budget, AsOf at, SourceFailures failures) {
            return List.of();
        }

        @Override
        public Map<EntityRef, Entity> fetchAll(Collection<EntityRef> refs, Duration budget, AsOf at, SourceFailures failures) {
            return Map.of();
        }
    };

    /** How fresh a kind's source is: the source, its last update, the threshold and whether it is past it. */
    record Freshness(String source, Instant lastUpdate, Duration staleAfter, boolean stale) {}

    /** The entities of a kind whose text matches, at most {@code limit} (a control's lookup). */
    List<EntityHit> search(String kind, String text, int limit, Duration budget, AsOf at);

    /** The entities of a kind matching a search text, at most {@code limit}. */
    List<EntityHit> list(String kind, String text, int limit, Duration budget, AsOf at, SourceFailures failures);

    /** Up to {@code max} entities of a kind, across every place that holds it. */
    List<EntityHit> listAcross(String kind, int max, Duration budget, AsOf at, SourceFailures failures);

    /** The entities asked for that exist, in the order asked. */
    Map<EntityRef, Entity> fetchAll(Collection<EntityRef> refs, Duration budget, AsOf at, SourceFailures failures);

    /** The freshness of the source answering for a kind ({@code source}: the one a document named, may be null); null when unknown. */
    default Freshness freshness(String kind, String source) {
        return null;
    }

    /** Which part of the data this provider answers for (a cache key part); empty when it answers for all. */
    default String partition() {
        return "";
    }

    /** The id of a computed entity with its parameters: {@code id~name=value…}, parameters sorted by name, values without trailing zeros. */
    default String computedId(String id, Map<String, ?> params) {
        StringBuilder b = new StringBuilder(encode(id == null ? "" : id));
        new TreeMap<String, Object>(params == null ? Map.<String, Object>of() : params).forEach((k, v) -> {
            if (v != null) {
                b.append('~').append(encode(k)).append('=').append(encode(paramText(v)));
            }
        });
        return b.toString();
    }

    private static String encode(String s) {
        if (s.indexOf('~') < 0 && s.indexOf('=') < 0 && s.indexOf('%') < 0 && s.indexOf('|') < 0) {
            return s;
        }
        return s.replace("%", "%25").replace("~", "%7E").replace("=", "%3D").replace("|", "%7C");
    }

    private static String paramText(Object v) {
        if (v instanceof java.math.BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Double d && d == CoreMath.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString(CoreMath.toLong(d));
        }
        return String.valueOf(v);
    }
}
