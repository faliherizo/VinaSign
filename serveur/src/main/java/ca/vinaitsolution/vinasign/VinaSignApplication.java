package ca.vinaitsolution.vinasign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Point d'entrée de la plateforme VinaSign. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VinaSignApplication {

    public static void main(String[] args) {
        SpringApplication.run(VinaSignApplication.class, args);
    }
}
