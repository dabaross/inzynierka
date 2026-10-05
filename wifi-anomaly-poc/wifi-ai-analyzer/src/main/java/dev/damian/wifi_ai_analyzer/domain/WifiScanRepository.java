package dev.damian.wifi_ai_analyzer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WifiScanRepository extends JpaRepository<WifiScanEntity, Long> {

    boolean existsByScanId(String scanId);
}
