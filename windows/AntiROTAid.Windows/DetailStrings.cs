using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// Course-detail + "How cards appear" (material settings) text, ported verbatim from
/// the Android ara app (AraI18n.kt) so the Windows detail screen is identical in all
/// 5 languages. The choice labels/descriptions are keyed by the engine's stable choice
/// keys (pdf_as_is, pdf_paragraphs, …) exactly like the phone's araChoiceLabel/Desc.
/// </summary>
public sealed record DetailText(
    string CardsLabel, string DetailProgress, string DetailFullPasses,
    string LearnedPace, string LearnedPaceFmt, string Learning, string ResumeHint,
    string DeleteCourse, string ConfirmAgain, string ConfirmDeletePermanent,
    string CourseCardsFmt, string VisualSlideTag, string CardFallback,
    string HowCardsAppear, string CoachModesBody, string OptAppearance,
    string ChPdfPages, string ChPdfParagraphs, string ChPptxAsIs, string ChDocxParagraphs, string ChImageAsIs,
    string DescPdfPages, string DescPdfParagraphs, string DescPptxAsIs, string DescDocxParagraphs, string DescImageAsIs)
{
    public string ChoiceLabel(string key) => key switch
    {
        "pdf_as_is" => ChPdfPages,
        "pdf_paragraphs" => ChPdfParagraphs,
        "pptx_as_is" => ChPptxAsIs,
        "docx_paragraphs" => ChDocxParagraphs,
        "image_as_is" => ChImageAsIs,
        _ => key,
    };

    public string ChoiceDesc(string key) => key switch
    {
        "pdf_as_is" => DescPdfPages,
        "pdf_paragraphs" => DescPdfParagraphs,
        "pptx_as_is" => DescPptxAsIs,
        "docx_paragraphs" => DescDocxParagraphs,
        "image_as_is" => DescImageAsIs,
        _ => "",
    };
}

public static class DetailI18n
{
    public static DetailText For(string code) => All.TryGetValue(code, out var v) ? v : All["en"];

    private static readonly Dictionary<string, DetailText> All = new()
    {
        ["ro"] = new(
            "Carduri", "Progres", "Parcurgeri complete", "Ritm învățat", "~%d carduri/sesiune", "se învață…",
            "Continuă de unde ai rămas", "Șterge cursul", "Sigur? Apasă din nou", "Confirmă ștergerea definitivă",
            "Cardurile cursului (%d)", "▣ Slide vizual", "Card",
            "⚙  Cum apar cardurile",
            "Cardurile se fac automat după tipul fișierului. Doar la PDF alegi: Pagini (pagina întreagă, derulabilă) sau Paragrafe. PPTX = un card/slide; Word = un card/paragraf; imagini = un card/imagine.",
            "Mod",
            "Pagini", "Paragrafe", "Slide-uri", "Paragrafe", "Imagini",
            "Fiecare pagină = un card cu pagina întreagă, derulabilă pe verticală.",
            "Fiecare paragraf = un card; paragrafele tăiate între pagini se reunesc.",
            "Fiecare slide = un card.", "Fiecare paragraf = un card.", "Fiecare imagine = un card."),

        ["en"] = new(
            "Cards", "Progress", "Full passes", "Learned pace", "~%d cards/session", "learning…",
            "Continue where you left off", "Delete course", "Sure? Tap again", "Confirm permanent deletion",
            "Course cards (%d)", "▣ Visual slide", "Card",
            "⚙  How cards appear",
            "Cards are made automatically by file type. Only PDF lets you pick: Pages (whole page, scrollable) or Paragraphs. PPTX = one card/slide; Word = one card/paragraph; images = one card/image.",
            "Mode",
            "Pages", "Paragraphs", "Slides", "Paragraphs", "Images",
            "Each page = one card with the whole page, scrolled vertically.",
            "Each paragraph = one card; paragraphs split across pages are rejoined.",
            "Each slide = one card.", "Each paragraph = one card.", "Each image = one card."),

        ["de"] = new(
            "Karten", "Fortschritt", "Vollständige Durchgänge", "Gelerntes Tempo", "~%d Karten/Sitzung", "wird gelernt…",
            "Weiter, wo du aufgehört hast", "Kurs löschen", "Sicher? Nochmal tippen", "Endgültiges Löschen bestätigen",
            "Kurskarten (%d)", "▣ Visuelle Folie", "Karte",
            "⚙  Wie Karten erscheinen",
            "Karten werden automatisch nach Dateityp erstellt. Nur bei PDF wählst du: Seiten (ganze Seite, scrollbar) oder Absätze. PPTX = eine Karte/Folie; Word = eine Karte/Absatz; Bilder = eine Karte/Bild.",
            "Modus",
            "Seiten", "Absätze", "Folien", "Absätze", "Bilder",
            "Jede Seite = eine Karte mit der ganzen Seite, vertikal scrollbar.",
            "Jeder Absatz = eine Karte; über Seiten getrennte Absätze werden zusammengefügt.",
            "Jede Folie = eine Karte.", "Jeder Absatz = eine Karte.", "Jedes Bild = eine Karte."),

        ["fr"] = new(
            "Cartes", "Progression", "Passages complets", "Rythme appris", "~%d cartes/session", "en cours…",
            "Reprends où tu t'es arrêté", "Supprimer le cours", "Sûr ? Appuie encore", "Confirmer la suppression définitive",
            "Cartes du cours (%d)", "▣ Diapo visuelle", "Carte",
            "⚙  Comment apparaissent les cartes",
            "Les cartes sont créées automatiquement selon le type de fichier. Seul le PDF te laisse choisir : Pages (page entière, défilante) ou Paragraphes. PPTX = une carte/diapo ; Word = une carte/paragraphe ; images = une carte/image.",
            "Mode",
            "Pages", "Paragraphes", "Diapos", "Paragraphes", "Images",
            "Chaque page = une carte avec la page entière, défilement vertical.",
            "Chaque paragraphe = une carte ; les paragraphes coupés entre pages sont réunis.",
            "Chaque diapo = une carte.", "Chaque paragraphe = une carte.", "Chaque image = une carte."),

        ["es"] = new(
            "Tarjetas", "Progreso", "Pasadas completas", "Ritmo aprendido", "~%d tarjetas/sesión", "aprendiendo…",
            "Continúa donde lo dejaste", "Eliminar curso", "¿Seguro? Pulsa otra vez", "Confirmar eliminación definitiva",
            "Tarjetas del curso (%d)", "▣ Diapositiva visual", "Tarjeta",
            "⚙  Cómo aparecen las tarjetas",
            "Las tarjetas se crean automáticamente según el tipo de archivo. Solo el PDF te deja elegir: Páginas (página entera, desplazable) o Párrafos. PPTX = una tarjeta/diapositiva; Word = una tarjeta/párrafo; imágenes = una tarjeta/imagen.",
            "Modo",
            "Páginas", "Párrafos", "Diapositivas", "Párrafos", "Imágenes",
            "Cada página = una tarjeta con la página entera, desplazable vertical.",
            "Cada párrafo = una tarjeta; los párrafos cortados entre páginas se reúnen.",
            "Cada diapositiva = una tarjeta.", "Cada párrafo = una tarjeta.", "Cada imagen = una tarjeta."),
    };
}
