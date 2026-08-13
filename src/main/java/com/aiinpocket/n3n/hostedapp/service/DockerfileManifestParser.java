package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import com.aiinpocket.n3n.hostedapp.dto.ServiceSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dockerfile 解析為 AppManifest（單一服務，名稱固定 "app"）。
 *
 * 取用的指令：
 *   ENV KEY=value / ENV KEY value → 有預設值的參數
 *   ARG NAME[=default]            → 參數（無預設即必填）
 *   EXPOSE port[/proto] ...       → 第一個埠為容器內埠（無 EXPOSE 時預設 8080）
 */
@Service
public class DockerfileManifestParser {

    static final String SERVICE_NAME = "app";
    static final int DEFAULT_PORT = 8080;

    private static final Pattern ENV_PAIR = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)=(\"[^\"]*\"|'[^']*'|\\S*)");
    private static final Pattern ARG_DEF = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)(?:=(.*))?");

    public AppManifest parse(String dockerfileContent) {
        ParamCollector params = new ParamCollector();
        Map<String, String> environment = new LinkedHashMap<>();
        Integer exposedPort = null;

        for (String instruction : logicalLines(dockerfileContent)) {
            String upper = instruction.toUpperCase();
            if (upper.startsWith("ENV ")) {
                parseEnv(instruction.substring(4).trim(), params, environment);
            } else if (upper.startsWith("ARG ")) {
                parseArg(instruction.substring(4).trim(), params);
            } else if (upper.startsWith("EXPOSE ") && exposedPort == null) {
                exposedPort = parseExpose(instruction.substring(7).trim());
            }
        }

        int internalPort = exposedPort == null ? DEFAULT_PORT : exposedPort;
        ServiceSpec service = ServiceSpec.builder()
                .name(SERVICE_NAME)
                .build(".")
                .ports(List.of(internalPort))
                .environment(environment)
                .dependsOn(List.of())
                .build();

        return AppManifest.builder()
                .type(AppManifest.TYPE_DOCKERFILE)
                .services(List.of(service))
                .params(params.toList())
                .webService(SERVICE_NAME)
                .internalPort(internalPort)
                .build();
    }

    /** 合併行接續（結尾反斜線）、去除註解與空行 */
    private List<String> logicalLines(String content) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : content.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.endsWith("\\")) {
                current.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            current.append(line);
            lines.add(current.toString().trim());
            current.setLength(0);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString().trim());
        }
        return lines;
    }

    /** ENV KEY=value [KEY2=value2 ...] 或舊式 ENV KEY value（值可含空白） */
    private void parseEnv(String args, ParamCollector params, Map<String, String> environment) {
        if (args.contains("=")) {
            Matcher matcher = ENV_PAIR.matcher(args);
            while (matcher.find()) {
                addEnvParam(matcher.group(1), unquote(matcher.group(2)), params, environment);
            }
            return;
        }
        int space = args.indexOf(' ');
        if (space > 0) {
            addEnvParam(args.substring(0, space), args.substring(space + 1).trim(),
                    params, environment);
        }
    }

    private void addEnvParam(String name, String value,
                             ParamCollector params, Map<String, String> environment) {
        environment.put(name, value);
        params.add(ParamSpec.builder()
                .name(name)
                .defaultValue(value)
                .required(false)
                .secret(ParamSpec.isSecretName(name))
                .build());
    }

    /** ARG NAME[=default]：無預設值即必填 */
    private void parseArg(String args, ParamCollector params) {
        Matcher matcher = ARG_DEF.matcher(args.trim());
        if (!matcher.matches()) {
            return;
        }
        String name = matcher.group(1);
        String defaultValue = matcher.group(2) == null ? null : unquote(matcher.group(2).trim());
        params.add(ParamSpec.builder()
                .name(name)
                .defaultValue(defaultValue)
                .required(defaultValue == null)
                .secret(ParamSpec.isSecretName(name))
                .build());
    }

    private Integer parseExpose(String args) {
        for (String token : args.split("\\s+")) {
            String port = token.split("/")[0].trim();
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException ignored) {
                // 可能是 ${PORT} 之類的變數，跳過取下一個
            }
        }
        return null;
    }

    private String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
