using System.IO;
using System.Text;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// Crash-safe JSON writes shared by every local store (courses, exams, settings): write to
/// a temp file, flush to disk, then atomically replace the target — so a crash or power loss
/// mid-write can never leave a half-written, unparseable state file. Keeps a short-lived
/// <c>.bak</c> during the swap so even a torn <see cref="File.Replace(string,string,string)"/>
/// leaves a recoverable copy.
/// </summary>
public static class AtomicFile
{
    public static void Write(string path, string content)
    {
        var dir = Path.GetDirectoryName(path);
        if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);

        var tmp = path + ".tmp";
        using (var fs = new FileStream(tmp, FileMode.Create, FileAccess.Write, FileShare.None))
        using (var sw = new StreamWriter(fs, new UTF8Encoding(false)))
        {
            sw.Write(content);
            sw.Flush();
            fs.Flush(true);   // flush to disk before the swap
        }

        if (File.Exists(path))
        {
            var bak = path + ".bak";
            File.Replace(tmp, path, bak, ignoreMetadataErrors: true);
            try { File.Delete(bak); } catch { /* leave the recoverable backup */ }
        }
        else File.Move(tmp, path);
    }
}
