package dev.damian.wifi_ai_analyzer.application;

import dev.damian.wifi_ai_analyzer.application.dto.AccessPointMessage;
import dev.damian.wifi_ai_analyzer.application.dto.WifiScanMessage;
import dev.damian.wifi_ai_analyzer.domain.AccessPointObservationEntity;
import dev.damian.wifi_ai_analyzer.domain.WifiScanEntity;
import dev.damian.wifi_ai_analyzer.domain.WifiScanRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WifiScanService {

    private static final Logger log = LoggerFactory.getLogger(WifiScanService.class);

    private final WifiScanRepository wifiScanRepository;

    public WifiScanService(WifiScanRepository wifiScanRepository) {
        this.wifiScanRepository = wifiScanRepository;
    }

    @Transactional
    public void handleScan(WifiScanMessage message) {
        if (message == null) {
            log.warn("Ignoring null wifi scan message");
            return;
        }

        if (message.getSchemaVersion() == null || !Integer.valueOf(1).equals(message.getSchemaVersion())) {
            log.warn(
                "Unsupported schema version {} for scanId {}. Only schemaVersion=1 is supported.",
                message.getSchemaVersion(),
                message.getScanId()
            );
            return;
        }

        if (message.getScanId() == null || message.getScanId().isBlank()) {
            log.warn("Ignoring wifi scan message without scanId");
            return;
        }

        if (wifiScanRepository.existsByScanId(message.getScanId())) {
            log.info("Ignoring duplicate wifi scan message with scanId={}", message.getScanId());
            return;
        }

        if (message.getSensorId() == null || message.getSensorId().isBlank()) {
            log.warn("Ignoring wifi scan message without sensorId for scanId={}", message.getScanId());
            return;
        }

        if (message.getTotalDetected() == null || message.getTotalDetected() < 0) {
            log.warn("Ignoring wifi scan message with invalid totalDetected for scanId={}", message.getScanId());
            return;
        }

        if (message.getResultsStored() == null || message.getResultsStored() < 0) {
            log.warn("Ignoring wifi scan message with invalid resultsStored for scanId={}", message.getScanId());
            return;
        }

        if (message.getAccessPoints() == null) {
            log.warn("Ignoring wifi scan message without accessPoints for scanId={}", message.getScanId());
            return;
        }

        if (message.getResultsStored() != message.getAccessPoints().size()) {
            log.warn(
                "Ignoring wifi scan message because resultsStored={} does not match accessPoints size={} for scanId={}",
                message.getResultsStored(),
                message.getAccessPoints().size(),
                message.getScanId()
            );
            return;
        }

        if (message.getTotalDetected() < message.getResultsStored()) {
            log.warn(
                "Ignoring wifi scan message because totalDetected={} is less than resultsStored={} for scanId={}",
                message.getTotalDetected(),
                message.getResultsStored(),
                message.getScanId()
            );
            return;
        }

        WifiScanEntity scan = new WifiScanEntity();
        scan.setScanId(message.getScanId());
        scan.setSensorId(message.getSensorId());
        scan.setSchemaVersion(message.getSchemaVersion());
        scan.setTotalDetected(message.getTotalDetected());
        scan.setResultsStored(message.getResultsStored());
        scan.setReceivedAt(Instant.now());

        for (AccessPointMessage accessPointMessage : message.getAccessPoints()) {
            if (accessPointMessage == null) {
                log.warn("Skipping null AP observation in scanId={}", message.getScanId());
                continue;
            }

            if (accessPointMessage.getSsid() == null || accessPointMessage.getSsid().isBlank()) {
                log.warn("Ignoring wifi scan message because AP ssid is blank for scanId={}", message.getScanId());
                return;
            }

            if (accessPointMessage.getBssid() == null || accessPointMessage.getBssid().isBlank()) {
                log.warn("Ignoring wifi scan message because AP bssid is blank for scanId={}", message.getScanId());
                return;
            }

            if (accessPointMessage.getRssi() == null) {
                log.warn("Ignoring wifi scan message because AP rssi is null for scanId={}", message.getScanId());
                return;
            }

            if (accessPointMessage.getChannel() == null || accessPointMessage.getChannel() <= 0) {
                log.warn("Ignoring wifi scan message because AP channel is invalid for scanId={}", message.getScanId());
                return;
            }

            if (accessPointMessage.getAuthMode() == null) {
                log.warn("Ignoring wifi scan message because AP authMode is null for scanId={}", message.getScanId());
                return;
            }

            AccessPointObservationEntity accessPointObservation = new AccessPointObservationEntity(
                accessPointMessage.getSsid(),
                accessPointMessage.getBssid(),
                accessPointMessage.getRssi(),
                accessPointMessage.getChannel(),
                accessPointMessage.getAuthMode()
            );
            scan.addAccessPoint(accessPointObservation);
        }

        WifiScanEntity savedScan = wifiScanRepository.save(scan);
        log.info(
            "Persisted wifi scan with scanId={} and {} access points",
            savedScan.getScanId(),
            savedScan.getAccessPoints().size()
        );
    }
}
