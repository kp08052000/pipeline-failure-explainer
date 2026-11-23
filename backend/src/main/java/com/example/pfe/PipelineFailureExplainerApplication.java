package com.example.pfe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PipelineFailureExplainerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineFailureExplainerApplication.class, args);
    }
}
