package com.buddygo.buddygoserver.domain.review.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReviewProperties.class)
public class ReviewConfig {
}
