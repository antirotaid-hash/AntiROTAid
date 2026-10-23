using System.Collections.Generic;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Views;

namespace AntiROTAid.Windows;

/// <summary>
/// AntiROTAid shell for Windows — a single-column, phone-style page host with a
/// back-stack (Home is the root; the Settings/Guide/Info pills and the exam/course
/// cards push sub-pages, each with its own back arrow). Closing hides to tray; the
/// app only exits from tray → Exit.
/// </summary>
public partial class MainWindow : Window
{
    public static MainWindow? Current { get; private set; }
    public bool ReallyExit { get; set; }

    private readonly Stack<UserControl> _stack = new();

    private WindowState _preFullscreen = WindowState.Normal;
    private bool _isFullscreen;

    public MainWindow()
    {
        InitializeComponent();
        Current = this;
        // Never let the phone-shaped window grow past the screen's working area,
        // or the top slides off-screen and looks "pinched". Clamp on open.
        var wa = SystemParameters.WorkArea;
        MaxHeight = wa.Height;
        MaxWidth = wa.Width;
        if (Height > wa.Height) Height = wa.Height - 8;
        if (Width > wa.Width) Width = wa.Width - 8;
        Loaded += (_, _) => Navigate(new HomePage(), root: true);
        KeyDown += (_, e) => { if (e.Key == System.Windows.Input.Key.F11) ToggleFullscreen(); };
    }

    /// <summary>F11 — borderless fullscreen (the phone is edge-to-edge/immersive).</summary>
    public void ToggleFullscreen()
    {
        if (!_isFullscreen)
        {
            _preFullscreen = WindowState;
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.NoResize;
            WindowState = WindowState.Normal;   // force a state change so Maximized re-lays out
            WindowState = WindowState.Maximized;
            _isFullscreen = true;
        }
        else
        {
            WindowStyle = WindowStyle.SingleBorderWindow;
            ResizeMode = ResizeMode.CanResize;
            WindowState = _preFullscreen;
            _isFullscreen = false;
        }
    }

    /// <summary>Push a page (or reset to it as the root).</summary>
    public void Navigate(UserControl page, bool root = false)
    {
        if (root) _stack.Clear();
        _stack.Push(page);
        ContentHost.Content = page;
    }

    /// <summary>Route helper kept for pages that navigate by name.</summary>
    public void Navigate(string route, object? param = null)
    {
        UserControl page = route switch
        {
            "home" => new HomePage(),
            "courses" => new CoursesPage(),
            "course" => new CourseDetailPage((Course)param!),
            "study" => new StudyPage(param as Course),
            "exams" => new ExamsPage(),
            "settings" => new SettingsPage(),
            "guide" => new TutorialPage(),
            "info" => new InfoPage(),
            _ => new HomePage(),
        };
        Navigate(page, root: route == "home");
    }

    /// <summary>Pop to the previous page (no-op at the root).</summary>
    public void Back()
    {
        if (_stack.Count <= 1) return;
        _stack.Pop();
        ContentHost.Content = _stack.Peek();
        (_stack.Peek() as IRefreshable)?.Refresh();
    }

    /// <summary>Re-apply UI text after a live language change.</summary>
    public void ReloadLanguage() => Navigate(new HomePage(), root: true);

    private UIElement? _modal;

    /// <summary>Show a full-window modal layer (e.g. the Guide chooser) over the current page.</summary>
    public void ShowOverlay(UIElement layer)
    {
        HideOverlay();
        _modal = layer;
        RootGrid.Children.Add(layer);
    }

    /// <summary>Dismiss the modal layer, if any.</summary>
    public void HideOverlay()
    {
        if (_modal != null) { RootGrid.Children.Remove(_modal); _modal = null; }
    }

    private CoachOverlay? _coach;

    /// <summary>Start the live "?" walkthrough for the current page (coach marks).</summary>
    public void StartCoach(List<CoachStep> steps)
    {
        if (steps.Count == 0) return;
        StopCoach();
        _coach = new CoachOverlay(steps, StopCoach);
        RootGrid.Children.Add(_coach);
    }

    private void StopCoach()
    {
        if (_coach != null) { RootGrid.Children.Remove(_coach); _coach = null; }
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!ReallyExit) { e.Cancel = true; Hide(); return; }
        base.OnClosing(e);
    }
}

/// <summary>Pages that want to refresh when navigated back to.</summary>
public interface IRefreshable { void Refresh(); }
