package dev.damian.wifi_ai_analyzer.application.dto;

import java.util.Locale;

public record FrameWindow(
        int schemaVersion, String windowId, String sensorId, String bssid,
        int channel, int durationMs, int beacon, int deauth, int disassoc, int retry) {

    public FrameWindow {
        if (schemaVersion != 1 || windowId == null || !windowId.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Invalid window version or ID");
        }
        bssid = new Stream(sensorId, bssid, channel).bssid();
        if (durationMs < 4500 || durationMs > 5500
                || !validCount(beacon) || !validCount(deauth) || !validCount(disassoc)
                || retry < 0 || retry > deauth + disassoc) {
            throw new IllegalArgumentException("Invalid duration or frame counts");
        }
    }

    private static boolean validCount(int count) {
        return count >= 0 && count <= 100_000;
    }

    public Stream stream() {
        return new Stream(sensorId, bssid, channel);
    }

    public record Stream(String sensorId, String bssid, int channel) {
        public Stream {
            if (sensorId == null || !sensorId.matches("[A-Za-z0-9_-]{1,64}")
                    || bssid == null || !bssid.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
                    || channel < 1 || channel > 14) {
                throw new IllegalArgumentException("Invalid sensor, BSSID or channel");
            }
            bssid = bssid.toLowerCase(Locale.ROOT);
        }
    }
}
