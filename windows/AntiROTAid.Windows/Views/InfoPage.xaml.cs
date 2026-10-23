using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace AntiROTAid.Windows.Views;

/// <summary>Home → Info. Ports the phone AraInfoScreen: lead, amber disclaimer,
/// the five privacy points, and the footer. Same policy as the first-run consent.</summary>
public partial class InfoPage : UserControl
{
    public InfoPage()
    {
        InitializeComponent();
        var t = PhoneI18n.For(I18n.Locale);
        Title.Text = t.InfoTitle;

        Body.Children.Add(new TextBlock
        {
            Text = t.InfoLead, FontSize = 14, Foreground = Brush("AraBody"),
            TextWrapping = TextWrapping.Wrap, LineHeight = 20, Margin = new Thickness(0, 0, 0, 14),
        });

        Body.Children.Add(DisclaimerWarning(t));

        foreach (var (title, body) in new[]
        {
            (t.Pol1T, t.Pol1B), (t.Pol2T, t.Pol2B), (t.Pol3T, t.Pol3B), (t.Pol4T, t.Pol4B), (t.Pol5T, t.Pol5B),
        })
            Body.Children.Add(PolicyPoint(title, body));

        Body.Children.Add(new TextBlock
        {
            Text = t.InfoFooter, FontSize = 12, Foreground = Brush("AraMuted"),
            TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 14, 0, 8),
        });
        Body.Children.Add(new FrameworkElement { Height = 8 });
    }

    private Border DisclaimerWarning(PhoneText t)
    {
        var amber = Color.FromRgb(0xFF, 0xB0, 0x20);
        var g = new Grid { Margin = new Thickness(14) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var warn = new TextBlock { Text = "⚠", FontSize = 18, Foreground = new SolidColorBrush(amber), VerticalAlignment = VerticalAlignment.Top };
        Grid.SetColumn(warn, 0); g.Children.Add(warn);
        var col = new StackPanel { Margin = new Thickness(10, 0, 0, 0) };
        col.Children.Add(new TextBlock { Text = t.WarnTitle, FontSize = 14, FontWeight = FontWeights.Black, Foreground = new SolidColorBrush(amber) });
        col.Children.Add(new TextBlock { Text = t.WarnBody, FontSize = 13, Foreground = Brush("AraBody"), TextWrapping = TextWrapping.Wrap, LineHeight = 18, Margin = new Thickness(0, 4, 0, 0) });
        Grid.SetColumn(col, 1); g.Children.Add(col);
        return new Border
        {
            CornerRadius = new CornerRadius(16), Margin = new Thickness(0, 0, 0, 14),
            Background = new SolidColorBrush(Color.FromArgb(0x1F, 0xFF, 0xB0, 0x20)),
            BorderThickness = new Thickness(1), BorderBrush = new SolidColorBrush(amber), Child = g,
        };
    }

    private Border PolicyPoint(string title, string body)
    {
        var col = new StackPanel { Margin = new Thickness(14) };
        col.Children.Add(new TextBlock { Text = title, FontSize = 14, FontWeight = FontWeights.Bold, Foreground = Brush("AraGreenDeep") });
        col.Children.Add(new TextBlock { Text = body, FontSize = 13, Foreground = Brush("AraBody"), TextWrapping = TextWrapping.Wrap, LineHeight = 18, Margin = new Thickness(0, 4, 0, 0) });
        return new Border
        {
            CornerRadius = new CornerRadius(16), Margin = new Thickness(0, 0, 0, 12),
            Background = Brush("AraGlassGradient"), BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
            Child = col,
        };
    }

    private void Back_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Back();
    private Brush Brush(string key) => (Brush)FindResource(key);
}
