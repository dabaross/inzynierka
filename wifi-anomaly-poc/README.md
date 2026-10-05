# Wi-Fi anomaly PoC

Projekt zbierający skany Wi-Fi z ESP32 przez MQTT i zapisujący je w backendzie Java.

- `scan/` — aktywny firmware ESP-IDF; konfiguracja i format wiadomości w [instrukcji firmware](scan/README.md).
- `wifi-ai-analyzer/` — backend Spring Boot, Java 21 i Maven Wrapper.
- `mosquitto/config/` — konfiguracja brokera MQTT.
- `docker-compose.yml` — lokalny broker Mosquitto i PostgreSQL.

## Uruchomienie lokalne

Z katalogu `wifi-anomaly-poc/` uruchom usługi:

```bash
docker compose up -d
```

Następnie uruchom backend:

```bash
cd wifi-ai-analyzer
./mvnw spring-boot:run
```

Konfiguracja backendu znajduje się w `wifi-ai-analyzer/src/main/resources/application.properties`.
Firmware skonfiguruj i wgraj zgodnie z instrukcją w `scan/README.md`.

## Zasady wersjonowania

Repozytorium przechowuje źródła, manifesty zależności, `scan/dependencies.lock`
i współdzielone ustawienia `scan/sdkconfig.defaults`.
Wyniki kompilacji (`build/`, `target/`), pobrane komponenty (`managed_components/`),
lokalny `sdkconfig`, ustawienia IDE, kopie zapasowe i pliki robocze MQTT
pozostają poza Git. Archiwów eksportowanych z projektu nie dodawaj do repozytorium.
Jedyną utrzymywaną wersją firmware jest `scan/main/`.
