package com.aiinpocket.n3n.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Base class for all Gateway messages.
 * Messages are typed (req, res, event) and contain various payloads.
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GatewayRequest.class, name = "req"),
    @JsonSubTypes.Type(value = GatewayResponse.class, name = "res"),
    @JsonSubTypes.Type(value = GatewayEvent.class, name = "event")
})
public abstract class GatewayMessage {

    /**
     * Message type: req, res, or event
     */
    private String type;

    /**
     * Unix timestamp in milliseconds
     */
    private long ts;

    protected GatewayMessage(String type) {
        this.type = type;
        this.ts = System.currentTimeMillis();
    }
}
