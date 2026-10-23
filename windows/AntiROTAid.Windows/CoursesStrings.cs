using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// "My courses" screen text, ported verbatim from the Android ara app (AraI18n.kt)
/// so the Windows course library is identical in all 5 languages.
/// </summary>
public sealed record CoursesText(
    string Title, string AddCourse, string LibraryFmt,
    string SortBy, string SortName, string SortRecent, string SortProgress, string SortExam,
    string TagMaterial, string CourseDetails, string Details, string OpenCards,
    string UnitCards, string UnitSlides, string Learned, string NotAssigned, string InExamsLabel,
    string NewCourseName, string PasteText, string Create, string Cancel, string ImportFile);

public static class CoursesI18n
{
    // The "Open the cards" sublabel, localized for full 5-language parity.
    public static string ScrollSub(string code) => code switch
    {
        "ro" => "derulează cardurile",
        "de" => "Lernkarten scrollen",
        "fr" => "fais défiler les cartes",
        "es" => "desplázate por las tarjetas",
        _    => "scroll study feed",
    };

    public static CoursesText For(string code) => All.TryGetValue(code, out var v) ? v : All["en"];

    private static readonly Dictionary<string, CoursesText> All = new()
    {
        ["ro"] = new("Cursurile mele", "＋  Adaugă curs", "Biblioteca ta · {0} materiale",
            "Sortează", "Nume", "Recente", "Progres", "Examen",
            "MATERIAL", "Detalii curs", "Detalii", "Deschide cardurile",
            "carduri", "slide-uri", "învățat", "Neatribuit niciunui examen", "În examen:",
            "Numele cursului", "Lipește aici textul lecției…", "Creează", "Anulează", "⇪  Importă fișier (PDF/PPTX/Word/poze)"),

        ["en"] = new("My courses", "＋  Add course", "Your library · {0} materials",
            "Sort", "Name", "Recent", "Progress", "Exam",
            "MATERIAL", "Course details", "Details", "Open the cards",
            "cards", "slides", "learned", "Not assigned to any exam", "In exam:",
            "Course name", "Paste your lesson text here…", "Create", "Cancel", "⇪  Import file (PDF/PPTX/Word/images)"),

        ["de"] = new("Meine Kurse", "＋  Kurs hinzufügen", "Deine Bibliothek · {0} Materialien",
            "Sortieren", "Name", "Neueste", "Fortschritt", "Prüfung",
            "MATERIAL", "Kursdetails", "Details", "Karten öffnen",
            "Karten", "Folien", "gelernt", "Keiner Prüfung zugeordnet", "In Prüfung:",
            "Kursname", "Füge hier deinen Lektionstext ein…", "Erstellen", "Abbrechen", "⇪  Datei importieren (PDF/PPTX/Word/Bilder)"),

        ["fr"] = new("Mes cours", "＋  Ajouter un cours", "Ta bibliothèque · {0} supports",
            "Trier", "Nom", "Récents", "Progrès", "Examen",
            "MATERIAL", "Détails du cours", "Détails", "Ouvrir les cartes",
            "cartes", "diapos", "appris", "Assigné à aucun examen", "Dans l'examen :",
            "Nom du cours", "Colle ici le texte de ta leçon…", "Créer", "Annuler", "⇪  Importer un fichier (PDF/PPTX/Word/images)"),

        ["es"] = new("Mis cursos", "＋  Añadir curso", "Tu biblioteca · {0} materiales",
            "Ordenar", "Nombre", "Recientes", "Progreso", "Examen",
            "MATERIAL", "Detalles del curso", "Detalles", "Abrir las tarjetas",
            "tarjetas", "diapositivas", "aprendido", "Sin examen asignado", "En examen:",
            "Nombre del curso", "Pega aquí el texto de tu lección…", "Crear", "Cancelar", "⇪  Importar archivo (PDF/PPTX/Word/imágenes)"),
    };
}
