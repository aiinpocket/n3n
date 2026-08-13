package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 參數收集器：以首次出現順序去重（決定性排序）。
 * 同名參數合併規則：任一出現點為必填即必填；預設值取第一個非 null。
 */
final class ParamCollector {

    private final Map<String, ParamSpec> params = new LinkedHashMap<>();

    void add(ParamSpec spec) {
        ParamSpec existing = params.get(spec.name());
        if (existing == null) {
            params.put(spec.name(), spec);
            return;
        }
        String defaultValue = existing.defaultValue() != null
                ? existing.defaultValue() : spec.defaultValue();
        boolean required = (existing.required() || spec.required()) && defaultValue == null;
        params.put(spec.name(), ParamSpec.builder()
                .name(spec.name())
                .defaultValue(defaultValue)
                .required(required)
                .secret(existing.secret() || spec.secret())
                .build());
    }

    void addAll(List<ParamSpec> specs) {
        specs.forEach(this::add);
    }

    List<ParamSpec> toList() {
        return new ArrayList<>(params.values());
    }
}
