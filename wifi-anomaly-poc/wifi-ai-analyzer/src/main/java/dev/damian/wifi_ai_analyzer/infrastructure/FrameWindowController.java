package dev.damian.wifi_ai_analyzer.infrastructure;

import dev.damian.wifi_ai_analyzer.application.FrameWindowService;
import dev.damian.wifi_ai_analyzer.application.FrameWindowService.Measurement;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow.Stream;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class FrameWindowController {
    private final FrameWindowService service;

    public FrameWindowController(FrameWindowService service) {
        this.service = service;
    }

    @GetMapping("/streams")
    public List<Stream> streams(@RequestParam(required = false) String sensorId,
                                @RequestParam(required = false) String bssid,
                                @RequestParam(required = false) Integer channel) {
        if (sensorId != null || bssid != null || channel != null) {
            try {
                // Validate each supplied filter with the same constraints as the input schema.
                new Stream(sensorId == null ? "filter" : sensorId,
                        bssid == null ? "00:00:00:00:00:00" : bssid,
                        channel == null ? 1 : channel);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }
        }
        return service.streams(sensorId, bssid == null ? null : bssid.toLowerCase(java.util.Locale.ROOT), channel);
    }

    @GetMapping("/windows")
    public List<Measurement> windows(@RequestParam String sensorId, @RequestParam String bssid,
                                     @RequestParam int channel,
                                     @RequestParam(defaultValue = "60") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1–1000");
        }
        try {
            return service.windows(new Stream(sensorId, bssid, channel), limit);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
