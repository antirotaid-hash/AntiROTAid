using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Views;

/// <summary>
/// Course detail — the Windows port of AraCourseDetail: a progress/stats card, the
/// "Open the cards" action, delete (confirm-twice), the "How cards appear" material
/// settings card (re-splits the deck via the engine), and a preview of every card.
/// </summary>
public partial class CourseDetailPage : UserControl
{
    private readonly Course _course;
    private bool _confirmDelete;

    public CourseDetailPage(Course course)
    {
        InitializeComponent();
        _course = course;
        Title.Text = course.Name;
        Build();
    }

    private void Build()
    {
        var d = DetailI18n.For(I18n.Locale);
        var cs = CoursesI18n.For(I18n.Locale);
        Host.Children.Clear();

        // ---- progress / stats card ----
        var statsCard = Glass(20);
        var col = new StackPanel { Margin = new Thickness(18) };
        col.Children.Add(new TextBlock { Text = _course.Name, FontSize = 20, FontWeight = FontWeights.Bold,
            Foreground = B("AraTitle"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 10) });
        int total = _course.CardCount;
        int pct = _course.LearnedPct;
        int passes = total > 0 && _course.KnownCount == total ? 1 : 0;
        col.Children.Add(StatRow(d.CardsLabel, total > 0 ? total.ToString() : "—"));
        col.Children.Add(StatRow(d.DetailProgress, $"{pct}%"));
        col.Children.Add(StatRow(d.DetailFullPasses, $"{passes}×"));
        col.Children.Add(StatRow(d.LearnedPace, d.Learning));
        col.Children.Add(ProgressBar(pct));
        statsCard.Child = col;
        Host.Children.Add(statsCard);

        // ---- Open the cards ----
        var open = new Border
        {
            Height = 54, CornerRadius = new CornerRadius(16), Cursor = Cursors.Hand,
            Margin = new Thickness(0, 14, 0, 0), Background = B("AraBrush"),
            Child = new TextBlock { Text = cs.OpenCards, FontSize = 16, FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)),
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center },
        };
        open.MouseLeftButtonUp += (_, _) => MainWindow.Current?.Navigate("study", _course);
        Host.Children.Add(open);

        // ---- Delete course (confirm twice) ----
        var del = new Border
        {
            Height = 48, CornerRadius = new CornerRadius(14), Cursor = Cursors.Hand,
            Margin = new Thickness(0, 12, 0, 0), BorderThickness = new Thickness(1), BorderBrush = B("AraDanger"),
            Child = new TextBlock { Text = _confirmDelete ? d.ConfirmAgain : d.DeleteCourse,
                FontSize = 14, FontWeight = FontWeights.Bold, Foreground = B("AraDanger"),
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center },
        };
        del.MouseLeftButtonUp += (_, _) => { _confirmDelete = true; Build(); };
        Host.Children.Add(del);
        if (_confirmDelete)
        {
            var confirm = new Border
            {
                Height = 44, Cursor = Cursors.Hand, Margin = new Thickness(0, 8, 0, 0),
                Background = Brushes.Transparent,
                Child = new TextBlock { Text = d.ConfirmDeletePermanent, FontSize = 14, FontWeight = FontWeights.Bold,
                    Foreground = B("AraDanger"), HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center },
            };
            confirm.MouseLeftButtonUp += (_, _) =>
            {
                App.Courses.Delete(_course.Id);
                MainWindow.Current?.Back();
            };
            Host.Children.Add(confirm);
        }

        // ---- "How cards appear" material settings ----
        var settings = MaterialSettingsCard();
        if (settings is not null) Host.Children.Add(settings);

        // ---- cards header + preview rows ----
        Host.Children.Add(new TextBlock { Text = string.Format(d.CourseCardsFmt, _course.CardCount),
            FontSize = 16, FontWeight = FontWeights.Bold, Foreground = B("AraTitle"),
            Margin = new Thickness(2, 16, 0, 0) });
        foreach (var card in _course.Cards) Host.Children.Add(CardRow(card, d));
    }

    // The engine's split-mode options for this material; null if unavailable or if
    // there is nothing to configure.
    private Border? MaterialSettingsCard()
    {
        var d = DetailI18n.For(I18n.Locale);
        var data = App.Courses.MaterialOptions(_course);
        if (data is not { } opts || !opts.TryGetProperty("options", out var list) || list.ValueKind != JsonValueKind.Array)
            return null;

        var card = Glass(20);
        var col = new StackPanel { Margin = new Thickness(18) };
        col.Children.Add(new TextBlock { Text = d.HowCardsAppear, FontSize = 16, FontWeight = FontWeights.Bold,
            Foreground = B("AraTitle"), Margin = new Thickness(0, 0, 0, 6) });
        col.Children.Add(new TextBlock { Text = d.CoachModesBody, FontSize = 12, LineHeight = 16,
            Foreground = B("AraMuted"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 12) });

        foreach (var o in list.EnumerateArray())
        {
            string key = o.TryGetProperty("key", out var k) ? k.GetString() ?? "" : "";
            var choices = o.TryGetProperty("choices", out var ch) && ch.ValueKind == JsonValueKind.Array ? ch : default;
            if (choices.ValueKind != JsonValueKind.Array) continue;
            string def = o.TryGetProperty("default", out var dv) ? dv.GetString() ?? "" : "";
            // Only "appearance" is user-facing; "" falls back to the type default.
            string selected = (key == "appearance" ? _course.Appearance : _course.CardSize);
            if (string.IsNullOrEmpty(selected)) selected = def;

            col.Children.Add(new TextBlock { Text = d.OptAppearance, FontSize = 13, FontWeight = FontWeights.SemiBold,
                Foreground = B("AraBody"), Margin = new Thickness(0, 4, 0, 6) });

            int count = choices.GetArrayLength();
            if (count >= 2)
            {
                var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };
                foreach (var cEl in choices.EnumerateArray())
                {
                    string val = cEl.GetString() ?? "";
                    row.Children.Add(Chip(d.ChoiceLabel(val), selected == val, () =>
                    {
                        Mouse.OverrideCursor = Cursors.Wait;
                        try
                        {
                            if (key == "appearance") App.Courses.ApplyMaterialSettings(_course, val, _course.CardSize);
                            else App.Courses.ApplyMaterialSettings(_course, _course.Appearance, val);
                        }
                        finally { Mouse.OverrideCursor = null; }
                        Build();
                    }));
                }
                col.Children.Add(row);
            }
            string desc = d.ChoiceDesc(selected);
            if (!string.IsNullOrEmpty(desc))
                col.Children.Add(new TextBlock { Text = desc, FontSize = 11, LineHeight = 15,
                    Foreground = B("AraMuted"), TextWrapping = TextWrapping.Wrap });
        }
        card.Child = col;
        card.Margin = new Thickness(0, 14, 0, 0);
        return card;
    }

    private Border CardRow(StudyCard card, DetailText d)
    {
        var b = new Border
        {
            CornerRadius = new CornerRadius(16), Padding = new Thickness(14),
            Margin = new Thickness(0, 12, 0, 0), Background = B("AraGlassGradient"),
            BorderThickness = new Thickness(1), BorderBrush = B("AraGlassEdgeGreen"),
        };
        var sp = new StackPanel();
        string label = (!string.IsNullOrWhiteSpace(card.SourceLabel) ? card.SourceLabel
            : !string.IsNullOrWhiteSpace(card.Title) ? card.Title : d.CardFallback).ToUpperInvariant();
        sp.Children.Add(new TextBlock { Text = label, FontSize = 11, FontWeight = FontWeights.Bold,
            Foreground = B("AraGreenDeep") });
        if (!string.IsNullOrWhiteSpace(card.Title))
            sp.Children.Add(new TextBlock { Text = card.Title, FontSize = 15, FontWeight = FontWeights.SemiBold,
                Foreground = B("AraTitle"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 6, 0, 0) });
        string previewBody = BodyWithoutLeadingTitle(card.Body, card.Title);
        string preview = card.IsImage ? d.VisualSlideTag
            : previewBody.Length > 160 ? previewBody[..160] + "…" : previewBody;
        if (!string.IsNullOrWhiteSpace(preview))
            sp.Children.Add(new TextBlock { Text = preview, FontSize = 13, LineHeight = 18,
                Foreground = B("AraBody"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 6, 0, 0) });
        b.Child = sp;
        return b;
    }

    // Drop the title if the body repeats it as its first line (mirrors the phone).
    private static string BodyWithoutLeadingTitle(string body, string title)
    {
        body = (body ?? "").Trim();
        title = (title ?? "").Trim();
        if (title.Length > 0 && body.StartsWith(title, StringComparison.OrdinalIgnoreCase))
            return body[title.Length..].TrimStart('\n', '\r', ' ', '\t', '.', '—', '-', ':');
        return body;
    }

    // ---- small builders ----
    private Border Glass(double radius) => new()
    {
        CornerRadius = new CornerRadius(radius), Background = B("AraGlassGradient"),
        BorderThickness = new Thickness(1), BorderBrush = B("AraGlassEdgeGreen"),
    };

    private Grid StatRow(string label, string value)
    {
        var g = new Grid { Margin = new Thickness(0, 0, 0, 6) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.Children.Add(new TextBlock { Text = label, FontSize = 13, Foreground = B("AraMuted") });
        var v = new TextBlock { Text = value, FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = B("AraTitle") };
        Grid.SetColumn(v, 1);
        g.Children.Add(v);
        return g;
    }

    private Grid ProgressBar(int pct)
    {
        var g = new Grid { Height = 6, Margin = new Thickness(0, 8, 0, 0) };
        g.Children.Add(new Border { CornerRadius = new CornerRadius(3), Background = B("AraGreenSoft") });
        var fill = new Border { CornerRadius = new CornerRadius(3), Background = B("AraGreen"),
            HorizontalAlignment = HorizontalAlignment.Left, Width = 0 };
        g.Children.Add(fill);
        g.Loaded += (_, _) => fill.Width = Math.Max(6, g.ActualWidth * pct / 100.0);
        return g;
    }

    private Border Chip(string text, bool selected, Action onClick)
    {
        var b = new Border
        {
            CornerRadius = new CornerRadius(12), Padding = new Thickness(14, 9, 14, 9),
            Margin = new Thickness(0, 0, 8, 0), Cursor = Cursors.Hand,
            Background = selected ? B("AraGreen") : B("AraGreenSoft"),
            Child = new TextBlock { Text = text, FontSize = 13,
                FontWeight = selected ? FontWeights.Bold : FontWeights.Medium,
                Foreground = selected ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : B("AraBody") },
        };
        b.MouseLeftButtonUp += (_, _) => onClick();
        return b;
    }

    private void Help_Click(object sender, RoutedEventArgs e)
    {
        var c = CoachI18n.For(I18n.Locale);
        MainWindow.Current?.StartCoach(new List<CoachStep>
        {
            new(Host.Children.Count > 0 ? (FrameworkElement)Host.Children[0] : Host, c.DetailProgress),
            new(Host.Children.Count > 1 ? (FrameworkElement)Host.Children[1] : Host, c.DetailStudy),
            new(Host, c.DetailSettings),
        });
    }

    private void Back_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Back();
    private Brush B(string key) => (Brush)FindResource(key);
}
