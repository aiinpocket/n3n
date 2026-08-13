package com.aiinpocket.n3n.hostedapp.dto;

import lombok.Builder;

import java.util.List;

/**
 * zip 解析結果：應用型態、服務清單、需使用者填寫的參數、對外 web 服務與埠。
 * 順序具決定性（服務依 compose 宣告順序、參數依首次出現順序）。
 *
 * @param type         compose | dockerfile
 * @param services     服務清單（dockerfile 型態固定一個名為 app 的服務）
 * @param params       參數（環境變數）定義，UI 據此渲染表單
 * @param webService   對外提供 HTTP 的服務名稱（第一個有 ports 的服務）
 * @param internalPort web 服務的容器內埠
 */
@Builder
public record AppManifest(
        String type,
        List<ServiceSpec> services,
        List<ParamSpec> params,
        String webService,
        Integer internalPort
) {

    public static final String TYPE_COMPOSE = "compose";
    public static final String TYPE_DOCKERFILE = "dockerfile";
}
