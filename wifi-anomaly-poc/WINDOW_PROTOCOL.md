# Protokół okna ramek (wersja 1)

Sensor publikuje na `wifi/window` jeden obiekt JSON na każde zakończone okno.
Publikacja używa MQTT QoS 1, bez flagi retain. Odbiorca nadaje `receivedAt`
podczas zapisu w PostgreSQL. Ten znacznik oznacza czas odbioru, nie początek
pomiaru. Ponowna dostawa tego samego `windowId` nie tworzy nowego rekordu.

```json
{
  "schemaVersion": 1,
  "windowId": "0123456789abcdef0123456789abcdef",
  "sensorId": "esp32-01",
  "bssid": "aa:bb:cc:dd:ee:ff",
  "channel": 6,
  "durationMs": 5000,
  "beacon": 48,
  "deauth": 0,
  "disassoc": 0,
  "retry": 0
}
```

`windowId` to 32 małe znaki szesnastkowe. `sensorId` ma 1–64 znaki ASCII:
litery, cyfry, `_` lub `-`. BSSID ma sześć dwucyfrowych oktetów szesnastkowych
rozdzielonych dwukropkiem; backend zapisuje go małymi literami. Kanał ma
wartość 1–14, czas okna 4500–5500 ms. Liczniki są nieujemne, każdy z trzech
liczników typów ramek nie przekracza 100000, a `retry` nie przekracza sumy
`deauth + disassoc`. `retry` zlicza ramki deauth i disassoc z ustawionym bitem
Retry; te same ramki są również liczone w odpowiednim liczniku typu.

Brak dostarczonego okna oznacza lukę pomiarową. Okno z beaconami i zerowymi
licznikami ramek rozłączających oznacza odebrany pomiar bez takich ramek.
Okno bez beaconów jest zapisywane, lecz nie ma wiarygodnego werdyktu detekcji.
