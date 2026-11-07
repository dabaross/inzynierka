package dev.damian.wifi_ai_analyzer.application;

import dev.damian.wifi_ai_analyzer.application.DisconnectDetector.Counts;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow.Stream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FrameWindowService {
    private final JdbcTemplate db;
    private final double countThreshold;
    private final double scoreThreshold;

    public FrameWindowService(JdbcTemplate db,
            @Value("${detector.count-threshold:10}") double countThreshold,
            @Value("${detector.score-threshold:5}") double scoreThreshold) {
        if (!Double.isFinite(countThreshold) || countThreshold <= 0
                || !Double.isFinite(scoreThreshold) || scoreThreshold <= 0) {
            throw new IllegalArgumentException("Detector thresholds must be positive and finite");
        }
        this.db = db;
        this.countThreshold = countThreshold;
        this.scoreThreshold = scoreThreshold;
    }

    public void ingest(FrameWindow window) {
        db.update("""
                INSERT INTO frame_window
                  (window_id, sensor_id, bssid, channel, duration_ms, beacon, deauth, disassoc, retry)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (window_id) DO NOTHING
                """, window.windowId(), window.sensorId(), window.bssid(), window.channel(),
                window.durationMs(), window.beacon(), window.deauth(), window.disassoc(), window.retry());
    }

    public List<Stream> streams(String sensorId, String bssid, Integer channel) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT sensor_id, bssid, channel FROM frame_window WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (sensorId != null) {
            sql.append(" AND sensor_id = ?");
            args.add(sensorId);
        }
        if (bssid != null) {
            sql.append(" AND bssid = ?");
            args.add(bssid);
        }
        if (channel != null) {
            sql.append(" AND channel = ?");
            args.add(channel);
        }
        sql.append(" ORDER BY sensor_id, bssid, channel");
        return db.query(sql.toString(), (rs, row) -> new Stream(rs.getString(1), rs.getString(2), rs.getInt(3)),
                args.toArray());
    }

    public boolean calibrate(Stream stream, int samples) {
        // Use exactly the selected windows, so a broker redelivery or a later insert
        // cannot change the reference data for an already calibrated stream.
        return db.update("""
                INSERT INTO frame_calibration (sensor_id, bssid, channel, window_ids)
                SELECT ?, ?, ?, array_agg(id ORDER BY id)
                FROM (
                    SELECT id, beacon FROM frame_window
                    WHERE sensor_id = ? AND bssid = ? AND channel = ?
                    ORDER BY id DESC LIMIT ?
                ) recent
                HAVING count(*) = ? AND min(beacon) > 0
                ON CONFLICT (sensor_id, bssid, channel) DO UPDATE
                SET window_ids = EXCLUDED.window_ids
                """, stream.sensorId(), stream.bssid(), stream.channel(),
                stream.sensorId(), stream.bssid(), stream.channel(), samples, samples) == 1;
    }

    public Results results(Stream stream, int limit) {
        List<Measurement> baseline = db.query("""
                SELECT w.* FROM frame_calibration c
                JOIN frame_window w ON w.id = ANY(c.window_ids)
                WHERE c.sensor_id = ? AND c.bssid = ? AND c.channel = ?
                ORDER BY w.id
                """, (rs, row) -> measurement(rs), stream.sensorId(), stream.bssid(), stream.channel());
        long cutoff = baseline.isEmpty() ? 0 : baseline.getLast().id();
        DisconnectDetector detector = baseline.isEmpty() ? null
                : new DisconnectDetector(baseline.stream().map(Measurement::counts).toList());

        List<Result> results = db.query("""
                SELECT * FROM frame_window WHERE sensor_id = ? AND bssid = ? AND channel = ?
                ORDER BY id DESC LIMIT ?
                """, (rs, row) -> {
                    Measurement window = measurement(rs);
                    Counts counts = window.counts();
                    boolean signalPresent = window.beacon() > 0;
                    Double score = detector != null && window.id() > cutoff && signalPresent
                            ? detector.score(counts) : null;
                    Boolean thresholdAlert = signalPresent
                            ? counts.deauth() + counts.disassoc() >= countThreshold : null;
                    return new Result(window, score, score == null ? null : score >= scoreThreshold,
                            thresholdAlert);
                }, stream.sensorId(), stream.bssid(), stream.channel(), limit);

        List<Result> withGaps = new ArrayList<>(results);
        for (int i = 0; i + 1 < results.size(); i++) {
            Result newer = results.get(i);
            Result older = results.get(i + 1);
            long intervalMs = newer.window().receivedAt().toEpochMilli()
                    - older.window().receivedAt().toEpochMilli();
            if (intervalMs > Math.max(8000, newer.window().durationMs() * 2L)) {
                withGaps.set(i, new Result(newer.window(), newer.score(), newer.mlAlert(),
                        newer.thresholdAlert(), intervalMs));
            }
        }
        return new Results(baseline.size(), countThreshold, scoreThreshold, withGaps);
    }

    private static Measurement measurement(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Measurement(rs.getLong("id"), rs.getString("window_id"),
                rs.getObject("received_at", OffsetDateTime.class).toInstant(),
                rs.getInt("duration_ms"), rs.getInt("beacon"), rs.getInt("deauth"),
                rs.getInt("disassoc"), rs.getInt("retry"));
    }

    public record Measurement(long id, String windowId, Instant receivedAt, int durationMs,
                              int beacon, int deauth, int disassoc, int retry) {
        Counts counts() {
            return Counts.perFiveSeconds(deauth, disassoc, durationMs);
        }
    }

    public record Result(Measurement window, Double score, Boolean mlAlert, Boolean thresholdAlert,
                         Long gapBeforeMs) {
        Result(Measurement window, Double score, Boolean mlAlert, Boolean thresholdAlert) {
            this(window, score, mlAlert, thresholdAlert, null);
        }
    }
    public record Results(int baselineSize, double countThreshold, double scoreThreshold, List<Result> windows) {}
}
