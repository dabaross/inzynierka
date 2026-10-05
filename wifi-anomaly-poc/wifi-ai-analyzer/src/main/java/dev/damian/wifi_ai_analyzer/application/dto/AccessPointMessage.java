package dev.damian.wifi_ai_analyzer.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccessPointMessage {

    @JsonProperty("ssid")
    private String ssid;

    @JsonProperty("bssid")
    private String bssid;

    @JsonProperty("rssi")
    private Integer rssi;

    @JsonProperty("channel")
    private Integer channel;

    @JsonProperty("authMode")
    private Integer authMode;

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public void setBssid(String bssid) {
        this.bssid = bssid;
    }

    public Integer getRssi() {
        return rssi;
    }

    public void setRssi(Integer rssi) {
        this.rssi = rssi;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public Integer getAuthMode() {
        return authMode;
    }

    public void setAuthMode(Integer authMode) {
        this.authMode = authMode;
    }
}
