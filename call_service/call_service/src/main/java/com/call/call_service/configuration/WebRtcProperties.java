package com.call.call_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.webrtc")
public class WebRtcProperties {
    private List<Map<String, Object>> iceServers = new ArrayList<>();

    public List<Map<String, Object>> getIceServers() {
        return iceServers;
    }

    public void setIceServers(List<Map<String, Object>> iceServers) {
        this.iceServers = iceServers;
    }
}
