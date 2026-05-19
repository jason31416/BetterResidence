package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.betterresidence.handler.checkers.DuplicateClaimNamesChecker;
import cn.jason31416.betterresidence.handler.checkers.SubclaimParentBoundsChecker;
import cn.jason31416.planetlib.util.PluginLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * This class is used to check if the data is valid or not.
 * If assertions on database are made, it should be registered as a checker here.
 */
public final class DataIntegrityHandler {
    private static final List<IntegrityChecker> CHECKERS = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile Path lastReportFile;

    static {
        registerChecker(new DuplicateClaimNamesChecker());
        registerChecker(new SubclaimParentBoundsChecker());
    }

    private DataIntegrityHandler() {
    }

    public static void registerChecker(IntegrityChecker checker) {
        CHECKERS.add(Objects.requireNonNull(checker, "checker"));
    }

    public static Path getLastReportFile() {
        return lastReportFile;
    }

    /**
     * Run all registered integrity checks without modifying stored data.
     *
     * @return the number of checks that returned corrupt rows or failed to execute.
     */
    public static int checkDataIntegrity() {
        return checkDataIntegrity(null);
    }

    /**
     * Run all registered integrity checks without modifying stored data.
     *
     * @param progressCallback called once when each checker finishes, including failed checker executions.
     * @return the number of checks that returned corrupt rows or failed to execute.
     */
    public static int checkDataIntegrity(Consumer<CheckProgress> progressCallback) {
        lastReportFile = null;
        List<CompletableFuture<CheckResult>> futures = CHECKERS.stream()
                .map(checker -> CompletableFuture.supplyAsync(() -> runChecker(checker)))
                .map(future -> future.thenApply(result -> {
                    notifyProgress(progressCallback, result);
                    return result;
                }))
                .toList();

        List<CheckResult> failedResults = futures.stream()
                .map(CompletableFuture::join)
                .filter(CheckResult::failed)
                .toList();

        if (!failedResults.isEmpty()) {
            writeReport(failedResults);
        }

        return failedResults.size();
    }

    private static void notifyProgress(Consumer<CheckProgress> progressCallback, CheckResult result) {
        if (progressCallback == null) {
            return;
        }
        try {
            progressCallback.accept(new CheckProgress(
                    result.name(),
                    result.failed(),
                    result.corruptedRows().size(),
                    result.executionError() != null
            ));
        } catch (Exception ignored) {
        }
    }

    private static CheckResult runChecker(IntegrityChecker checker) {
        try {
            List<Map<String, Object>> corruptedRows = checker.findCorruptedRows();
            return CheckResult.completed(checker, corruptedRows == null ? List.of() : corruptedRows);
        } catch (Exception e) {
            return CheckResult.executionError(checker, e);
        }
    }

    private static void writeReport(List<CheckResult> failedResults) {
        LocalDateTime generatedAt = LocalDateTime.now();
        Path reportDirectory = BetterResidence.getInstance().getDataFolder().toPath().resolve("report");
        Path reportFile = reportDirectory.resolve("data-integrity-" + FILE_TIME_FORMAT.format(generatedAt) + ".txt");

        try {
            Files.createDirectories(reportDirectory);
            Files.writeString(reportFile, createReport(generatedAt, failedResults), StandardCharsets.UTF_8);
            lastReportFile = reportFile;
            PluginLogger.warning("Data integrity check found " + failedResults.size() + " failed checks. Report: " + reportFile);
        } catch (IOException e) {
            PluginLogger.error("Failed to write data integrity report: " + e.getMessage());
        }
    }

    private static String createReport(LocalDateTime generatedAt, List<CheckResult> failedResults) {
        StringBuilder report = new StringBuilder();
        report.append("BetterResidence Data Integrity Report\n");
        report.append("Generated At: ").append(REPORT_TIME_FORMAT.format(generatedAt)).append('\n');
        report.append("Failed Checks: ").append(failedResults.size()).append("\n\n");

        for (int i = 0; i < failedResults.size(); i++) {
            appendResult(report, failedResults.get(i));
            if (i + 1 < failedResults.size()) {
                report.append('\n');
            }
        }

        return report.toString();
    }

    private static void appendResult(StringBuilder report, CheckResult result) {
        report.append("============================================================\n");
        report.append("Check: ").append(result.name()).append('\n');
        report.append("Description:\n").append(indent(result.description())).append("\n\n");

        if (result.executionError() != null) {
            report.append("Check Execution Error:\n");
            report.append(indent(stackTrace(result.executionError()))).append('\n');
            report.append("============================================================\n");
            return;
        }

        report.append("Corrupted Rows: ").append(result.corruptedRows().size()).append("\n\n");
        report.append("Example Row:\n");
        appendRow(report, result.corruptedRows().getFirst());
        report.append("============================================================\n");
    }

    private static void appendRow(StringBuilder report, Map<String, Object> row) {
        if (row.isEmpty()) {
            report.append("  <empty row>\n");
            return;
        }
        row.forEach((key, value) -> report.append("  ")
                .append(key)
                .append(": ")
                .append(value)
                .append('\n'));
    }

    private static String indent(String text) {
        String value = text == null ? "" : text;
        return "  " + value.replace("\n", "\n  ");
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString().stripTrailing();
    }

    public interface IntegrityChecker {
        String name();

        String description();

        List<Map<String, Object>> findCorruptedRows();
    }

    public record CheckProgress(String name, boolean failed, int corruptedRows, boolean executionError) {
    }

    private record CheckResult(String name, String description,
                               List<Map<String, Object>> corruptedRows, Throwable executionError) {
        private static CheckResult completed(IntegrityChecker checker, List<Map<String, Object>> corruptedRows) {
            return new CheckResult(
                    checker.name(),
                    checker.description(),
                    List.copyOf(corruptedRows),
                    null
            );
        }

        private static CheckResult executionError(IntegrityChecker checker, Throwable executionError) {
            return new CheckResult(
                    checker.name(),
                    checker.description(),
                    List.of(),
                    executionError
            );
        }

        private boolean failed() {
            return executionError != null || !corruptedRows.isEmpty();
        }
    }

}
