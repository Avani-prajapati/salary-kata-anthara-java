package com.acme.salary.reporting;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayAggregatorConfig {

    @Bean
    public PayAggregator payAggregator() {
        return new PayAggregator();
    }
}
