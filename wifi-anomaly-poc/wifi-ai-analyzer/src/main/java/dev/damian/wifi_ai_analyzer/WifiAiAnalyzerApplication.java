package dev.damian.wifi_ai_analyzer;

import dev.damian.wifi_ai_analyzer.domain.RawFrameEventEntity;
import dev.damian.wifi_ai_analyzer.domain.RawFrameEventRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Instant;

@SpringBootApplication
public class WifiAiAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WifiAiAnalyzerApplication.class, args);
	}

	@Bean
	public CommandLineRunner testZapisu(RawFrameEventRepository repository) {
		return args -> {
			RawFrameEventEntity event = new RawFrameEventEntity();
			event.setFrameType("DEAUTH");
			event.setSrcMac("AA:BB:CC:DD:EE:FF");
			event.setRssi(-67);
			event.setReceivedAt(Instant.now());

			repository.save(event);

			System.out.println("Zapisano zdarzenie z id: " + event.getId());
		};
	}
}