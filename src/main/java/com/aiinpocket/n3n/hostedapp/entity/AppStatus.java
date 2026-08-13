package com.aiinpocket.n3n.hostedapp.entity;

/**
 * Hosted App 狀態常數（VARCHAR 存放，避免 enum schema 綁死）。
 */
public final class AppStatus {

    public static final String CREATED = "created";
    public static final String DEPLOYING = "deploying";
    public static final String RUNNING = "running";
    public static final String STOPPED = "stopped";
    public static final String FAILED = "failed";

    private AppStatus() {
    }
}
