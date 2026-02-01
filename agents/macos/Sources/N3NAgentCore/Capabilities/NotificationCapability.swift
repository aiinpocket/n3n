import Foundation
import UserNotifications

/// Capability for showing system notifications.
public final class NotificationCapability: Capability {

    public let id = "notification"
    public let name = "Notification"
    public let description = "Show system notifications"

    public init() {}

    public func execute(args: [String: Any]) async throws -> Any? {
        guard let title = args["title"] as? String else {
            throw CapabilityError.invalidArguments("Missing 'title' argument")
        }

        let body = args["body"] as? String
        let subtitle = args["subtitle"] as? String
        let sound = args["sound"] as? Bool ?? true

        // Request permission if needed
        let center = UNUserNotificationCenter.current()

        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .notDetermined {
            do {
                try await center.requestAuthorization(options: [.alert, .sound])
            } catch {
                throw CapabilityError.permissionDenied("Notification permission denied")
            }
        }

        // Create notification
        let content = UNMutableNotificationContent()
        content.title = title

        if let body = body {
            content.body = body
        }

        if let subtitle = subtitle {
            content.subtitle = subtitle
        }

        if sound {
            content.sound = .default
        }

        // Create request
        let identifier = UUID().uuidString
        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            trigger: nil  // Deliver immediately
        )

        try await center.add(request)

        return [
            "success": true,
            "id": identifier
        ]
    }
}
