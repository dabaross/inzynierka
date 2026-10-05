package dev.damian.wifi_ai_analyzer.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WifiScanMessage {

    @JsonProperty("schemaVersion")
    private Integer schemaVersion;

    @JsonProperty("scanId")
    private String scanId;

    @JsonProperty("sensorId")
    private String sensorId;

    @JsonProperty("totalDetected")
    private Integer totalDetected;

    @JsonProperty("resultsStored")
    private Integer resultsStored;

    @JsonProperty("accessPoints")
    private List<AccessPointMessage> accessPoints = new ArrayList<>();

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
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

    public List<AccessPointMessage> getAccessPoints() {
        return accessPoints;
    }

    public void setAccessPoints(List<AccessPointMessage> accessPoints) {
        this.accessPoints = accessPoints == null ? new ArrayList<>() : accessPoints;
    }
}
