# ESP32: skan Wi-Fi wysyłany przez MQTT

Komplet plików komponentu `main` dla ESP-IDF 6.x. Program:

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

W menu `Konfiguracja skanera Wi-Fi` ustaw:

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
