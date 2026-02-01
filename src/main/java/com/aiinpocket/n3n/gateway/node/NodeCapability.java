package com.aiinpocket.n3n.gateway.node;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Defines a capability that a local agent can provide.
 * Examples: system.run, screen.capture, input.click, etc.
 */
@Data
@Builder
public class NodeCapability {

    /**
     * Capability identifier (e.g., "system.run", "screen.capture")
     */
    private String name;

    /**
     * Human-readable display name
     */
    private String displayName;

    /**
     * Description of what this capability does
     */
    private String description;

    /**
     * Category for grouping (e.g., "system", "screen", "input")
     */
    private String category;

    /**
     * Parameter schema for this capability
     */
    private Map<String, Object> parameterSchema;

    /**
     * Output schema
     */
    private Map<String, Object> outputSchema;

    /**
     * Required permissions for this capability
     */
    private List<String> requiredPermissions;

    /**
     * Whether this capability is async (long-running)
     */
    private boolean async;

    /**
     * Timeout in milliseconds (0 = no timeout)
     */
    private long timeout;

    // Common capability names
    public static final String SYSTEM_RUN = "system.run";
    public static final String SYSTEM_APPLESCRIPT = "system.applescript";
    public static final String SYSTEM_NOTIFY = "system.notify";
    public static final String SCREEN_CAPTURE = "screen.capture";
    public static final String SCREEN_OCR = "screen.ocr";
    public static final String INPUT_CLICK = "input.click";
    public static final String INPUT_TYPE = "input.type";
    public static final String INPUT_KEY = "input.key";
    public static final String APP_OPEN = "app.open";
    public static final String APP_LIST = "app.list";
    public static final String APP_FOCUS = "app.focus";
    public static final String APP_QUIT = "app.quit";
    public static final String CLIPBOARD_READ = "clipboard.read";
    public static final String CLIPBOARD_WRITE = "clipboard.write";
    public static final String FS_READ = "fs.read";
    public static final String FS_WRITE = "fs.write";
    public static final String FS_LIST = "fs.list";
    public static final String BROWSER_OPEN = "browser.open";
    public static final String SHORTCUTS_RUN = "shortcuts.run";
    public static final String SHORTCUTS_LIST = "shortcuts.list";
}
