package dev.damian.wifi_ai_analyzer.application;

import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow.Stream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FrameWindowService {
    private final JdbcTemplate db;

    public FrameWindowService(JdbcTemplate db) {
        this.db = db;
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

    public List<Measurement> windows(Stream stream, int limit) {
        return db.query("""
                SELECT * FROM frame_window WHERE sensor_id = ? AND bssid = ? AND channel = ?
                ORDER BY id DESC LIMIT ?
                """, (rs, row) -> new Measurement(rs.getLong("id"), rs.getString("window_id"),
                        rs.getObject("received_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("duration_ms"), rs.getInt("beacon"), rs.getInt("deauth"),
                        rs.getInt("disassoc"), rs.getInt("retry")),
                stream.sensorId(), stream.bssid(), stream.channel(), limit);
    }

    public record Measurement(long id, String windowId, Instant receivedAt, int durationMs,
                              int beacon, int deauth, int disassoc, int retry) {}
}
