package dev.damian.wifi_ai_analyzer.infrastructure.mqtt;

import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Component
public class MqttTestSubscriber implements MqttCallback {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "wifi-analyzer/test";

    private MqttClient client;

    @PostConstruct
    public void connectAndSubscribe() throws Exception {
        client = new MqttClient(BROKER_URL, MqttClient.generateClientId());

        client.setCallback(this);
        client.connect();
        client.subscribe(TOPIC);

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