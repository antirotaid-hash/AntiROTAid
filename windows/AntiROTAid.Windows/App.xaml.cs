using System;
using System.Linq;
using System.Windows;
using AntiROTAid.Windows.Services;
using AntiROTAid.Windows.Themes;

namespace AntiROTAid.Windows;

public partial class App : Application
{
    public static ModeController? Controller { get; private set; }
    public static CourseStore Courses { get; private set; } = new();
    public static ExamStore Exams { get; private set; } = new();
    /// <summary>Set on a genuine first run (right after onboarding/consent) so the Home screen
    /// plays the first-run tutorial once; HomePage clears it and persists TutorialSeen.</summary>
    public static bool FirstRunTutorialPending { get; set; }
    private TrayIcon? _tray;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Surface any unhandled exception to a log file (and a dialog) instead of a
        // silent WER crash, so failures are diagnosable.
        var log = System.IO.Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "AntiROTAid", "crash.log");
        DispatcherUnhandledException += (_, ev) =>
        {
            try
            {
                System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(log)!);
                System.IO.File.WriteAllText(log, ev.Exception.ToString());
            }
            catch { }
            MessageBox.Show(ev.Exception.Message, I18n.T.ErrorTitle);
            ev.Handled = true;
        };

        // 1. Brand look first, so the first paint is already ARA.
        var settings = Settings.Load();
        AraTheme.Apply(settings.Theme);

        // 1b. First-run onboarding (language -> disclaimer -> privacy/consent),
        //     identical in flow to the Android app. Shown once, before anything else.
        if (!settings.LanguageChosen)
        {
            var onboard = new OnboardingWindow(settings.Language);
            onboard.ShowDialog();
            settings.Language = onboard.ChosenCode;
            settings.LanguageChosen = true;
            settings.Save();
            // Genuine first run: play the tutorial once after consent (Part 7).
            FirstRunTutorialPending = true;
        }
        else if (!settings.TutorialSeen)
        {
            // Migration: existing users (installed before the tutorial flag) have already
            // used the app — mark it seen so we never surprise them with the walkthrough.
            settings.TutorialSeen = true;
            settings.Save();
        }
        I18n.Set(settings.Language);

        // 2. Local course + exam stores (demo lessons + user data + progress). No cloud.
        Courses.Load();
        Exams.Load();
        // Auto-delete after deadline: if the user turned it on, purge past-date exams (and
        // any user course thereby left with no exam) on every launch, as the setting promises.
        if (settings.AutoDelete) AutoDeleteService.Run(Courses, Exams);

        // 3. AntiROTAid Mode controller. The overlay targets the PERSISTED active course
        //    (the one "Done → next course" advanced to), NOT just the first — otherwise a
        //    restart would silently reset the user back to the first course. ActiveCourse
        //    already falls back to the first course-with-cards when nothing is saved yet.
        Controller = new ModeController { IntervalMinutes = settings.IntervalMinutes };
        var active = Courses.ActiveCourse;
        if (active is not null) Controller.SetActiveCourse(active);

        // 3. Main window + system tray. Closing the window hides to tray; the app
        //    only really exits from tray → Exit (ShutdownMode = OnExplicitShutdown).
        var main = new MainWindow();
        _tray = new TrayIcon(main, Controller);
        main.Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Controller?.Stop();
        _tray?.Dispose();
        base.OnExit(e);
    }
}
