package com.eventease.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ActuatorInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("app", Map.of(
                "name", "EventEase",
                "description", "Modern Event Management Platform with AI Assistant & RAG",
                "version", "0.0.1-SNAPSHOT"
        ));
    }
}
