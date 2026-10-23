using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// "My exams" editing text, ported verbatim from the Android ara app (AraI18n.kt /
/// AraPlan) so the Windows exam planner is identical in all 5 languages: coverage
/// knob, engine plan summary, materials, date. Parameterized lines are methods.
/// </summary>
public sealed record ExamText(
    string New, string Default, string PickDate, string Cancel, string Done,
    string LabelData, string LabelMaterials, string LabelPlan, string PlanFmt,
    string Estimate, string Empty, string AddMaterials, string NoMaterials, string OpenMaterialsHint,
    string TimeWarning, string OpenPlanSettings, string DeleteExam, string ConfirmAgain,
    string FromCourses, string ImportShort, string SortDate, string SortName, string NoDate,
    string EditBtn, string SetBtn, string TimeLabel,
    string CoverageTitle, string CoverageSub, string CoverageOnce, string CoverageOther,
    string CoverageMenuInterval, string CoverageIntervalUnset, string CoverageMinLabel,
    string CoverageMinHint, string CoverageSetMinutes, string CoverageNoTimeWhole,
    string CoverageCountLabel)
{
    public string NTimes(int n) => _nTimes(n);
    public string IntervalClosed(int min) => _intervalClosed(min);
    public string PassesLive(int n) => string.Format(_passesLive, n);
    public string CountLive(int n) => string.Format(_countLive, n);

    private System.Func<int, string> _nTimes = n => $"{n}×";
    private System.Func<int, string> _intervalClosed = min => $"· {min} min";
    private string _passesLive = "~{0}";
    private string _countLive = "{0}";

    private ExamText With(System.Func<int, string> nt, System.Func<int, string> ic, string pl, string cl)
    { _nTimes = nt; _intervalClosed = ic; _passesLive = pl; _countLive = cl; return this; }

    public static ExamText For(string code) => All.TryGetValue(code, out var v) ? v : All["en"];

    private static readonly Dictionary<string, ExamText> All = new()
    {
        ["ro"] = new ExamText("Examen nou", "Examen", "Alege data →", "Anulează", "Gata",
            "Data", "Materiale", "Plan", "o unitate la ~{0} min · {1}/zi",
            "Folosesc 10h/săptămână ca estimare. Pune-ți timpul real în Setări pentru un plan exact.",
            "Niciun examen încă. Creează unul, apoi adaugă-i materiale (PDF/PPTX/Word/text/poze).",
            "Adaugă materiale", "Niciun material importat încă.",
            "Deschide materialele o dată (Detalii) ca să le număr unitățile și să calculez planul.",
            "⚠ Nu ai timp să parcurgi toată materia de atâtea ori până la examen. Scade numărul de parcurgeri sau mută data mai târziu.",
            "→ Setări · Plan de studiu", "Șterge examenul", "Sigur? Apasă din nou",
            "＋ Adaugă", "＋ Din cursuri", "Dată", "Nume", "fără dată",
            "Schimbă", "Setează", "Ora",
            "De câte ori parcurgi materia",
            "Pentru tot examenul. Alege de câte ori să apară toată materia până la examen, sau NEDEFINIT și spui tu la câte minute apare un card.",
            "O dată", "Alt număr…", "NEDEFINIT — pun eu minutele", "NEDEFINIT · alege minutele",
            "Minute între carduri", "Un card nou apare la fiecare câteva minute petrecute pe dispozitiv.",
            "Completează la câte minute apare un card.",
            "Nu mai e timp pentru toată lecția la ritmul ăsta — mărește orele/săptămână sau micșorează intervalul.",
            "de câte ori")
            .With(n => $"De {n} ori", min => $"NEDEFINIT · un card la {min} min",
                "La ritmul actual mai poți parcurge toată materia de ~{0} ori până la examen.",
                "Toată materia va fi parcursă de {0} ori până la examen."),

        ["en"] = new ExamText("New exam", "Exam", "Pick the date →", "Cancel", "Done",
            "Date", "Materials", "Plan", "one unit every ~{0} min · {1}/day",
            "Using 10h/week as an estimate. Set your real time in Settings for an exact plan.",
            "No exams yet. Create one, then add materials to it (PDF/PPTX/Word/text/images).",
            "Add materials", "No materials imported yet.",
            "Open the materials once (Details) so I can count their units and compute the plan.",
            "⚠ There isn't time to cover the whole material that many times before the exam. Lower the number of passes or move the date later.",
            "→ Settings · Study plan", "Delete exam", "Sure? Tap again",
            "＋ Add", "＋ From courses", "Date", "Name", "no date",
            "Change", "Set", "Time",
            "How many times to cover the material",
            "For the whole exam. Pick how many times the whole material appears before the exam, or UNDEFINED and you set how many minutes between cards.",
            "Once", "Other number…", "UNDEFINED — I set the minutes", "UNDEFINED · pick the minutes",
            "Minutes between cards", "A new card appears every few minutes you spend on the device.",
            "Fill in how many minutes between cards.",
            "No time left for a full pass at this pace — raise your hours/week or shorten the interval.",
            "how many times")
            .With(n => $"{n} times", min => $"UNDEFINED · a card every {min} min",
                "At the current pace you can still cover the whole material ~{0} times before the exam.",
                "The whole material will be covered {0} times before the exam."),

        ["de"] = new ExamText("Neue Prüfung", "Prüfung", "Datum wählen →", "Abbrechen", "Fertig",
            "Datum", "Materialien", "Plan", "eine Einheit alle ~{0} Min · {1}/Tag",
            "Schätze mit 10 Std./Woche. Trage deine echte Zeit in den Einstellungen ein für einen genauen Plan.",
            "Noch keine Prüfungen. Erstelle eine und füge ihr Materialien hinzu (PDF/PPTX/Word/Text/Bilder).",
            "Materialien hinzufügen", "Noch keine Materialien importiert.",
            "Öffne die Materialien einmal (Details), damit ich die Einheiten zählen und den Plan berechnen kann.",
            "⚠ Es reicht die Zeit nicht, den ganzen Stoff so oft bis zur Prüfung durchzugehen. Verringere die Durchgänge oder verschiebe das Datum nach hinten.",
            "→ Einstellungen · Lernplan", "Prüfung löschen", "Sicher? Nochmal tippen",
            "＋ Hinzu", "＋ Aus Kursen", "Datum", "Name", "kein Datum",
            "Ändern", "Festlegen", "Uhrzeit",
            "Wie oft den Stoff durchgehen",
            "Für die ganze Prüfung. Wähle, wie oft der gesamte Stoff bis zur Prüfung erscheint, oder UNBESTIMMT und du legst die Minuten pro Karte fest.",
            "Einmal", "Andere Zahl…", "UNBESTIMMT — ich setze die Minuten", "UNBESTIMMT · Minuten wählen",
            "Minuten zwischen Karten", "Eine neue Karte erscheint alle paar Minuten am Gerät.",
            "Trag ein, wie viele Minuten zwischen Karten.",
            "Keine Zeit mehr für einen ganzen Durchgang — erhöhe Stunden/Woche oder verkürze das Intervall.",
            "wie oft")
            .With(n => $"{n}-mal", min => $"UNBESTIMMT · eine Karte alle {min} Min",
                "Beim aktuellen Tempo schaffst du den ganzen Stoff noch ~{0}-mal bis zur Prüfung.",
                "Der ganze Stoff wird bis zur Prüfung {0}-mal durchgegangen."),

        ["fr"] = new ExamText("Nouvel examen", "Examen", "Choisir la date →", "Annuler", "Terminé",
            "Date", "Supports", "Plan", "une unité toutes les ~{0} min · {1}/jour",
            "J'utilise 10h/semaine comme estimation. Mets ton temps réel dans les Réglages pour un plan exact.",
            "Aucun examen pour l'instant. Crées-en un, puis ajoute-lui des supports (PDF/PPTX/Word/texte/images).",
            "Ajouter des supports", "Aucun support importé pour l'instant.",
            "Ouvre les supports une fois (Détails) pour que je compte leurs unités et calcule le plan.",
            "⚠ Pas assez de temps pour couvrir toute la matière autant de fois avant l'examen. Réduis le nombre de passages ou repousse la date.",
            "→ Réglages · Plan d'étude", "Supprimer l'examen", "Sûr ? Appuie encore",
            "＋ Ajouter", "＋ Depuis les cours", "Date", "Nom", "sans date",
            "Modifier", "Définir", "Heure",
            "Combien de fois couvrir la matière",
            "Pour tout l'examen. Choisis combien de fois toute la matière apparaît avant l'examen, ou INDÉFINI et tu fixes les minutes entre les cartes.",
            "Une fois", "Autre nombre…", "INDÉFINI — je règle les minutes", "INDÉFINI · choisis les minutes",
            "Minutes entre les cartes", "Une nouvelle carte apparaît toutes les quelques minutes passées sur l'appareil.",
            "Indique combien de minutes entre les cartes.",
            "Plus le temps pour un passage complet à ce rythme — augmente tes heures/semaine ou réduis l'intervalle.",
            "combien de fois")
            .With(n => $"{n} fois", min => $"INDÉFINI · une carte toutes les {min} min",
                "À ce rythme tu peux encore couvrir toute la matière ~{0} fois avant l'examen.",
                "Toute la matière sera couverte {0} fois avant l'examen."),

        ["es"] = new ExamText("Examen nuevo", "Examen", "Elegir la fecha →", "Cancelar", "Hecho",
            "Fecha", "Materiales", "Plan", "una unidad cada ~{0} min · {1}/día",
            "Uso 10h/semana como estimación. Pon tu tiempo real en Ajustes para un plan exacto.",
            "Ningún examen todavía. Crea uno y añádele materiales (PDF/PPTX/Word/texto/imágenes).",
            "Añadir materiales", "Ningún material importado todavía.",
            "Abre los materiales una vez (Detalles) para que cuente sus unidades y calcule el plan.",
            "⚠ No hay tiempo para cubrir toda la materia tantas veces antes del examen. Baja el número de pasadas o mueve la fecha más adelante.",
            "→ Ajustes · Plan de estudio", "Eliminar examen", "¿Seguro? Pulsa otra vez",
            "＋ Añadir", "＋ De cursos", "Fecha", "Nombre", "sin fecha",
            "Cambiar", "Fijar", "Hora",
            "Cuántas veces cubrir la materia",
            "Para todo el examen. Elige cuántas veces aparece toda la materia antes del examen, o INDEFINIDO y tú fijas los minutos entre tarjetas.",
            "Una vez", "Otro número…", "INDEFINIDO — yo pongo los minutos", "INDEFINIDO · elige los minutos",
            "Minutos entre tarjetas", "Aparece una tarjeta nueva cada pocos minutos que pasas en el dispositivo.",
            "Indica cuántos minutos entre tarjetas.",
            "No queda tiempo para una pasada completa a este ritmo — sube tus horas/semana o acorta el intervalo.",
            "cuántas veces")
            .With(n => $"{n} veces", min => $"INDEFINIDO · una tarjeta cada {min} min",
                "A este ritmo aún puedes cubrir toda la materia ~{0} veces antes del examen.",
                "Toda la materia se cubrirá {0} veces antes del examen."),
    };
}
