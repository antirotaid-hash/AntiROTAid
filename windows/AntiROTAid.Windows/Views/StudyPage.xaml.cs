using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using AntiROTAid.Windows.Engine;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Themes;

namespace AntiROTAid.Windows.Views;

public partial class StudyPage : UserControl
{
    private readonly Course? _course;
    private readonly Action? _onExit;            // set when hosted in the floating overlay
    private readonly List<Border> _cardBorders = new();
    private static readonly double[] Scales = { 0.9, 1.0, 1.15, 1.3 };
    private int _scaleIdx = 1;
    private double Scale => Scales[_scaleIdx];
    private int _resumeTarget;
    // True once the initial resume-scroll has settled: from here a scroll persists the
    // position. Kept deliberately simple/unconditional so it is never "off" on card 0.
    private bool _ready;

    private static readonly Regex NumBullet = new(@"^\s*(\d+)[\.\)]\s+(.*)$");

    public StudyPage(Course? course, Action? onExit = null)
    {
        InitializeComponent();
        _course = course ?? (App.Courses.Courses.Count > 0 ? App.Courses.Courses[0] : null);
        _onExit = onExit;

        Tagline.Text = Home.For(I18n.Locale).Tagline;
        LabelToolbar();
        // In the floating overlay the coach host lives on the (hidden) main window, so
        // hide the "?" there — everything else (the whole feed) is identical.
        if (_onExit != null) HelpBtn.Visibility = Visibility.Collapsed;

        if (_course is null || _course.CardCount == 0)
        {
            Scroller.Visibility = Visibility.Collapsed;
            Empty.Visibility = Visibility.Visible;
            Empty.Text = I18n.T.PickCourse;
            RemoveBtn.IsEnabled = false;
            return;
        }

        // Studying a course makes it the active overlay deck too (same as Android).
        App.Controller?.SetDeck(_course.Cards);
        App.Controller?.SetActiveCourse(_course);
        // Open where the user last left off (resume), like the phone overlay/feed.
        // ResumeIndex reconciles against the current deck (fingerprint/CardId) and clamps.
        _resumeTarget = App.Courses.ResumeIndex(_course);
        if (_resumeTarget >= _course.CardCount) _resumeTarget = 0;
        BuildFeed();
        UpdateCounter(_resumeTarget);
        Loaded += (_, _) =>
        {
            if (_resumeTarget > 0) ScrollToCard(_resumeTarget);
            LoadVisiblePdf(_resumeTarget);
            // Become "ready" AFTER the programmatic resume-scroll has settled (deferred to a
            // later dispatcher turn), so that scroll's own ScrollChanged doesn't overwrite the
            // saved spot. This runs for every course, INCLUDING one opened at card 0.
            Dispatcher.BeginInvoke(new Action(() => _ready = true),
                System.Windows.Threading.DispatcherPriority.Background);
        };
    }

    private void LabelToolbar()
    {
        var s = StudyI18n.For(I18n.Locale);
        TextSizeBtn.Content = PillContent("Aa", s.TextSize);
        ThemeBtn.Content = PillContent("◌", s.Theme);
        RemoveBtn.Content = PillContent("✕", s.RemoveCard);
    }

    private object PillContent(string glyph, string label)
    {
        var sp = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };
        sp.Children.Add(new TextBlock { Text = glyph, FontSize = 20, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraGreen"), VerticalAlignment = VerticalAlignment.Center });
        sp.Children.Add(new TextBlock { Text = label, FontSize = 15, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraTitle"), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(9, 0, 0, 0) });
        return sp;
    }

    // ---- feed ----
    private void BuildFeed()
    {
        Feed.Children.Clear();
        _cardBorders.Clear();
        _pendingPdf.Clear();
        if (_course is null) return;
        for (int i = 0; i < _course.Cards.Count; i++)
        {
            var b = CardControl(_course.Cards[i], i, _course.Cards.Count);
            _cardBorders.Add(b);
            Feed.Children.Add(b);
        }
    }

    private Border CardControl(StudyCard card, int index, int total)
    {
        var s = StudyI18n.For(I18n.Locale);
        var outer = new Border
        {
            CornerRadius = new CornerRadius(22), Padding = new Thickness(28, 26, 28, 26),
            Margin = new Thickness(0, 0, 0, 18),
            Background = Brush("AraGlassGradient"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGreen"),
        };
        var col = new StackPanel();

        // Header: ● COURSE chip + i / total
        var head = new Grid();
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var chip = new Border
        {
            CornerRadius = new CornerRadius(13), Padding = new Thickness(12, 5, 14, 6),
            Background = Brush("AraGreenSoft"), HorizontalAlignment = HorizontalAlignment.Left,
        };
        var chipRow = new StackPanel { Orientation = Orientation.Horizontal };
        chipRow.Children.Add(new Ellipse { Width = 8, Height = 8, Fill = Brush("AraGreen"),
            VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) });
        chipRow.Children.Add(new TextBlock { Text = (_course?.Name ?? "").ToUpperInvariant(),
            FontSize = 13, FontWeight = FontWeights.Bold, Foreground = Brush("AraGreen") });
        chip.Child = chipRow;
        Grid.SetColumn(chip, 0);
        head.Children.Add(chip);
        var idx = new TextBlock { Text = $"{index + 1} / {total}", FontSize = 14, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraMuted"), VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(idx, 2);
        head.Children.Add(idx);
        col.Children.Add(head);

        // Title + gold underline (only when the card actually has a title)
        if (!string.IsNullOrWhiteSpace(card.Title))
        {
            col.Children.Add(new TextBlock { Text = card.Title, FontSize = 28 * Scale, FontWeight = FontWeights.Bold,
                TextWrapping = TextWrapping.Wrap, Foreground = Brush("AraTitle"), Margin = new Thickness(0, 16, 0, 0) });
            col.Children.Add(new Border { Height = 4, Width = 120, CornerRadius = new CornerRadius(2),
                HorizontalAlignment = HorizontalAlignment.Left, Background = Brush("AraBrush"), Margin = new Thickness(0, 12, 0, 0) });
        }

        // Body. Image/slide cards (file imports) render the slide image or PDF page,
        // exactly like the phone's VisualCardPanel; text cards render formatted prose.
        if (card.IsImage && card.HasImage) RenderVisual(col, card, index);
        RenderBody(col, BodyWithoutTitle(card.Body, card.Title));

        // Scroll hint (not on last card)
        if (index < total - 1)
            col.Children.Add(new TextBlock { Text = s.ScrollNext + "  ↑", FontSize = 13, FontWeight = FontWeights.SemiBold,
                Foreground = Brush("AraGreen"), HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 28, 0, 0) });

        // Actions: Save spot & exit | Next card / Done
        var actions = new Grid { Margin = new Thickness(0, 16, 0, 0) };
        actions.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        actions.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(12) });
        actions.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var save = ActionBtn(s.SaveExit, primary: false);
        save.MouseLeftButtonUp += (_, _) => Exit();
        Grid.SetColumn(save, 0);
        actions.Children.Add(save);
        bool last = index == total - 1;
        var next = ActionBtn(last ? s.Done : s.NextCard, primary: true);
        next.MouseLeftButtonUp += (_, _) =>
        {
            if (last) { Finish(); return; }
            // Persist the destination explicitly, so the position is saved even if the scroll
            // event doesn't fire (robust independent of ScrollChanged timing).
            if (_course != null) App.Courses.SavePosition(_course, index + 1);
            ScrollToCard(index + 1);
        };
        Grid.SetColumn(next, 2);
        actions.Children.Add(next);
        col.Children.Add(actions);

        outer.Child = col;
        return outer;
    }

    private void RenderBody(StackPanel col, string body)
    {
        if (string.IsNullOrWhiteSpace(body)) return;
        foreach (var raw in body.Replace("\r\n", "\n").Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0) continue;

            // Bullet: "• text"
            if (line.StartsWith("•"))
            {
                col.Children.Add(Bullet(null, line.TrimStart('•', ' ')));
                continue;
            }
            // Numbered: "1. text"
            var m = NumBullet.Match(line);
            if (m.Success)
            {
                col.Children.Add(Bullet(m.Groups[1].Value + ".", m.Groups[2].Value.Trim()));
                continue;
            }
            // Section header: a short line ending with ":"
            if (line.EndsWith(":") && line.Length <= 48)
            {
                col.Children.Add(SectionHeader(line.TrimEnd(':', ' ')));
                continue;
            }
            // Plain paragraph line
            col.Children.Add(new TextBlock { Text = line, FontSize = 17 * Scale, TextWrapping = TextWrapping.Wrap,
                Foreground = Brush("AraBody"), Margin = new Thickness(0, 12, 0, 0), LineHeight = 26 * Scale });
        }
    }

    /// <summary>The slide title is already the card heading; drop a body's first line if
    /// it just repeats it, so the card isn't redundant (mirrors bodyWithoutLeadingTitle).</summary>
    private static string BodyWithoutTitle(string body, string title)
    {
        if (string.IsNullOrWhiteSpace(title)) return body;
        var lines = body.Replace("\r", "").Split('\n');
        for (int i = 0; i < lines.Length; i++)
        {
            if (lines[i].Trim().Length == 0) continue;
            if (string.Equals(lines[i].Trim(), title.Trim(), StringComparison.OrdinalIgnoreCase))
            {
                var rest = new List<string>(lines);
                rest.RemoveAt(i);
                return string.Join("\n", rest).Trim();
            }
            break;
        }
        return body;
    }

    private sealed class PendingPdf { public int Index; public Image Img = null!; public StudyCard Card = null!; public bool Loaded; }
    private readonly List<PendingPdf> _pendingPdf = new();

    /// <summary>Render an imported slide/figure image or an AS-IS PDF page. Embedded images
    /// (base64/file) load immediately; PDF pages are rendered lazily as they scroll into
    /// view, so opening a 100-page PDF deck doesn't freeze — the phone renders per page too.</summary>
    private void RenderVisual(StackPanel col, StudyCard card, int index)
    {
        var img = new Image { Stretch = Stretch.Uniform, StretchDirection = StretchDirection.DownOnly };
        var frame = new Border
        {
            CornerRadius = new CornerRadius(16), Margin = new Thickness(0, 18, 0, 4),
            Background = new SolidColorBrush(Colors.White), ClipToBounds = true,
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
            Child = img,
        };

        if (card.ImageBase64.Length > 0) img.Source = SlideImage.FromBase64(card.ImageBase64);
        else if (card.ImagePath.Length > 0) img.Source = SlideImage.FromFile(card.ImagePath);
        else if (card.PdfDocPath.Length > 0)
        {
            // Placeholder height keeps the layout stable until the page is rendered.
            img.MinHeight = 360;
            _pendingPdf.Add(new PendingPdf { Index = index, Img = img, Card = card });
        }
        col.Children.Add(frame);
    }

    /// <summary>Render the PDF pages for cards near <paramref name="current"/> (± a couple),
    /// on a background thread, then set the image on the UI thread.</summary>
    private void LoadVisiblePdf(int current)
    {
        if (_pendingPdf.Count == 0) return;
        foreach (var p in _pendingPdf)
        {
            if (p.Loaded || p.Index < current - 1 || p.Index > current + 2) continue;
            p.Loaded = true;
            var card = p.Card; var img = p.Img;
            System.Threading.Tasks.Task.Run(() =>
            {
                var bmp = SlideImage.FromPdfPage(card.PdfDocPath, card.PdfPage, 1000);
                if (bmp is null) return;
                Dispatcher.BeginInvoke(() => { img.MinHeight = 0; img.Source = bmp; });
            });
        }
    }

    private FrameworkElement SectionHeader(string text)
    {
        var sp = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 20, 0, 4) };
        sp.Children.Add(new Border { Width = 5, Height = 22 * Scale, CornerRadius = new CornerRadius(2),
            Background = Brush("AraBrush"), Margin = new Thickness(0, 0, 12, 0), VerticalAlignment = VerticalAlignment.Center });
        sp.Children.Add(new TextBlock { Text = text, FontSize = 19 * Scale, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraGreen"), VerticalAlignment = VerticalAlignment.Center });
        return sp;
    }

    private FrameworkElement Bullet(string? marker, string text)
    {
        var g = new Grid { Margin = new Thickness(0, 12, 0, 0) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        FrameworkElement mk;
        if (marker is null)
            mk = new Ellipse { Width = 8, Height = 8, Fill = Brush("AraGreen"),
                VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(2, 9 * Scale, 0, 0) };
        else
            mk = new TextBlock { Text = marker, FontSize = 16 * Scale, FontWeight = FontWeights.Bold,
                Foreground = Brush("AraGreen"), VerticalAlignment = VerticalAlignment.Top };
        Grid.SetColumn(mk, 0);
        g.Children.Add(mk);
        var tb = new TextBlock { Text = text, FontSize = 17 * Scale, TextWrapping = TextWrapping.Wrap,
            Foreground = Brush("AraBody"), LineHeight = 26 * Scale };
        Grid.SetColumn(tb, 1);
        g.Children.Add(tb);
        return g;
    }

    private Border ActionBtn(string text, bool primary)
    {
        var b = new Border
        {
            CornerRadius = new CornerRadius(16), Cursor = System.Windows.Input.Cursors.Hand,
            Padding = new Thickness(0, 15, 0, 15),
            Background = primary ? Brush("AraBrush") : Brush("AraGlassGradient"),
            BorderThickness = new Thickness(1),
            BorderBrush = primary ? Brush("AraGreen") : Brush("AraGlassEdgeGreen"),
            Child = new TextBlock
            {
                Text = text, FontSize = 15, FontWeight = FontWeights.Bold,
                HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center,
                Foreground = primary ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : Brush("AraTitle"),
            },
        };
        return b;
    }

    // ---- scroll tracking ----
    private void ScrollToCard(int i)
    {
        if (i < 0 || i >= _cardBorders.Count) return;
        var pt = _cardBorders[i].TransformToVisual(Feed).Transform(new Point(0, 0));
        Scroller.ScrollToVerticalOffset(pt.Y);
    }

    private void Scroller_ScrollChanged(object sender, ScrollChangedEventArgs e)
    {
        if (_cardBorders.Count == 0) return;
        // Which card's top is at/above the viewport top → current card.
        int current = 0;
        for (int i = 0; i < _cardBorders.Count; i++)
        {
            var y = _cardBorders[i].TransformToVisual(Feed).Transform(new Point(0, 0)).Y;
            if (y <= e.VerticalOffset + 40) current = i;
        }
        UpdateCounter(current);
        LoadVisiblePdf(current);
        // Remember where we are so the overlay + feed resume here (after the initial
        // layout has settled, so we don't clobber the saved spot with 0).
        if (_ready && _course != null) App.Courses.SavePosition(_course, current);
        // Progress fill across the whole feed.
        double frac = e.ExtentHeight <= e.ViewportHeight ? 1.0
            : e.VerticalOffset / (e.ExtentHeight - e.ViewportHeight);
        ProgressFill.Width = Math.Max(6, ProgressTrack.ActualWidth * Math.Clamp(frac, 0, 1));
    }

    private void UpdateCounter(int current)
    {
        if (_course is null) return;
        Counter.Text = $"{current + 1}/{_course.CardCount}";
    }

    // ---- toolbar ----
    private void TextSize_Click(object sender, RoutedEventArgs e)
    {
        _scaleIdx = (_scaleIdx + 1) % Scales.Length;
        BuildFeed();
    }

    private void Theme_Click(object sender, RoutedEventArgs e)
    {
        var next = (AraThemeStyle)(((int)AraTheme.Active + 1) % Enum.GetValues<AraThemeStyle>().Length);
        AraTheme.Apply(next);
        var s = Settings.Load(); s.Theme = next; s.Save();
    }

    private void Remove_Click(object sender, RoutedEventArgs e)
    {
        if (_course is null || _course.Cards.Count <= 1) return;
        int current = CurrentCard();
        // DeleteCard persists the mutated deck + remaps progress/resume/fingerprint centrally,
        // so the overlay and a later re-open stay consistent (not just this in-memory list).
        App.Courses.DeleteCard(_course, current);
        App.Controller?.SetDeck(_course.Cards);
        BuildFeed();
        int show = Math.Min(current, _course.Cards.Count - 1);
        UpdateCounter(show);
        if (show > 0) Dispatcher.BeginInvoke(() => ScrollToCard(show));
    }

    private int CurrentCard()
    {
        int current = 0;
        for (int i = 0; i < _cardBorders.Count; i++)
        {
            var y = _cardBorders[i].TransformToVisual(Feed).Transform(new Point(0, 0)).Y;
            if (y <= Scroller.VerticalOffset + 40) current = i;
        }
        return current;
    }

    // In the overlay this closes the floating window; in the app it pops the page.
    // Persist the current spot first, so "Save spot & exit" / ← / overlay-close all resume
    // exactly where the user left off, independent of scroll-event timing.
    private void Exit()
    {
        // Always persist the current spot on the way out (not gated by any flag), so
        // "Save spot & exit" / ← / overlay-close resume exactly where the user is.
        if (_course != null) App.Courses.SavePosition(_course, CurrentCard());
        if (_onExit != null) _onExit(); else MainWindow.Current?.Back();
    }
    private void Back_Click(object sender, RoutedEventArgs e) => Exit();

    /// <summary>Final "Done" on the last card: mark this course completed and advance to the
    /// next eligible course (deterministic, circular) exactly like the phone. In the overlay
    /// this just closes — the next appearance opens the now-active next course. In the app we
    /// replace this feed with the next course's feed (or restart when it's the only course).</summary>
    private void Finish()
    {
        if (_course is null) { Exit(); return; }
        var next = App.Courses.CompleteAndAdvance(_course);
        App.Controller?.SetActiveCourse(next);
        if (_onExit != null) { _onExit(); return; }        // overlay closes; next appearance = next course
        MainWindow.Current?.Back();                        // pop this feed…
        MainWindow.Current?.Navigate(new StudyPage(next)); // …and open the next course's feed
    }

    private void Help_Click(object sender, RoutedEventArgs e)
    {
        var c = CoachI18n.For(I18n.Locale);
        MainWindow.Current?.StartCoach(new List<CoachStep>
        {
            new(TextSizeBtn, c.FeedFont),
            new(ThemeBtn, c.FeedTheme),
            new(RemoveBtn, c.FeedDelete),
            new(ProgressTrack, c.FeedProgress),
            new(null, c.FeedScroll),
            new(null, c.FeedCta),
        });
    }

    private Brush Brush(string key) => (Brush)FindResource(key);
}
