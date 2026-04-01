package ru.hse.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import ru.hse.Account;
import ru.hse.OperationException;
import ru.hse.client.Client;
import ru.hse.server.Server;

public final class LoadScenario {
    private static final int DEFAULT_ITERATIONS = 10_000;
    private static final int DEFAULT_PORT = 7_080;
    private static final int DEFAULT_INVALID_LOGIN_STEP = 250;
    private static final int PROGRESS_STEP = 1_000;

    private LoadScenario() {
    }

    public static void main(String[] args) throws IOException {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_ITERATIONS;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        int invalidLoginStep =
                args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_INVALID_LOGIN_STEP;

        Path profilingDir = Path.of("build", "profiling");
        Files.createDirectories(profilingDir);
        String runId =
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(LocalDateTime.now());
        Path databaseBase = profilingDir.resolve("db-" + runId).resolve("accounts");
        Path summaryFile = profilingDir.resolve("load-test-summary-" + runId + ".txt");

        Server server = new Server();
        Instant startedAt = Instant.now();
        ScenarioStats stats = new ScenarioStats(iterations, port, invalidLoginStep, databaseBase);

        try {
            server.start(port, databaseBase.toString());
            server.waitStarted();
            Client client = new Client("http://localhost:" + port);
            executeScenario(client, iterations, invalidLoginStep, stats);
        } finally {
            server.stop();
            stats.duration = Duration.between(startedAt, Instant.now());
            writeSummary(summaryFile, stats);
        }

        System.out.println("Load test completed.");
        System.out.println("Summary: " + summaryFile.toAbsolutePath());
    }

    private static void executeScenario(
            Client client, int iterations, int invalidLoginStep, ScenarioStats stats) {
        for (int index = 0; index < iterations; index++) {
            String login = "load_user_" + index;
            String password = "password_" + index;

            if (invalidLoginStep > 0 && index % invalidLoginStep == 0) {
                stats.failedLoginAttempts.incrementAndGet();
                tryWrongLogin(client, login);
            }

            try {
                Account account = client.register(login, password);
                stats.registeredAccounts.incrementAndGet();

                Client.deposit(account, 100.0d);
                stats.successfulDeposits.incrementAndGet();

                try {
                    Client.withdraw(account, 200.0d);
                } catch (OperationException expected) {
                    stats.expectedWithdrawFailures.incrementAndGet();
                }

                Client.withdraw(account, 50.0d);
                stats.successfulWithdrawals.incrementAndGet();
                client.logout(account);
                stats.successfulLogouts.incrementAndGet();
            } catch (OperationException exception) {
                stats.unexpectedErrors.add(login + ": " + exception);
            }

            if ((index + 1) % PROGRESS_STEP == 0 || index + 1 == iterations) {
                System.out.println("Processed " + (index + 1) + " / " + iterations + " accounts");
            }
        }
    }

    private static void tryWrongLogin(Client client, String login) {
        try {
            client.login(login, "wrong-password");
        } catch (OperationException ignored) {
            // Failed logins are part of the scenario and are tracked separately.
        }
    }

    private static void writeSummary(Path summaryFile, ScenarioStats stats) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Load test summary");
        lines.add("=================");
        lines.add("Iterations: " + stats.iterations);
        lines.add("Port: " + stats.port);
        lines.add("Invalid login step: " + stats.invalidLoginStep);
        lines.add("Database: " + stats.databaseBase.toAbsolutePath());
        lines.add("Duration: " + stats.duration);
        lines.add("Registered accounts: " + stats.registeredAccounts.get());
        lines.add("Successful deposits: " + stats.successfulDeposits.get());
        lines.add("Expected failed withdrawals: " + stats.expectedWithdrawFailures.get());
        lines.add("Successful withdrawals: " + stats.successfulWithdrawals.get());
        lines.add("Successful logouts: " + stats.successfulLogouts.get());
        lines.add("Failed login attempts: " + stats.failedLoginAttempts.get());
        lines.add("Unexpected errors: " + stats.unexpectedErrors.size());
        if (!stats.unexpectedErrors.isEmpty()) {
            lines.add("");
            lines.add("Unexpected errors details:");
            lines.addAll(stats.unexpectedErrors);
        }
        Files.write(summaryFile, lines);
    }

    private static final class ScenarioStats {
        private final int iterations;
        private final int port;
        private final int invalidLoginStep;
        private final Path databaseBase;
        private final AtomicInteger registeredAccounts = new AtomicInteger();
        private final AtomicInteger successfulDeposits = new AtomicInteger();
        private final AtomicInteger expectedWithdrawFailures = new AtomicInteger();
        private final AtomicInteger successfulWithdrawals = new AtomicInteger();
        private final AtomicInteger successfulLogouts = new AtomicInteger();
        private final AtomicInteger failedLoginAttempts = new AtomicInteger();
        private final List<String> unexpectedErrors = new ArrayList<>();
        private Duration duration = Duration.ZERO;

        private ScenarioStats(int iterations, int port, int invalidLoginStep, Path databaseBase) {
            this.iterations = iterations;
            this.port = port;
            this.invalidLoginStep = invalidLoginStep;
            this.databaseBase = databaseBase;
        }
    }
}
