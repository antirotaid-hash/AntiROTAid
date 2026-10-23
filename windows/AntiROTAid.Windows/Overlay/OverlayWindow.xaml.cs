using System;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using AntiROTAid.Windows.Engine;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Overlay;

/// <summary>
/// A single always-on-top study card, floating over whatever is on screen. It does
/// NOT cover or block the whole desktop — it is a dismissible card that the user can
/// move. "Continuă" closes this card; "Oprește modul" stops AntiROTAid Mode entirely.
/// </summary>
public partial class OverlayWindow : Window
{
    public event Action? CardClosed;
    private bool _closedFired;

    public OverlayWindow(StudyCard card)
    {
        InitializeComponent();
        HeaderSuffix.Text = "·  " + I18n.T.OverlayHeader;
        ContinueBtn.Content = I18n.T.Continue;
        StopBtn.Content = I18n.T.OverlayStop;
        SourceLabel.Text = card.SourceLabel;
        SourceLabel.Visibility = string.IsNullOrWhiteSpace(card.SourceLabel)
            ? Visibility.Collapsed : Visibility.Visible;
        TitleText.Text = string.IsNullOrWhiteSpace(card.Title) ? card.SourceLabel : card.Title;

        // Rotating nag, like the phone overlay.
        var nag = NagI18n.Random(I18n.Locale);
        if (!string.IsNullOrEmpty(nag)) { NagText.Text = nag; NagBanner.Visibility = Visibility.Visible; }

        // Image / PDF slide cards render the slide; text cards render formatted prose.
        if (card.IsImage && card.HasImage) RenderVisual(card);
        RenderBody(BodyWithoutTitle(card.Body, card.Title));
    }

    private static readonly Regex NumBullet = new(@"^\s*(\d+)[\.\)]\s+(.*)$");

    private void RenderBody(string body)
    {
        if (string.IsNullOrWhiteSpace(body))
        {
            if (BodyHost.Children.Count == 0)
                BodyHost.Children.Add(new TextBlock { Text = "—", FontSize = 16, Foreground = B("AraBody") });
            return;
        }
        foreach (var raw in body.Replace("\r\n", "\n").Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0) continue;
            if (line.StartsWith("•")) { BodyHost.Children.Add(Bullet(null, line.TrimStart('•', ' '))); continue; }
            var m = NumBullet.Match(line);
            if (m.Success) { BodyHost.Children.Add(Bullet(m.Groups[1].Value + ".", m.Groups[2].Value.Trim())); continue; }
            if (line.EndsWith(":") && line.Length <= 48) { BodyHost.Children.Add(Header(line.TrimEnd(':', ' '))); continue; }
            BodyHost.Children.Add(new TextBlock { Text = line, FontSize = 16, TextWrapping = TextWrapping.Wrap,
                Foreground = B("AraBody"), LineHeight = 24, Margin = new Thickness(0, 8, 0, 0) });
        }
    }

    private void RenderVisual(StudyCard card)
    {
        var img = new Image { Stretch = Stretch.Uniform, StretchDirection = StretchDirection.DownOnly, MaxHeight = 300 };
        if (card.ImageBase64.Length > 0) img.Source = SlideImage.FromBase64(card.ImageBase64);
        else if (card.ImagePath.Length > 0) img.Source = SlideImage.FromFile(card.ImagePath);
        else if (card.PdfDocPath.Length > 0) img.Source = SlideImage.FromPdfPage(card.PdfDocPath, card.PdfPage, 900);
        BodyHost.Children.Add(new Border
        {
            CornerRadius = new CornerRadius(14), Margin = new Thickness(0, 0, 0, 6), ClipToBounds = true,
            Background = new SolidColorBrush(Colors.White),
            BorderThickness = new Thickness(1), BorderBrush = B("AraGlassEdgeGreen"), Child = img,
        });
    }

    private FrameworkElement Header(string text)
    {
        var sp = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 12, 0, 2) };
        sp.Children.Add(new Border { Width = 5, Height = 20, CornerRadius = new CornerRadius(2),
            Background = B("AraBrush"), Margin = new Thickness(0, 0, 10, 0), VerticalAlignment = VerticalAlignment.Center });
        sp.Children.Add(new TextBlock { Text = text, FontSize = 17, FontWeight = FontWeights.Bold,
            Foreground = B("AraGreen"), VerticalAlignment = VerticalAlignment.Center });
        return sp;
    }

    private FrameworkElement Bullet(string? marker, string text)
    {
        var g = new Grid { Margin = new Thickness(0, 8, 0, 0) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(26) });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        FrameworkElement mk = marker is null
            ? new System.Windows.Shapes.Ellipse { Width = 7, Height = 7, Fill = B("AraGreen"),
                VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(2, 8, 0, 0) }
            : new TextBlock { Text = marker, FontSize = 15, FontWeight = FontWeights.Bold, Foreground = B("AraGreen"), VerticalAlignment = VerticalAlignment.Top };
        Grid.SetColumn(mk, 0); g.Children.Add(mk);
        var tb = new TextBlock { Text = text, FontSize = 16, TextWrapping = TextWrapping.Wrap, Foreground = B("AraBody"), LineHeight = 24 };
        Grid.SetColumn(tb, 1); g.Children.Add(tb);
        return g;
    }

    // Drop the body's first line when it just repeats the title (mirrors the phone).
    private static string BodyWithoutTitle(string body, string title)
    {
        if (string.IsNullOrWhiteSpace(title)) return body ?? "";
        var lines = (body ?? "").Replace("\r", "").Split('\n');
        for (int i = 0; i < lines.Length; i++)
        {
            if (lines[i].Trim().Length == 0) continue;
            if (string.Equals(lines[i].Trim(), title.Trim(), StringComparison.OrdinalIgnoreCase))
            {
                var rest = new System.Collections.Generic.List<string>(lines);
                rest.RemoveAt(i);
                return string.Join("\n", rest).Trim();
            }
            break;
        }
        return body ?? "";
    }

    private Brush B(string key) => (Brush)FindResource(key);

    public void ShowCard()
    {
        Opacity = 0;
        Show();
        // Center on the working area once the real size is known.
        var wa = SystemParameters.WorkArea;
        Left = wa.Left + (wa.Width - ActualWidth) / 2;
        Top = wa.Top + (wa.Height - ActualHeight) / 2.4;   // slightly above centre
        Activate();

        BeginAnimation(OpacityProperty,
            new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(220)));
    }

    /// <summary>Let the user drag the card by pressing anywhere on it.</summary>
    private void Card_MouseDown(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.ChangedButton == System.Windows.Input.MouseButton.Left) DragMove();
    }

    /// <summary>Close without user interaction (e.g. mode stopped).</summary>
    public void CloseCard()
    {
        _closedFired = true; // suppress CardClosed for a programmatic stop
        Close();
    }

    private void Continue_Click(object sender, RoutedEventArgs e) => Close();

    private void Stop_Click(object sender, RoutedEventArgs e)
    {
        _closedFired = true;
        App.Controller?.Stop();
        Close();
    }

    protected override void OnClosed(EventArgs e)
    {
        base.OnClosed(e);
        if (!_closedFired) CardClosed?.Invoke();
    }
}
