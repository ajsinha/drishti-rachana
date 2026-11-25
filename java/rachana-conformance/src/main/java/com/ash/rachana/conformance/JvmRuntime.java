/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The JVM runtime program of the conformance suite (conformance/CONFORMANCE.md section 5): cases as JSON lines on standard input, the
 * actual document of each on standard output, answered by {@link Cases}. Arguments: {@code [--profiles a,b] <formats file>...}: the
 * Core format table first, then a profile's names when given. Run by {@code tools/conformance/rachana_conformance.py --jvm}.
 */
public final class JvmRuntime {
    private JvmRuntime() { }

    public static void main(String[] a) throws Exception {
        List<String> files = new ArrayList<>(List.of(a));
        String profiles = "core";
        if (!files.isEmpty() && files.get(0).equals("--host-nodes")) {   // the documents as a host's own node type (HostNodes)
            Cases.documents = HostNodes::of;
            files = files.subList(1, files.size());
        }
        if (files.size() >= 2 && files.get(0).equals("--profiles")) {
            profiles = files.get(1);
            files = files.subList(2, files.size());
        }
        Cases.init(profiles, files);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        in.readLine();                                                   // the driver's greeting
        out.println(Cases.hello("jvm"));
        out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) { continue; }
            out.println(Cases.answer(line));
            out.flush();
        }
    }
}
