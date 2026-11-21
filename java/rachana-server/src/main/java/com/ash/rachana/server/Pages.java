/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.SutraProblem;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The server's own pages, as HTML text: the index of Sutras, a Sutra's page ({@code <rachana-view>} over the Data Protocol, live), the
 * problems page and sign-in. No inline script anywhere (the Content-Security-Policy allows scripts from the server only); every value is
 * escaped. Stateless.
 */
final class Pages {

    private Pages() {
    }

    /** Escapes text for an HTML element or a quoted attribute. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private static String head(String title) {
        return "<!doctype html><html lang=en><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<meta name=referrer content=same-origin><title>" + esc(title) + "</title>\n";
    }

    private static final String STYLE = "<style>body{font:15px/1.5 system-ui,sans-serif;margin:0;color:#1d2433;background:#f6f7f9}"
            + "header{display:flex;gap:16px;align-items:center;padding:8px 16px;background:#fff;border-bottom:1px solid #dde1e7}"
            + "header a{color:inherit;text-decoration:none;font-weight:600}header .who{margin-left:auto;color:#5b6475}"
            + "header form{margin:0}main{padding:16px;max-width:1200px;margin:0 auto}button{font:inherit;cursor:pointer}"
            + ".problem{color:#a3261b}.card{background:#fff;border:1px solid #dde1e7;border-radius:8px;padding:16px}"
            + "label{display:block;margin:8px 0 4px}input{font:inherit;padding:6px 8px;width:100%;box-sizing:border-box}"
            + "@media (prefers-color-scheme:dark){body{background:#14171c;color:#e6e8ec}header,.card{background:#1c2027;border-color:#2c323c}"
            + "header .who{color:#9aa3b2}.problem{color:#ff8a80}}</style>\n";

    /** The bar above every signed-in page: home, the caller, sign-out (a form with the session's CSRF token). */
    private static String bar(Users.Principal who, String csrf, boolean canSignOut) {
        StringBuilder b = new StringBuilder("<header><a href=\"/\">Rachana</a><span class=who>").append(esc(who.toString())).append("</span>");
        if (canSignOut) {
            b.append("<form method=post action=\"/signout\"><input type=hidden name=csrf value=\"").append(esc(csrf))
                    .append("\"><button type=submit>Sign out</button></form>");
        }
        return b.append("</header>\n").toString();
    }

    /** The index: the Sutras the caller may open, and how many problems the project has. */
    static String index(Collection<Project.Sutra> sutras, int problems, Users.Principal who, String csrf, boolean canSignOut) {
        StringBuilder b = new StringBuilder(head("Sutras")).append(STYLE).append(bar(who, csrf, canSignOut)).append("<main><h1>Sutras</h1><ul>");
        for (Project.Sutra s : sutras) {
            b.append("<li><a href=\"/v/").append(esc(s.name())).append("\">").append(esc(s.name())).append("</a>");
            if (!s.problems().isEmpty()) {
                b.append(" <span class=problem>(").append(s.problems().size()).append(" problem").append(s.problems().size() == 1 ? "" : "s")
                        .append(")</span>");
            }
            b.append("</li>");
        }
        b.append("</ul>");
        if (problems > 0) {
            b.append("<p><a href=\"/problems\">").append(problems).append(" problem").append(problems == 1 ? "" : "s").append("</a></p>");
        }
        return b.append("</main>\n").toString();
    }

    /** A Sutra's page: the Elements pack, then {@code <rachana-view>} over the Sutra, the protocol base, the runtime and its libraries. */
    static String view(Project.Sutra s, String elements, boolean libraries, Users.Principal who, String csrf, boolean canSignOut) {
        String n = esc(s.name());
        return head(s.name()) + STYLE
                + "<meta name=\"rachana-csrf\" content=\"" + esc(csrf == null ? "" : csrf) + "\">\n"   // a live table's writes send it (X-Rachana-CSRF)
                + "<link rel=stylesheet href=\"/assets/" + esc(elements) + "/drishti-elements.css\">\n"
                + "<script src=\"/assets/" + esc(elements) + "/drishti-elements.js\"></script>\n"
                + "<script type=module src=\"/assets/rachana/src/rachana_view.js\"></script>\n"
                + bar(who, csrf, canSignOut) + "<main>"
                + "<rachana-view src=\"/sutras/" + n + ".sutra.yaml\" data=\"" + RachanaServer.DATA + "\" runtime=\"/assets/rachana/rachana.runtime.js\""
                + (libraries ? " libraries=\"/libraries/" + n + ".libraries.json\"" : "") + " live></rachana-view>"
                + "</main>\n";
    }

    /** The problems page: the project's and each Sutra's, by file and line. */
    static String problems(List<String> project, Map<String, Project.Sutra> sutras, Map<String, String> tables, Users.Principal who, String csrf,
            boolean canSignOut) {
        StringBuilder b = new StringBuilder(head("Problems")).append(STYLE).append(bar(who, csrf, canSignOut)).append("<main><h1>Problems</h1>");
        if (project.isEmpty() && tables.isEmpty() && sutras.values().stream().allMatch(s -> s.problems().isEmpty())) {
            b.append("<p>None.</p>");
        }
        for (String p : project) {
            b.append("<p class=problem>").append(esc(p)).append("</p>");
        }
        tables.forEach((t, why) -> b.append("<p class=problem>table ").append(esc(t)).append(": ").append(esc(why)).append("</p>"));
        for (Project.Sutra s : sutras.values()) {
            for (SutraProblem p : s.problems()) {
                b.append("<p class=problem>").append(esc(p.location() == null ? s.name() : p.location().toString())).append(": ")
                        .append(esc(p.code())).append(": ").append(esc(p.message())).append("</p>");
            }
        }
        return b.append("</main>\n").toString();
    }

    /** The sign-in form (its CSRF token double-submitted: the form field and a cookie). */
    static String signIn(String csrf, String next, String message) {
        return head("Sign in") + STYLE + "<main><div class=card style=\"max-width:360px;margin:64px auto\"><h1>Sign in</h1>"
                + (message == null ? "" : "<p class=problem role=alert>" + esc(message) + "</p>")
                + "<form method=post action=\"/signin\"><input type=hidden name=csrf value=\"" + esc(csrf) + "\">"
                + "<input type=hidden name=next value=\"" + esc(next) + "\">"
                + "<label for=user>Name</label><input id=user name=user autocomplete=username required autofocus>"
                + "<label for=password>Password</label><input id=password name=password type=password autocomplete=current-password required>"
                + "<p><button type=submit>Sign in</button></p></form></div></main>\n";
    }

    /** A short page for a refusal or an error. */
    static String message(String title, String text) {
        return head(title) + STYLE + "<main><h1>" + esc(title) + "</h1><p>" + esc(text) + "</p><p><a href=\"/\">Home</a></p></main>\n";
    }
}
