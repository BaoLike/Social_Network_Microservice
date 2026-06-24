package com.call.call_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.call.call_service.configuration.WebRtcProperties;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.call.call_service.repository.httpclient")
@EnableConfigurationProperties(WebRtcProperties.class)
@EnableScheduling
public class CallServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallServiceApplication.class, args);
    }
}
