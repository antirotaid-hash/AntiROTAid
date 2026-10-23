using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;
using AntiROTAid.Windows.Themes;

namespace AntiROTAid.Windows.Views;

/// <summary>
/// Settings, ported to match the phone app's AraSettings exactly: language chips,
/// study plan (weekly phone hours + multi-exam mode), auto-delete, text size,
/// card preview, theme tiles, and privacy/info lines. No "minutes between cards".
/// </summary>
public partial class SettingsPage : UserControl
{
    private static readonly int[] Quick = { 5, 10, 15, 20, 25, 30, 40 };
    private static readonly (double title, double body)[] FontSizes =
        { (20, 14), (24, 16), (28, 18), (32, 20) };

    private Settings _s = Settings.Load();
    // Coach-mark anchors (language / study-plan / look sections).
    private FrameworkElement? _coachLang, _coachPlan, _coachLook;

    public SettingsPage()
    {
        InitializeComponent();
        Title.Text = PhoneI18n.For(I18n.Locale).SettingsTitle;
        Build();
    }

    private void Build()
    {
        var t = PhoneI18n.For(I18n.Locale);
        Body.Children.Clear();

        // Language
        Body.Children.Add(SectionLabel(t.Language));
        Body.Children.Add(_coachLang = Glass(col =>
        {
            col.Children.Add(Muted(t.LanguageSub, 11));
            var row = ChipRow();
            foreach (var (code, name) in I18n.Languages)
                row.Children.Add(Chip($"{Badge(code)}  {name}", code == I18n.Locale, () =>
                {
                    _s.Language = code; _s.Save(); I18n.Set(code);
                    MainWindow.Current?.ReloadLanguage();
                }));
            col.Children.Add(Scroll(row));
        }));

        // Study plan
        Body.Children.Add(SectionLabel(t.PlanLabel));
        Body.Children.Add(_coachPlan = Glass(col =>
        {
            col.Children.Add(Semi(t.WeeklyPhoneTime, 13));
            col.Children.Add(Muted(t.WeeklyPhoneSub, 11));
            // hours field + quick chips
            var field = new Border
            {
                CornerRadius = new CornerRadius(12), Margin = new Thickness(0, 4, 0, 0),
                Background = Brush("AraGreenSoft"), BorderThickness = new Thickness(1),
                BorderBrush = Brush("AraGlassEdgeGreen"), Padding = new Thickness(12, 8, 12, 8),
            };
            var fr = new Grid();
            fr.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            fr.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var hours = new TextBox
            {
                Text = _s.WeeklyHours.ToString(), FontSize = 16, FontWeight = FontWeights.Bold,
                Background = Brushes.Transparent, Foreground = Brush("AraTitle"), BorderThickness = new Thickness(0),
                CaretBrush = Brush("AraTitle"), VerticalContentAlignment = VerticalAlignment.Center,
            };
            hours.LostFocus += (_, _) => CommitHours(hours);
            hours.KeyDown += (_, e) => { if (e.Key == Key.Enter) CommitHours(hours); };
            Grid.SetColumn(hours, 0); fr.Children.Add(hours);
            var suffix = new TextBlock { Text = t.HoursPerWeek, FontSize = 12, Foreground = Brush("AraMuted"),
                VerticalAlignment = VerticalAlignment.Center };
            Grid.SetColumn(suffix, 1); fr.Children.Add(suffix);
            field.Child = fr;
            col.Children.Add(field);

            var qrow = ChipRow();
            foreach (var h in Quick)
                qrow.Children.Add(Chip($"{h}h", _s.WeeklyHours == h, () => { _s.WeeklyHours = h; _s.Save(); Build(); }));
            col.Children.Add(Scroll(qrow));

            col.Children.Add(Semi(t.MoreExams, 13, top: 6));
            var mrow = new StackPanel { Orientation = Orientation.Horizontal };
            mrow.Children.Add(Chip(t.Combine, _s.StudyMode == "combine", () => { _s.StudyMode = "combine"; _s.Save(); Build(); }));
            mrow.Children.Add(new FrameworkElement { Width = 8 });
            mrow.Children.Add(Chip(t.OneAtTime, _s.StudyMode == "sequential", () => { _s.StudyMode = "sequential"; _s.Save(); Build(); }));
            col.Children.Add(mrow);
            col.Children.Add(Muted(_s.StudyMode == "combine" ? t.CombineDesc : t.OneAtTimeDesc, 11));
        }));

        // Auto-delete
        Body.Children.Add(SectionLabel(t.AutoDeleteSection));
        Body.Children.Add(Glass(col =>
        {
            col.Children.Add(Semi(t.AutoDeleteTitle, 13));
            col.Children.Add(Muted(t.AutoDeleteSub, 11));
            var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 0) };
            row.Children.Add(Chip(t.OnWord, _s.AutoDelete, () =>
            {
                if (!_s.AutoDelete && Confirm(t.AutoDeleteTitle, t.AutoDeleteDialogBody, t.AutoDeleteDeleteNow, t.Cancel))
                {
                    _s.AutoDelete = true; _s.Save();
                    // "Delete now": actually run the purge the dialog just promised.
                    Services.AutoDeleteService.Run(App.Courses, App.Exams);
                    Build();
                }
            }));
            row.Children.Add(new FrameworkElement { Width = 8 });
            row.Children.Add(Chip(t.OffWord, !_s.AutoDelete, () => { if (_s.AutoDelete) { _s.AutoDelete = false; _s.Save(); Build(); } }));
            col.Children.Add(row);
        }));

        // Text size
        Body.Children.Add(SectionLabel(t.TextSize));
        var fonts = new Grid { Margin = new Thickness(0, 0, 0, 0) };
        for (int i = 0; i < 4; i++) fonts.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var labels = new[] { t.FontSmall, t.FontMedium, t.FontLarge, t.FontXLarge };
        for (int i = 0; i < 4; i++)
        {
            int idx = i;
            var tile = FontTile(labels[i], 10 + i * 4, idx == _s.Font, () => { _s.Font = idx; _s.Save(); Build(); });
            tile.Margin = new Thickness(i == 0 ? 0 : 4, 0, i == 3 ? 0 : 4, 0);
            Grid.SetColumn(tile, i); fonts.Children.Add(tile);
        }
        Body.Children.Add(fonts);
        _coachLook = fonts;

        // Card preview
        Body.Children.Add(SectionLabel(t.CardPreview));
        Body.Children.Add(CardPreview(t));

        // Theme
        Body.Children.Add(SectionLabel(t.AppTheme));
        var trow = new StackPanel { Orientation = Orientation.Horizontal };
        foreach (AraThemeStyle style in Enum.GetValues<AraThemeStyle>())
            trow.Children.Add(ThemeTile(style, t));
        Body.Children.Add(Scroll(trow));

        // Privacy / info
        Body.Children.Add(SectionLabel(t.PrivacyInfoTitle));
        Body.Children.Add(Glass(col =>
        {
            col.Children.Add(InfoLine("◆", t.InfoFilesT, t.InfoFilesB));
            col.Children.Add(InfoLine("▣", t.InfoStorageT, t.InfoStorageB));
            col.Children.Add(InfoLine("◍", t.InfoNoNetT, t.InfoNoNetB));
            col.Children.Add(InfoLine("▤", t.InfoStudyT, t.InfoStudyB));
            col.Children.Add(Muted(t.AppVersionLocal, 11));
        }));

        Body.Children.Add(new FrameworkElement { Height = 16 });
    }

    private void CommitHours(TextBox box)
    {
        if (int.TryParse(box.Text, out var h) && h is >= 1 and <= 168) { _s.WeeklyHours = h; _s.Save(); Build(); }
        else box.Text = _s.WeeklyHours.ToString();
    }

    // ---- building blocks (mirror the phone composables) ----
    private TextBlock SectionLabel(string t) => new()
    { Text = t, FontSize = 15, FontWeight = FontWeights.Bold, Foreground = Brush("AraTitle"), Margin = new Thickness(0, 16, 0, 8) };

    private TextBlock Muted(string t, double sz, double top = 0) => new()
    { Text = t, FontSize = sz, Foreground = Brush("AraMuted"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, top, 0, 0), LineHeight = sz * 1.4 };

    private TextBlock Semi(string t, double sz, double top = 0) => new()
    { Text = t, FontSize = sz, FontWeight = FontWeights.SemiBold, Foreground = Brush("AraBody"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, top, 0, 0) };

    private Border Glass(Action<StackPanel> fill)
    {
        var col = new StackPanel { Margin = new Thickness(18) };
        fill(col);
        // Consistent 8px vertical rhythm between the items in the box.
        for (int i = 1; i < col.Children.Count; i++)
            if (col.Children[i] is FrameworkElement fe)
                fe.Margin = new Thickness(fe.Margin.Left, Math.Max(fe.Margin.Top, 8), fe.Margin.Right, fe.Margin.Bottom);
        return new Border
        {
            CornerRadius = new CornerRadius(18), Background = Brush("AraGlassGradient"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
            Margin = new Thickness(0, 0, 0, 0), Child = col,
        };
    }

    private StackPanel ChipRow() => new() { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 0) };
    private ScrollViewer Scroll(UIElement child) => new()
    { HorizontalScrollBarVisibility = ScrollBarVisibility.Auto, VerticalScrollBarVisibility = ScrollBarVisibility.Disabled, Content = child };

    private Border Chip(string text, bool sel, Action onClick)
    {
        var b = new Border
        {
            CornerRadius = new CornerRadius(14), Padding = new Thickness(14, 7, 14, 8), Margin = new Thickness(0, 0, 8, 0),
            Cursor = Cursors.Hand, BorderThickness = new Thickness(1),
            Background = sel ? Brush("AraBrush") : Brush("AraGreenSoft"),
            BorderBrush = sel ? Brush("AraGreen") : Brush("AraGlassEdgeGreen"),
            Child = new TextBlock
            {
                Text = text, FontSize = 13, FontWeight = sel ? FontWeights.Bold : FontWeights.Medium,
                Foreground = sel ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : Brush("AraTitle"),
            },
        };
        b.MouseLeftButtonUp += (_, _) => onClick();
        return b;
    }

    private Border FontTile(string label, int aaSize, bool sel, Action onClick)
    {
        var b = new Border
        {
            Height = 56, CornerRadius = new CornerRadius(14), Cursor = Cursors.Hand,
            Background = sel ? Brush("AraGreenSoft") : new SolidColorBrush(Color.FromArgb(0x14, 0xFF, 0xFF, 0xFF)),
        };
        var col = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        col.Children.Add(new TextBlock { Text = "Aa", FontSize = aaSize, FontWeight = FontWeights.Bold,
            Foreground = sel ? Brush("AraGreenDeep") : Brush("AraBody"), HorizontalAlignment = HorizontalAlignment.Center });
        col.Children.Add(new TextBlock { Text = label, FontSize = 9, Foreground = sel ? Brush("AraGreen") : Brush("AraMuted"),
            HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 2, 0, 0) });
        b.Child = col;
        b.MouseLeftButtonUp += (_, _) => onClick();
        return b;
    }

    private Border CardPreview(PhoneText t)
    {
        var (title, body) = FontSizes[Math.Clamp(_s.Font, 0, 3)];
        var col = new StackPanel { Margin = new Thickness(18) };
        var tag = new Border
        {
            CornerRadius = new CornerRadius(20), Background = Brush("AraGreenSoft"), HorizontalAlignment = HorizontalAlignment.Left,
            Padding = new Thickness(12, 6, 12, 6),
            Child = new TextBlock { Text = t.PreviewTag, FontSize = 11, FontWeight = FontWeights.Bold, Foreground = Brush("AraGreenDeep") },
        };
        col.Children.Add(tag);
        col.Children.Add(new TextBlock { Text = t.PreviewTitle, FontSize = title, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraTitle"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 10, 0, 0), LineHeight = title * 1.18 });
        col.Children.Add(new Border { Width = 52, Height = 4, CornerRadius = new CornerRadius(2),
            HorizontalAlignment = HorizontalAlignment.Left, Background = Brush("AraBrush"), Margin = new Thickness(0, 10, 0, 0) });
        col.Children.Add(new TextBlock { Text = t.PreviewBody, FontSize = body, Foreground = Brush("AraBody"),
            TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 10, 0, 0), LineHeight = body * 1.48 });
        return new Border
        {
            CornerRadius = new CornerRadius(22), Background = Brush("AraGlassGradient"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"), Child = col,
        };
    }

    private Border ThemeTile(AraThemeStyle style, PhoneText t)
    {
        bool sel = style == _s.Theme;
        var accent = new SolidColorBrush(AraTheme.AccentFor(style));
        var col = new StackPanel { Margin = new Thickness(12) };
        var head = new Border
        {
            Height = 72, CornerRadius = new CornerRadius(14),
            Background = new SolidColorBrush(Color.FromArgb(0x3D, AraTheme.AccentFor(style).R, AraTheme.AccentFor(style).G, AraTheme.AccentFor(style).B)),
            BorderThickness = new Thickness(1), BorderBrush = new SolidColorBrush(Color.FromArgb(0x59, AraTheme.AccentFor(style).R, AraTheme.AccentFor(style).G, AraTheme.AccentFor(style).B)),
        };
        var bars = new StackPanel { Margin = new Thickness(10) };
        bars.Children.Add(new Border { Width = 42, Height = 5, CornerRadius = new CornerRadius(3), Background = accent, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 0, 0, 6) });
        bars.Children.Add(new Border { Width = 62, Height = 5, CornerRadius = new CornerRadius(3), Background = new SolidColorBrush(Color.FromArgb(0xA6, AraTheme.AccentFor(style).R, AraTheme.AccentFor(style).G, AraTheme.AccentFor(style).B)), HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 0, 0, 6) });
        bars.Children.Add(new Border { Width = 50, Height = 5, CornerRadius = new CornerRadius(3), Background = Brush("AraMuted"), HorizontalAlignment = HorizontalAlignment.Left, Opacity = 0.45 });
        head.Child = bars;
        col.Children.Add(head);
        col.Children.Add(new TextBlock { Text = AraTheme.Labels[style], FontSize = 12, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraTitle"), Margin = new Thickness(0, 8, 0, 0) });
        col.Children.Add(new TextBlock { Text = sel ? t.Selected : t.Tap, FontSize = 10, FontWeight = sel ? FontWeights.Bold : FontWeights.Normal,
            Foreground = sel ? accent : Brush("AraMuted"), Margin = new Thickness(0, 6, 0, 0) });
        var tile = new Border
        {
            Width = 132, Height = 158, Margin = new Thickness(0, 0, 12, 0), Cursor = Cursors.Hand,
            CornerRadius = new CornerRadius(22),
            Background = new LinearGradientBrush(Color.FromArgb(0x1F, 0xFF, 0xFF, 0xFF), Color.FromArgb(0x08, 0, 0, 0), 90),
            BorderThickness = new Thickness(sel ? 1.6 : 1),
            BorderBrush = sel ? accent : Brush("AraGlassEdge"),
            Child = col,
        };
        tile.MouseLeftButtonUp += (_, _) =>
        {
            _s.Theme = style; _s.Save(); AraTheme.Apply(style); Build();
        };
        return tile;
    }

    private Border InfoLine(string icon, string title, string body)
    {
        var g = new Grid { Margin = new Thickness(0, 2, 0, 2) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(30) });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var ic = new TextBlock { Text = icon, FontSize = 18, FontWeight = FontWeights.Bold, Foreground = Brush("AraGreenDeep"), VerticalAlignment = VerticalAlignment.Top };
        Grid.SetColumn(ic, 0); g.Children.Add(ic);
        var col = new StackPanel();
        col.Children.Add(new TextBlock { Text = title, FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = Brush("AraTitle") });
        col.Children.Add(new TextBlock { Text = body, FontSize = 12, Foreground = Brush("AraBody"), TextWrapping = TextWrapping.Wrap, LineHeight = 17 });
        Grid.SetColumn(col, 1); g.Children.Add(col);
        return new Border { Child = g };
    }

    private string Badge(string code) => code.ToUpperInvariant();

    private bool Confirm(string title, string body, string ok, string cancel)
        => MessageBox.Show(body, title, MessageBoxButton.OKCancel, MessageBoxImage.Warning) == MessageBoxResult.OK;

    private void Back_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Back();

    private void Help_Click(object sender, RoutedEventArgs e)
    {
        var c = CoachI18n.For(I18n.Locale);
        MainWindow.Current?.StartCoach(new System.Collections.Generic.List<CoachStep>
        {
            new(_coachLang, c.SetLang),
            new(_coachPlan, c.SetPlan),
            new(_coachLook, c.SetLook),
        });
    }

    private Brush Brush(string key) => (Brush)FindResource(key);
}
