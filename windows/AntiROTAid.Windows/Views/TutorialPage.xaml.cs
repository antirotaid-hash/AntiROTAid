using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace AntiROTAid.Windows.Views;

/// <summary>
/// Home → Guide. Ports the phone AraTutorial: a 6-page image-led walkthrough with
/// mock cards, page dots and Back / Next / Done.
/// </summary>
public partial class TutorialPage : UserControl
{
    private const int Pages = 6;
    private int _step;

    public TutorialPage()
    {
        InitializeComponent();
        var t = PhoneI18n.For(I18n.Locale);
        Title.Text = t.GuideTitle;
        Skip.Text = t.GuideSkip;
        Render();
    }

    private void Render()
    {
        var t = PhoneI18n.For(I18n.Locale);
        Page.Children.Clear();
        switch (_step)
        {
            case 0: Welcome(t); break;
            case 1: Mock(MockStudyCard(t)); Text(t.Tut0T, t.Tut0B); break;
            case 2: Mock(MockExamCard(t)); Text(t.Tut1T, t.Tut1B); break;
            case 3: Mock(MockLibraryCard(t)); Text(t.Tut2T, t.Tut2B); break;
            case 4: Mock(MockOverlayCard(t)); Text(t.Tut3T, t.Tut3B); break;
            default: Mock(MockActivationCard(t)); Text(t.Tut4T, t.Tut4B); break;
        }

        Dots.Children.Clear();
        for (int i = 0; i < Pages; i++)
            Dots.Children.Add(new Border
            {
                Width = i == _step ? 10 : 7, Height = i == _step ? 10 : 7,
                CornerRadius = new CornerRadius(5), Margin = new Thickness(4, 0, 4, 0),
                Background = i == _step ? Brush("AraGreen") : Brush("AraGreenSoft"),
            });

        BackBtn.Visibility = _step > 0 ? Visibility.Visible : Visibility.Collapsed;
        BackBtn.Content = t.GuideBack;
        NextText.Text = _step == 0 ? t.TutStart : _step < Pages - 1 ? t.GuideNext : t.GuideDone;
    }

    private void Welcome(PhoneText t)
    {
        var col = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
        col.Children.Add(Logo(38));
        col.Children.Add(new TextBlock { Text = t.TutWelcomeT, FontSize = 22, FontWeight = FontWeights.Black,
            Foreground = Brush("AraTitle"), TextAlignment = TextAlignment.Center, Margin = new Thickness(0, 14, 0, 0), TextWrapping = TextWrapping.Wrap });
        col.Children.Add(new TextBlock { Text = t.TutWelcomeB, FontSize = 14, Foreground = Brush("AraBody"),
            TextAlignment = TextAlignment.Center, LineHeight = 20, Margin = new Thickness(0, 14, 0, 0), TextWrapping = TextWrapping.Wrap });
        Page.Children.Add(col);
    }

    private void Mock(UIElement card)
    {
        var wrap = new Border { HorizontalAlignment = HorizontalAlignment.Center, Child = card, Margin = new Thickness(0, 0, 0, 18) };
        Page.Children.Add(wrap);
    }

    private void Text(string title, string body)
    {
        Page.Children.Add(new TextBlock { Text = title, FontSize = 19, FontWeight = FontWeights.Black, Foreground = Brush("AraTitle"),
            TextAlignment = TextAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center });
        Page.Children.Add(new TextBlock { Text = body, FontSize = 14, Foreground = Brush("AraBody"), LineHeight = 20,
            TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap, MaxWidth = 460, Margin = new Thickness(0, 8, 0, 0) });
    }

    // ---- mock cards (sample content, mirrors the phone's tutorial mocks) ----
    private Border Shell(StackPanel col, double w = 300) => new()
    {
        Width = w, CornerRadius = new CornerRadius(20), Padding = new Thickness(18),
        Background = Brush("AraGlassGradient"), BorderThickness = new Thickness(1), BorderBrush = Brush("AraGreen"), Child = col,
    };

    private Border MockStudyCard(PhoneText t)
    {
        var col = new StackPanel();
        col.Children.Add(TagChip(t.MTagAlgebra));
        col.Children.Add(new TextBlock { Text = t.MEqTitle, FontSize = 22, FontWeight = FontWeights.Bold, Foreground = Brush("AraTitle"), Margin = new Thickness(0, 10, 0, 0), TextWrapping = TextWrapping.Wrap });
        col.Children.Add(new Border { Width = 52, Height = 4, CornerRadius = new CornerRadius(2), HorizontalAlignment = HorizontalAlignment.Left, Background = Brush("AraBrush"), Margin = new Thickness(0, 10, 0, 10) });
        col.Children.Add(Bullet(t.MEqIdea1));
        col.Children.Add(Bullet(t.MEqIdea2));
        return Shell(col);
    }

    private Border MockExamCard(PhoneText t)
    {
        var col = new StackPanel();
        col.Children.Add(new TextBlock { Text = t.MExamTitle, FontSize = 18, FontWeight = FontWeights.Bold, Foreground = Brush("AraTitle") });
        col.Children.Add(KV(t.LabelData, t.MDateVal));
        col.Children.Add(KV(t.LabelPlan, t.MPlanVal));
        return Shell(col);
    }

    private Border MockLibraryCard(PhoneText t)
    {
        var col = new StackPanel();
        col.Children.Add(TagChip(t.MTagAlgebra));
        col.Children.Add(new TextBlock { Text = t.MNeural, FontSize = 20, FontWeight = FontWeights.Bold, Foreground = Brush("AraTitle"), Margin = new Thickness(0, 8, 0, 8), TextWrapping = TextWrapping.Wrap });
        col.Children.Add(KV(t.MCourse1.Split('—')[0].Trim(), t.MCourse1.Contains('—') ? t.MCourse1.Split('—')[1].Trim() : ""));
        col.Children.Add(KV(t.MCourse2.Split('—')[0].Trim(), t.MCourse2.Contains('—') ? t.MCourse2.Split('—')[1].Trim() : ""));
        return Shell(col);
    }

    private Border MockOverlayCard(PhoneText t)
    {
        var col = new StackPanel();
        col.Children.Add(TagChip(t.PreviewTag));
        col.Children.Add(new TextBlock { Text = t.MBalanceTitle, FontSize = 20, FontWeight = FontWeights.Bold, Foreground = Brush("AraTitle"), Margin = new Thickness(0, 8, 0, 8), TextWrapping = TextWrapping.Wrap });
        col.Children.Add(Bullet(t.MBalanceBody));
        return Shell(col);
    }

    private Border MockActivationCard(PhoneText t)
    {
        var col = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
        col.Children.Add(Logo(30));
        var btn = new Border
        {
            CornerRadius = new CornerRadius(16), Background = Brush("AraBrush"), Padding = new Thickness(24, 14, 24, 14), Margin = new Thickness(0, 14, 0, 0),
            Child = new TextBlock { Text = Home.For(I18n.Locale).StartTitle, FontSize = 15, FontWeight = FontWeights.Black, Foreground = new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) },
        };
        col.Children.Add(btn);
        return Shell(col, 320);
    }

    private Border TagChip(string text) => new()
    {
        CornerRadius = new CornerRadius(13), Background = Brush("AraGreenSoft"), HorizontalAlignment = HorizontalAlignment.Left, Padding = new Thickness(12, 5, 12, 6),
        Child = new TextBlock { Text = text, FontSize = 11, FontWeight = FontWeights.Bold, Foreground = Brush("AraGreenDeep") },
    };

    private FrameworkElement Bullet(string text)
    {
        var g = new Grid { Margin = new Thickness(0, 6, 0, 0) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var dot = new System.Windows.Shapes.Ellipse { Width = 7, Height = 7, Fill = Brush("AraGreen"), VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 6, 0, 0), HorizontalAlignment = HorizontalAlignment.Left };
        Grid.SetColumn(dot, 0); g.Children.Add(dot);
        var tb = new TextBlock { Text = text, FontSize = 14, Foreground = Brush("AraBody"), TextWrapping = TextWrapping.Wrap, LineHeight = 20 };
        Grid.SetColumn(tb, 1); g.Children.Add(tb);
        return g;
    }

    private FrameworkElement KV(string k, string v)
    {
        var g = new Grid { Margin = new Thickness(0, 8, 0, 0) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var kk = new TextBlock { Text = k, FontSize = 12, Foreground = Brush("AraMuted"), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 12, 0) };
        Grid.SetColumn(kk, 0); g.Children.Add(kk);
        var vv = new TextBlock { Text = v, FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = Brush("AraTitle"), TextAlignment = TextAlignment.Right, HorizontalAlignment = HorizontalAlignment.Right };
        Grid.SetColumn(vv, 1); g.Children.Add(vv);
        return g;
    }

    private Image Logo(double h) => new()
    {
        Source = new BitmapImage(new Uri("pack://application:,,,/Assets/ara_wordmark.png")),
        Height = h, Stretch = Stretch.Uniform, HorizontalAlignment = HorizontalAlignment.Center,
    };

    private void Skip_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Back();
    private void Back_Click(object sender, RoutedEventArgs e) { if (_step > 0) { _step--; Render(); } }
    private void Next_Click(object sender, RoutedEventArgs e)
    {
        if (_step < Pages - 1) { _step++; Render(); } else MainWindow.Current?.Back();
    }

    private Brush Brush(string key) => (Brush)FindResource(key);
}
