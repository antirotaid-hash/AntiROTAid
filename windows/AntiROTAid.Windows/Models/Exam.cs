using System;
using System.Collections.Generic;

namespace AntiROTAid.Windows.Models;

/// <summary>
/// One exam: a name, a date+time, the materials (course ids) studied for it, and the
/// coverage knob. Mirrors the Android <c>AraExam</c> (AraPlan.kt). Local-only JSON.
///
/// Coverage (one knob for the WHOLE exam):
///  - <see cref="CoverageCount"/> mode ("count"): how many full passes over all the
///    exam's material before the date; the engine derives the card interval.
///  - "interval" mode: the user pins the minutes between cards and we show, live, how
///    many full passes still fit.
/// </summary>
public sealed class Exam
{
    public const string CoverageModeCount = "count";
    public const string CoverageModeInterval = "interval";

    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Name { get; set; } = "";
    public DateTime Date { get; set; } = DateTime.Today.AddDays(7);
    public List<string> MaterialIds { get; set; } = new();
    public string CoverageMode { get; set; } = CoverageModeCount;
    public int CoverageCount { get; set; } = 1;
    public int CoverageIntervalMin { get; set; }

    public int DaysLeft => (int)(Date.Date - DateTime.Today).TotalDays;
}
