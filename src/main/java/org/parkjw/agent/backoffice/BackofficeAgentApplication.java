package org.parkjw.agent.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BackofficeAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackofficeAgentApplication.class, args);
	}

}
