using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace AntiROTAid.Windows;

/// <summary>
/// First-run onboarding, identical in flow to the Android ara app:
///   stage 0  language pick   ->   stage 1  disclaimer   ->   stage 2  privacy/consent.
/// Shown once, modally, before the main window. Returns the chosen language via
/// <see cref="ChosenCode"/>. Fully local — no network, nothing leaves the device.
/// </summary>
public partial class OnboardingWindow : Window
{
    public string ChosenCode { get; private set; }

    private int _stage;
    private bool _agreed;
    private static readonly Color Amber = Color.FromRgb(0xFF, 0xB0, 0x20);

    public OnboardingWindow(string currentCode)
    {
        InitializeComponent();
        ChosenCode = string.IsNullOrEmpty(currentCode) ? "ro" : currentCode;
        AgreeRow.MouseLeftButtonUp += (_, __) => ToggleAgree();
        Render();
    }

    private Brush B(string key) => (Brush)FindResource(key);

    private void OnNext(object sender, RoutedEventArgs e)
    {
        if (_stage == 0) { _stage = 1; }
        else if (_stage == 1) { _stage = 2; _agreed = false; }
        else { if (_agreed) { DialogResult = true; return; } }
        Render();
    }

    private void ToggleAgree()
    {
        if (_stage != 2) return;
        _agreed = !_agreed;
        AgreeCheck.Visibility = _agreed ? Visibility.Visible : Visibility.Collapsed;
        AgreeBox.Background = _agreed ? B("AraGreen") : B("AraGreenSoft");
        NextBtn.IsEnabled = _agreed;
    }

    private void Render()
    {
        var t = Onboard.For(ChosenCode);
        Host.Children.Clear();
        AgreeRow.Visibility = Visibility.Collapsed;

        switch (_stage)
        {
            case 0: RenderLanguage(t); break;
            case 1: RenderDisclaimer(t); break;
            default: RenderConsent(t); break;
        }
    }

    // ---- stage 0: language --------------------------------------------------
    private void RenderLanguage(OnboardText t)
    {
        Host.Children.Add(Wordmark(44, HorizontalAlignment.Left));
        Host.Children.Add(Heading(t.LangPickTitle, 26));
        Host.Children.Add(Lead(t.LangPickLead));

        foreach (var (code, name) in I18n.Languages)
        {
            bool sel = code == ChosenCode;
            var row = new Border
            {
                CornerRadius = new CornerRadius(16),
                Background = sel ? B("AraGreenSoft") : new SolidColorBrush(Color.FromArgb(0x12, 0xFF, 0xFF, 0xFF)),
                BorderThickness = new Thickness(sel ? 1.5 : 1),
                BorderBrush = sel ? B("AraGreen") : B("AraGlassEdgeGreen"),
                Padding = new Thickness(16),
                Margin = new Thickness(0, 0, 0, 10),
                Cursor = Cursors.Hand,
            };
            var r = new StackPanel { Orientation = Orientation.Horizontal };
            var badge = new Border
            {
                Width = 34, Height = 34, CornerRadius = new CornerRadius(10),
                Background = B("AraGreenSoft"),
                BorderThickness = new Thickness(1), BorderBrush = B("AraGlassEdgeGreen"),
                Child = new TextBlock
                {
                    Text = code.ToUpperInvariant(), Foreground = B("AraGreenDeep"),
                    FontSize = 12, FontWeight = FontWeights.Bold,
                    HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
                },
            };
            r.Children.Add(badge);
            r.Children.Add(new TextBlock
            {
                Text = name, Foreground = B("AraTitle"), FontSize = 17,
                FontWeight = sel ? FontWeights.Bold : FontWeights.Medium,
                Margin = new Thickness(14, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center,
            });
            row.Child = r;
            string picked = code;
            row.MouseLeftButtonUp += (_, __) => { ChosenCode = picked; Render(); };
            Host.Children.Add(row);
        }

        NextBtn.Content = t.Continue;
        NextBtn.IsEnabled = true;
    }

    // ---- stage 1: disclaimer ------------------------------------------------
    private void RenderDisclaimer(OnboardText t)
    {
        var amber = new SolidColorBrush(Amber);
        var amberFill = new SolidColorBrush(Color.FromArgb(0x1F, 0xFF, 0xB0, 0x20));

        var icon = new Border
        {
            Width = 72, Height = 72, CornerRadius = new CornerRadius(22),
            Background = amberFill, BorderThickness = new Thickness(1.5), BorderBrush = amber,
            Margin = new Thickness(0, 8, 0, 20), HorizontalAlignment = HorizontalAlignment.Left,
            Child = new TextBlock { Text = "⚠", Foreground = amber, FontSize = 38,
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center },
        };
        Host.Children.Add(icon);
        Host.Children.Add(new TextBlock
        {
            Text = t.WarnTitle, Foreground = amber, FontSize = 32, FontWeight = FontWeights.Bold,
            Margin = new Thickness(0, 0, 0, 14),
        });
        Host.Children.Add(new TextBlock
        {
            Text = t.WarnBody, Foreground = B("AraTitle"), FontSize = 16, LineHeight = 24,
            TextWrapping = TextWrapping.Wrap,
        });

        NextBtn.Content = t.Continue;
        NextBtn.IsEnabled = true;
    }

    // ---- stage 2: privacy / consent -----------------------------------------
    private void RenderConsent(OnboardText t)
    {
        var brand = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };
        brand.Children.Add(Wordmark(34, HorizontalAlignment.Left));
        Host.Children.Add(brand);
        Host.Children.Add(Heading(t.PrivacyTitle, 26));
        Host.Children.Add(Lead(t.PrivacyLead));

        foreach (var (title, body) in t.Policy)
        {
            var card = new Border
            {
                CornerRadius = new CornerRadius(16),
                Background = B("AraGlassGradient"),
                BorderThickness = new Thickness(1), BorderBrush = B("AraGlassEdgeGreen"),
                Padding = new Thickness(14), Margin = new Thickness(0, 0, 0, 12),
            };
            var col = new StackPanel();
            col.Children.Add(new TextBlock { Text = title, Foreground = B("AraGreenDeep"),
                FontSize = 14, FontWeight = FontWeights.Bold, Margin = new Thickness(0, 0, 0, 4) });
            col.Children.Add(new TextBlock { Text = body, Foreground = B("AraBody"),
                FontSize = 13, LineHeight = 18, TextWrapping = TextWrapping.Wrap });
            card.Child = col;
            Host.Children.Add(card);
        }

        AgreeLabel.Text = t.ConsentAgree;
        AgreeCheck.Visibility = _agreed ? Visibility.Visible : Visibility.Collapsed;
        AgreeBox.Background = _agreed ? B("AraGreen") : B("AraGreenSoft");
        AgreeRow.Visibility = Visibility.Visible;
        NextBtn.Content = t.ConsentContinue;
        NextBtn.IsEnabled = _agreed;
    }

    // ---- small builders -----------------------------------------------------
    private TextBlock Heading(string s, double size) => new()
    {
        Text = s, Foreground = B("AraTitle"), FontSize = size, FontWeight = FontWeights.Bold,
        TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 18, 0, 4),
    };

    private TextBlock Lead(string s) => new()
    {
        Text = s, Foreground = B("AraMuted"), FontSize = 14, LineHeight = 19,
        TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 18),
    };

    private static Image Wordmark(double height, HorizontalAlignment align)
    {
        var img = new Image { Height = height, HorizontalAlignment = align, Stretch = Stretch.Uniform };
        try { img.Source = new BitmapImage(new Uri("pack://application:,,,/Assets/ara_wordmark.png")); }
        catch { /* asset missing → just no logo */ }
        return img;
    }
}
