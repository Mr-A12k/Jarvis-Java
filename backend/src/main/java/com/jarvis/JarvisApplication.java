package com.jarvis;

import com.jarvis.config.JarvisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(JarvisProperties.class)
public class JarvisApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(JarvisApplication.class, args);
    }
}
