package com.aiinpocket.n3n.ai.chain.impl;

import com.aiinpocket.n3n.ai.chain.Chain;
import com.aiinpocket.n3n.ai.chain.ChainContext;
import com.aiinpocket.n3n.ai.chain.ChainResult;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 路由 Chain
 *
 * 根據輸入內容選擇執行不同的 Chain。
 * 類似 LangChain 的 RouterChain。
 */
@Slf4j
public class RouterChain implements Chain {

    @Getter
    private final String name;
    private final Map<String, Chain> routes;
    private final Chain defaultRoute;
    private final Function<Map<String, Object>, String> router;
    private final AiService aiService;
    private final String routingPrompt;

    @Builder
    public RouterChain(String name, Map<String, Chain> routes, Chain defaultRoute,
                       Function<Map<String, Object>, String> router,
                       AiService aiService, String routingPrompt) {
        this.name = name != null ? name : "router_chain";
        this.routes = routes != null ? new HashMap<>(routes) : new HashMap<>();
        this.defaultRoute = defaultRoute;
        this.router = router;
        this.aiService = aiService;
        this.routingPrompt = routingPrompt;
    }

    @Override
    public ChainResult run(Map<String, Object> inputs) {
        ChainContext context = ChainContext.of(inputs);
        invoke(context);
        return ChainResult.fromContext(context);
    }

    @Override
    public ChainContext invoke(ChainContext context) {
        try {
            // 決定路由
            String routeKey = determineRoute(context.getInputs());
            log.debug("RouterChain {} selected route: {}", name, routeKey);

            // 取得對應的 Chain
            Chain selectedChain = routes.get(routeKey);
            if (selectedChain == null) {
                if (defaultRoute != null) {
                    log.debug("Using default route");
                    selectedChain = defaultRoute;
                } else {
                    context.setError("No route found for key: " + routeKey);
                    return context;
                }
            }

            // 執行選中的 Chain
            selectedChain.invoke(context);

            // 記錄路由資訊
            context.setIntermediate("selected_route", routeKey);
            context.setIntermediate("selected_chain", selectedChain.getName());

            return context;

        } catch (Exception e) {
            log.error("RouterChain {} failed", name, e);
            context.setError("Router Chain failed: " + e.getMessage());
            return context;
        }
    }

    /**
     * 決定路由
     */
    private String determineRoute(Map<String, Object> inputs) {
        // 如果有自訂路由函數
        if (router != null) {
            return router.apply(inputs);
        }

        // 如果有 AI 路由
        if (aiService != null && routingPrompt != null) {
            return aiRoute(inputs);
        }

        // 預設使用 "route" 鍵
        Object routeValue = inputs.get("route");
        if (routeValue != null) {
            return routeValue.toString();
        }

        return "default";
    }

    /**
     * 使用 AI 決定路由
     */
    private String aiRoute(Map<String, Object> inputs) {
        String input = inputs.get("input") != null ? inputs.get("input").toString() : "";

        String prompt = routingPrompt.replace("{input}", input)
                .replace("{routes}", String.join(", ", routes.keySet()));

        String response = aiService.generateText(prompt);

        // 解析回應，找到匹配的路由
        String lowerResponse = response.toLowerCase().trim();
        for (String route : routes.keySet()) {
            if (lowerResponse.contains(route.toLowerCase())) {
                return route;
            }
        }

        return "default";
    }

    @Override
    public String[] getInputKeys() {
        return new String[]{"input"};
    }

    @Override
    public String[] getOutputKeys() {
        return new String[]{"output"};
    }

    /**
     * 新增路由
     */
    public RouterChain addRoute(String key, Chain chain) {
        routes.put(key, chain);
        return this;
    }

    /**
     * 建立簡單的路由 Chain
     */
    public static RouterChain of(Map<String, Chain> routes) {
        return RouterChain.builder()
                .routes(routes)
                .build();
    }

    /**
     * 建立帶預設路由的路由 Chain
     */
    public static RouterChain of(Map<String, Chain> routes, Chain defaultRoute) {
        return RouterChain.builder()
                .routes(routes)
                .defaultRoute(defaultRoute)
                .build();
    }
}
