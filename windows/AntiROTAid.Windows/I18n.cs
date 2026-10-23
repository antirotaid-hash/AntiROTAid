using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// UI strings for the full AntiROTAid app, in the same 5 languages as the Android
/// app (RO/EN/DE/FR/ES). Uses init-properties so each language block is explicit and
/// cannot be mis-ordered. Switch language live via <see cref="I18n.Set"/>.
/// </summary>
public sealed record Strings
{
    // Brand / nav
    public required string AppName { get; init; }
    public required string NavHome { get; init; }
    public required string NavCourses { get; init; }
    public required string NavStudy { get; init; }
    public required string NavExams { get; init; }
    public required string NavTutorial { get; init; }
    public required string NavSettings { get; init; }

    // Home
    public required string Tagline { get; init; }
    public required string HomeHeadline { get; init; }
    public required string HomeSub { get; init; }
    public required string StatusActive { get; init; }
    public required string StatusInactive { get; init; }
    public required string StatusActiveSub { get; init; }
    public required string StatusInactiveSub { get; init; }
    public required string StartMode { get; init; }
    public required string StopMode { get; init; }
    public required string StartModeSub { get; init; }
    public required string MyExams { get; init; }
    public required string MyExamsSub { get; init; }
    public required string MyCourses { get; init; }
    public required string MyCoursesSub { get; init; }
    public required string Open { get; init; }
    public required string ChipOffline { get; init; }
    public required string ChipStudy { get; init; }
    public required string ChipLocal { get; init; }
    public required string PrivacyMain { get; init; }
    public required string PrivacySub { get; init; }

    // Courses
    public required string CoursesTitle { get; init; }
    public required string CoursesSub { get; init; }
    public required string AddCourse { get; init; }
    public required string NewCourseName { get; init; }
    public required string NewCourseText { get; init; }
    public required string Create { get; init; }
    public required string Cancel { get; init; }
    public required string NoCourses { get; init; }
    public required string CardsLabel { get; init; }     // "{0} carduri"
    public required string DemoBadge { get; init; }
    public required string Delete { get; init; }
    public required string StudyBtn { get; init; }

    // Course detail
    public required string CardsHeader { get; init; }
    public required string Back { get; init; }

    // Study
    public required string StudyTitle { get; init; }
    public required string Prev { get; init; }
    public required string Next { get; init; }
    public required string Known { get; init; }
    public required string Repeat { get; init; }
    public required string CardOf { get; init; }         // "{0} / {1}"
    public required string PickCourse { get; init; }

    // Exams
    public required string ExamsTitle { get; init; }
    public required string ExamsSub { get; init; }
    public required string AddExam { get; init; }
    public required string ExamName { get; init; }
    public required string NoExams { get; init; }
    public required string Days { get; init; }           // "zile" / "days" / ...

    // Tutorial
    public required string TutorialTitle { get; init; }
    public required string TutBody { get; init; }

    // Settings
    public required string SettingsTitle { get; init; }
    public required string Theme { get; init; }
    public required string Language { get; init; }
    public required string Interval { get; init; }
    public required string Minutes { get; init; }

    // Error dialog title
    public required string ErrorTitle { get; init; }

    // File picker + default (untitled) names
    public required string FilePickerCourseFiles { get; init; }   // filter group label
    public required string FilePickerAllFiles { get; init; }
    public required string UntitledCourse { get; init; }          // fallback when name is blank
    public required string UntitledExam { get; init; }

    // Overlay + tray
    public required string OverlayHeader { get; init; }
    public required string Continue { get; init; }
    public required string OverlayStop { get; init; }
    public required string TrayStart { get; init; }
    public required string TrayStop { get; init; }
    public required string TrayShow { get; init; }
    public required string TrayExit { get; init; }
}

public static class I18n
{
    public static string Locale { get; private set; } = "ro";
    public static Strings T { get; private set; }

    static I18n() => T = All["ro"];

    public static readonly List<(string Code, string Name)> Languages = new()
    {
        ("ro", "Română"), ("en", "English"), ("de", "Deutsch"),
        ("fr", "Français"), ("es", "Español"),
    };

    public static void Set(string locale)
    {
        Locale = All.ContainsKey(locale) ? locale : "en";
        T = All[Locale];
    }

    private static readonly Dictionary<string, Strings> All = new()
    {
        ["ro"] = new Strings
        {
            AppName = "AntiROTAid",
            NavHome = "Acasă", NavCourses = "Cursuri", NavStudy = "Studiu",
            NavExams = "Examene", NavTutorial = "Ghid", NavSettings = "Setări",
            Tagline = "Transformă. Învață. Recuperează-ți timpul.",
            HomeHeadline = "Transformă-ți materialul în carduri de studiu.",
            HomeSub = "Alege un curs și AntiROTAid ți-l arată în carduri clare și scurte, pe care le parcurgi oricând.",
            StatusActive = "Activ", StatusInactive = "Inactiv",
            StatusActiveSub = "Cardurile apar peste ecran, la interval.",
            StatusInactiveSub = "Modul AntiROTAid este oprit.",
            StartMode = "Pornește AntiROTAid Mode", StopMode = "Oprește AntiROTAid Mode",
            StartModeSub = "Cardurile apar apoi peste alte aplicații, cât ești la calculator.",
            MyExams = "Examenele mele", MyExamsSub = "Examene cu dată, materiale și un plan automat de studiu.",
            MyCourses = "Cursurile mele", MyCoursesSub = "Toate materialele tale, ca o bibliotecă.",
            Open = "Deschide",
            ChipOffline = "Offline", ChipStudy = "Mod studiu", ChipLocal = "Doar local",
            PrivacyMain = "Fără cont. Fără tracking. Doar local.",
            PrivacySub = "Totul rămâne pe acest dispozitiv. Detalii la Ghid.",
            CoursesTitle = "Cursurile mele", CoursesSub = "Materialele tale, transformate în carduri.",
            AddCourse = "Adaugă curs (text)", NewCourseName = "Numele cursului",
            NewCourseText = "Lipește aici textul lecției…", Create = "Creează", Cancel = "Renunță",
            NoCourses = "Niciun curs încă. Începe cu unul demo sau adaugă text.",
            CardsLabel = "{0} carduri", DemoBadge = "DEMO", Delete = "Șterge", StudyBtn = "Studiază",
            CardsHeader = "Carduri", Back = "Înapoi",
            StudyTitle = "Studiu", Prev = "Înapoi", Next = "Înainte",
            Known = "Știu", Repeat = "Repetă", CardOf = "{0} / {1}",
            PickCourse = "Alege un curs de la Cursuri ca să studiezi.",
            ExamsTitle = "Examenele mele", ExamsSub = "Adaugă un examen cu dată ca să-ți faci un plan.",
            AddExam = "Adaugă examen", ExamName = "Numele examenului", NoExams = "Niciun examen încă.", Days = "zile",
            TutorialTitle = "Ghid și confidențialitate",
            TutBody = "AntiROTAid îți transformă cursurile în carduri scurte și ți le arată peste alte aplicații cât ești la calculator — local, offline. Alege un curs la Cursuri, apasă Studiază și parcurge cardurile. Pornește AntiROTAid Mode de pe Acasă ca să primești carduri peste ecran la interval. Nimic nu pleacă de pe dispozitivul tău.",
            SettingsTitle = "Setări", Theme = "Temă", Language = "Limbă",
            Interval = "Interval între carduri (minute)", Minutes = "min",
            ErrorTitle = "AntiROTAid — eroare",
            FilePickerCourseFiles = "Fișiere de curs", FilePickerAllFiles = "Toate fișierele",
            UntitledCourse = "Curs nou", UntitledExam = "Examen",
            OverlayHeader = "un moment de învățare", Continue = "Continuă", OverlayStop = "Oprește modul",
            TrayStart = "Pornește modul", TrayStop = "Oprește modul", TrayShow = "Deschide aplicația", TrayExit = "Ieșire",
        },

        ["en"] = new Strings
        {
            AppName = "AntiROTAid",
            NavHome = "Home", NavCourses = "Courses", NavStudy = "Study",
            NavExams = "Exams", NavTutorial = "Guide", NavSettings = "Settings",
            Tagline = "Transform. Learn. Reclaim your time.",
            HomeHeadline = "Turn your material into study cards.",
            HomeSub = "Pick a course and AntiROTAid shows it as clear, short cards you can go through anytime.",
            StatusActive = "Active", StatusInactive = "Inactive",
            StatusActiveSub = "Cards appear over your screen on an interval.",
            StatusInactiveSub = "AntiROTAid Mode is off.",
            StartMode = "Start AntiROTAid Mode", StopMode = "Stop AntiROTAid Mode",
            StartModeSub = "Cards then appear over other apps while you're at your computer.",
            MyExams = "My exams", MyExamsSub = "Exams with a date, materials and an automatic study plan.",
            MyCourses = "My courses", MyCoursesSub = "All your materials, like a library.",
            Open = "Open",
            ChipOffline = "Offline", ChipStudy = "Study Mode", ChipLocal = "Local only",
            PrivacyMain = "No account. No tracking. Local only.",
            PrivacySub = "Everything stays on this device. Details under Guide.",
            CoursesTitle = "My courses", CoursesSub = "Your materials, turned into cards.",
            AddCourse = "Add course (text)", NewCourseName = "Course name",
            NewCourseText = "Paste your lesson text here…", Create = "Create", Cancel = "Cancel",
            NoCourses = "No courses yet. Start with a demo or add text.",
            CardsLabel = "{0} cards", DemoBadge = "DEMO", Delete = "Delete", StudyBtn = "Study",
            CardsHeader = "Cards", Back = "Back",
            StudyTitle = "Study", Prev = "Previous", Next = "Next",
            Known = "Known", Repeat = "Repeat", CardOf = "{0} / {1}",
            PickCourse = "Pick a course from Courses to study.",
            ExamsTitle = "My exams", ExamsSub = "Add an exam with a date to build a plan.",
            AddExam = "Add exam", ExamName = "Exam name", NoExams = "No exams yet.", Days = "days",
            TutorialTitle = "Guide and privacy",
            TutBody = "AntiROTAid turns your courses into short cards and shows them over other apps while you're at your computer — local, offline. Pick a course under Courses, press Study and go through the cards. Start AntiROTAid Mode from Home to get cards over your screen on an interval. Nothing leaves your device.",
            SettingsTitle = "Settings", Theme = "Theme", Language = "Language",
            Interval = "Interval between cards (minutes)", Minutes = "min",
            ErrorTitle = "AntiROTAid — error",
            FilePickerCourseFiles = "Course files", FilePickerAllFiles = "All files",
            UntitledCourse = "New course", UntitledExam = "Exam",
            OverlayHeader = "a moment to learn", Continue = "Continue", OverlayStop = "Stop mode",
            TrayStart = "Start Mode", TrayStop = "Stop Mode", TrayShow = "Show App", TrayExit = "Exit",
        },

        ["de"] = new Strings
        {
            AppName = "AntiROTAid",
            NavHome = "Start", NavCourses = "Kurse", NavStudy = "Lernen",
            NavExams = "Prüfungen", NavTutorial = "Anleitung", NavSettings = "Einstellungen",
            Tagline = "Verwandeln. Lernen. Hol dir deine Zeit zurück.",
            HomeHeadline = "Mach aus deinem Material Lernkarten.",
            HomeSub = "Wähle einen Kurs und AntiROTAid zeigt ihn als klare, kurze Karten, die du jederzeit durchgehen kannst.",
            StatusActive = "Aktiv", StatusInactive = "Inaktiv",
            StatusActiveSub = "Karten erscheinen in Intervallen über dem Bildschirm.",
            StatusInactiveSub = "AntiROTAid-Modus ist aus.",
            StartMode = "AntiROTAid Mode starten", StopMode = "AntiROTAid Mode stoppen",
            StartModeSub = "Karten erscheinen dann über anderen Apps, während du am Computer bist.",
            MyExams = "Meine Prüfungen", MyExamsSub = "Prüfungen mit Datum, Material und automatischem Lernplan.",
            MyCourses = "Meine Kurse", MyCoursesSub = "All deine Materialien, wie eine Bibliothek.",
            Open = "Öffnen",
            ChipOffline = "Offline", ChipStudy = "Lernmodus", ChipLocal = "Nur lokal",
            PrivacyMain = "Kein Konto. Kein Tracking. Nur lokal.",
            PrivacySub = "Alles bleibt auf diesem Gerät. Details unter Anleitung.",
            CoursesTitle = "Meine Kurse", CoursesSub = "Deine Materialien als Karten.",
            AddCourse = "Kurs hinzufügen (Text)", NewCourseName = "Kursname",
            NewCourseText = "Füge hier deinen Lektionstext ein…", Create = "Erstellen", Cancel = "Abbrechen",
            NoCourses = "Noch keine Kurse. Beginne mit einem Demo oder füge Text hinzu.",
            CardsLabel = "{0} Karten", DemoBadge = "DEMO", Delete = "Löschen", StudyBtn = "Lernen",
            CardsHeader = "Karten", Back = "Zurück",
            StudyTitle = "Lernen", Prev = "Zurück", Next = "Weiter",
            Known = "Gewusst", Repeat = "Wiederholen", CardOf = "{0} / {1}",
            PickCourse = "Wähle einen Kurs unter Kurse zum Lernen.",
            ExamsTitle = "Meine Prüfungen", ExamsSub = "Füge eine Prüfung mit Datum hinzu, um einen Plan zu erstellen.",
            AddExam = "Prüfung hinzufügen", ExamName = "Prüfungsname", NoExams = "Noch keine Prüfungen.", Days = "Tage",
            TutorialTitle = "Anleitung und Datenschutz",
            TutBody = "AntiROTAid macht aus deinen Kursen kurze Karten und zeigt sie über anderen Apps, während du am Computer bist — lokal, offline. Wähle einen Kurs unter Kurse, drücke Lernen und geh die Karten durch. Starte den AntiROTAid-Modus von Start, um Karten in Intervallen über dem Bildschirm zu erhalten. Nichts verlässt dein Gerät.",
            SettingsTitle = "Einstellungen", Theme = "Design", Language = "Sprache",
            Interval = "Intervall zwischen Karten (Minuten)", Minutes = "Min",
            ErrorTitle = "AntiROTAid — Fehler",
            FilePickerCourseFiles = "Kursdateien", FilePickerAllFiles = "Alle Dateien",
            UntitledCourse = "Neuer Kurs", UntitledExam = "Prüfung",
            OverlayHeader = "ein Moment zum Lernen", Continue = "Weiter", OverlayStop = "Modus stoppen",
            TrayStart = "Modus starten", TrayStop = "Modus stoppen", TrayShow = "App öffnen", TrayExit = "Beenden",
        },

        ["fr"] = new Strings
        {
            AppName = "AntiROTAid",
            NavHome = "Accueil", NavCourses = "Cours", NavStudy = "Étude",
            NavExams = "Examens", NavTutorial = "Guide", NavSettings = "Réglages",
            Tagline = "Transforme. Apprends. Récupère ton temps.",
            HomeHeadline = "Transforme ton matériel en cartes d'étude.",
            HomeSub = "Choisis un cours et AntiROTAid te le montre en cartes claires et courtes, à parcourir à tout moment.",
            StatusActive = "Actif", StatusInactive = "Inactif",
            StatusActiveSub = "Les cartes apparaissent à l'écran par intervalles.",
            StatusInactiveSub = "Le mode AntiROTAid est désactivé.",
            StartMode = "Démarrer AntiROTAid Mode", StopMode = "Arrêter AntiROTAid Mode",
            StartModeSub = "Les cartes apparaissent ensuite par-dessus les autres applis pendant que tu es à l'ordinateur.",
            MyExams = "Mes examens", MyExamsSub = "Examens avec date, supports et un plan d'étude automatique.",
            MyCourses = "Mes cours", MyCoursesSub = "Tous tes supports, comme une bibliothèque.",
            Open = "Ouvrir",
            ChipOffline = "Hors ligne", ChipStudy = "Mode étude", ChipLocal = "Local uniquement",
            PrivacyMain = "Pas de compte. Pas de suivi. Local uniquement.",
            PrivacySub = "Tout reste sur cet appareil. Détails sous Guide.",
            CoursesTitle = "Mes cours", CoursesSub = "Tes supports, transformés en cartes.",
            AddCourse = "Ajouter un cours (texte)", NewCourseName = "Nom du cours",
            NewCourseText = "Colle ici le texte de ta leçon…", Create = "Créer", Cancel = "Annuler",
            NoCourses = "Aucun cours. Commence par un démo ou ajoute du texte.",
            CardsLabel = "{0} cartes", DemoBadge = "DÉMO", Delete = "Supprimer", StudyBtn = "Étudier",
            CardsHeader = "Cartes", Back = "Retour",
            StudyTitle = "Étude", Prev = "Précédent", Next = "Suivant",
            Known = "Su", Repeat = "À revoir", CardOf = "{0} / {1}",
            PickCourse = "Choisis un cours dans Cours pour étudier.",
            ExamsTitle = "Mes examens", ExamsSub = "Ajoute un examen avec une date pour créer un plan.",
            AddExam = "Ajouter un examen", ExamName = "Nom de l'examen", NoExams = "Aucun examen.", Days = "jours",
            TutorialTitle = "Guide et confidentialité",
            TutBody = "AntiROTAid transforme tes cours en cartes courtes et te les montre par-dessus les autres applis pendant que tu es à l'ordinateur — local, hors ligne. Choisis un cours dans Cours, appuie sur Étudier et parcours les cartes. Démarre AntiROTAid Mode depuis l'Accueil pour recevoir des cartes à l'écran par intervalles. Rien ne quitte ton appareil.",
            SettingsTitle = "Réglages", Theme = "Thème", Language = "Langue",
            Interval = "Intervalle entre les cartes (minutes)", Minutes = "min",
            ErrorTitle = "AntiROTAid — erreur",
            FilePickerCourseFiles = "Fichiers de cours", FilePickerAllFiles = "Tous les fichiers",
            UntitledCourse = "Nouveau cours", UntitledExam = "Examen",
            OverlayHeader = "un moment pour apprendre", Continue = "Continuer", OverlayStop = "Arrêter le mode",
            TrayStart = "Démarrer", TrayStop = "Arrêter", TrayShow = "Ouvrir l'app", TrayExit = "Quitter",
        },

        ["es"] = new Strings
        {
            AppName = "AntiROTAid",
            NavHome = "Inicio", NavCourses = "Cursos", NavStudy = "Estudio",
            NavExams = "Exámenes", NavTutorial = "Guía", NavSettings = "Ajustes",
            Tagline = "Transforma. Aprende. Recupera tu tiempo.",
            HomeHeadline = "Convierte tu material en tarjetas de estudio.",
            HomeSub = "Elige un curso y AntiROTAid te lo muestra en tarjetas claras y cortas que repasas cuando quieras.",
            StatusActive = "Activo", StatusInactive = "Inactivo",
            StatusActiveSub = "Las tarjetas aparecen sobre la pantalla por intervalos.",
            StatusInactiveSub = "El modo AntiROTAid está apagado.",
            StartMode = "Iniciar AntiROTAid Mode", StopMode = "Detener AntiROTAid Mode",
            StartModeSub = "Las tarjetas aparecen luego sobre otras apps mientras estás en el ordenador.",
            MyExams = "Mis exámenes", MyExamsSub = "Exámenes con fecha, materiales y un plan de estudio automático.",
            MyCourses = "Mis cursos", MyCoursesSub = "Todos tus materiales, como una biblioteca.",
            Open = "Abrir",
            ChipOffline = "Sin conexión", ChipStudy = "Modo estudio", ChipLocal = "Solo local",
            PrivacyMain = "Sin cuenta. Sin rastreo. Solo local.",
            PrivacySub = "Todo se queda en este dispositivo. Detalles en Guía.",
            CoursesTitle = "Mis cursos", CoursesSub = "Tus materiales, convertidos en tarjetas.",
            AddCourse = "Añadir curso (texto)", NewCourseName = "Nombre del curso",
            NewCourseText = "Pega aquí el texto de tu lección…", Create = "Crear", Cancel = "Cancelar",
            NoCourses = "Aún no hay cursos. Empieza con un demo o añade texto.",
            CardsLabel = "{0} tarjetas", DemoBadge = "DEMO", Delete = "Eliminar", StudyBtn = "Estudiar",
            CardsHeader = "Tarjetas", Back = "Atrás",
            StudyTitle = "Estudio", Prev = "Anterior", Next = "Siguiente",
            Known = "Lo sé", Repeat = "Repasar", CardOf = "{0} / {1}",
            PickCourse = "Elige un curso en Cursos para estudiar.",
            ExamsTitle = "Mis exámenes", ExamsSub = "Añade un examen con fecha para crear un plan.",
            AddExam = "Añadir examen", ExamName = "Nombre del examen", NoExams = "Aún no hay exámenes.", Days = "días",
            TutorialTitle = "Guía y privacidad",
            TutBody = "AntiROTAid convierte tus cursos en tarjetas cortas y te las muestra sobre otras apps mientras estás en el ordenador — local, sin conexión. Elige un curso en Cursos, pulsa Estudiar y repasa las tarjetas. Inicia AntiROTAid Mode desde Inicio para recibir tarjetas sobre la pantalla por intervalos. Nada sale de tu dispositivo.",
            SettingsTitle = "Ajustes", Theme = "Tema", Language = "Idioma",
            Interval = "Intervalo entre tarjetas (minutos)", Minutes = "min",
            ErrorTitle = "AntiROTAid — error",
            FilePickerCourseFiles = "Archivos de curso", FilePickerAllFiles = "Todos los archivos",
            UntitledCourse = "Curso nuevo", UntitledExam = "Examen",
            OverlayHeader = "un momento para aprender", Continue = "Continuar", OverlayStop = "Detener modo",
            TrayStart = "Iniciar modo", TrayStop = "Detener modo", TrayShow = "Abrir app", TrayExit = "Salir",
        },
    };
}
