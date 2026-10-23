using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace AntiROTAid.Windows.Views;

/// <summary>
/// Guide chooser overlay — the Windows port of the phone's AraGuideChooser. Asked when
/// the user taps "Ghid" on Home: pick the slide-based quick guide or the live
/// point-at-each-button walkthrough. Full-window glass scrim with two choice cards.
/// </summary>
public sealed class GuideChooser : Grid
{
    public GuideChooser(HomeText h, Action onQuick, Action onLive, Action onDismiss)
    {
        // Scrim over the whole window; clicking the dim area outside the panel dismisses.
        Background = new SolidColorBrush(Color.FromArgb(0xE6, 0x09, 0x08, 0x06));
        Cursor = Cursors.Arrow;
        MouseLeftButtonDown += (_, _) => onDismiss();

        var panel = new StackPanel
        {
            HorizontalAlignment = HorizontalAlignment.Stretch,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(24, 0, 24, 0),
            MaxWidth = 620,
        };
        // Clicks on the panel itself must not fall through to the scrim's dismiss.
        panel.MouseLeftButtonDown += (_, e) => e.Handled = true;

        panel.Children.Add(new TextBlock
        {
            Text = h.GuideAskTitle, Foreground = B("AraTitle"), FontSize = 22,
            FontWeight = FontWeights.Black, TextWrapping = TextWrapping.Wrap,
        });
        panel.Children.Add(new TextBlock
        {
            Text = h.GuideAskLead, Foreground = B("AraMuted"), FontSize = 14,
            TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 8, 0, 14),
        });

        panel.Children.Add(ChoiceCard("▤", h.GuideQuick, h.GuideQuickSub, onQuick));
        panel.Children.Add(ChoiceCard("◉", h.GuideLive, h.GuideLiveSub, onLive));

        var back = new Border
        {
            Height = 52, CornerRadius = new CornerRadius(14), Cursor = Cursors.Hand,
            Margin = new Thickness(0, 14, 0, 0), BorderThickness = new Thickness(1),
            BorderBrush = B("AraGlassEdgeGreen"), Background = B("AraGlassGradient"),
            Child = new TextBlock
            {
                Text = h.GuideBack, Foreground = B("AraGreen"), FontWeight = FontWeights.Bold,
                FontSize = 15, HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
            },
        };
        back.MouseLeftButtonDown += (_, e) => { e.Handled = true; onDismiss(); };
        panel.Children.Add(back);

        Children.Add(panel);
    }

    private static Brush B(string key) => (Brush)Application.Current.FindResource(key);

    private Border ChoiceCard(string glyph, string title, string sub, Action onClick)
    {
        var row = new Grid { Margin = new Thickness(18) };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var glyphBox = new Border
        {
            Width = 48, Height = 48, CornerRadius = new CornerRadius(14),
            Background = B("AraGreenSoft"), BorderThickness = new Thickness(1),
            BorderBrush = B("AraGlassEdgeGreen"), VerticalAlignment = VerticalAlignment.Center,
            Child = new TextBlock
            {
                Text = glyph, Foreground = B("AraGreenDeep"), FontSize = 22, FontWeight = FontWeights.Black,
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
            },
        };
        Grid.SetColumn(glyphBox, 0);
        row.Children.Add(glyphBox);

        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 0, 0) };
        texts.Children.Add(new TextBlock { Text = title, Foreground = B("AraTitle"), FontSize = 17, FontWeight = FontWeights.Black });
        texts.Children.Add(new TextBlock
        {
            Text = sub, Foreground = B("AraMuted"), FontSize = 12, LineHeight = 16,
            TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 2, 0, 0),
        });
        Grid.SetColumn(texts, 1);
        row.Children.Add(texts);

        var chevron = new TextBlock
        {
            Text = "›", Foreground = B("AraGreenDeep"), FontSize = 24, FontWeight = FontWeights.Bold,
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(chevron, 2);
        row.Children.Add(chevron);

        var card = new Border
        {
            CornerRadius = new CornerRadius(20), Cursor = Cursors.Hand, Margin = new Thickness(0, 0, 0, 12),
            Background = B("AraGlassGradient"), BorderThickness = new Thickness(1),
            BorderBrush = B("AraGlassEdgeGreen"), Child = row,
        };
        card.MouseLeftButtonDown += (_, e) => { e.Handled = true; onClick(); };
        return card;
    }
}
