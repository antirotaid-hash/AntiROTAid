using System;
using System.Collections.Generic;

namespace AntiROTAid.Windows.Models;

/// <summary>
/// A course (a set of study cards generated from one lesson). Mirrors the Android
/// "material / course" concept. Stored locally as JSON — no cloud, no account.
/// </summary>
public sealed class Course
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Name { get; set; } = "";
    public string FileName { get; set; } = "";          // e.g. demo_algebra.txt
    public string SourceType { get; set; } = "demo";    // demo | text
    public bool IsDemo { get; set; }
    public int SlideCount { get; set; }
    public DateTime Added { get; set; } = DateTime.Now;
    public List<StudyCard> Cards { get; set; } = new();

    // Source kept so the deck can be re-split when the material settings change
    // (mirrors the phone re-calling lesson_feed / lesson_feed_file). One of these is set:
    public string SourceText { get; set; } = "";   // pasted-text / demo raw text
    public string LocalPath { get; set; } = "";     // imported file on disk

    // Per-material card settings (the "How cards appear" card). Only Appearance is
    // user-facing (the split mode; "" = engine default per file type, e.g. PDF pages
    // vs paragraphs). CardSize is fixed "compact" for wire-compat, exactly like the
    // phone's AraMaterialSettings.
    public string Appearance { get; set; } = "";
    public string CardSize { get; set; } = "compact";

    public int CardCount => Cards.Count;
    public int KnownCount => Cards.FindAll(c => c.State == CardState.Known).Count;
    public int LearnedPct => CardCount == 0 ? 0 : (int)System.Math.Round(100.0 * KnownCount / CardCount);
}
