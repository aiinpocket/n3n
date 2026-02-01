package com.aiinpocket.n3n.gateway.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

/**
 * Request message sent from platform to agent or agent to platform.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GatewayRequest extends GatewayMessage {

    /**
     * Unique request ID for tracking responses
     */
    private String id;

    /**
     * Method to invoke (e.g., "node.invoke", "node.register", "connect")
     */
    private String method;

    /**
     * Request parameters
     */
    private Map<String, Object> params;

    public GatewayRequest() {
        super("req");
        this.id = UUID.randomUUID().toString();
    }

    public GatewayRequest(String method, Map<String, Object> params) {
        this();
        this.method = method;
        this.params = params;
    }

    public static GatewayRequest create(String method, Map<String, Object> params) {
        return new GatewayRequest(method, params);
    }
}
