package dev.damian.wifi_ai_analyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "access_point_observation")
public class AccessPointObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wifi_scan_id", nullable = false)
    private WifiScanEntity wifiScan;

    @Column(name = "ssid", nullable = false)
    private String ssid;

    @Column(name = "bssid", nullable = false, length = 17)
    private String bssid;

    @Column(name = "rssi", nullable = false)
    private Integer rssi;

    @Column(name = "channel", nullable = false)
    private Integer channel;

    @Column(name = "auth_mode", nullable = false)
    private Integer authMode;

    public AccessPointObservationEntity() {
    }

    public AccessPointObservationEntity(String ssid, String bssid, Integer rssi, Integer channel, Integer authMode) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.channel = channel;
        this.authMode = authMode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WifiScanEntity getWifiScan() {
        return wifiScan;
    }

    public void setWifiScan(WifiScanEntity wifiScan) {
        this.wifiScan = wifiScan;
    }

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
