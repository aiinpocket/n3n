package com.aiinpocket.n3n.gateway.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event message pushed from server to connected agents.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GatewayEvent extends GatewayMessage {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    /**
     * Event name (e.g., "node.status", "execution.progress", "connect.challenge")
     */
    private String event;

    /**
     * Event payload
     */
    private Map<String, Object> payload;

    /**
     * Sequence number for ordering
     */
    private long seq;

    public GatewayEvent() {
        super("event");
        this.seq = SEQUENCE.incrementAndGet();
    }

    public GatewayEvent(String event, Map<String, Object> payload) {
        this();
        this.event = event;
        this.payload = payload;
    }

    public static GatewayEvent create(String event, Map<String, Object> payload) {
        return new GatewayEvent(event, payload);
    }

    // Common events
    public static final String CONNECT_CHALLENGE = "connect.challenge";
    public static final String CONNECT_OK = "connect.ok";
    public static final String CONNECT_ERROR = "connect.error";
    public static final String NODE_REGISTERED = "node.registered";
    public static final String NODE_STATUS = "node.status";
    public static final String EXECUTION_START = "execution.start";
    public static final String EXECUTION_PROGRESS = "execution.progress";
    public static final String EXECUTION_COMPLETE = "execution.complete";
    public static final String PING = "ping";
    public static final String PONG = "pong";
}
