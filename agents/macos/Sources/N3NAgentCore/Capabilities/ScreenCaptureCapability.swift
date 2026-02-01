import Foundation
import AppKit
import ScreenCaptureKit

/// Capability for capturing screen screenshots.
public final class ScreenCaptureCapability: Capability {

    public let id = "screen.capture"
    public let name = "Screen Capture"
    public let description = "Capture screenshots of the screen or specific windows"

    public init() {}

    public func execute(args: [String: Any]) async throws -> Any? {
        let displayId = args["displayId"] as? Int ?? 0
        let format = args["format"] as? String ?? "png"
        let quality = args["quality"] as? Double ?? 0.8

        // Check screen recording permission
        guard CGPreflightScreenCaptureAccess() else {
            CGRequestScreenCaptureAccess()
            throw CapabilityError.permissionDenied("Screen recording permission required")
        }

        // Capture screen
        let image = try await captureScreen(displayId: displayId)

        // Convert to requested format
        let data = try convertImage(image, format: format, quality: quality)

        return [
            "data": data.base64EncodedString(),
            "format": format,
            "width": Int(image.size.width),
            "height": Int(image.size.height)
        ]
    }

    private func captureScreen(displayId: Int) async throws -> NSImage {
        // Get available displays
        let displays = CGDisplayCopyAllDisplayModes(CGMainDisplayID(), nil) as? [CGDisplayMode] ?? []

        // Use CGDisplayCreateImage for simple capture
        let targetDisplay = displayId == 0 ? CGMainDisplayID() : CGDirectDisplayID(displayId)

        guard let cgImage = CGDisplayCreateImage(targetDisplay) else {
            throw CapabilityError.executionFailed("Failed to capture screen")
        }

        return NSImage(cgImage: cgImage, size: NSSize(width: cgImage.width, height: cgImage.height))
    }

    private func convertImage(_ image: NSImage, format: String, quality: Double) throws -> Data {
        guard let tiffData = image.tiffRepresentation,
              let bitmapRep = NSBitmapImageRep(data: tiffData) else {
            throw CapabilityError.executionFailed("Failed to convert image")
        }

        let data: Data?

        switch format.lowercased() {
        case "jpeg", "jpg":
            data = bitmapRep.representation(using: .jpeg, properties: [.compressionFactor: quality])
        case "png":
            data = bitmapRep.representation(using: .png, properties: [:])
        case "tiff":
            data = bitmapRep.representation(using: .tiff, properties: [:])
        default:
            data = bitmapRep.representation(using: .png, properties: [:])
        }

        guard let imageData = data else {
            throw CapabilityError.executionFailed("Failed to encode image")
        }

        return imageData
    }
}
