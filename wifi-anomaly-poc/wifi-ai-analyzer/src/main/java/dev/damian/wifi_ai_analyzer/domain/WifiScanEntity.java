package dev.damian.wifi_ai_analyzer.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wifi_scan")
public class WifiScanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false, unique = true, length = 64)
    private String scanId;

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "total_detected", nullable = false)
    private Integer totalDetected;

    @Column(name = "results_stored", nullable = false)
    private Integer resultsStored;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @OneToMany(mappedBy = "wifiScan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessPointObservationEntity> accessPoints = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Integer getTotalDetected() {
        return totalDetected;
    }

    public void setTotalDetected(Integer totalDetected) {
        this.totalDetected = totalDetected;
    }

    public Integer getResultsStored() {
        return resultsStored;
    }

    public void setResultsStored(Integer resultsStored) {
        this.resultsStored = resultsStored;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public List<AccessPointObservationEntity> getAccessPoints() {
        return accessPoints;
    }

    public void setAccessPoints(List<AccessPointObservationEntity> accessPoints) {
        this.accessPoints.clear();
        if (accessPoints != null) {
            accessPoints.forEach(this::addAccessPoint);
        }
    }

    public void addAccessPoint(AccessPointObservationEntity accessPoint) {
        accessPoints.add(accessPoint);
        accessPoint.setWifiScan(this);
    }
}
