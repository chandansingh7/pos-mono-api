package com.pos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Backup configuration. SQL backup requires pg_dump/psql (PostgreSQL).
 * Schedule uses cron expression (e.g. "0 0 2 * * ?" = daily at 2 AM).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.backup")
public class BackupConfig {

    /** Directory for scheduled backup files. Default: ./backups */
    private String directory = "./backups";

    /** Enable scheduled backup. */
    private boolean scheduleEnabled = false;

    /** Cron expression for schedule (e.g. "0 0 2 * * ?" = 2 AM daily). */
    private String scheduleCron = "0 0 2 * * ?";

    /** Backup format for scheduled runs: json or sql */
    private String scheduleFormat = "json";

    /** Path to pg_dump executable. Empty = use "pg_dump" from PATH. */
    private String pgDumpPath = "pg_dump";

    /** Path to psql executable. Empty = use "psql" from PATH. */
    private String psqlPath = "psql";

    /** Enable SQL backup (requires PostgreSQL and pg_dump on server). */
    private boolean sqlBackupEnabled = true;
}
