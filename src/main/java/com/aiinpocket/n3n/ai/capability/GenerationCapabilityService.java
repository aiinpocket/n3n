package com.aiinpocket.n3n.ai.capability;

import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流程生成時的「環境自我認知」。
 *
 * <p>先前 AI 生成流程時會挑到在本部署根本跑不起來的節點——例如叫使用者提供
 * 「Chrome 瀏覽器安裝路徑」（流程其實跑在沒有瀏覽器的伺服器容器裡），或在使用者
 * 沒有任何 Google／Slack 憑證時仍假設用 Google Sheets 存資料、用 Slack 發通知。
 * 生成出來的流程 100% 執行失敗，而使用者完全不知道該怎麼補救。
 *
 * <p>這個服務把「這台機器實際做得到什麼」變成 AI 看得到的事實：
 * 哪些節點因為缺憑證不可用、哪些因為環境不具備而不可用，讓生成階段就避開它們。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationCapabilityService {

    /** CDP 探測結果的快取時間：環境不會頻繁變動，但也不該永久快取。 */
    private static final Duration BROWSER_PROBE_TTL = Duration.ofMinutes(5);

    private static final int BROWSER_PROBE_TIMEOUT_SECONDS = 2;

    /**
     * 需要外部帳號授權、但節點本身沒有用 {@code getCredentialType()} 宣告的整合。
     * 這些節點靠 config 內的 webhook URL／SMTP 設定運作，無法從憑證庫推斷可用性，
     * 因此一律視為「使用者沒有明講就不要用」。
     */
    private static final Set<String> REQUIRES_EXPLICIT_REQUEST = Set.of(
        "slack", "discord", "line", "sendEmail", "email", "gmail",
        "instagram", "facebook", "ssh", "ftp", "executeCommand"
    );

    /** 零設定就能用的節點：沒有這些能力的替代方案時，優先推薦它們。 */
    private static final List<String> ZERO_SETUP_NODES = List.of(
        "httpRequest", "html", "code", "setFields", "filter", "condition",
        "aggregate", "loop", "merge", "sort", "splitOut", "itemLists",
        "json", "markdown", "regex", "datetime", "text", "saveArtifact",
        "scheduleTrigger", "webhookTrigger", "formTrigger", "trigger"
    );

    private final NodeHandlerRegistry handlerRegistry;
    private final CredentialRepository credentialRepository;

    private final OkHttpClient probeClient = new OkHttpClient.Builder()
        .connectTimeout(BROWSER_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BROWSER_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build();

    /** 快取的瀏覽器可用性探測結果（值 + 取得時間），避免每次生成都打一次 CDP。 */
    private final AtomicReference<BrowserProbe> browserProbe = new AtomicReference<>(null);

    @Value("${n3n.browser.cdp.host:localhost}")
    private String cdpHost = "localhost";

    @Value("${n3n.browser.cdp.port:9222}")
    private int cdpPort = 9222;

    private record BrowserProbe(boolean available, long probedAtMillis) {}

    /**
     * 本次生成不該使用的節點型別：缺憑證、或環境不具備。
     *
     * @param userId 生成流程的使用者；null 時只回傳環境層面的不可用節點
     * @return 不可用的節點型別（不可變）
     */
    public Set<String> unavailableNodeTypes(UUID userId) {
        Set<String> unavailable = new TreeSet<>();

        if (!isBrowserAvailable()) {
            unavailable.add("browser");
        }

        Set<String> ownedCredentialTypes = ownedCredentialTypes(userId);
        for (String type : handlerRegistry.getRegisteredTypes()) {
            String required = requiredCredentialType(type);
            if (required != null && !ownedCredentialTypes.contains(required)) {
                unavailable.add(type);
            }
        }

        return Collections.unmodifiableSet(unavailable);
    }

    /**
     * 產生給流程生成 prompt 用的「執行環境事實」區塊。
     *
     * <p>刻意寫成事實陳述而非規則，讓模型自己推導出正確選擇；規則本身放在
     * system prompt（flow-generation.md）裡。
     */
    public String describeForPrompt(UUID userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Runtime Environment (facts — the flow will execute here)\n\n");
        sb.append("- Flows run **on a Linux server container**, NOT on the user's own computer.\n");
        sb.append("- The user's local files, local programs and local browser are **unreachable**.\n");

        if (isBrowserAvailable()) {
            sb.append("- A headless browser IS reachable, so `browser` may be used for pages ")
              .append("that genuinely require JavaScript rendering.\n");
        } else {
            sb.append("- **No browser is installed in this container.** The `browser` node cannot work at all. ")
              .append("Fetch web pages with `httpRequest` and parse them with `html` instead.\n");
        }

        Set<String> owned = ownedCredentialTypes(userId);
        sb.append("\n## Credentials the user has already set up\n\n");
        if (owned.isEmpty()) {
            sb.append("- (none)\n");
        } else {
            for (String type : new TreeSet<>(owned)) {
                sb.append("- ").append(type).append("\n");
            }
        }

        Set<String> unavailable = unavailableNodeTypes(userId);
        if (!unavailable.isEmpty()) {
            sb.append("\n## Node types that CANNOT run for this user\n\n");
            sb.append("These are missing credentials or environment support. ");
            sb.append("Do NOT use them unless the user explicitly named that service; ");
            sb.append("if the user did name one, use it but say clearly that a credential must be added first.\n\n");
            sb.append("`").append(String.join("`, `", unavailable)).append("`\n");
        }

        sb.append("\n## Node types that always work with zero setup\n\n");
        sb.append("Prefer these when the user did not name a specific external service.\n\n");
        sb.append("`").append(String.join("`, `", availableZeroSetupNodes())).append("`\n");

        sb.append("\nNote: `saveArtifact` stores a file in the user's built-in artifact library ");
        sb.append("(visible in the app under 作品庫 / Artifacts) and needs no external account. ");
        sb.append("It is the correct answer for a plain \"save it\" / \"存起來\" request.\n");

        return sb.toString();
    }

    /** 平台內建、零設定的節點中實際有註冊的那些。 */
    private List<String> availableZeroSetupNodes() {
        return ZERO_SETUP_NODES.stream()
            .filter(handlerRegistry::hasHandler)
            .toList();
    }

    /**
     * 節點宣告的憑證型別；沒有宣告但屬於「要外部帳號才能用」的整合，
     * 以節點型別本身當作憑證識別（使用者一定不會有這種憑證，因此視為不可用）。
     */
    private String requiredCredentialType(String nodeType) {
        if (REQUIRES_EXPLICIT_REQUEST.contains(nodeType)) {
            return "__explicit__" + nodeType;
        }
        NodeHandler handler = handlerRegistry.findHandler(nodeType).orElse(null);
        if (handler instanceof MultiOperationNodeHandler multiOp) {
            return multiOp.getCredentialType();
        }
        return null;
    }

    /** 使用者已設定的憑證型別。查詢失敗時回傳空集合（保守：寧可少推薦也不要推薦不能用的）。 */
    private Set<String> ownedCredentialTypes(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        try {
            Set<String> types = new LinkedHashSet<>();
            for (Credential credential : credentialRepository.findByOwnerId(userId)) {
                if (credential.getType() != null) {
                    types.add(credential.getType());
                }
            }
            return Collections.unmodifiableSet(types);
        } catch (Exception e) {
            log.warn("Failed to read credentials for capability check: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 探測 CDP 端點是否真的活著。探測結果快取 {@link #BROWSER_PROBE_TTL}，
     * 失敗（最常見：容器內沒有瀏覽器）只在 debug 等級記錄，不製造雜訊。
     */
    public boolean isBrowserAvailable() {
        BrowserProbe cached = browserProbe.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.probedAtMillis() < BROWSER_PROBE_TTL.toMillis()) {
            return cached.available();
        }

        boolean available = probeBrowser();
        browserProbe.set(new BrowserProbe(available, now));
        return available;
    }

    private boolean probeBrowser() {
        String url = "http://" + cdpHost + ":" + cdpPort + "/json/version";
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = probeClient.newCall(request).execute()) {
                boolean ok = response.isSuccessful();
                log.debug("Browser CDP probe {} -> {}", url, ok ? "available" : response.code());
                return ok;
            }
        } catch (Exception e) {
            log.debug("Browser CDP probe {} unavailable: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * 給前端／診斷用的能力摘要。
     */
    public Map<String, Object> capabilitySummary(UUID userId) {
        return Map.of(
            "browserAvailable", isBrowserAvailable(),
            "ownedCredentialTypes", new TreeSet<>(ownedCredentialTypes(userId)),
            "unavailableNodeTypes", unavailableNodeTypes(userId),
            "zeroSetupNodeTypes", availableZeroSetupNodes()
        );
    }
}
