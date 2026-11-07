# Wi-Fi anomaly PoC

ESP32 liczy ramki zarządzające wybranej sieci Wi-Fi 2,4 GHz w oknach po 5 sekund.
Mosquitto przekazuje okna do Spring Boot, a backend zapisuje je w PostgreSQL.
Panel porównuje alert oparty na progu liczby ramek z wynikiem modelu kNN.
Firmware może też wykonać dotychczasowy skan sieci.

- `scan/` — aktywny firmware ESP-IDF; konfiguracja i format wiadomości w [instrukcji firmware](scan/README.md).
- `wifi-ai-analyzer/` — backend Spring Boot, Java 21 i Maven Wrapper.
- `mosquitto/config/` — konfiguracja brokera MQTT.
- `docker-compose.yml` — lokalny broker Mosquitto i PostgreSQL.
- `WINDOW_PROTOCOL.md` — wspólny format okna dla ESP32, backendu i próby syntetycznej.

## Uruchomienie lokalne na Ubuntu

Z katalogu `wifi-anomaly-poc/` uruchom usługi:

```bash
docker compose up -d
```

Następnie uruchom backend:

```bash
cd wifi-ai-analyzer
./mvnw spring-boot:run
```

W drugim terminalu, także w katalogu `wifi-anomaly-poc/`, wykonaj
`python3 demo.py`. Skrypt wymaga działających usług Docker Compose i backendu.
Testy Javy uruchom poleceniem `cd wifi-ai-analyzer && ./mvnw test`.

Konfiguracja backendu znajduje się w `wifi-ai-analyzer/src/main/resources/application.properties`.
Firmware skonfiguruj i wgraj zgodnie z instrukcją w `scan/README.md`.

## Próba bez ESP32

Po uruchomieniu usług i backendu otwórz [panel](http://localhost:8080).
Z katalogu `wifi-anomaly-poc/` uruchom:

```bash
python3 demo.py
```

Skrypt przesyła okna przez Mosquitto, kalibruje model na 30 oknach i sprawdza
alarmy. Używa ustawień domyślnych. W panelu wybierz strumień `demo-...`.
Każde uruchomienie tworzy osobny strumień danych syntetycznych.

## Pomiar własnej sieci

1. Ustaw router na stały kanał 1, 6 lub 11 z szerokością 20 MHz.
   Połącz komputer z portem LAN routera i odczytaj jego adres IPv4.
2. W `scan/` uruchom `idf.py menuconfig`. W menu **Konfiguracja sensora Wi-Fi**
   wpisz SSID i hasło sieci testowej oraz URI brokera
   `mqtt://ADRES_KOMPUTERA:1883`. Włącz monitor ramek i wpisz BSSID sieci testowej.
   Domyślny temat okien to `wifi/window`. Zbuduj i wgraj zgodnie z [instrukcją firmware](scan/README.md).
3. W panelu wybierz sensor, BSSID i kanał. Sprawdź, czy co około 5 s pojawia się
   okno z beaconami. Zbierz co najmniej 30 okien zwykłego ruchu, czyli około
   2,5 minuty. Kliknij **Ucz na ostatnich 30 oknach**.
4. Zbierz kolejne okna zwykłego ruchu oraz pomiary podczas kontrolowanego
   eksperymentu we własnej sieci. Zapisz czas i warunki prób. Pobierz CSV dla
   każdego przebiegu z panelu, który pokazuje ostatnie 60 okien. API
   `GET /api/windows` przyjmuje parametr `limit` do 1000 oraz `sensorId`,
   `bssid` i `channel`. Trzymaj dane uczące oddzielnie od prób oceny.
5. Na obecnym etapie sprawdź tylko, czy kilka kolejnych okien z beaconami dociera
   z ESP32 przez MQTT do API. Nie generuj ramek deauth w eterze. Próbę
   wykrywania rzeczywistej serii wykonaj osobno po przygotowaniu karty i
   kontrolowanego stanowiska. Odnotuj okna bez beaconów i możliwe luki.

Na Ubuntu z zainstalowanym ESP-IDF i podłączonym ESP32 użyj osobnego terminala:

```bash
source ~/esp/esp-idf/export.sh
cd scan
idf.py menuconfig
idf.py build
idf.py -p /dev/ttyUSB0 flash monitor
```

W `menuconfig` wpisz BSSID TP-Linka, włącz monitor oraz ustaw URI brokera na
`mqtt://ADRES_IP_UBUNTU:1883`. W razie innego portu szeregowego podstaw jego
ścieżkę zamiast `/dev/ttyUSB0`. Dane urządzenia są w lokalnym `scan/sdkconfig`
poza Gitem. Wyniki można też obserwować poleceniem
`docker compose exec mosquitto mosquitto_sub -t wifi/window -v` z katalogu PoC.

## Jak działa wykrywanie

- Próg sprawdza, czy suma deauth i disassoc wynosi co najmniej 10 ramek w
  przeliczeniu na 5 s. Retransmisje wliczają się do sumy i mają osobny licznik.
- kNN porównuje osobno liczbę deauth i disassoc z 30 normalnymi oknami.
  Skaluje każdą cechę odchyleniem standardowym z danych uczących (minimum 1).
  Wynik to średnia odległość euklidesowa od 3 najbliższych okien uczących.
  Domyślny próg alarmu wynosi 5. Wynik jest dostępny dla okien po kalibracji.
- Brak beaconów w odebranym oknie wstrzymuje oba werdykty. Długa przerwa
  między zapisami jest pokazana jako możliwa luka; nie jest liczona jako okno
  z zerową liczbą ramek rozłączających. Opóźnienie sieci też może ją wywołać.
- Progi można ustawić przez `DETECTOR_COUNT_THRESHOLD` i `DETECTOR_SCORE_THRESHOLD`.
  Domyślne wartości służą do uruchomienia demonstratora. Skuteczność obu metod
  trzeba ocenić na osobnych pomiarach ze znanym przebiegiem eksperymentu.

## Zasady wersjonowania

Repozytorium przechowuje źródła, manifesty zależności, `scan/dependencies.lock`
i współdzielone ustawienia `scan/sdkconfig.defaults`.
Wyniki kompilacji (`build/`, `target/`), pobrane komponenty (`managed_components/`),
lokalny `sdkconfig`, ustawienia IDE, kopie zapasowe i pliki robocze MQTT
pozostają poza Git. Archiwów eksportowanych z projektu nie dodawaj do repozytorium.
Jedyną utrzymywaną wersją firmware jest `scan/main/`.
