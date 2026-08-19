package net.player005.vanillablocking;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks Modrinth whether a newer release exists. Runs off the main thread and
 * fails silently: an unreachable API is never worth a stack trace in
 * someone's console.
 */
public final class UpdateChecker {

    /** Matches the first {@code "version_number": "..."} in the response. */
    private static final Pattern VERSION_NUMBER = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final Logger logger;
    private final String project;
    private final String currentVersion;

    public UpdateChecker(@NotNull Logger logger, @NotNull String project, @NotNull String currentVersion) {
        this.logger = logger;
        this.project = project;
        this.currentVersion = currentVersion;
    }

    /**
     * Fetches the newest published version and logs a note when it is newer
     * than the running one. Blocking - call it from an async task.
     */
    public void check() {
        if (project.isBlank()) return;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/project/" + project + "/version"))
                    .header("User-Agent", "VanillaSwordBlocking/" + currentVersion)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            String latest = firstVersionNumber(response.body());
            if (latest == null) return;

            if (isNewer(latest, currentVersion)) {
                logger.info("VanillaSwordBlocking {} is available - you are running {}.", latest, currentVersion);
            }
        } catch (Exception exception) {
            logger.debug("Update check failed.", exception);
        }
    }

    /**
     * Modrinth returns versions newest first.
     */
    static @Nullable String firstVersionNumber(@NotNull String body) {
        Matcher matcher = VERSION_NUMBER.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Compares dotted version numbers numerically, ignoring any suffix such
     * as {@code -SNAPSHOT} or {@code +build}.
     */
    static boolean isNewer(@NotNull String candidate, @NotNull String current) {
        int[] left = parse(candidate);
        int[] right = parse(current);

        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static int[] parse(@NotNull String version) {
        String cleaned = version.toLowerCase(Locale.ROOT).split("[-+]", 2)[0].trim();
        String[] parts = cleaned.split("\\.");

        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].replaceAll("\\D", ""));
            } catch (NumberFormatException exception) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
