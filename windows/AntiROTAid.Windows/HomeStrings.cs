using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// Home-screen text, ported verbatim from the Android ara app (AraI18n.kt) so the
/// Windows home is identical in all 5 languages. Kept as its own table to mirror the
/// exact wording the phone uses.
/// </summary>
public sealed record HomeText(
    string MenuSettings, string MenuGuide, string MenuInfo,
    string Tagline, string HeroTitle, string HeroBody,
    string StartTitle, string StartSub,
    string ExamsTitle, string ExamsSub, string CoursesTitle, string CoursesSub,
    string Open, string ChipOffline, string ChipStudy, string ChipLocal, string Footer,
    string GuideAskTitle, string GuideAskLead, string GuideQuick, string GuideQuickSub,
    string GuideLive, string GuideLiveSub, string GuideBack,
    string OnTitle, string OnSub);

public static class Home
{
    public static HomeText For(string code) => All.TryGetValue(code, out var v) ? v : All["en"];

    private static readonly Dictionary<string, HomeText> All = new()
    {
        ["ro"] = new(
            MenuSettings: "Setări", MenuGuide: "Ghid", MenuInfo: "Info",
            Tagline: "Transformă. Învață. Recâștigă-ți timpul.",
            HeroTitle: "Transformă lecțiile/cursurile\nîn carduri de studiu scrollabile.",
            HeroBody: "Importă lecțiile/cursurile tale, iar AntiROTAid le transformă în carduri clare, concise și ușor de parcurs oricând.",
            StartTitle: "Pornește AntiROTAid Mode",
            StartSub: "Apasă ca să pornești — apoi îți arată carduri peste alte aplicații cât stai pe dispozitiv",
            ExamsTitle: "Examenele mele", ExamsSub: "Examene cu dată, materiale și plan automat de studiu",
            CoursesTitle: "Cursurile mele", CoursesSub: "Toate materialele tale, ca o bibliotecă",
            Open: "Deschide", ChipOffline: "Offline", ChipStudy: "Study Mode", ChipLocal: "Local only",
            Footer: "Totul rămâne pe dispozitiv. Detalii la Info.",
            GuideAskTitle: "Cum vrei ghidul?", GuideAskLead: "Alege cum să-ți arăt aplicația.",
            GuideQuick: "Ghid rapid", GuideQuickSub: "Câteva ecrane cu exemple — pe scurt.",
            GuideLive: "Ghid live", GuideLiveSub: "Îți arăt fiecare buton chiar pe ecran, pas cu pas.",
            GuideBack: "Înapoi",
            OnTitle: "Oprește AntiROTAid Mode", OnSub: "Mod activ — cardurile apar peste alte aplicații"),

        ["en"] = new(
            MenuSettings: "Settings", MenuGuide: "Guide", MenuInfo: "Info",
            Tagline: "Transform. Learn. Reclaim your time.",
            HeroTitle: "Turn your course material\ninto scrollable study cards.",
            HeroBody: "Import your course files and AntiROTAid turns them into clear, concise cards you can go through anytime.",
            StartTitle: "Start AntiROTAid Mode",
            StartSub: "Tap to start — cards then appear over other apps while you're on your device",
            ExamsTitle: "My exams", ExamsSub: "Exams with a date, materials and an automatic study plan",
            CoursesTitle: "My courses", CoursesSub: "All your materials, like a library",
            Open: "Open", ChipOffline: "Offline", ChipStudy: "Study Mode", ChipLocal: "Local only",
            Footer: "Everything stays on your device. Details under Info.",
            GuideAskTitle: "How do you want the guide?", GuideAskLead: "Choose how I show you the app.",
            GuideQuick: "Quick guide", GuideQuickSub: "A few screens with examples — short.",
            GuideLive: "Live walkthrough", GuideLiveSub: "I point at each button right on the screen, step by step.",
            GuideBack: "Back",
            OnTitle: "Stop AntiROTAid Mode", OnSub: "Active — cards appear over other apps"),

        ["de"] = new(
            MenuSettings: "Einstellungen", MenuGuide: "Anleitung", MenuInfo: "Info",
            Tagline: "Umwandeln. Lernen. Zeit zurückholen.",
            HeroTitle: "Verwandle dein Kursmaterial\nin scrollbare Lernkarten.",
            HeroBody: "Importiere deine Kursdateien und AntiROTAid macht daraus klare, knappe Karten, die du jederzeit durchgehen kannst.",
            StartTitle: "AntiROTAid-Modus starten",
            StartSub: "Zum Starten tippen — dann erscheinen Karten über anderen Apps, während du am Gerät bist",
            ExamsTitle: "Meine Prüfungen", ExamsSub: "Prüfungen mit Datum, Materialien und automatischem Lernplan",
            CoursesTitle: "Meine Kurse", CoursesSub: "All deine Materialien, wie eine Bibliothek",
            Open: "Öffnen", ChipOffline: "Offline", ChipStudy: "Lernmodus", ChipLocal: "Nur lokal",
            Footer: "Alles bleibt auf deinem Gerät. Details unter Info.",
            GuideAskTitle: "Wie möchtest du die Anleitung?", GuideAskLead: "Wähle, wie ich dir die App zeige.",
            GuideQuick: "Schnellanleitung", GuideQuickSub: "Ein paar Screens mit Beispielen — kurz.",
            GuideLive: "Live-Anleitung", GuideLiveSub: "Ich zeige dir jeden Button direkt auf dem Bildschirm, Schritt für Schritt.",
            GuideBack: "Zurück",
            OnTitle: "AntiROTAid-Modus stoppen", OnSub: "Aktiv — Karten erscheinen über anderen Apps"),

        ["fr"] = new(
            MenuSettings: "Réglages", MenuGuide: "Guide", MenuInfo: "Infos",
            Tagline: "Transforme. Apprends. Récupère ton temps.",
            HeroTitle: "Transforme ton support de cours\nen cartes d'étude défilables.",
            HeroBody: "Importe tes fichiers de cours et AntiROTAid les transforme en cartes claires et concises, à parcourir à tout moment.",
            StartTitle: "Lancer le mode AntiROTAid",
            StartSub: "Appuie pour lancer — des cartes apparaîtront alors par-dessus les autres applis pendant que tu es sur ton appareil",
            ExamsTitle: "Mes examens", ExamsSub: "Examens avec date, supports et plan d'étude automatique",
            CoursesTitle: "Mes cours", CoursesSub: "Tous tes supports, comme une bibliothèque",
            Open: "Ouvrir", ChipOffline: "Hors ligne", ChipStudy: "Mode étude", ChipLocal: "Local uniquement",
            Footer: "Tout reste sur ton appareil. Détails dans Infos.",
            GuideAskTitle: "Comment veux-tu le guide ?", GuideAskLead: "Choisis comment je te montre l'appli.",
            GuideQuick: "Guide rapide", GuideQuickSub: "Quelques écrans avec des exemples — court.",
            GuideLive: "Visite guidée", GuideLiveSub: "Je pointe chaque bouton directement à l'écran, étape par étape.",
            GuideBack: "Retour",
            OnTitle: "Arrêter le mode AntiROTAid", OnSub: "Actif — les cartes apparaissent par-dessus les autres applis"),

        ["es"] = new(
            MenuSettings: "Ajustes", MenuGuide: "Guía", MenuInfo: "Info",
            Tagline: "Transforma. Aprende. Recupera tu tiempo.",
            HeroTitle: "Convierte el material del curso\nen tarjetas de estudio deslizables.",
            HeroBody: "Importa los archivos de tu curso y AntiROTAid los convierte en tarjetas claras y concisas que puedes repasar cuando quieras.",
            StartTitle: "Iniciar el modo AntiROTAid",
            StartSub: "Toca para iniciar — luego te muestra tarjetas sobre otras apps mientras estás en el dispositivo",
            ExamsTitle: "Mis exámenes", ExamsSub: "Exámenes con fecha, materiales y plan de estudio automático",
            CoursesTitle: "Mis cursos", CoursesSub: "Todos tus materiales, como una biblioteca",
            Open: "Abrir", ChipOffline: "Sin conexión", ChipStudy: "Modo estudio", ChipLocal: "Solo local",
            Footer: "Todo se queda en tu dispositivo. Detalles en Info.",
            GuideAskTitle: "¿Cómo quieres la guía?", GuideAskLead: "Elige cómo te muestro la app.",
            GuideQuick: "Guía rápida", GuideQuickSub: "Unas pantallas con ejemplos — breve.",
            GuideLive: "Recorrido en vivo", GuideLiveSub: "Te señalo cada botón en la pantalla, paso a paso.",
            GuideBack: "Atrás",
            OnTitle: "Detener el modo AntiROTAid", OnSub: "Activo — las tarjetas aparecen sobre otras apps"),
    };
}
