package no.digdir.forsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for forsystem — Digdir's invoicing pre-system for Altinn products
 * under the FinMod price model.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ForsystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForsystemApplication.class, args);
    }
}
