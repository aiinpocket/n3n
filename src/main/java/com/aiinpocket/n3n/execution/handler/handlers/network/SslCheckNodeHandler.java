package com.aiinpocket.n3n.execution.handler.handlers.network;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSL 證書檢查節點：連上目標網站，回報證書還有幾天到期。
 *
 * 官方範本「SSL 證書到期提醒」的核心；輸出 daysRemaining 供 condition 節點判斷是否告警。
 */
@Component
@Slf4j
public class SslCheckNodeHandler extends AbstractNodeHandler {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    @Override
    public String getType() {
        return "sslCheck";
    }

    @Override
    public String getDisplayName() {
        return "SSL Certificate Check";
    }

    @Override
    public String getDescription() {
        return "Connect to a host and report when its SSL certificate expires (daysRemaining). "
                + "檢查網站 SSL 證書的到期日，輸出剩餘天數供後續判斷。";
    }

    @Override
    public String getCategory() {
        return "Network";
    }

    @Override
    public String getIcon() {
        return "safety-certificate";
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object host = config.get("host");
        if ((host == null || host.toString().isBlank())) {
            Object url = config.get("url");
            if (url == null || url.toString().isBlank()) {
                return ValidationResult.invalid("host", "Host (or url) is required");
            }
        }
        return ValidationResult.valid();
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String host = getStringConfig(context, "host", "");
        int port = getIntConfig(context, "port", 443);

        // 也接受填整串網址（一般使用者比較會貼網址而不是主機名）
        if (host.isBlank()) {
            host = getStringConfig(context, "url", "");
        }
        if (host.contains("://")) {
            try {
                URI uri = URI.create(host.trim());
                if (uri.getHost() != null) {
                    host = uri.getHost();
                    if (uri.getPort() > 0) {
                        port = uri.getPort();
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // 解析不了就當純主機名處理
            }
        }
        host = host.trim();
        if (host.isBlank()) {
            return NodeExecutionResult.failure("Host is required — 請填要檢查的網站（例如 example.com）");
        }

        int timeoutMs = getIntConfig(context, "timeout", DEFAULT_TIMEOUT_SECONDS) * 1000;

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                socket.startHandshake();

                java.security.cert.Certificate[] certs = socket.getSession().getPeerCertificates();
                if (certs.length == 0 || !(certs[0] instanceof X509Certificate cert)) {
                    return NodeExecutionResult.failure("No X.509 certificate presented by " + host);
                }

                Instant notAfter = cert.getNotAfter().toInstant();
                Instant notBefore = cert.getNotBefore().toInstant();
                long daysRemaining = Duration.between(Instant.now(), notAfter).toDays();

                Map<String, Object> output = new LinkedHashMap<>();
                output.put("host", host);
                output.put("port", port);
                output.put("valid", Instant.now().isAfter(notBefore) && Instant.now().isBefore(notAfter));
                output.put("daysRemaining", daysRemaining);
                output.put("expiresAt", notAfter.toString());
                output.put("issuedAt", notBefore.toString());
                output.put("subject", cert.getSubjectX500Principal().getName());
                output.put("issuer", cert.getIssuerX500Principal().getName());
                return NodeExecutionResult.success(output);
            }
        } catch (Exception e) {
            log.warn("SSL check failed for {}:{} — {}", host, port, e.getMessage());
            return NodeExecutionResult.failure("SSL check failed for " + host + ": " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("host", Map.of(
                "type", "string",
                "title", "Host",
                "description", "Domain to check, e.g. example.com (a full URL also works). 要檢查的網站"
        ));
        properties.put("port", Map.of(
                "type", "integer",
                "title", "Port",
                "default", 443
        ));
        properties.put("timeout", Map.of(
                "type", "integer",
                "title", "Timeout (seconds)",
                "default", DEFAULT_TIMEOUT_SECONDS
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", java.util.List.of("host")
        );
    }
}
