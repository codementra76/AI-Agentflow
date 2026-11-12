package com.agentflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ScheduleConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
