using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace AntiROTAid.Windows.Views;

public partial class HomePage : UserControl, IRefreshable
{
    public HomePage()
    {
        InitializeComponent();
        var h = Home.For(I18n.Locale);
        SettingsPill.Content = h.MenuSettings;
        GuidePill.Content = h.MenuGuide;
        InfoPill.Content = h.MenuInfo;
        Tagline.Text = h.Tagline;
        Hero.Text = h.HeroTitle;
        HeroBody.Text = h.HeroBody;
        StartTitle.Text = h.StartTitle;
        StartSub.Text = h.StartSub;
        ExamsTitle.Text = h.ExamsTitle;
        ExamsSub.Text = h.ExamsSub;
        ExamsOpen.Text = h.Open;
        CoursesTitle.Text = h.CoursesTitle;
        CoursesSub.Text = h.CoursesSub;
        CoursesOpen.Text = h.Open;
        Chip1.Text = h.ChipOffline;
        Chip2.Text = h.ChipStudy;
        Chip3.Text = h.ChipLocal;
        Footer.Text = h.Footer;

        if (App.Controller is { } c) c.StateChanged += Refresh;
        Loaded += (_, _) =>
        {
            Refresh();
            MaybeRunFirstRunTutorial();
        };
    }

    /// <summary>On a genuine first run (right after onboarding/consent) play the home
    /// walkthrough once, then persist TutorialSeen so it never auto-shows again. Deferred to
    /// Loaded/Background so control anchors are laid out before the coach points at them.</summary>
    private void MaybeRunFirstRunTutorial()
    {
        if (!App.FirstRunTutorialPending) return;
        App.FirstRunTutorialPending = false;
        var s = Settings.Load(); s.TutorialSeen = true; s.Save();
        Dispatcher.BeginInvoke(new System.Action(StartHomeCoach),
            System.Windows.Threading.DispatcherPriority.Background);
    }

    public void Refresh()
    {
        var h = Home.For(I18n.Locale);
        bool active = App.Controller?.IsActive ?? false;
        if (active)
        {
            // ON — red gradient, white text (mirrors the phone's OverlayControl "on" state).
            StartCard.Background = new LinearGradientBrush(
                Color.FromRgb(0xEF, 0x53, 0x50), Color.FromRgb(0xD6, 0x45, 0x45), 0);
            StartTitle.Text = h.OnTitle;
            StartSub.Text = h.OnSub;
            StartTitle.Foreground = Brushes.White;
            StartSub.Foreground = new SolidColorBrush(Color.FromArgb(0xDB, 0xFF, 0xFF, 0xFF));
        }
        else
        {
            // OFF — gold AraBrush, dark text.
            StartCard.Background = (Brush)FindResource("AraBrush");
            StartTitle.Text = h.StartTitle;
            StartSub.Text = h.StartSub;
            StartTitle.Foreground = new SolidColorBrush(Color.FromRgb(0x16, 0x11, 0x0A));
            StartSub.Foreground = new SolidColorBrush(Color.FromRgb(0x37, 0x24, 0x00));
        }
    }

    // Single toggle: start when off, stop when on.
    private void Start_Click(object sender, RoutedEventArgs e)
    {
        if (App.Controller?.IsActive == true) App.Controller?.Stop();
        else App.Controller?.Start();
    }
    private void Settings_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Navigate("settings");

    // The "Ghid" pill opens the guide chooser (ports Android AraGuideChooser): pick the
    // slide-based quick guide, or the live point-at-each-button walkthrough.
    private void Guide_Click(object sender, RoutedEventArgs e)
    {
        var h = Home.For(I18n.Locale);
        MainWindow.Current?.ShowOverlay(new GuideChooser(h,
            onQuick: () => { MainWindow.Current?.HideOverlay(); MainWindow.Current?.Navigate("guide"); },
            onLive: () => { MainWindow.Current?.HideOverlay(); StartHomeCoach(); },
            onDismiss: () => MainWindow.Current?.HideOverlay()));
    }

    // The live coach-mark walkthrough of the home screen — points at each control in turn.
    private void StartHomeCoach()
    {
        var c = CoachI18n.For(I18n.Locale);
        MainWindow.Current?.StartCoach(new System.Collections.Generic.List<CoachStep>
        {
            new(SettingsPill, c.Settings),
            new(GuidePill, c.Guide),
            new(InfoPill, c.Info),
            new(StartCard, c.Overlay),
            new(ExamsOpen, c.Exams),
            new(CoursesOpen, c.Courses),
        });
    }
    private void Info_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Navigate("info");
    private void Exams_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Navigate("exams");
    private void Courses_Click(object sender, RoutedEventArgs e) => MainWindow.Current?.Navigate("courses");
}
