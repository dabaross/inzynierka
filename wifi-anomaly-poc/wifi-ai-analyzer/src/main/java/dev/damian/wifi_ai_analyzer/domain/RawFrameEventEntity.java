package dev.damian.wifi_ai_analyzer.domain; // móiw Javie w jakim folderze leży klasa.

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity // mówi Javie, że ta klasa to encja JPA, czyli będzie mapowana na tabelę w bazie danych za pomocą Hibernate
public class RawFrameEventEntity {

    @Id // mówi Javie, że to pole jest kluczem głównym w tabeli bazy danych
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    //ta wartość ma być generowana przez bzae danych, niech baza nadaje kolejne wartości (1, 2, 3, ...)
    private Long id;

    // poniższe pola to zwykłe pola klasy, które stają sie kolumnami o tych samych nazwach w tabeli bazy danych
    private String frameType;
    private String srcMac;
    private Integer rssi;
    private String ssid;
    private String bssid;
    private Instant receivedAt;

    // poniżej są gettery i settery do wszystkich pól klasy. Hibernate potrzebuje ich do mapowania encji na tabelę w bazie danych

    // gettery:
    public Long getId() {
        return id;
    }

    public String getFrameType() {
        return frameType;
    }

    public String getSrcMac() {
        return srcMac;
    }

    public Integer getRssi() {
        return rssi;
    }

    public String getSsid() {
        return ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    // settery:
    public void setId(Long id) {
        this.id = id;
    }

    public void setFrameType(String frameType) {
        this.frameType = frameType;
    }

    public void setSrcMac(String srcMac){
        this.srcMac = srcMac;
    }

    public void setRssi (Integer rssi){
        this.rssi = rssi;
    }

    public void setSsid (String ssid){
        this.ssid = ssid;
    }

    public void setBssid (String bssid){
        this.bssid = bssid;
    }

    public void setReceivedAt (Instant receivedAt){
        this.receivedAt = receivedAt;
    }
}
