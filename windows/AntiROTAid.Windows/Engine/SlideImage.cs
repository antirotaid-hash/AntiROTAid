using System;
using System.IO;
using System.Threading.Tasks;
using System.Windows.Media.Imaging;
using Windows.Data.Pdf;
using Windows.Storage;
using Windows.Storage.Streams;

namespace AntiROTAid.Windows.Engine;

/// <summary>
/// Turns a card's engine media into a WPF image, matching the phone's VisualCardPanel:
/// an embedded slide/figure image (base64 or on-device file), or a page of an on-device
/// PDF rendered AS-IS at full width (Android uses PdfRenderer; here Windows.Data.Pdf).
/// </summary>
public static class SlideImage
{
    public static BitmapImage? FromBase64(string b64)
    {
        if (string.IsNullOrEmpty(b64)) return null;
        try { return FromBytes(Convert.FromBase64String(b64)); }
        catch { return null; }
    }

    public static BitmapImage? FromFile(string path)
    {
        try { return File.Exists(path) ? FromBytes(File.ReadAllBytes(path)) : null; }
        catch { return null; }
    }

    /// <summary>Render one PDF page (1-based) to a bitmap ~<paramref name="widthPx"/> wide,
    /// keeping aspect ratio — the "AS-IS page" the phone shows. Runs the WinRT async work
    /// on a background thread to avoid blocking / dead-locking the UI thread.</summary>
    public static BitmapImage? FromPdfPage(string path, int page, int widthPx)
    {
        if (string.IsNullOrEmpty(path) || page < 1) return null;
        try
        {
            var bytes = Task.Run(() => RenderPdfPageAsync(path, page, widthPx)).GetAwaiter().GetResult();
            return bytes is null ? null : FromBytes(bytes);
        }
        catch { return null; }
    }

    private static async Task<byte[]?> RenderPdfPageAsync(string path, int page, int widthPx)
    {
        var file = await StorageFile.GetFileFromPathAsync(Path.GetFullPath(path));
        var doc = await PdfDocument.LoadFromFileAsync(file);
        if (doc.PageCount == 0) return null;
        uint idx = (uint)Math.Clamp(page - 1, 0, (int)doc.PageCount - 1);
        using var p = doc.GetPage(idx);
        using var ms = new InMemoryRandomAccessStream();
        var opts = new PdfPageRenderOptions
        {
            DestinationWidth = (uint)Math.Clamp(widthPx, 360, 1600),
            BackgroundColor = global::Windows.UI.Color.FromArgb(255, 255, 255, 255),
        };
        await p.RenderToStreamAsync(ms, opts);
        var reader = new DataReader(ms.GetInputStreamAt(0));
        uint len = (uint)ms.Size;
        await reader.LoadAsync(len);
        var buf = new byte[len];
        reader.ReadBytes(buf);
        return buf;
    }

    private static BitmapImage FromBytes(byte[] bytes)
    {
        var bmp = new BitmapImage();
        using var ms = new MemoryStream(bytes);
        bmp.BeginInit();
        bmp.CacheOption = BitmapCacheOption.OnLoad;
        bmp.StreamSource = ms;
        bmp.EndInit();
        bmp.Freeze();
        return bmp;
    }
}
