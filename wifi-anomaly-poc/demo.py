"""Próba integracji na oznaczonych danych syntetycznych. Uruchom z katalogu PoC."""

import json
import os
import subprocess
import time
import uuid
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE_URL = os.getenv("DEMO_URL", "http://localhost:8080")
TOPIC = os.getenv("MQTT_WINDOW_TOPIC", "wifi/window")
STREAM = {"sensorId": "demo-" + uuid.uuid4().hex[:8], "bssid": "02:00:00:00:00:01", "channel": 6}
QUERY = urlencode(STREAM)


def window(deauth, disassoc, beacon=48):
    return {
        "schemaVersion": 1,
        "windowId": uuid.uuid4().hex,
        **STREAM,
        "durationMs": 5000,
        "beacon": beacon,
        "deauth": deauth,
        "disassoc": disassoc,
        "retry": 0,
    }


def publish(windows):
    if os.getenv("DEMO_LOCAL_MQTT") == "1":
        command = ["mosquitto_pub"]
    else:
        command = ["docker", "compose", "exec", "-T", "mosquitto", "mosquitto_pub"]
    subprocess.run(command + ["-t", TOPIC, "-q", "1", "-l"],
                   input="".join((item if isinstance(item, str) else json.dumps(item)) + "\n"
                                 for item in windows),
                   text=True, check=True, timeout=30)


def read_windows(expected):
    for _ in range(30):
        with urlopen(f"{BASE_URL}/api/windows?{QUERY}", timeout=5) as response:
            results = json.load(response)
        if len(results["windows"]) >= expected:
            return results
        time.sleep(0.5)
    raise RuntimeError("Brak okien w API. Sprawdź połączenie backendu z brokerem.")


def main():
    print(f"Strumień syntetyczny: {STREAM['sensorId']}", flush=True)
    normal = [window(index % 2, 0) for index in range(30)]
    publish(normal)
    before_calibration = read_windows(30)
    assert before_calibration["baselineSize"] == 0
    assert all(item["score"] is None and item["mlAlert"] is None
               for item in before_calibration["windows"])

    with urlopen(Request(f"{BASE_URL}/api/calibration?{QUERY}", method="POST"), timeout=5):
        pass

    quiet, moderate, burst, missing_signal = (
        window(1, 0), window(7, 0), window(30, 12), window(0, 0, beacon=0)
    )
    invalid = window(0, 0)
    del invalid["deauth"]
    publish([quiet, moderate, burst, burst, invalid, "{invalid-json", missing_signal])

    results = read_windows(34)
    assert results["baselineSize"] == 30
    windows = {item["window"]["windowId"]: item for item in results["windows"]}
    assert len(results["windows"]) == len(windows) == 34, "Duplikat lub błędny pomiar trafił do bazy"
    assert all(windows[sample["windowId"]]["score"] is None for sample in normal)
    assert windows[quiet["windowId"]]["mlAlert"] is False
    assert windows[quiet["windowId"]]["thresholdAlert"] is False
    assert windows[moderate["windowId"]]["mlAlert"] is True
    assert windows[moderate["windowId"]]["thresholdAlert"] is False
    assert windows[burst["windowId"]]["mlAlert"] is True
    assert windows[burst["windowId"]]["thresholdAlert"] is True
    assert windows[missing_signal["windowId"]]["mlAlert"] is None
    assert windows[missing_signal["windowId"]]["thresholdAlert"] is None
    print(f"OK: 34 unikalne okna, kalibracja i oba alarmy. Panel: {BASE_URL}")


if __name__ == "__main__":
    main()
