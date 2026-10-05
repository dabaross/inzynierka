package dev.damian.wifi_ai_analyzer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.damian.wifi_ai_analyzer.application.WifiScanService;
import dev.damian.wifi_ai_analyzer.application.dto.WifiScanMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WifiScanMqttSubscriber implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(WifiScanMqttSubscriber.class);

    private final WifiScanService wifiScanService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${mqtt.qos}")
    private int qos;

    @Value("${mqtt.client-id}")
    private String clientId;

    private MqttClient client;

    public WifiScanMqttSubscriber(WifiScanService wifiScanService, ObjectMapper objectMapper) {
        this.wifiScanService = wifiScanService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void connectAndSubscribe() {
        try {
            if (client == null || !client.isConnected()) {
                client = new MqttClient(brokerUrl, clientId);
            }
            client.setCallback(this);
            client.connect();
            client.subscribe(topic, qos);
            log.info("Connected to MQTT broker {} and subscribed to topic {} with QoS {}", brokerUrl, topic, qos);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker {} and subscribe to topic {}", brokerUrl, topic, e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost for broker {}. Attempting reconnect...", brokerUrl, cause);
        reconnectAndSubscribe();
    }

    private void reconnectAndSubscribe() {
        try {
            if (client == null) {
                client = new MqttClient(brokerUrl, clientId);
            }
            if (!client.isConnected()) {
                client.connect();
            }
            client.subscribe(topic, qos);
            log.info("Reconnected to MQTT broker {} and subscribed to topic {} with QoS {}", brokerUrl, topic, qos);
        } catch (MqttException e) {
            log.error("Failed to reconnect to MQTT broker {} and re-subscribe to topic {}", brokerUrl, topic, e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.info("Received MQTT message on topic {}: {}", topic, payload);
            WifiScanMessage wifiScanMessage = objectMapper.readValue(payload, WifiScanMessage.class);
            wifiScanService.handleScan(wifiScanMessage);
        } catch (Exception e) {
            log.error("Failed to parse and process MQTT payload for topic {}", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op for inbound messages
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException e) {
            log.warn("Error while disconnecting MQTT client", e);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("Error while closing MQTT client", e);
                }
            }
        }
    }
}
