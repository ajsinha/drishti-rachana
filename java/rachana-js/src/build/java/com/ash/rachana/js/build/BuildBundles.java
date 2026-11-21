/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js.build;

import java.io.File;
import org.teavm.backend.javascript.JSModuleType;
import org.teavm.diagnostics.Problem;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

/**
 * Builds the two bundles with TeaVM's tool API (split step A15), in a JVM whose class path puts this module's classes first: the
 * patched {@code StatementRenderer} and {@code PreciseDependencyAnalyzer} (src/build/java, Apache-2.0, TeaVM's files with one fix
 * each: parentheses kept, D15-2; no domain merging, G1 R1) then replace TeaVM's own. Run by the module's build (exec plugin).
 * Arguments: {@code <output folder> <optimisation level> <minify true|false> [bench]}. tools/js/build_js.py runs the conformance
 * suite on both bundles after every build.
 */
public final class BuildBundles {

    private BuildBundles() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        TeaVMOptimizationLevel level = TeaVMOptimizationLevel.valueOf(args.length > 1 ? args[1] : "ADVANCED");
        boolean minify = args.length <= 2 || Boolean.parseBoolean(args[2]);
        boolean failed = false;
        java.util.List<String[]> bundles = new java.util.ArrayList<>(java.util.List.of(
                new String[] { "com.ash.rachana.js.RuntimeMain", "rachana.runtime.js" },
                new String[] { "com.ash.rachana.js.ToolsMain", "rachana.tools.js" }));
        if (args.length > 3 && args[3].equals("bench")) {
            bundles.add(new String[] { "com.ash.rachana.js.BenchMain", "rachana.bench.js" });   // measuring only, never shipped
        }
        for (String[] b : bundles) {
            TeaVMTool tool = new TeaVMTool();
            tool.setLog(new ConsoleTeaVMToolLog(false));
            tool.setClassLoader(BuildBundles.class.getClassLoader());
            tool.setTargetType(TeaVMTargetType.JAVASCRIPT);
            tool.setJsModuleType(JSModuleType.ES2015);
            tool.setMainClass(b[0]);
            tool.setTargetDirectory(out);
            tool.setTargetFileName(b[1]);
            tool.setOptimizationLevel(level);
            tool.setObfuscated(minify);
            tool.setDebugInformationGenerated(false);
            tool.setSourceMapsFileGenerated(false);
            tool.setIncremental(false);
            tool.generate();
            for (Problem p : tool.getProblemProvider().getSevereProblems()) {
                System.err.println(b[1] + ": " + p.getText() + " " + java.util.Arrays.toString(p.getParams()));
                failed = true;
            }
            System.out.println(b[1] + ": " + new File(out, b[1]).length() + " bytes");
        }
        if (failed) {
            System.exit(1);
        }
    }
}
