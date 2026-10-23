using System;
using System.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// Loads the packaged Romanian demo lessons (offline, shipped inside the app) and
/// turns them into <see cref="Course"/> objects via <see cref="CardGenerator"/>.
/// Names are derived from the file name exactly like the Android app (e.g.
/// demo_history_original.txt → "History original").
/// </summary>
public static class DemoLessonLoader
{
    private static string DemoDir => Path.Combine(AppContext.BaseDirectory, "Assets", "demo");
    private static readonly Regex SlideRx =
        new(@"^\s*(Slide|Slaid|Diapozitiv)\s*\d+", RegexOptions.IgnoreCase | RegexOptions.Multiline);

    public static IReadOnlyList<Course> LoadAll()
    {
        var courses = new List<Course>();
        if (!Directory.Exists(DemoDir)) return courses;

        foreach (var file in Directory.GetFiles(DemoDir, "*.txt"))
        {
            var key = Path.GetFileNameWithoutExtension(file);
            string text;
            try { text = File.ReadAllText(file); }
            catch { continue; }

            courses.Add(new Course
            {
                Id = "demo:" + key,
                Name = NiceName(key),
                FileName = Path.GetFileName(file),
                SourceType = "demo",
                IsDemo = true,
                SourceText = text,   // kept so material settings can re-split the deck
                SlideCount = SlideRx.Matches(text).Count,
                Cards = new List<StudyCard>(CardGenerator.Generate(text)),
            });
        }
        return courses;
    }

    /// <summary>demo_history_original → "History original".</summary>
    private static string NiceName(string key)
    {
        var s = key.StartsWith("demo_", StringComparison.OrdinalIgnoreCase) ? key[5..] : key;
        s = s.Replace('_', ' ').Trim();
        if (s.Length == 0) return key;
        return char.ToUpperInvariant(s[0]) + s[1..];
    }
}
