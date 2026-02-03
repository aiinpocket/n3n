import Foundation
import AppKit

/// Manages system permissions required by the agent.
public final class PermissionManager {

    public static let shared = PermissionManager()

    private init() {}

    /// All permissions required by the agent
    public enum Permission: String, CaseIterable {
        case automation = "Automation"
        case accessibility = "Accessibility"
        case fullDiskAccess = "Full Disk Access"

        var description: String {
            switch self {
            case .automation:
                return "允許控制其他應用程式（如 Notes、Safari）"
            case .accessibility:
                return "允許模擬鍵盤和滑鼠操作"
            case .fullDiskAccess:
                return "允許存取檔案系統"
            }
        }

        var settingsPath: String {
            switch self {
            case .automation:
                return "x-apple.systempreferences:com.apple.preference.security?Privacy_Automation"
            case .accessibility:
                return "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
            case .fullDiskAccess:
                return "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles"
            }
        }
    }

    /// Check if automation permission is granted for a specific app
    public func checkAutomationPermission(for bundleId: String) -> Bool {
        let script = """
        tell application "System Events"
            return true
        end tell
        """

        var error: NSDictionary?
        if let appleScript = NSAppleScript(source: script) {
            appleScript.executeAndReturnError(&error)
            return error == nil
        }
        return false
    }

    /// Request user to grant permissions
    public func requestPermissions() {
        print("""

        ╔══════════════════════════════════════════════════════════════╗
        ║              N3N Agent 權限設定                               ║
        ╠══════════════════════════════════════════════════════════════╣
        ║                                                              ║
        ║  此 Agent 需要以下權限才能正常運作：                           ║
        ║                                                              ║
        ║  1. 自動化 (Automation)                                      ║
        ║     → 允許控制 Notes、Safari 等應用程式                       ║
        ║                                                              ║
        ║  2. 輔助使用 (Accessibility) [選用]                          ║
        ║     → 允許模擬鍵盤和滑鼠操作                                  ║
        ║                                                              ║
        ╚══════════════════════════════════════════════════════════════╝

        """)
    }

    /// Open system preferences to the specified permission panel
    public func openSettings(for permission: Permission) {
        if let url = URL(string: permission.settingsPath) {
            NSWorkspace.shared.open(url)
        }
    }

    /// Interactive permission setup wizard
    public func runSetupWizard() async {
        requestPermissions()

        print("正在開啟系統偏好設定...")
        print("請在「隱私權與安全性」中允許 N3N Agent 的權限。")
        print("")

        // Open Automation settings
        openSettings(for: .automation)

        print("按 Enter 繼續...")
        _ = readLine()

        // Test if permission was granted
        if checkAutomationPermission(for: "com.apple.Notes") {
            print("✅ 自動化權限已授予")
        } else {
            print("⚠️  自動化權限尚未授予，部分功能可能無法使用")
        }

        print("")
        print("權限設定完成。您現在可以執行 'n3n-agent run' 來啟動 Agent。")
    }
}
