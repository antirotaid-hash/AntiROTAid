using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using AntiROTAid.Windows.Engine;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Views;

public partial class ExamsPage : UserControl
{
    private readonly HashSet<string> _confirmDelete = new();
    private readonly HashSet<string> _editingDate = new();
    private readonly HashSet<string> _attachOpen = new();
    private readonly HashSet<string> _settingsOpen = new();   // material ids with the ⚙ panel expanded
    private readonly HashSet<string> _customCount = new();    // exams where "Other…" opened the free-number box
    private string _sort = "date";

    public ExamsPage()
    {
        InitializeComponent();
        var t = I18n.T;
        Title.Text = t.ExamsTitle;
        Sub.Text = t.ExamsSub;
        AddBtn.Content = t.AddExam;
        NameBox.Tag = t.ExamName;
        DateBox.SelectedDate = DateTime.Today.AddDays(7);
        Rebuild();
    }

    // ---- engine study plan ----------------------------------------------------
    private sealed record PlanRow(int Units, double Interval, double PerDay, bool Covered);

    private double DailyPhoneMin()
    {
        var s = Settings.Load();
        int weekly = s.WeeklyHours > 0 ? s.WeeklyHours * 60 : 600;
        return weekly / 7.0;
    }

    /// <summary>Ask the engine (study_plan) to pace every exam against the weekly budget.</summary>
    private Dictionary<string, PlanRow> PlanFor()
    {
        var map = new Dictionary<string, PlanRow>();
        var s = Settings.Load();
        int weekly = s.WeeklyHours > 0 ? s.WeeklyHours * 60 : 600;

        var exams = App.Exams.Exams.Select(e =>
        {
            int units = e.MaterialIds.Sum(m => Math.Max(1, App.Courses.Get(m)?.CardCount ?? 0));
            int reps = e.CoverageMode == Exam.CoverageModeInterval ? 1 : Math.Max(1, e.CoverageCount);
            return new
            {
                id = e.Id,
                name = e.Name,
                exam_iso = e.Date.ToString("yyyy-MM-dd"),
                num_units = units,
                repeats = reps,
            };
        }).ToArray();
        if (exams.Length == 0) return map;

        var data = EngineBridge.Shared.CallData("study_plan", new
        {
            exams,
            weekly_phone_minutes = weekly,
            mode = s.StudyMode,
        });
        if (data is not { } d || !d.TryGetProperty("exams", out var arr) || arr.ValueKind != JsonValueKind.Array)
            return map;

        foreach (var ex in arr.EnumerateArray())
        {
            string id = ex.TryGetProperty("id", out var i) ? (i.GetString() ?? "") : "";
            map[id] = new PlanRow(
                GetInt(ex, "num_units"),
                GetDouble(ex, "interval_minutes"),
                GetDouble(ex, "appearances_per_day"),
                !ex.TryGetProperty("fully_covered", out var fc) || fc.ValueKind != JsonValueKind.False);
        }
        return map;
    }

    private static int GetInt(JsonElement o, string k) =>
        o.TryGetProperty(k, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetInt32() : 0;
    private static double GetDouble(JsonElement o, string k) =>
        o.TryGetProperty(k, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetDouble() : 0;

    // ---- list -----------------------------------------------------------------
    private void Rebuild()
    {
        var x = ExamI18n;
        List.Children.Clear();
        if (App.Exams.Exams.Count == 0)
        {
            List.Children.Add(new TextBlock
            {
                Text = x.Empty, FontSize = 13, Foreground = Brush("AraMuted"),
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(2, 6, 0, 0),
            });
            return;
        }
        // Sort bar (only meaningful with more than one exam), mirroring the phone.
        if (App.Exams.Exams.Count > 1) List.Children.Add(SortBar());

        var plan = PlanFor();
        IEnumerable<Exam> ordered = _sort == "name"
            ? App.Exams.Exams.OrderBy(e => e.Name, StringComparer.CurrentCultureIgnoreCase)
            : App.Exams.Exams.OrderBy(e => e.Date);
        foreach (var ex in ordered)
            List.Children.Add(ExamCard(ex, plan.GetValueOrDefault(ex.Id)));
    }

    private FrameworkElement SortBar()
    {
        var c = CoursesI18n.For(I18n.Locale);
        string dateLabel = I18n.Locale switch { "ro" => "Dată", "de" => "Datum", "fr" => "Date", "es" => "Fecha", _ => "Date" };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 14) };
        row.Children.Add(new TextBlock { Text = c.SortBy, FontSize = 12, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraMuted"), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 12, 0) });
        row.Children.Add(SortPill("date", dateLabel));
        row.Children.Add(SortPill("name", c.SortName));
        return row;
    }

    private Border SortPill(string key, string label)
    {
        bool sel = key == _sort;
        var b = new Border
        {
            CornerRadius = new CornerRadius(14), Padding = new Thickness(16, 8, 16, 8),
            Margin = new Thickness(0, 0, 8, 0), Cursor = Cursors.Hand,
            Background = sel ? Brush("AraBrush") : Brush("AraGreenSoft"),
            BorderThickness = new Thickness(1), BorderBrush = sel ? Brush("AraGreen") : Brush("AraGlassEdgeGreen"),
            Child = new TextBlock { Text = label, FontSize = 14, FontWeight = sel ? FontWeights.Bold : FontWeights.Medium,
                Foreground = sel ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : Brush("AraTitle") },
        };
        b.MouseLeftButtonUp += (_, _) => { _sort = key; Rebuild(); };
        return b;
    }

    private ExamText ExamI18n => ExamText.For(I18n.Locale);

    private Border ExamCard(Exam e, PlanRow? plan)
    {
        var x = ExamI18n;
        var card = new Border
        {
            CornerRadius = new CornerRadius(20), Padding = new Thickness(18), Margin = new Thickness(0, 0, 0, 14),
            Background = Brush("AraGlassGradient"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var col = new StackPanel();

        // Name
        col.Children.Add(new TextBlock { Text = e.Name, FontSize = 18, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraTitle"), Margin = new Thickness(0, 0, 0, 10) });

        // Date row (editable)
        col.Children.Add(DateRow(e));

        // Materials count
        col.Children.Add(StatRow(x.LabelMaterials, e.MaterialIds.Count.ToString()));

        // Plan summary from the engine
        int units = plan?.Units ?? 0;
        if (e.MaterialIds.Count == 0)
        {
            // nothing to plan yet
        }
        else if (units <= 0 || plan is null)
        {
            col.Children.Add(new TextBlock { Text = x.OpenMaterialsHint, FontSize = 11, Foreground = Brush("AraMuted"),
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 0) });
        }
        else
        {
            col.Children.Add(StatRow(x.LabelPlan, string.Format(x.PlanFmt, (int)plan.Interval, (int)plan.PerDay)));
            if (e.CoverageMode == Exam.CoverageModeCount && !plan.Covered)
            {
                col.Children.Add(new TextBlock { Text = x.TimeWarning, FontSize = 11, Foreground = Brush("AraDanger"),
                    TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 0) });
                var fix = new TextBlock { Text = x.OpenPlanSettings, FontSize = 12, FontWeight = FontWeights.Bold,
                    Foreground = Brush("AraGreenDeep"), Cursor = Cursors.Hand, Margin = new Thickness(0, 2, 0, 0) };
                fix.MouseLeftButtonUp += (_, _) => MainWindow.Current?.Navigate("settings");
                col.Children.Add(fix);
            }
        }

        // Coverage control (one knob for the whole exam) + live readout
        col.Children.Add(CoverageControl(e, units));

        // Materials list
        if (e.MaterialIds.Count > 0) col.Children.Add(MaterialsList(e));

        // Attach panel (inline "from courses" picker)
        if (_attachOpen.Contains(e.Id)) col.Children.Add(AttachPanel(e));

        // Chips: import into exam | from courses
        var chips = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 12, 0, 0) };
        chips.Children.Add(Chip(x.ImportShort, () => ImportInto(e)));
        chips.Children.Add(Chip(x.FromCourses, () =>
        {
            if (!_attachOpen.Add(e.Id)) _attachOpen.Remove(e.Id);
            Rebuild();
        }));
        col.Children.Add(chips);

        // Delete (tap to arm, again to confirm)
        bool armed = _confirmDelete.Contains(e.Id);
        var del = new Border
        {
            CornerRadius = new CornerRadius(12), Margin = new Thickness(0, 10, 0, 0), Cursor = Cursors.Hand,
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraDanger"), Padding = new Thickness(0, 11, 0, 11),
            Child = new TextBlock { Text = armed ? x.ConfirmAgain : x.DeleteExam, FontSize = 14, FontWeight = FontWeights.Bold,
                Foreground = Brush("AraDanger"), HorizontalAlignment = HorizontalAlignment.Center },
        };
        del.MouseLeftButtonUp += (_, _) =>
        {
            if (armed) { App.Exams.Delete(e.Id); _confirmDelete.Remove(e.Id); }
            else _confirmDelete.Add(e.Id);
            Rebuild();
        };
        col.Children.Add(del);

        card.Child = col;
        return card;
    }

    // Date + time, tap to reveal inline editors.
    private FrameworkElement DateRow(Exam e)
    {
        var x = ExamI18n;
        var wrap = new StackPanel { Margin = new Thickness(0, 0, 0, 4) };
        var fmt = e.Date.ToString("d MMM yyyy, HH:mm", CultureInfo.GetCultureInfo(LocaleTag()));

        var row = new Grid { Cursor = Cursors.Hand };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(new TextBlock { Text = x.LabelData, FontSize = 13, Foreground = Brush("AraMuted"),
            VerticalAlignment = VerticalAlignment.Center });
        var right = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        right.Children.Add(new TextBlock { Text = fmt, FontSize = 14, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraTitle"), VerticalAlignment = VerticalAlignment.Center });
        right.Children.Add(new TextBlock { Text = "  ✎ " + x.EditBtn, FontSize = 12, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraGreenDeep"), VerticalAlignment = VerticalAlignment.Center });
        Grid.SetColumn(right, 1);
        row.Children.Add(right);
        row.MouseLeftButtonUp += (_, _) =>
        {
            if (!_editingDate.Add(e.Id)) _editingDate.Remove(e.Id);
            Rebuild();
        };
        wrap.Children.Add(row);

        if (_editingDate.Contains(e.Id))
        {
            var edit = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 8, 0, 4) };
            var dp = new DatePicker { Width = 150, Height = 38, SelectedDate = e.Date,
                Background = Brush("AraGreenSoft"), Foreground = Brush("AraTitle"), BorderBrush = Brush("AraGlassEdgeGreen") };
            var tb = new TextBox { Width = 66, Height = 38, Margin = new Thickness(8, 0, 0, 0), FontSize = 14,
                Text = e.Date.ToString("HH:mm"), VerticalContentAlignment = VerticalAlignment.Center,
                HorizontalContentAlignment = HorizontalAlignment.Center,
                Background = Brush("AraGreenSoft"), Foreground = Brush("AraTitle"),
                CaretBrush = Brush("AraTitle"), BorderBrush = Brush("AraGlassEdgeGreen") };
            var set = new Border
            {
                CornerRadius = new CornerRadius(10), Margin = new Thickness(8, 0, 0, 0), Cursor = Cursors.Hand,
                Background = Brush("AraBrush"), Padding = new Thickness(16, 0, 16, 0), VerticalAlignment = VerticalAlignment.Center,
                Child = new TextBlock { Text = x.SetBtn, FontSize = 14, FontWeight = FontWeights.Bold,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)),
                    VerticalAlignment = VerticalAlignment.Center },
            };
            set.MouseLeftButtonUp += (_, _) =>
            {
                var day = dp.SelectedDate ?? e.Date.Date;
                var time = ParseTime(tb.Text) ?? e.Date.TimeOfDay;
                e.Date = day.Date + time;
                _editingDate.Remove(e.Id);
                App.Exams.Save(e);
                Rebuild();
            };
            edit.Children.Add(dp); edit.Children.Add(tb); edit.Children.Add(set);
            wrap.Children.Add(edit);
        }
        return wrap;
    }

    // The coverage knob: pick full passes (count) or pin minutes between cards (interval).
    private FrameworkElement CoverageControl(Exam e, int totalUnits)
    {
        var x = ExamI18n;
        var box = new Border
        {
            CornerRadius = new CornerRadius(16), Margin = new Thickness(0, 12, 0, 0), Padding = new Thickness(14),
            Background = Brush("AraGlassGradient"), BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var col = new StackPanel();
        col.Children.Add(new TextBlock { Text = x.CoverageTitle, FontSize = 14, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraTitle") });
        col.Children.Add(new TextBlock { Text = x.CoverageSub, FontSize = 11, Foreground = Brush("AraMuted"),
            TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 8) });

        bool isInterval = e.CoverageMode == Exam.CoverageModeInterval;
        var presets = new[] { 1, 2, 3, 5 };
        // Show the free-number box when the count isn't a preset OR the user explicitly
        // picked "Other…" (mirrors the phone's customCount) — otherwise you couldn't type
        // a custom number of repeats when the current value happened to be a preset.
        bool custom = !isInterval && (_customCount.Contains(e.Id) || !presets.Contains(e.CoverageCount));

        var combo = new ComboBox { Height = 42, FontSize = 15, Foreground = Brush("AraTitle") };
        var style = TryFindResource("AraCombo") as Style;
        if (style != null) combo.Style = style;
        else { combo.Background = Brush("AraGreenSoft"); }
        foreach (var n in presets)
            combo.Items.Add(new ComboBoxItem { Content = n == 1 ? x.CoverageOnce : x.NTimes(n), Tag = $"count:{n}" });
        combo.Items.Add(new ComboBoxItem { Content = x.CoverageOther, Tag = "other" });
        combo.Items.Add(new ComboBoxItem { Content = x.CoverageMenuInterval, Tag = "interval" });
        combo.SelectedIndex = isInterval ? presets.Length + 1
            : custom ? presets.Length
            : Array.IndexOf(presets, e.CoverageCount);
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedItem is not ComboBoxItem it || it.Tag is not string tag) return;
            if (tag == "interval") { _customCount.Remove(e.Id); e.CoverageMode = Exam.CoverageModeInterval; if (e.CoverageIntervalMin <= 0) e.CoverageIntervalMin = 0; }
            else if (tag == "other") { _customCount.Add(e.Id); e.CoverageMode = Exam.CoverageModeCount; }
            else if (tag.StartsWith("count:"))
            { _customCount.Remove(e.Id); e.CoverageMode = Exam.CoverageModeCount; e.CoverageCount = int.Parse(tag[6..]); }
            App.Exams.Save(e);
            Rebuild();
        };
        col.Children.Add(combo);

        // Number field for interval minutes or a custom count.
        if (isInterval)
        {
            col.Children.Add(NumberField(x.CoverageMinLabel, e.CoverageIntervalMin, "min", 1, 1440, v =>
            { e.CoverageIntervalMin = v; App.Exams.Save(e); Rebuild(); }));
            col.Children.Add(new TextBlock { Text = x.CoverageMinHint, FontSize = 11, Foreground = Brush("AraMuted"),
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 6, 0, 0) });
        }
        else if (custom)
        {
            col.Children.Add(NumberField(x.CoverageCountLabel, e.CoverageCount, "×", 1, 99, v =>
            { e.CoverageCount = v; App.Exams.Save(e); Rebuild(); }));
        }

        // Live readout.
        var live = LiveReadout(e, totalUnits);
        if (live != null) col.Children.Add(live);

        box.Child = col;
        return box;
    }

    // "At the current pace you can still cover..." — the live line.
    private TextBlock? LiveReadout(Exam e, int totalUnits)
    {
        var x = ExamI18n;
        if (totalUnits <= 0) return null;
        double daysLeft = Math.Max(0, (e.Date - DateTime.Now).TotalDays);
        var t = new TextBlock { FontSize = 12, FontWeight = FontWeights.SemiBold, TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 8, 0, 0), LineHeight = 16 };
        if (e.CoverageMode == Exam.CoverageModeInterval)
        {
            int gap = e.CoverageIntervalMin;
            if (gap < 1) { t.Text = x.CoverageSetMinutes; t.Foreground = Brush("AraMuted"); return t; }
            double passes = (DailyPhoneMin() * daysLeft / gap) / totalUnits;
            if (passes >= 1.0) { t.Text = x.PassesLive((int)passes); t.Foreground = Brush("AraGreen"); }
            else { t.Text = x.CoverageNoTimeWhole; t.Foreground = Brush("AraDanger"); }
        }
        else { t.Text = x.CountLive(e.CoverageCount); t.Foreground = Brush("AraBody"); }
        return t;
    }

    private FrameworkElement MaterialsList(Exam e)
    {
        var d = DetailI18n.For(I18n.Locale);
        var wrap = new StackPanel { Margin = new Thickness(0, 12, 0, 0) };
        foreach (var m in e.MaterialIds.ToList())
        {
            var course = App.Courses.Get(m);
            var row = new Grid { Margin = new Thickness(0, 0, 0, 6) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Name + current appearance sub-label (like the phone).
            var nameCol = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            nameCol.Children.Add(new TextBlock { Text = "• " + (course?.Name ?? m), FontSize = 13,
                Foreground = Brush("AraBody"), TextWrapping = TextWrapping.Wrap });
            if (course is { } cc && !string.IsNullOrEmpty(cc.Appearance))
                nameCol.Children.Add(new TextBlock { Text = d.ChoiceLabel(cc.Appearance), FontSize = 11,
                    Foreground = Brush("AraMuted") });
            row.Children.Add(nameCol);

            // ⚙ toggle the inline settings panel.
            var gear = new TextBlock { Text = "⚙", FontSize = 16, Foreground = Brush("AraGreenDeep"), Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(8, 0, 0, 0) };
            gear.MouseLeftButtonUp += (_, _) =>
            {
                if (!_settingsOpen.Add(m)) _settingsOpen.Remove(m);
                Rebuild();
            };
            Grid.SetColumn(gear, 1);
            row.Children.Add(gear);

            var rm = new TextBlock { Text = "✕", FontSize = 14, Foreground = Brush("AraDanger"), Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 0, 0) };
            rm.MouseLeftButtonUp += (_, _) => { _settingsOpen.Remove(m); App.Exams.RemoveMaterial(e.Id, m); Rebuild(); };
            Grid.SetColumn(rm, 2);
            row.Children.Add(rm);
            wrap.Children.Add(row);

            if (_settingsOpen.Contains(m) && course is not null)
            {
                var settings = MaterialSettingsInline(course);
                if (settings is not null) wrap.Children.Add(settings);
            }
        }
        return wrap;
    }

    // The "How cards appear" settings for one material, inline (ports MaterialSettingsCard).
    private FrameworkElement? MaterialSettingsInline(Course course)
    {
        var d = DetailI18n.For(I18n.Locale);
        var data = App.Courses.MaterialOptions(course);
        if (data is not { } opts || !opts.TryGetProperty("options", out var list) || list.ValueKind != JsonValueKind.Array)
            return null;

        var box = new Border
        {
            CornerRadius = new CornerRadius(16), Margin = new Thickness(0, 2, 0, 8), Padding = new Thickness(14),
            Background = Brush("AraGlassGradient"), BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var col = new StackPanel();
        col.Children.Add(new TextBlock { Text = d.HowCardsAppear, FontSize = 14, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraTitle"), Margin = new Thickness(0, 0, 0, 6) });
        col.Children.Add(new TextBlock { Text = d.CoachModesBody, FontSize = 11, LineHeight = 15,
            Foreground = Brush("AraMuted"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 10) });

        foreach (var o in list.EnumerateArray())
        {
            string key = o.TryGetProperty("key", out var k) ? k.GetString() ?? "" : "";
            if (!o.TryGetProperty("choices", out var choices) || choices.ValueKind != JsonValueKind.Array) continue;
            string def = o.TryGetProperty("default", out var dv) ? dv.GetString() ?? "" : "";
            string selected = key == "appearance" ? course.Appearance : course.CardSize;
            if (string.IsNullOrEmpty(selected)) selected = def;

            col.Children.Add(new TextBlock { Text = d.OptAppearance, FontSize = 13, FontWeight = FontWeights.SemiBold,
                Foreground = Brush("AraBody"), Margin = new Thickness(0, 2, 0, 6) });

            if (choices.GetArrayLength() >= 2)
            {
                var chipRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };
                foreach (var cEl in choices.EnumerateArray())
                {
                    string val = cEl.GetString() ?? "";
                    chipRow.Children.Add(SettingChip(d.ChoiceLabel(val), selected == val, () =>
                    {
                        Mouse.OverrideCursor = Cursors.Wait;
                        try
                        {
                            if (key == "appearance") App.Courses.ApplyMaterialSettings(course, val, course.CardSize);
                            else App.Courses.ApplyMaterialSettings(course, course.Appearance, val);
                        }
                        finally { Mouse.OverrideCursor = null; }
                        Rebuild();
                    }));
                }
                col.Children.Add(chipRow);
            }
            string desc = d.ChoiceDesc(selected);
            if (!string.IsNullOrEmpty(desc))
                col.Children.Add(new TextBlock { Text = desc, FontSize = 11, LineHeight = 15,
                    Foreground = Brush("AraMuted"), TextWrapping = TextWrapping.Wrap });
        }
        box.Child = col;
        return box;
    }

    private Border SettingChip(string text, bool selected, Action onClick)
    {
        var b = new Border
        {
            CornerRadius = new CornerRadius(12), Padding = new Thickness(14, 9, 14, 9),
            Margin = new Thickness(0, 0, 8, 0), Cursor = Cursors.Hand,
            Background = selected ? Brush("AraGreen") : Brush("AraGreenSoft"),
            Child = new TextBlock { Text = text, FontSize = 13, FontWeight = selected ? FontWeights.Bold : FontWeights.Medium,
                Foreground = selected ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : Brush("AraBody") },
        };
        b.MouseLeftButtonUp += (_, _) => onClick();
        return b;
    }

    // Inline "attach existing course" picker (checkbox list).
    private FrameworkElement AttachPanel(Exam e)
    {
        var x = ExamI18n;
        var box = new Border
        {
            CornerRadius = new CornerRadius(14), Margin = new Thickness(0, 10, 0, 0), Padding = new Thickness(12),
            Background = Brush("AraGreenSoft"), BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var col = new StackPanel();
        col.Children.Add(new TextBlock { Text = x.AddMaterials, FontSize = 14, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraTitle"), Margin = new Thickness(0, 0, 0, 8) });
        if (App.Courses.Courses.Count == 0)
            col.Children.Add(new TextBlock { Text = x.NoMaterials, FontSize = 13, Foreground = Brush("AraMuted") });
        foreach (var c in App.Courses.Courses)
        {
            bool inExam = e.MaterialIds.Contains(c.Id);
            var row = new StackPanel { Orientation = Orientation.Horizontal, Cursor = Cursors.Hand, Margin = new Thickness(0, 3, 0, 3) };
            row.Children.Add(new TextBlock { Text = inExam ? "☑" : "☐", FontSize = 18, Foreground = Brush("AraGreen"),
                VerticalAlignment = VerticalAlignment.Center });
            row.Children.Add(new TextBlock { Text = "  " + c.Name, FontSize = 14, Foreground = Brush("AraBody"),
                VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap });
            row.MouseLeftButtonUp += (_, _) =>
            {
                if (inExam) App.Exams.RemoveMaterial(e.Id, c.Id); else App.Exams.AddMaterial(e.Id, c.Id);
                Rebuild();
            };
            col.Children.Add(row);
        }
        box.Child = col;
        return box;
    }

    private async void ImportInto(Exam e)
    {
        var dlg = new Microsoft.Win32.OpenFileDialog
        {
            Filter = $"{I18n.T.FilePickerCourseFiles}|*.pdf;*.pptx;*.docx;*.txt;*.png;*.jpg;*.jpeg;*.webp|{I18n.T.FilePickerAllFiles}|*.*",
            CheckFileExists = true,
        };
        if (dlg.ShowDialog() != true) return;
        // Off the UI thread so the window doesn't freeze on large files.
        Mouse.OverrideCursor = Cursors.Wait;
        Course? c;
        try { c = await App.Courses.AddFromFileAsync("", dlg.FileName); }
        finally { Mouse.OverrideCursor = null; }
        if (c != null) App.Exams.AddMaterial(e.Id, c.Id);
        Rebuild();
    }

    // ---- small building blocks -----------------------------------------------
    private FrameworkElement StatRow(string label, string value)
    {
        var g = new Grid { Margin = new Thickness(0, 4, 0, 0) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.Children.Add(new TextBlock { Text = label, FontSize = 13, Foreground = Brush("AraMuted") });
        var v = new TextBlock { Text = value, FontSize = 13, FontWeight = FontWeights.SemiBold, Foreground = Brush("AraTitle") };
        Grid.SetColumn(v, 1);
        g.Children.Add(v);
        return g;
    }

    private Border Chip(string text, Action onClick)
    {
        var b = new Border
        {
            CornerRadius = new CornerRadius(12), Cursor = Cursors.Hand, Margin = new Thickness(0, 0, 8, 0),
            Padding = new Thickness(14, 9, 14, 9), Background = Brush("AraGreenSoft"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
            Child = new TextBlock { Text = text, FontSize = 13, FontWeight = FontWeights.SemiBold, Foreground = Brush("AraGreenDeep") },
        };
        b.MouseLeftButtonUp += (_, _) => onClick();
        return b;
    }

    // Number entry + Set button (commit on Set), mirroring LockableNumberField.
    private FrameworkElement NumberField(string label, int value, string suffix, int min, int max, Action<int> onCommit)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 8, 0, 0) };
        var tb = new TextBox { Width = 120, Height = 40, FontSize = 15, VerticalContentAlignment = VerticalAlignment.Center,
            Padding = new Thickness(10, 0, 10, 0), Text = value > 0 ? value.ToString() : "", Tag = label,
            Background = Brush("AraGreenSoft"), Foreground = Brush("AraTitle"),
            CaretBrush = Brush("AraTitle"), BorderBrush = Brush("AraGlassEdgeGreen") };
        var set = new Border
        {
            CornerRadius = new CornerRadius(10), Margin = new Thickness(10, 0, 0, 0), Cursor = Cursors.Hand,
            Background = Brush("AraBrush"), Padding = new Thickness(18, 0, 18, 0), VerticalAlignment = VerticalAlignment.Center,
            Child = new TextBlock { Text = ExamI18n.SetBtn, FontSize = 15, FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)), VerticalAlignment = VerticalAlignment.Center },
        };
        set.MouseLeftButtonUp += (_, _) =>
        {
            if (int.TryParse(tb.Text.Trim(), out int n) && n >= min && n <= max) onCommit(n);
        };
        row.Children.Add(tb); row.Children.Add(set);
        return row;
    }

    private static TimeSpan? ParseTime(string s)
        => TimeSpan.TryParseExact(s?.Trim(), new[] { "hh\\:mm", "h\\:mm" }, CultureInfo.InvariantCulture, out var t) ? t : null;

    private static string LocaleTag() => I18n.Locale switch
    {
        "ro" => "ro-RO", "de" => "de-DE", "fr" => "fr-FR", "es" => "es-ES", _ => "en-US",
    };

    // ---- top-bar add ----------------------------------------------------------
    private void Back_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Back();

    private void Help_Click(object sender, RoutedEventArgs e)
    {
        var c = CoachI18n.For(I18n.Locale);
        MainWindow.Current?.StartCoach(new List<CoachStep>
        {
            new(AddBtn, c.ExamsNew),
            new(List.Children.Count > 0 ? (FrameworkElement)List.Children[0] : AddBtn, c.ExamsCard),
        });
    }

    private void Add_Click(object sender, RoutedEventArgs e)
    {
        var name = NameBox.Text?.Trim();
        if (string.IsNullOrWhiteSpace(name)) name = ExamI18n.Default;
        var day = DateBox.SelectedDate ?? DateTime.Today.AddDays(7);
        var time = ParseTime(TimeBox.Text) ?? new TimeSpan(9, 0, 0);
        App.Exams.Add(name, day.Date + time);
        NameBox.Clear();
        Rebuild();
    }

    private Brush Brush(string key) => (Brush)FindResource(key);
}
