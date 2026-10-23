using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Views;

namespace AntiROTAid.Windows.Overlay;

/// <summary>
/// The floating study overlay — the Windows equivalent of the phone's AraOverlayContent,
/// which hosts the WHOLE AraCardFeed over the screen. Rather than a single card, this is a
/// borderless, always-on-top, full-screen window that reuses <see cref="StudyPage"/> (the
/// same whole-course scrollable feed the app uses): resume, progress bar, per-card
/// Next / Save-spot-&amp;-exit CTAs, text-size / theme / remove toolbar. "Save spot &amp; exit"
/// or ← closes this appearance (the mode stays on and it floats up again next interval);
/// the user turns the mode off from Home.
/// </summary>
public sealed class OverlayFeedWindow : Window
{
    public OverlayFeedWindow(Course course)
    {
        Title = "AntiROTAid";
        WindowStyle = WindowStyle.None;
        ResizeMode = ResizeMode.NoResize;
        ShowInTaskbar = false;
        Topmost = true;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        WindowState = WindowState.Maximized;   // edge-to-edge, like the phone overlay
        Background = (Brush)Application.Current.FindResource("AraBgGradient");
        FontFamily = (System.Windows.Media.FontFamily)Application.Current.FindResource("AppFont");

        // Reuse the exact study feed; exiting it closes this overlay window.
        Content = new StudyPage(course, onExit: Close);

        // Esc is a quick dismiss (same as the feed's exit CTA).
        KeyDown += (_, e) => { if (e.Key == Key.Escape) Close(); };
    }
}
