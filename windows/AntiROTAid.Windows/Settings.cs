using System;
using System.IO;
using System.Text.Json;
using AntiROTAid.Windows.Themes;

namespace AntiROTAid.Windows;

/// <summary>
/// Local-only preferences (theme, language, card interval). Stored as a small JSON
/// file under %AppData%\AntiROTAid. No account, no cloud, no network — ever.
/// </summary>
public sealed class Settings
{
    public AraThemeStyle Theme { get; set; } = AraThemeStyle.DarkGold;
    public string Language { get; set; } = "ro";
    public int IntervalMinutes { get; set; } = 5;
    /// <summary>False until the first-run language picker has been answered once.</summary>
    public bool LanguageChosen { get; set; } = false;
    /// <summary>False until the first-run tutorial (the home walkthrough) has run once,
    /// shown right after the user accepts onboarding/consent. Never auto-shown again.</summary>
    public bool TutorialSeen { get; set; } = false;

    // --- Study plan + look, mirroring the Android AraStore / AraPlanStore ---
    /// <summary>Text size: 0=Small 1=Medium 2=Large 3=XLarge (AraFont).</summary>
    public int Font { get; set; } = 1;
    /// <summary>Average phone time per week, in hours (Variant B, owner-stated).</summary>
    public int WeeklyHours { get; set; } = 10;
    /// <summary>Multiple exams: "combine" (parallel) or "sequential" (one at a time).</summary>
    public string StudyMode { get; set; } = "combine";
    /// <summary>Auto-delete materials after their exam date passes.</summary>
    public bool AutoDelete { get; set; } = false;

    private static string Dir =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "AntiROTAid");
    private static string FilePath => Path.Combine(Dir, "settings.json");

    public static Settings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath)) ?? new Settings();
        }
        catch { /* corrupt/unreadable → defaults */ }
        return new Settings();
    }

    public void Save()
    {
        try
        {
            Services.AtomicFile.Write(FilePath,
                JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { /* best-effort; never crash on a settings write */ }
    }
}
