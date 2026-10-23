using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Views;

public partial class CoursesPage : UserControl, IRefreshable
{
    private string _sort = "name";

    public CoursesPage()
    {
        InitializeComponent();
        var t = CoursesI18n.For(I18n.Locale);
        Title.Text = t.Title;
        AddBtn.Content = t.AddCourse;
        NameBox.Tag = t.NewCourseName;
        ImportBtn.Content = t.ImportFile;
        CreateBtn.Content = t.Create;
        CancelBtn.Content = t.Cancel;
        BuildSort();
        Refresh();
    }

    public void Refresh()
    {
        var t = CoursesI18n.For(I18n.Locale);
        LibraryCount.Text = string.Format(t.LibraryFmt, App.Courses.Courses.Count);
        BuildList();
    }

    // ---- sort pills ----
    private void BuildSort()
    {
        var t = CoursesI18n.For(I18n.Locale);
        SortRow.Children.Clear();
        SortRow.Children.Add(new TextBlock
        {
            Text = t.SortBy, FontSize = 12, FontWeight = FontWeights.SemiBold,
            Foreground = Brush("AraMuted"), VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 12, 0),
        });
        foreach (var (key, label) in new[] { ("name", t.SortName), ("recent", t.SortRecent), ("progress", t.SortProgress), ("exam", t.SortExam) })
            SortRow.Children.Add(SortPill(key, label));
    }

    private Border SortPill(string key, string label)
    {
        bool sel = key == _sort;
        var b = new Border
        {
            CornerRadius = new CornerRadius(14), Padding = new Thickness(16, 8, 16, 8),
            Margin = new Thickness(0, 0, 8, 0), Cursor = Cursors.Hand,
            Background = sel ? Brush("AraBrush") : Brush("AraGreenSoft"),
            BorderThickness = new Thickness(1),
            BorderBrush = sel ? Brush("AraGreen") : Brush("AraGlassEdgeGreen"),
            Child = new TextBlock
            {
                Text = label, FontSize = 14, FontWeight = sel ? FontWeights.Bold : FontWeights.Medium,
                Foreground = sel ? new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)) : Brush("AraTitle"),
            },
        };
        b.MouseLeftButtonUp += (_, _) => { _sort = key; BuildSort(); BuildList(); };
        return b;
    }

    // ---- course cards ----
    private IEnumerable<Course> Sorted()
    {
        var list = App.Courses.Courses.AsEnumerable();
        return _sort switch
        {
            "recent" => list.OrderByDescending(c => c.Added),
            "progress" => list.OrderByDescending(c => c.LearnedPct),
            // By nearest exam date (soonest first); courses in no exam sort last.
            "exam" => list.OrderBy(c => NearestExam(c.Id) ?? DateTime.MaxValue).ThenBy(c => c.Name),
            _ => list.OrderBy(c => c.Name),
        };
    }

    // The exams a course belongs to, and the soonest of their dates (for the "exam" sort).
    private static List<Exam> ExamsFor(string courseId)
        => App.Exams.Exams.Where(e => e.MaterialIds.Contains(courseId)).ToList();

    private static DateTime? NearestExam(string courseId)
    {
        var ex = ExamsFor(courseId);
        return ex.Count == 0 ? null : ex.Min(e => e.Date);
    }

    private void BuildList()
    {
        List.Children.Clear();
        foreach (var c in Sorted()) List.Children.Add(Card(c));
    }

    private Border Card(Course c)
    {
        var t = CoursesI18n.For(I18n.Locale);
        var card = new Border
        {
            CornerRadius = new CornerRadius(20), Padding = new Thickness(18),
            Margin = new Thickness(0, 0, 0, 16),
            Background = Brush("AraGlassGradient"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var root = new StackPanel();

        // Top row: MATERIAL + DEMO | Course details ›
        var top = new Grid();
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var tagRow = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        tagRow.Children.Add(new TextBlock { Text = t.TagMaterial, FontSize = 11, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraMuted"), VerticalAlignment = VerticalAlignment.Center });
        if (c.IsDemo) tagRow.Children.Add(Badge("DEMO", "AraGreenDeep", "AraGreen"));
        Grid.SetColumn(tagRow, 0);
        top.Children.Add(tagRow);
        var detailsPill = OutlinePill(t.CourseDetails + " ›", "AraGreen");
        detailsPill.MouseLeftButtonUp += (_, _) => MainWindow.Current?.Navigate("course", c);
        Grid.SetColumn(detailsPill, 1);
        top.Children.Add(detailsPill);
        root.Children.Add(top);

        // Middle: thumbnail + title/filename/TXT
        var mid = new Grid { Margin = new Thickness(0, 16, 0, 0) };
        mid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        mid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        mid.Children.Add(Thumbnail());
        var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 0, 0) };
        info.Children.Add(new TextBlock { Text = c.Name, FontSize = 26, FontWeight = FontWeights.Bold,
            Foreground = Brush("AraTitle"), TextWrapping = TextWrapping.Wrap });
        info.Children.Add(new TextBlock { Text = string.IsNullOrWhiteSpace(c.FileName) ? "" : c.FileName,
            FontSize = 13, Foreground = Brush("AraMuted"), Margin = new Thickness(0, 4, 0, 0) });
        info.Children.Add(FileTypePill(c.FileName));
        Grid.SetColumn(info, 1);
        mid.Children.Add(info);
        root.Children.Add(mid);

        // Stats pill
        var stats = new Border
        {
            CornerRadius = new CornerRadius(14), Padding = new Thickness(4, 14, 4, 14),
            Margin = new Thickness(0, 16, 0, 0),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var statsGrid = new Grid();
        for (int i = 0; i < 3; i++) statsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        AddStat(statsGrid, 0, "cards", c.CardCount.ToString(), t.UnitCards);
        AddStat(statsGrid, 1, "slides", c.SlideCount.ToString(), t.UnitSlides);
        AddStat(statsGrid, 2, "learned", c.LearnedPct + "%", t.Learned);
        stats.Child = statsGrid;
        root.Children.Add(stats);

        // Which exams this material belongs to — or that it's unassigned (mirrors the phone).
        var exams = ExamsFor(c.Id);
        root.Children.Add(exams.Count == 0
            ? new TextBlock { Text = t.NotAssigned, FontSize = 13, Foreground = Brush("AraMuted"),
                FontWeight = FontWeights.Medium, Margin = new Thickness(4, 14, 0, 0) }
            : new TextBlock { Text = $"{t.InExamsLabel} {string.Join(", ", exams.Select(e => e.Name))}",
                FontSize = 13, Foreground = Brush("AraGreenDeep"), FontWeight = FontWeights.SemiBold,
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(4, 14, 0, 0) });

        // Progress bar
        var bar = new Border
        {
            Height = 6, CornerRadius = new CornerRadius(3), Margin = new Thickness(0, 12, 0, 0),
            Background = Brush("AraGreenSoft"),
        };
        var fill = new Border
        {
            Height = 6, CornerRadius = new CornerRadius(3), HorizontalAlignment = HorizontalAlignment.Left,
            Background = Brush("AraBrush"), Width = 0,
        };
        var barGrid = new Grid();
        barGrid.Children.Add(bar);
        barGrid.Children.Add(fill);
        fill.Loaded += (_, _) => { fill.Width = Math.Max(6, barGrid.ActualWidth * c.LearnedPct / 100.0); };
        root.Children.Add(barGrid);

        // Bottom: Open the cards | Details
        var bottom = new Grid { Margin = new Thickness(0, 16, 0, 0) };
        bottom.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(2, GridUnitType.Star) });
        bottom.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10) });
        bottom.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var open = new Border
        {
            CornerRadius = new CornerRadius(16), Background = Brush("AraBrush"),
            Cursor = Cursors.Hand, Padding = new Thickness(0, 12, 0, 12),
        };
        var openСol = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
        openСol.Children.Add(new TextBlock { Text = t.OpenCards, FontSize = 15, FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A)), HorizontalAlignment = HorizontalAlignment.Center });
        openСol.Children.Add(new TextBlock { Text = CoursesI18n.ScrollSub(I18n.Locale), FontSize = 11, FontWeight = FontWeights.SemiBold,
            Foreground = new SolidColorBrush(Color.FromRgb(0x37, 0x24, 0x00)), HorizontalAlignment = HorizontalAlignment.Center });
        open.Child = openСol;
        open.MouseLeftButtonUp += (_, _) => MainWindow.Current?.Navigate("study", c);
        Grid.SetColumn(open, 0);
        bottom.Children.Add(open);
        var details = new Border
        {
            CornerRadius = new CornerRadius(16), Cursor = Cursors.Hand,
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
            Background = Brush("AraGlassGradient"),
            Child = new TextBlock { Text = t.Details, FontSize = 15, FontWeight = FontWeights.Bold,
                Foreground = Brush("AraTitle"), HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center },
        };
        details.MouseLeftButtonUp += (_, _) => MainWindow.Current?.Navigate("course", c);
        Grid.SetColumn(details, 2);
        bottom.Children.Add(details);
        root.Children.Add(bottom);

        card.Child = root;
        return card;
    }

    private Border Thumbnail()
    {
        // A small dark box with a gold "constellation" — evokes the Android thumbnail.
        var box = new Border
        {
            Width = 120, Height = 120, CornerRadius = new CornerRadius(16),
            Background = Brush("AraGreenSoft"),
            BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        };
        var canvas = new System.Windows.Controls.Canvas { Width = 120, Height = 120 };
        var pts = new[] { (30.0, 40.0), (70.0, 30.0), (85.0, 70.0), (50.0, 85.0), (25.0, 75.0) };
        var gold = Brush("AraGreen");
        for (int i = 0; i < pts.Length; i++)
        {
            var (x1, y1) = pts[i];
            var (x2, y2) = pts[(i + 1) % pts.Length];
            canvas.Children.Add(new Line { X1 = x1, Y1 = y1, X2 = x2, Y2 = y2, Stroke = gold, StrokeThickness = 1, Opacity = 0.7 });
            var dot = new Ellipse { Width = 5, Height = 5, Fill = gold };
            System.Windows.Controls.Canvas.SetLeft(dot, x1 - 2.5);
            System.Windows.Controls.Canvas.SetTop(dot, y1 - 2.5);
            canvas.Children.Add(dot);
        }
        box.Child = canvas;
        return box;
    }

    private void AddStat(Grid g, int col, string kind, string value, string unit)
    {
        var sp = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };
        sp.Children.Add(StatIcon(kind));
        var col2 = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(9, 0, 0, 0) };
        col2.Children.Add(new TextBlock { Text = value, FontSize = 20, FontWeight = FontWeights.Bold, Foreground = Brush("AraTitle") });
        col2.Children.Add(new TextBlock { Text = unit, FontSize = 12, Foreground = Brush("AraMuted") });
        sp.Children.Add(col2);
        Grid.SetColumn(sp, col);
        g.Children.Add(sp);
    }

    // Small line-art stat icons drawn to match the Android course card.
    private FrameworkElement StatIcon(string kind)
    {
        var stroke = Brush("AraMuted");
        var c = new System.Windows.Controls.Canvas { Width = 22, Height = 22, VerticalAlignment = VerticalAlignment.Center };
        switch (kind)
        {
            case "cards": // list rows
                for (int i = 0; i < 3; i++)
                {
                    double y = 5 + i * 6;
                    c.Children.Add(new Line { X1 = 2, Y1 = y, X2 = 5, Y2 = y, Stroke = stroke, StrokeThickness = 2 });
                    c.Children.Add(new Line { X1 = 8, Y1 = y, X2 = 20, Y2 = y, Stroke = stroke, StrokeThickness = 2 });
                }
                break;
            case "slides": // vertical bars in a frame
                var frame = new System.Windows.Shapes.Rectangle { Width = 18, Height = 16, Stroke = stroke, StrokeThickness = 1.5, RadiusX = 2, RadiusY = 2 };
                System.Windows.Controls.Canvas.SetLeft(frame, 2); System.Windows.Controls.Canvas.SetTop(frame, 3);
                c.Children.Add(frame);
                for (int i = 0; i < 3; i++)
                    c.Children.Add(new Line { X1 = 6 + i * 5, Y1 = 5, X2 = 6 + i * 5, Y2 = 17, Stroke = stroke, StrokeThickness = 1.5 });
                break;
            default: // "learned": half-filled circle
                var ring = new Ellipse { Width = 16, Height = 16, Stroke = stroke, StrokeThickness = 1.5 };
                System.Windows.Controls.Canvas.SetLeft(ring, 3); System.Windows.Controls.Canvas.SetTop(ring, 3);
                c.Children.Add(ring);
                var half = new System.Windows.Shapes.Path
                {
                    Fill = stroke,
                    Data = Geometry.Parse("M 11,3 A 8,8 0 0 1 11,19 Z"),
                };
                c.Children.Add(half);
                break;
        }
        return c;
    }

    private Border Badge(string text, string fg, string edge) => new()
    {
        CornerRadius = new CornerRadius(8), Margin = new Thickness(10, 0, 0, 0), Padding = new Thickness(8, 1, 8, 2),
        Background = Brush("AraGreenSoft"), BorderThickness = new Thickness(1), BorderBrush = Brush(edge),
        VerticalAlignment = VerticalAlignment.Center,
        Child = new TextBlock { Text = text, FontSize = 10, FontWeight = FontWeights.Bold, Foreground = Brush(fg) },
    };

    // One colored pill reflecting the course's ACTUAL file type — ports RealFileTypePill.
    private Border FileTypePill(string fileName)
    {
        string ext = (fileName ?? "").Contains('.') ? fileName!.Substring(fileName.LastIndexOf('.') + 1).ToLowerInvariant() : "";
        var (label, tint) = ext switch
        {
            "pptx" or "ppt" => ("PPTX", Color.FromRgb(0xFF, 0x55, 0x33)),
            "pdf" => ("PDF", Color.FromRgb(0xFF, 0x3B, 0x30)),
            "docx" or "doc" => ("DOCX", Color.FromRgb(0x4C, 0x8D, 0xFF)),
            "png" or "jpg" or "jpeg" or "webp" or "gif" or "bmp" or "tiff" or "tif" => ("IMG", Color.FromRgb(0x22, 0xC5, 0x5E)),
            "md" or "markdown" => ("MD", Color.FromRgb(0x63, 0xB3, 0xFF)),
            _ => ("TXT", Color.FromRgb(0x63, 0xB3, 0xFF)),
        };
        return new Border
        {
            CornerRadius = new CornerRadius(10), Padding = new Thickness(9, 4, 9, 5),
            Margin = new Thickness(0, 8, 0, 0), HorizontalAlignment = HorizontalAlignment.Left,
            Background = new SolidColorBrush(Color.FromArgb(0x1F, tint.R, tint.G, tint.B)),
            BorderThickness = new Thickness(1),
            BorderBrush = new SolidColorBrush(Color.FromArgb(0x8C, tint.R, tint.G, tint.B)),
            Child = new TextBlock { Text = label, FontSize = 11, FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(tint) },
        };
    }

    private Border OutlinePill(string text, string fg) => new()
    {
        CornerRadius = new CornerRadius(12), Padding = new Thickness(14, 7, 14, 7), Cursor = Cursors.Hand,
        BorderThickness = new Thickness(1), BorderBrush = Brush("AraGlassEdgeGreen"),
        Child = new TextBlock { Text = text, FontSize = 13, FontWeight = FontWeights.Bold, Foreground = Brush(fg) },
    };

    private void Back_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Back();

    private void Help_Click(object sender, RoutedEventArgs e)
    {
        var c = CoachI18n.For(I18n.Locale);
        MainWindow.Current?.StartCoach(new List<CoachStep>
        {
            new(AddBtn, c.CoursesImport),
            new(List.Children.Count > 0 ? (FrameworkElement)List.Children[0] : AddBtn, c.CoursesCard),
        });
    }

    private void Add_Click(object sender, RoutedEventArgs e)
        => AddPanel.Visibility = AddPanel.Visibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible;

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        AddPanel.Visibility = Visibility.Collapsed; NameBox.Clear(); TextBox_.Clear();
    }

    private void Create_Click(object sender, RoutedEventArgs e)
    {
        var text = TextBox_.Text?.Trim();
        if (string.IsNullOrWhiteSpace(text)) return;
        App.Courses.AddFromText(NameBox.Text, text);
        NameBox.Clear(); TextBox_.Clear();
        AddPanel.Visibility = Visibility.Collapsed;
        Refresh();
    }

    // Import a real file (PDF/PPTX/Word/text/image) — the engine turns it into cards,
    // exactly like the phone. No network; the file is read locally.
    private async void Import_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.OpenFileDialog
        {
            Title = CoursesI18n.For(I18n.Locale).ImportFile,
            Filter = $"{I18n.T.FilePickerCourseFiles}|*.pdf;*.pptx;*.docx;*.txt;*.png;*.jpg;*.jpeg;*.webp|{I18n.T.FilePickerAllFiles}|*.*",
            CheckFileExists = true,
        };
        if (dlg.ShowDialog() != true) return;

        // Import off the UI thread (engine extraction can take seconds on big files) so the
        // window stays responsive; the wait cursor shows progress.
        Mouse.OverrideCursor = Cursors.Wait;
        Course? created;
        try { created = await App.Courses.AddFromFileAsync(NameBox.Text, dlg.FileName); }
        finally { Mouse.OverrideCursor = null; }

        if (created is null)
        {
            MessageBox.Show(Window.GetWindow(this)!, CoursesI18n.For(I18n.Locale).ImportFile,
                "AntiROTAid", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        NameBox.Clear(); TextBox_.Clear();
        AddPanel.Visibility = Visibility.Collapsed;
        Refresh();
    }

    private Brush Brush(string key) => (Brush)FindResource(key);
}
