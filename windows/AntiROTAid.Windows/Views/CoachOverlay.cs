using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;

namespace AntiROTAid.Windows.Views;

/// <summary>One walkthrough step: the control to spotlight (null = none) and its text.</summary>
public sealed record CoachStep(FrameworkElement? Target, string Text);

/// <summary>
/// Live coach-marks overlay (the Windows port of AraCoach/CoachHost): dims the whole
/// screen, cuts a glowing rounded "spotlight" over the control being explained, and
/// shows a callout bubble with the step text + Back / Next / Done — exactly like the
/// phone's "?" walkthrough. Hosted on top of the current page in MainWindow.
/// </summary>
public sealed class CoachOverlay : Grid
{
    private readonly List<CoachStep> _steps;
    private readonly Action _onDone;
    private int _index;

    public CoachOverlay(List<CoachStep> steps, Action onDone)
    {
        _steps = steps;
        _onDone = onDone;
        Background = Brushes.Transparent; // hit-test the whole window
        Cursor = Cursors.Arrow;
        Loaded += (_, _) => Build();
        SizeChanged += (_, _) => Build();
        // Click anywhere on the dim → advance (mirrors the phone).
        MouseLeftButtonDown += (_, _) => Next();
    }

    private Brush B(string key) => (Brush)Application.Current.FindResource(key);

    private void Next() { if (_index >= _steps.Count - 1) Stop(); else { _index++; Build(); } }
    private void Back() { if (_index > 0) { _index--; Build(); } }
    private void Stop() => _onDone();

    private void Build()
    {
        if (ActualWidth <= 0 || ActualHeight <= 0) return;
        Children.Clear();
        var step = _steps[_index];
        Rect? hole = TargetBounds(step.Target);

        // Dim everything, with a rounded hole punched over the target (EvenOdd).
        double w = ActualWidth, h = ActualHeight;
        var geo = new CombinedGeometry
        {
            GeometryCombineMode = GeometryCombineMode.Exclude,
            Geometry1 = new RectangleGeometry(new Rect(0, 0, w, h)),
        };
        if (hole is { } r)
        {
            var pad = 6.0;
            var inner = new Rect(r.Left - pad, r.Top - pad, r.Width + pad * 2, r.Height + pad * 2);
            geo.Geometry2 = new RectangleGeometry(inner, 16, 16);
        }
        else geo.Geometry2 = new RectangleGeometry(new Rect(0, 0, 0, 0));
        Children.Add(new Path { Data = geo, Fill = new SolidColorBrush(Color.FromArgb(0xE6, 0x09, 0x08, 0x06)) });

        // Glowing ring around the spotlight (no fill — the real control shows through).
        if (hole is { } rr)
        {
            var pad = 6.0;
            var ring = new Border
            {
                Width = rr.Width + pad * 2, Height = rr.Height + pad * 2,
                CornerRadius = new CornerRadius(16), BorderThickness = new Thickness(2),
                BorderBrush = B("AraGreen"), IsHitTestVisible = false,
                HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
                Margin = new Thickness(rr.Left - pad, rr.Top - pad, 0, 0),
            };
            Children.Add(ring);
        }

        Children.Add(Callout(hole, step.Text));
    }

    private Rect? TargetBounds(FrameworkElement? target)
    {
        if (target is null || !target.IsVisible) return null;
        try
        {
            var tl = target.TransformToVisual(this).Transform(new Point(0, 0));
            return new Rect(tl.X, tl.Y, target.ActualWidth, target.ActualHeight);
        }
        catch { return null; }
    }

    private FrameworkElement Callout(Rect? hole, string text)
    {
        var c = CoachI18n.For(I18n.Locale);
        double h = ActualHeight;
        double targetBottom = hole?.Bottom ?? h / 2;
        double targetTop = hole?.Top ?? h / 2;
        bool below = targetBottom < h * 0.55;
        double y = below ? targetBottom + 14 : targetTop - 14 - 210;
        y = Math.Clamp(y, 40, Math.Max(40, h - 240));

        var bubble = new Border
        {
            CornerRadius = new CornerRadius(20), Background = B("AraBgBottom"),
            BorderThickness = new Thickness(1), BorderBrush = B("AraGlassEdgeGreen"),
            Margin = new Thickness(18, y, 18, 0), Padding = new Thickness(18),
            HorizontalAlignment = HorizontalAlignment.Stretch, VerticalAlignment = VerticalAlignment.Top,
            MaxWidth = 640,
        };
        var col = new StackPanel();

        var head = new Grid();
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        head.Children.Add(new TextBlock { Text = $"{_index + 1}/{_steps.Count}", FontSize = 12,
            FontWeight = FontWeights.Black, Foreground = B("AraGreen") });
        var skip = new TextBlock { Text = c.Skip, FontSize = 12, Foreground = B("AraMuted"), Cursor = Cursors.Hand };
        skip.MouseLeftButtonDown += (_, e) => { e.Handled = true; Stop(); };
        Grid.SetColumn(skip, 1);
        head.Children.Add(skip);
        col.Children.Add(head);

        col.Children.Add(new TextBlock { Text = text, FontSize = 15, Foreground = B("AraBody"),
            TextWrapping = TextWrapping.Wrap, LineHeight = 21, Margin = new Thickness(0, 12, 0, 12) });

        var btns = new Grid();
        btns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        btns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10) });
        btns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        if (_index > 0)
        {
            var back = PillButton(c.Back, primary: false);
            back.MouseLeftButtonDown += (_, e) => { e.Handled = true; Back(); };
            Grid.SetColumn(back, 0);
            btns.Children.Add(back);
        }
        var next = PillButton(_index >= _steps.Count - 1 ? c.Done : c.Next, primary: true);
        next.MouseLeftButtonDown += (_, e) => { e.Handled = true; Next(); };
        Grid.SetColumn(next, 2);
        btns.Children.Add(next);
        col.Children.Add(btns);

        bubble.Child = col;
        // Wrap so clicks on the bubble background don't fall through to "next".
        bubble.MouseLeftButtonDown += (_, e) => e.Handled = true;
        return bubble;
    }

    private Border PillButton(string text, bool primary) => new()
    {
        Height = 46, CornerRadius = new CornerRadius(13), Cursor = Cursors.Hand,
        Background = primary ? B("AraBrush") : new SolidColorBrush(Color.FromArgb(0x1A, 0xFF, 0xFF, 0xFF)),
        BorderThickness = new Thickness(primary ? 0 : 1), BorderBrush = B("AraGlassEdgeGreen"),
        Child = new TextBlock
        {
            Text = text, FontWeight = FontWeights.Bold, FontSize = 14,
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
            Foreground = primary ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : B("AraBody"),
        },
    };
}
