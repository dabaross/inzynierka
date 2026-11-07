# ESP32: skan i monitor ramek Wi-Fi

Komplet plików komponentu `main` dla ESP-IDF 6.x. W domyślnym trybie skanu program:

1. inicjalizuje NVS i Wi-Fi,
2. wykonuje jeden skan wszystkich kanałów,
3. buduje jedną wiadomość JSON zawierającą listę wykrytych BSS,
4. łączy ESP32 z siecią skonfigurowaną w `menuconfig`,
5. publikuje skan z QoS 1 na temat `wifi/scan`,
6. zwalnia JSON z RAM dopiero po potwierdzeniu publikacji przez broker.

## Konfiguracja i uruchomienie

W aktywnym środowisku ESP-IDF przejdź do katalogu `scan/`.
Projekt używa ESP32; wersje zależności zapisano w `dependencies.lock`.
ESP-IDF pobiera komponenty do ignorowanego katalogu `managed_components/`.

```bash
idf.py menuconfig
```

W menu `Konfiguracja sensora Wi-Fi` ustaw:

* SSID i hasło Wi-Fi,
* URI brokera, np. `mqtt://192.168.1.96:1883`,
* temat `wifi/scan`,
* nazwę sensora, np. `esp32-01`,
* maksymalną liczbę wyników skanu.

Budowanie i uruchomienie:

```bash
idf.py build
idf.py -p /dev/ttyUSB0 flash monitor
```

## Format wiadomości

```json
{
  "schemaVersion": 1,
  "scanId": "d36e8b93dd597e599a920a6fcfc94d0b",
  "sensorId": "esp32-01",
  "totalDetected": 5,
  "resultsStored": 5,
  "accessPoints": [
    {
      "ssid": "mieszkanie2",
      "bssid": "AA:BB:CC:DD:EE:FF",
      "rssi": -62,
      "channel": 6,
      "authMode": 3
    }
  ]
}
```

`scanId` identyfikuje jeden skan i powinien później mieć ograniczenie `UNIQUE`
w bazie. Chroni to przed utworzeniem dwóch rekordów, gdy wiadomość QoS 1 zostanie
dostarczona ponownie.

`totalDetected` oznacza liczbę BSS wykrytych przez sterownik. `resultsStored`
oznacza liczbę rekordów, które zmieściły się w tablicy i zostały wysłane.

Czas odbioru powinien na tym etapie nadać backend. Firmware nie synchronizuje
jeszcze zegara przez SNTP.

## Pliki lokalne

`sdkconfig` zawiera konfigurację konkretnego urządzenia i pozostaje lokalny.
W repozytorium znajduje się `sdkconfig.defaults` z domyślnym targetem ESP32;
SSID i hasło ustaw przez `menuconfig`. Katalogi `build/` i
`managed_components/` są odtwarzane przez ESP-IDF i nie są wersjonowane.

## Monitor ramek zarządzających

Ten sam firmware ma tryb monitorowania aktywowany w `idf.py menuconfig`.
Włącz opcję **Monitoruj ramki zarządzające zamiast jednorazowego skanu**,
wpisz BSSID punktu dostępowego sieci testowej i ustaw dane Wi-Fi oraz brokera.
ESP32 łączy się z wybranym BSSID w trybie STA, żeby wysyłać wyniki.
Jednocześnie odbiera ramki w trybie promiscuous na kanale tego połączenia.

Co około 5 s sensor wysyła JSON w wersji 1 na `wifi/window` z QoS 1.
Komunikat zawiera identyfikator okna, sensor, BSSID, kanał, czas trwania
w milisekundach oraz liczniki `beacon`, `deauth`, `disassoc` i `retry`.
`retry` obejmuje tylko deauth i disassoc z ustawionym bitem Retry.
Po restarcie sensora lub utracie połączenia w historii może powstać luka.
