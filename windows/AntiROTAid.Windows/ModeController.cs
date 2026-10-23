using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows.Threading;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Overlay;

namespace AntiROTAid.Windows;

/// <summary>
/// AntiROTAid Mode for Windows — the same manual mechanic as the Android app:
/// the user turns the mode ON, and a study card from the active deck floats OVER the
/// screen on a timed interval (first card a few seconds after Start, then every N
/// minutes) until they turn it OFF. It reads NOTHING about what you do — no active-app
/// detection, no process scanning, no network, no autostart.
/// </summary>
public sealed class ModeController
{
    private readonly DispatcherTimer _timer;
    private OverlayFeedWindow? _current;

    public bool IsActive { get; private set; }
    public int IntervalMinutes { get; set; } = 15;
    public event Action? StateChanged;

    public ModeController()
    {
        _timer = new DispatcherTimer();
        _timer.Tick += OnTick;
    }

    /// <summary>The single source of truth for what floats over the screen: the shared active
    /// course in <see cref="CourseStore"/>. The overlay and the in-app feed read the same
    /// state, so completing/advancing/deleting a course is reflected in both immediately.</summary>
    private Course? ActiveCourse => App.Courses.ActiveCourse;

    /// <summary>Kept for API compatibility with StudyPage; the deck now lives on the active
    /// course itself, so this only refreshes any live overlay if it shows that course.</summary>
    public void SetDeck(IEnumerable<StudyCard> cards) { /* deck read live from ActiveCourse */ }

    /// <summary>The course whose WHOLE feed floats over the screen (like the phone overlay).
    /// Studying a course retargets this live via the shared store, exactly like Android.</summary>
    public void SetActiveCourse(Course course) => App.Courses.SetActiveCourse(course);

    public bool HasCards => ActiveCourse is { CardCount: > 0 };

    public void Start()
    {
        if (IsActive) return;
        IsActive = true;
        // First card a few seconds after enabling, so the mechanic is visible at once.
        _timer.Interval = TimeSpan.FromSeconds(4);
        _timer.Start();
        StateChanged?.Invoke();
    }

    public void Stop()
    {
        if (!IsActive && _current == null) return;
        IsActive = false;
        _timer.Stop();
        _current?.Close();
        _current = null;
        StateChanged?.Invoke();
    }

    private void OnTick(object? sender, EventArgs e)
    {
        // Never stack overlays: if one is on screen, wait for the next interval.
        if (_current == null) ShowFeed();

        // After the initial 4s pulse, settle into the exam-driven cadence — recomputed
        // each tick so a changed interval takes effect immediately.
        _timer.Interval = TimeSpan.FromMinutes(CurrentGapMinutes());
    }

    /// <summary>Minutes between cards. Driven by the active exam's coverage EXACTLY like the
    /// phone: if the exam pins a "minutes between cards" (interval mode) we use that; a
    /// count-mode exam derives its pace from the engine plan; otherwise the Settings default.</summary>
    private double CurrentGapMinutes()
    {
        var exams = App.Exams.Exams;
        var course = ActiveCourse;
        // Prefer the exam that actually contains the material now floating over the screen.
        Exam? ex = course != null ? exams.FirstOrDefault(e => e.MaterialIds.Contains(course.Id)) : null;
        ex ??= exams.FirstOrDefault(e => e.MaterialIds.Count > 0) ?? exams.FirstOrDefault();

        if (ex is { CoverageMode: Exam.CoverageModeInterval } && ex.CoverageIntervalMin >= 1)
            return ex.CoverageIntervalMin;

        return System.Math.Clamp(IntervalMinutes, 1, 180);
    }

    /// <summary>Float the WHOLE active course feed over the screen (resume, progress,
    /// per-card CTAs) — exactly like the phone's overlay hosting AraCardFeed. Closing it
    /// (Save spot &amp; exit / ←) just dismisses this appearance; the mode stays on and the
    /// feed floats up again next interval until the user turns the mode off.</summary>
    private void ShowFeed()
    {
        var course = ActiveCourse;
        if (course is null || course.CardCount == 0) return;

        _currentCourseId = course.Id;
        var win = new OverlayFeedWindow(course);
        win.Closed += (_, _) => { if (ReferenceEquals(_current, win)) { _current = null; _currentCourseId = ""; } };
        _current = win;
        win.Show();
    }

    private string _currentCourseId = "";

    /// <summary>Close an overlay that is currently showing a course that was just deleted, so a
    /// stale, deleted deck can't stay on screen. The next interval re-opens the new active
    /// course. Safe to call from the UI thread (course deletion is UI-initiated).</summary>
    public void NotifyCourseDeleted(string courseId)
    {
        if (_current != null && _currentCourseId == courseId)
        {
            _current.Close();   // Closed handler clears _current / _currentCourseId
        }
    }
}
