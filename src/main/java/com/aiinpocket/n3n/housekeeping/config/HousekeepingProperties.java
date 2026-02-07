package com.aiinpocket.n3n.housekeeping.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for housekeeping.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "housekeeping")
public class HousekeepingProperties {

    /**
     * Whether housekeeping is enabled.
     */
    private boolean enabled = true;

    /**
     * Number of days to retain execution records.
     * Records older than this will be processed.
     */
    private int retentionDays = 30;

    /**
     * Whether to archive records to history tables.
     * If false, records are deleted directly.
     */
    private boolean archiveToHistory = false;

    /**
     * Number of records to process per batch.
     */
    private int batchSize = 1000;

    /**
     * Cron expression for housekeeping schedule.
     * Default: 2 AM every day
     */
    private String cron = "0 0 2 * * ?";

    /**
     * Number of days to retain records in history tables.
     * 0 means keep forever.
     */
    private int historyRetentionDays = 365;

    /**
     * Number of days to retain activity log records.
     * Default: 90 days. 0 means keep forever.
     */
    private int activityRetentionDays = 90;
}
