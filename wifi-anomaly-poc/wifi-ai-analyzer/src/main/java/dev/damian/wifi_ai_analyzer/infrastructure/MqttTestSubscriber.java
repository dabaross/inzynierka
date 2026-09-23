package dev.damian.wifi_ai_analyzer.infrastructure.mqtt; // pełna nazwa klasy i jej logiczne miejsce w projekcie, tzw pakiet.

import jakarta.annotation.PostConstruct; // import adnotacji springa @PostConstruct
import org.springframework.stereotype.Component; // import adnotacji springa @Component

// klasy i interfejsy z biblioteki paho
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;  // interfejs służący do śledzenia wiadomości wysłanych przez MQTT
import org.eclipse.paho.client.mqttv3.MqttCallback; // interfejs określający jakie meotdy mają zostać wywołane
import org.eclipse.paho.client.mqttv3.MqttClient; // klient mqtt (obiekt)
import org.eclipse.paho.client.mqttv3.MqttMessage; // wiadomośc mqtt (obiekt)

@Component // ta adnotacja mówi Springowi "ta klasa jest częścią aplikajcji, utwórz jej obiekt i zarządzaj nim"
public class MqttTestSubscriber implements MqttCallback { // klasa MqttTestSubscriber implementuje interfejs MqttCallback
//interfejs mówi jakie metody klasaa musi posiadać
// Paho, ta klasa zgłasza się jako odbiorca zdarzeń MQTT.
// Ma wszystkie wymagane metody. Można jej przekazywać wiadomości i informacje o błędach.
// Paho to kod który dodajemy do aplikacji, zeby potrafiła komunikowac sie przez MQTT, Paho musi wiedziec gdzie ma przekazywac informacje, dlatego implementujemy konretny jego intefejs

    // stałe konfiuguracyjne przechowujące adres połączenia i temat mqtt
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "wifi-analyzer/test";

    // pole klasy o typie MqttClient
    private MqttClient client;

    // PostConstruct mówi Springowi - gdy utworzysz obiekt tej klasy, uruchom tę metodę:
    @PostConstruct
    public void connectAndSubscribe() throws Exception { // może zgłosić wyjątek i przekazać go metodzie wywołującej do obsłużenia
        client = new MqttClient(BROKER_URL, MqttClient.generateClientId());
        // do wcześniej utworzonego pola klasy client przypisujemy nowy obiekt - klienta MQTT
        // przkazujemy argumenty, URL ze stałej, losowo  wygenerowane ID

        client.setCallback(this); // gdy nastapi zdarzenie MQTT wywołaj metody tego obiektu, tej klasy
        client.connect(); // połącz z brokerem
        client.subscribe(TOPIC); // subskrybyh topic

        System.out.println("Połączono z MQTT. Nasłuchiwanie tematu: " + TOPIC);
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("Utracono połączenie z brokerem MQTT: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());

        System.out.println("Odebrano wiadomość MQTT");
        System.out.println("Temat: " + topic);
        System.out.println("Treść: " + payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Ta aplikacja obecnie tylko odbiera wiadomości.
    }
}
