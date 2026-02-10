package com.aiinpocket.n3n.backup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 雲端同步自動組態
 */
@Configuration
@EnableConfigurationProperties(CloudSyncProperties.class)
public class CloudSyncAutoConfiguration {
}
