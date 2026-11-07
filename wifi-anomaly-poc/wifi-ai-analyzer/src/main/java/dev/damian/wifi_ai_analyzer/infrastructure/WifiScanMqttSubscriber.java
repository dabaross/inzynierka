package dev.damian.wifi_ai_analyzer.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import dev.damian.wifi_ai_analyzer.application.FrameWindowService;
import dev.damian.wifi_ai_analyzer.application.dto.FrameWindow;
import dev.damian.wifi_ai_analyzer.application.WifiScanService;
import dev.damian.wifi_ai_analyzer.application.dto.WifiScanMessage;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class WifiScanMqttSubscriber implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(WifiScanMqttSubscriber.class);

    private final WifiScanService wifiScanService;
    private final ObjectMapper objectMapper;
    private final FrameWindowService frameWindowService;
    private final ObjectReader windowReader;

    @Value("${mqtt.window-topic}")
    private String windowTopic;

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${mqtt.qos}")
    private int qos;

    @Value("${mqtt.client-id}")
    private String clientId;

    private MqttClient client;

    public WifiScanMqttSubscriber(WifiScanService wifiScanService, ObjectMapper objectMapper,
            FrameWindowService frameWindowService) {
        this.wifiScanService = wifiScanService;
        this.objectMapper = objectMapper;
        this.frameWindowService = frameWindowService;
        this.windowReader = objectMapper.readerFor(FrameWindow.class)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void connectAndSubscribe() {
        try {
            if (client == null) {
                client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                client.setCallback(this);
            }
            if (!client.isConnected()) {
                var options = new MqttConnectOptions();
                options.setConnectionTimeout(5);
                options.setCleanSession(false);
                client.connect(options);
                try {
                    client.subscribe(new String[]{topic, windowTopic}, new int[]{qos, qos});
                } catch (MqttException e) {
                    client.disconnectForcibly();
                    throw e;
                }
                log.info("Subscribed to MQTT topics {} and {}", topic, windowTopic);
            }
        } catch (MqttException e) {
            log.warn("MQTT connection or subscription failed: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String receivedTopic, MqttMessage message) throws Exception {
        if (receivedTopic.equals(windowTopic)) {
            if (message.getPayload().length > 4096 || message.isRetained()) {
                log.warn("Ignoring oversized or retained frame window");
                return;
            }
            FrameWindow window;
            try {
                window = windowReader.readValue(message.getPayload());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Ignoring invalid frame window: {}", e.getOriginalMessage());
                return;
            }
            // A failed database write propagates to Paho before it acknowledges QoS 1.
            frameWindowService.ingest(window);
            return;
        }

        try {
            WifiScanMessage scan = objectMapper.readValue(message.getPayload(), WifiScanMessage.class);
            wifiScanService.handleScan(scan);
        } catch (Exception e) {
            log.error("Failed to parse and process MQTT payload for topic {}", receivedTopic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op for inbound messages
    }

    @PreDestroy
    public synchronized void shutdown() {
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
