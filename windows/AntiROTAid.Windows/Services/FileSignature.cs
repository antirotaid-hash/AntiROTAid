using System;
using System.IO;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// Cheap magic-byte check so a file whose real content doesn't match its extension (e.g. a
/// text file renamed <c>bad.pdf</c>) is rejected at import time instead of becoming a broken
/// visual card. Only guards the formats we import; unknown/plain extensions (txt/md/pasted)
/// are always allowed.
/// </summary>
public static class FileSignature
{
    public static bool MatchesExtension(string path)
    {
        string ext = Path.GetExtension(path).TrimStart('.').ToLowerInvariant();
        byte[] head;
        try
        {
            using var fs = File.OpenRead(path);
            head = new byte[8];
            int n = fs.Read(head, 0, head.Length);
            if (n < head.Length) Array.Resize(ref head, n);
        }
        catch { return false; }

        switch (ext)
        {
            case "pdf":
                return StartsWith(head, (byte)'%', (byte)'P', (byte)'D', (byte)'F');
            // OOXML (pptx/docx/xlsx) are ZIP containers → "PK\x03\x04".
            case "pptx":
            case "docx":
            case "xlsx":
                return StartsWith(head, (byte)'P', (byte)'K', 0x03, 0x04);
            case "png":
                return StartsWith(head, 0x89, (byte)'P', (byte)'N', (byte)'G');
            case "jpg":
            case "jpeg":
                return StartsWith(head, 0xFF, 0xD8, 0xFF);
            case "gif":
                return StartsWith(head, (byte)'G', (byte)'I', (byte)'F', (byte)'8');
            case "bmp":
                return StartsWith(head, (byte)'B', (byte)'M');
            default:
                return true;   // txt / md / anything else: no signature to enforce
        }
    }

    private static bool StartsWith(byte[] data, params byte[] prefix)
    {
        if (data.Length < prefix.Length) return false;
        for (int i = 0; i < prefix.Length; i++)
            if (data[i] != prefix[i]) return false;
        return true;
    }
}
