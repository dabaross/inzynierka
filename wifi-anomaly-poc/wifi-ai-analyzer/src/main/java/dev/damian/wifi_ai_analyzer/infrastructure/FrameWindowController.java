package dev.damian.wifi_ai_analyzer.infrastructure;

import dev.damian.wifi_ai_analyzer.application.FrameWindowService;
import dev.damian.wifi_ai_analyzer.application.FrameWindowService.Results;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow.Stream;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
            stream(sensorId == null ? "filter" : sensorId,
                    bssid == null ? "00:00:00:00:00:00" : bssid,
                    channel == null ? 1 : channel);
        }
        return service.streams(sensorId, bssid == null ? null : bssid.toLowerCase(java.util.Locale.ROOT), channel);
    }

    @GetMapping("/windows")
    public Results windows(@RequestParam String sensorId, @RequestParam String bssid,
            @RequestParam int channel, @RequestParam(defaultValue = "60") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1–1000");
        }
        return service.results(stream(sensorId, bssid, channel), limit);
    }

    @PostMapping("/calibration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void calibrate(@RequestParam String sensorId, @RequestParam String bssid,
            @RequestParam int channel, @RequestParam(defaultValue = "30") int samples) {
        if (samples != 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "samples must be 30");
        }
        if (!service.calibrate(stream(sensorId, bssid, channel), samples)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Collect 30 recent windows with beacons before calibrating");
        }
    }

    private Stream stream(String sensorId, String bssid, int channel) {
        try {
            return new Stream(sensorId, bssid, channel);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
