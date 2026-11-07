package dev.damian.wifi_ai_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.damian.wifi_ai_analyzer.application.DisconnectDetector;
import dev.damian.wifi_ai_analyzer.application.DisconnectDetector.Counts;
import java.util.Collections;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DisconnectDetectorTests {
    @Test
    void normalizesWindowAndScoresThreeNearestNeighbors() {
        DisconnectDetector detector = new DisconnectDetector(Collections.nCopies(30, new Counts(0, 0)));
        assertEquals(0, detector.score(new Counts(0, 0)), 1e-9);
        assertEquals(5, detector.score(Counts.perFiveSeconds(2, 0, 2000)), 1e-9);
        assertEquals(Math.hypot(30, 12), detector.score(new Counts(30, 12)), 1e-9);
    }

    @Test
    void usesStandardDeviationAndMeanOfThreeClosestWindows() {
        DisconnectDetector detector = new DisconnectDetector(IntStream.range(0, 30)
                .mapToObj(value -> new Counts(value, 0)).toList());
        double variance = IntStream.range(0, 30)
                .mapToDouble(value -> Math.pow(value - 14.5, 2)).average().orElseThrow();
        assertEquals(2 / Math.sqrt(variance), detector.score(new Counts(30, 0)), 1e-9);
    }
}
