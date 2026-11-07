package dev.damian.wifi_ai_analyzer.application;

import java.util.List;

public final class DisconnectDetector {
    private static final int NEIGHBORS = 3;
    private final List<Counts> baseline;
    private final double deauthScale;
    private final double disassocScale;

    public DisconnectDetector(List<Counts> baseline) {
        if (baseline.size() < NEIGHBORS) {
            throw new IllegalArgumentException("At least three baseline windows are required");
        }
        this.baseline = List.copyOf(baseline);
        this.deauthScale = scale(baseline.stream().mapToDouble(Counts::deauth).toArray());
        this.disassocScale = scale(baseline.stream().mapToDouble(Counts::disassoc).toArray());
    }

    public double score(Counts window) {
        return baseline.stream()
                .mapToDouble(normal -> Math.hypot(
                        (window.deauth() - normal.deauth()) / deauthScale,
                        (window.disassoc() - normal.disassoc()) / disassocScale))
                .sorted()
                .limit(NEIGHBORS)
                .average()
                .orElseThrow();
    }

    private static double scale(double[] values) {
        double mean = java.util.Arrays.stream(values).average().orElseThrow();
        double variance = java.util.Arrays.stream(values)
                .map(value -> Math.pow(value - mean, 2))
                .average().orElseThrow();
        return Math.max(1, Math.sqrt(variance));
    }

    public record Counts(double deauth, double disassoc) {
        public static Counts perFiveSeconds(int deauth, int disassoc, int durationMs) {
            return new Counts(deauth * 5000.0 / durationMs, disassoc * 5000.0 / durationMs);
        }
    }
}
