package com.aiinpocket.n3n.gateway.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Response message for a request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GatewayResponse extends GatewayMessage {

    /**
     * ID matching the original request
     */
    private String id;

    /**
     * Whether the request was successful
     */
    private boolean ok;

    /**
     * Response payload (if successful)
     */
    private Map<String, Object> payload;

    /**
     * Error details (if failed)
     */
    private GatewayError error;

    public GatewayResponse() {
        super("res");
    }

    public static GatewayResponse success(String requestId, Map<String, Object> payload) {
        GatewayResponse response = new GatewayResponse();
        response.setId(requestId);
        response.setOk(true);
        response.setPayload(payload);
        return response;
    }

    public static GatewayResponse error(String requestId, String code, String message) {
        GatewayResponse response = new GatewayResponse();
        response.setId(requestId);
        response.setOk(false);
        response.setError(new GatewayError(code, message));
        return response;
    }

    @Data
    public static class GatewayError {
        private String code;
        private String message;

        public GatewayError() {}

        public GatewayError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
