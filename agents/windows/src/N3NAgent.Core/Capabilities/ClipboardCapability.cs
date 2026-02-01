using System.Runtime.InteropServices;

namespace N3NAgent.Core.Capabilities;

/// <summary>
/// Capability for clipboard operations.
/// </summary>
public class ClipboardCapability : ICapability
{
    public string Id => "clipboard.operation";
    public string Name => "Clipboard";
    public string Description => "Read and write clipboard content";

    // Windows API imports
    [DllImport("user32.dll")]
    private static extern bool OpenClipboard(IntPtr hWndNewOwner);

    [DllImport("user32.dll")]
    private static extern bool CloseClipboard();

    [DllImport("user32.dll")]
    private static extern bool EmptyClipboard();

    [DllImport("user32.dll")]
    private static extern IntPtr GetClipboardData(uint uFormat);

    [DllImport("user32.dll")]
    private static extern IntPtr SetClipboardData(uint uFormat, IntPtr hMem);

    [DllImport("user32.dll")]
    private static extern bool IsClipboardFormatAvailable(uint format);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GlobalLock(IntPtr hMem);

    [DllImport("kernel32.dll")]
    private static extern bool GlobalUnlock(IntPtr hMem);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GlobalAlloc(uint uFlags, UIntPtr dwBytes);

    [DllImport("kernel32.dll")]
    private static extern UIntPtr GlobalSize(IntPtr hMem);

    private const uint CF_TEXT = 1;
    private const uint CF_UNICODETEXT = 13;
    private const uint GMEM_MOVEABLE = 0x0002;

    public async Task<object?> ExecuteAsync(Dictionary<string, object> args)
    {
        if (!args.TryGetValue("operation", out var opObj) || opObj == null)
            throw CapabilityException.InvalidArguments("Missing 'operation' argument");

        var operation = opObj.ToString()!.ToLower();

        return operation switch
        {
            "read" => await ReadClipboard(),
            "write" => await WriteClipboard(args),
            "clear" => await ClearClipboard(),
            _ => throw CapabilityException.InvalidArguments($"Unknown operation: {operation}")
        };
    }

    private Task<Dictionary<string, object>> ReadClipboard()
    {
        return Task.Run(() =>
        {
            string? text = null;

            if (!OpenClipboard(IntPtr.Zero))
                throw CapabilityException.ExecutionFailed("Failed to open clipboard");

            try
            {
                if (IsClipboardFormatAvailable(CF_UNICODETEXT))
                {
                    var handle = GetClipboardData(CF_UNICODETEXT);
                    if (handle != IntPtr.Zero)
                    {
                        var ptr = GlobalLock(handle);
                        if (ptr != IntPtr.Zero)
                        {
                            try
                            {
                                text = Marshal.PtrToStringUni(ptr);
                            }
                            finally
                            {
                                GlobalUnlock(handle);
                            }
                        }
                    }
                }
                else if (IsClipboardFormatAvailable(CF_TEXT))
                {
                    var handle = GetClipboardData(CF_TEXT);
                    if (handle != IntPtr.Zero)
                    {
                        var ptr = GlobalLock(handle);
                        if (ptr != IntPtr.Zero)
                        {
                            try
                            {
                                text = Marshal.PtrToStringAnsi(ptr);
                            }
                            finally
                            {
                                GlobalUnlock(handle);
                            }
                        }
                    }
                }
            }
            finally
            {
                CloseClipboard();
            }

            if (text != null)
            {
                return new Dictionary<string, object>
                {
                    ["type"] = "text",
                    ["content"] = text
                };
            }

            return new Dictionary<string, object>
            {
                ["type"] = "empty"
            };
        });
    }

    private Task<Dictionary<string, object>> WriteClipboard(Dictionary<string, object> args)
    {
        if (!args.TryGetValue("content", out var contentObj) || contentObj == null)
            throw CapabilityException.InvalidArguments("Missing 'content' argument");

        var content = contentObj.ToString()!;

        return Task.Run(() =>
        {
            if (!OpenClipboard(IntPtr.Zero))
                throw CapabilityException.ExecutionFailed("Failed to open clipboard");

            try
            {
                EmptyClipboard();

                var bytes = System.Text.Encoding.Unicode.GetBytes(content + "\0");
                var hGlobal = GlobalAlloc(GMEM_MOVEABLE, (UIntPtr)bytes.Length);

                if (hGlobal == IntPtr.Zero)
                    throw CapabilityException.ExecutionFailed("Failed to allocate memory");

                var ptr = GlobalLock(hGlobal);
                if (ptr == IntPtr.Zero)
                    throw CapabilityException.ExecutionFailed("Failed to lock memory");

                try
                {
                    Marshal.Copy(bytes, 0, ptr, bytes.Length);
                }
                finally
                {
                    GlobalUnlock(hGlobal);
                }

                if (SetClipboardData(CF_UNICODETEXT, hGlobal) == IntPtr.Zero)
                    throw CapabilityException.ExecutionFailed("Failed to set clipboard data");

                return new Dictionary<string, object>
                {
                    ["success"] = true,
                    ["length"] = content.Length
                };
            }
            finally
            {
                CloseClipboard();
            }
        });
    }

    private Task<Dictionary<string, object>> ClearClipboard()
    {
        return Task.Run(() =>
        {
            if (!OpenClipboard(IntPtr.Zero))
                throw CapabilityException.ExecutionFailed("Failed to open clipboard");

            try
            {
                EmptyClipboard();
                return new Dictionary<string, object>
                {
                    ["success"] = true
                };
            }
            finally
            {
                CloseClipboard();
            }
        });
    }
}
