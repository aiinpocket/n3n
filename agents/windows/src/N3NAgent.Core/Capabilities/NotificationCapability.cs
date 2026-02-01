using System.Diagnostics;

namespace N3NAgent.Core.Capabilities;

/// <summary>
/// Capability for displaying system notifications.
/// </summary>
public class NotificationCapability : ICapability
{
    public string Id => "notification.show";
    public string Name => "Notification";
    public string Description => "Display system notifications";

    public async Task<object?> ExecuteAsync(Dictionary<string, object> args)
    {
        if (!args.TryGetValue("title", out var titleObj) || titleObj == null)
            throw CapabilityException.InvalidArguments("Missing 'title' argument");

        var title = titleObj.ToString()!;
        var message = args.TryGetValue("message", out var msgObj) && msgObj != null
            ? msgObj.ToString()!
            : "";

        return await ShowNotification(title, message);
    }

    private async Task<Dictionary<string, object>> ShowNotification(string title, string message)
    {
        // Use PowerShell to show Windows toast notification
        var script = $@"
[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

$template = @""
<toast>
    <visual>
        <binding template=""ToastText02"">
            <text id=""1"">{EscapeXml(title)}</text>
            <text id=""2"">{EscapeXml(message)}</text>
        </binding>
    </visual>
</toast>
""@

$xml = New-Object Windows.Data.Xml.Dom.XmlDocument
$xml.LoadXml($template)

$toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
$notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier(""N3N Agent"")
$notifier.Show($toast)
";

        try
        {
            using var process = new Process();
            process.StartInfo = new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = $"-NoProfile -ExecutionPolicy Bypass -Command \"{script.Replace("\"", "\\\"")}\"",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true
            };

            process.Start();
            await process.WaitForExitAsync();

            // Fallback to BurntToast if available, or msg.exe for simpler notifications
            if (process.ExitCode != 0)
            {
                // Try simpler approach using msg.exe (works on Windows Server)
                await ShowSimpleNotification(title, message);
            }

            return new Dictionary<string, object>
            {
                ["success"] = true,
                ["title"] = title,
                ["message"] = message
            };
        }
        catch (Exception ex)
        {
            // Fallback - try simpler method
            try
            {
                await ShowSimpleNotification(title, message);
                return new Dictionary<string, object>
                {
                    ["success"] = true,
                    ["title"] = title,
                    ["message"] = message,
                    ["method"] = "fallback"
                };
            }
            catch
            {
                throw CapabilityException.ExecutionFailed($"Failed to show notification: {ex.Message}");
            }
        }
    }

    private async Task ShowSimpleNotification(string title, string message)
    {
        // Use VBScript as a fallback for showing message box
        var vbsScript = $@"
Set objShell = CreateObject(""WScript.Shell"")
objShell.Popup ""{EscapeVbs(message)}"", 5, ""{EscapeVbs(title)}"", 64
";

        var tempFile = Path.Combine(Path.GetTempPath(), $"n3n_notify_{Guid.NewGuid():N}.vbs");

        try
        {
            await File.WriteAllTextAsync(tempFile, vbsScript);

            using var process = new Process();
            process.StartInfo = new ProcessStartInfo
            {
                FileName = "wscript.exe",
                Arguments = $"\"{tempFile}\"",
                UseShellExecute = false,
                CreateNoWindow = true
            };

            process.Start();
            // Don't wait - let notification show and auto-dismiss
        }
        finally
        {
            // Clean up temp file after a delay
            _ = Task.Delay(10000).ContinueWith(_ =>
            {
                try { File.Delete(tempFile); } catch { /* ignore */ }
            });
        }
    }

    private static string EscapeXml(string text)
    {
        return text
            .Replace("&", "&amp;")
            .Replace("<", "&lt;")
            .Replace(">", "&gt;")
            .Replace("\"", "&quot;")
            .Replace("'", "&apos;");
    }

    private static string EscapeVbs(string text)
    {
        return text
            .Replace("\"", "\"\"")
            .Replace("\r", "")
            .Replace("\n", " ");
    }
}
