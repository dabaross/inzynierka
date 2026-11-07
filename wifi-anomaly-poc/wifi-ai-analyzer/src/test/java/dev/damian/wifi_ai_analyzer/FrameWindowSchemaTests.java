package dev.damian.wifi_ai_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow;
import org.junit.jupiter.api.Test;

class FrameWindowSchemaTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsVersionedWindowAndNormalizesBssid() throws Exception {
        FrameWindow window = mapper.readValue("""
                {"schemaVersion":1,"windowId":"0123456789abcdef0123456789abcdef",
                 "sensorId":"esp32-01","bssid":"AA:BB:CC:DD:EE:FF","channel":6,
                 "durationMs":5000,"beacon":48,"deauth":2,"disassoc":1,"retry":1}
                """, FrameWindow.class);
        assertEquals("aa:bb:cc:dd:ee:ff", window.bssid());
        assertEquals(1, window.retry());
    }

    @Test
    void rejectsMissingAndInvalidCounts() {
        var reader = mapper.readerFor(FrameWindow.class)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        String valid = """
                {"schemaVersion":1,"windowId":"0123456789abcdef0123456789abcdef",
                 "sensorId":"esp32-01","bssid":"aa:bb:cc:dd:ee:ff","channel":6,
                 "durationMs":5000,"beacon":48,"deauth":2,"disassoc":1,"retry":1}
                """;
        assertThrows(Exception.class, () -> reader.readValue(valid.replace("\"deauth\":2,", "")));
        assertThrows(Exception.class, () -> reader.readValue(valid.replace("\"retry\":1", "\"retry\":4")));
    }
}
