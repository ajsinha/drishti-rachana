/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * {@code java -jar rachana-server-<version>-all.jar <command>}: {@code serve --config <file> [--port <n>]} starts the server (prints
 * {@code rachana-server listening on <url>} when ready; Ctrl+C stops it), {@code check --config <file>} checks a configuration and prints
 * its resolved settings, {@code hash-password} reads a password (from the console, or the first line of standard input) and prints its hash
 * for the users file. Exit status: 0 done, 1 a configuration with problems, 2 a usage error.
 */
public final class Main {

    static final String HELP = """
            rachana-server <command>
              serve --config <rachana-server.yaml> [--port <n>]   start the server
              check --config <rachana-server.yaml>                check a configuration, print its resolved settings
              hash-password                                       read a password, print its hash for the users file
            """;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("help")) {
            System.out.print(HELP);
            return args.length == 0 ? 2 : 0;
        }
        String cmd = args[0];
        Path config = null;
        Integer port = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> config = i + 1 < args.length ? Path.of(args[++i]) : null;
                case "--port" -> port = i + 1 < args.length ? Integer.parseInt(args[++i]) : null;
                default -> {
                    System.err.println("rachana-server: unknown argument " + args[i] + "\n" + HELP);
                    return 2;
                }
            }
        }
        switch (cmd) {
            case "hash-password" -> {
                char[] pw = password();
                if (pw == null || pw.length == 0) {
                    System.err.println("rachana-server: no password given");
                    return 2;
                }
                System.out.println(Users.hash(pw, Users.ITERATIONS));
                Arrays.fill(pw, '\0');
                return 0;
            }
            case "serve", "check" -> {
                if (config == null) {
                    System.err.println("rachana-server: " + cmd + " needs --config <file> (the one configuration file)");
                    return 2;
                }
                ServerConfig c;
                try {
                    c = ServerConfig.read(config);
                    if (port != null) {
                        c = c.withPort(port);
                    }
                } catch (ServerConfig.Invalid e) {
                    Path at = config;
                    e.problems().forEach(p -> System.err.println(at + ": " + p));
                    return 1;
                } catch (IOException e) {
                    System.err.println("rachana-server: " + config + " could not be read");
                    return 1;
                }
                if (cmd.equals("check")) {
                    c.describe().forEach(System.out::println);
                    return 0;
                }
                RachanaServer s;
                try {
                    s = RachanaServer.start(c);
                } catch (ServerConfig.Invalid e) {
                    e.problems().forEach(p -> System.err.println("rachana-server: " + p));
                    return 1;
                }
                Runtime.getRuntime().addShutdownHook(new Thread(s::close, "rachana-server-stop"));
                System.out.println("rachana-server listening on " + s.url() + "/");
                System.out.flush();
                Thread.currentThread().join();
                return 0;
            }
            default -> {
                System.err.println("rachana-server: unknown command " + cmd + "\n" + HELP);
                return 2;
            }
        }
    }

    private static char[] password() throws IOException {
        Console c = System.console();
        if (c != null) {
            char[] a = c.readPassword("Password: ");
            char[] b = c.readPassword("Again: ");
            if (a == null || !Arrays.equals(a, b)) {
                System.err.println("rachana-server: the two passwords differ");
                return null;
            }
            return a;
        }
        String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        return line == null ? null : line.toCharArray();
    }
}
