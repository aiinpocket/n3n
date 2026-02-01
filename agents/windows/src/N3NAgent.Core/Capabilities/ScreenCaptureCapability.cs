using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

namespace N3NAgent.Core.Capabilities;

/// <summary>
/// Capability for capturing screen screenshots.
/// </summary>
public class ScreenCaptureCapability : ICapability
{
    public string Id => "screen.capture";
    public string Name => "Screen Capture";
    public string Description => "Capture screenshots of the screen or specific windows";

    public async Task<object?> ExecuteAsync(Dictionary<string, object> args)
    {
        var displayId = args.TryGetValue("displayId", out var d) ? Convert.ToInt32(d) : 0;
        var format = args.TryGetValue("format", out var f) ? f.ToString() ?? "png" : "png";
        var quality = args.TryGetValue("quality", out var q) ? Convert.ToDouble(q) : 0.8;

        return await Task.Run(() => CaptureScreen(displayId, format, quality));
    }

    private Dictionary<string, object> CaptureScreen(int displayId, string format, double quality)
    {
        // Get screen bounds
        var bounds = GetScreenBounds(displayId);

        using var bitmap = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format32bppArgb);
        using var graphics = Graphics.FromImage(bitmap);

        graphics.CopyFromScreen(bounds.X, bounds.Y, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);

        // Convert to requested format
        using var ms = new MemoryStream();
        var imageFormat = format.ToLower() switch
        {
            "jpeg" or "jpg" => ImageFormat.Jpeg,
            "png" => ImageFormat.Png,
            "bmp" => ImageFormat.Bmp,
            _ => ImageFormat.Png
        };

        if (imageFormat == ImageFormat.Jpeg)
        {
            var encoder = GetEncoder(ImageFormat.Jpeg);
            if (encoder != null)
            {
                var encoderParams = new EncoderParameters(1);
                encoderParams.Param[0] = new EncoderParameter(Encoder.Quality, (long)(quality * 100));
                bitmap.Save(ms, encoder, encoderParams);
            }
            else
            {
                bitmap.Save(ms, imageFormat);
            }
        }
        else
        {
            bitmap.Save(ms, imageFormat);
        }

        return new Dictionary<string, object>
        {
            ["data"] = Convert.ToBase64String(ms.ToArray()),
            ["format"] = format,
            ["width"] = bounds.Width,
            ["height"] = bounds.Height
        };
    }

    private static Rectangle GetScreenBounds(int displayId)
    {
        var screens = System.Windows.Forms.Screen.AllScreens;
        if (displayId >= 0 && displayId < screens.Length)
        {
            return screens[displayId].Bounds;
        }
        return System.Windows.Forms.Screen.PrimaryScreen?.Bounds ?? new Rectangle(0, 0, 1920, 1080);
    }

    private static ImageCodecInfo? GetEncoder(ImageFormat format)
    {
        return ImageCodecInfo.GetImageEncoders()
            .FirstOrDefault(codec => codec.FormatID == format.Guid);
    }
}
