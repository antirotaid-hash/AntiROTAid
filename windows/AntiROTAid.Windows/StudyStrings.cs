using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// Study-feed screen text, ported verbatim from the Android ara app (AraI18n.kt)
/// so the Windows card feed reads identically in all 5 languages.
/// </summary>
public sealed record StudyText(
    string TextSize, string Theme, string RemoveCard,
    string ScrollNext, string NextCard, string Done, string SaveExit);

public static class StudyI18n
{
    public static StudyText For(string code) => All.TryGetValue(code, out var v) ? v : All["en"];

    private static readonly Dictionary<string, StudyText> All = new()
    {
        ["ro"] = new("Dimensiune text", "Temă", "Scoate cardul",
            "Scroll pentru cardul următor", "Următorul card", "Am terminat", "Rămân aici și ies"),
        ["en"] = new("Text size", "Theme", "Remove card",
            "Scroll for the next card", "Next card", "Done", "Save spot & exit"),
        ["de"] = new("Textgröße", "Thema", "Karte entfernen",
            "Scrolle für die nächste Karte", "Nächste Karte", "Fertig", "Merken & beenden"),
        ["fr"] = new("Taille du texte", "Thème", "Retirer la carte",
            "Scrolle pour la carte suivante", "Carte suivante", "Terminé", "Garder & quitter"),
        ["es"] = new("Tamaño del texto", "Tema", "Quitar tarjeta",
            "Desliza para la siguiente tarjeta", "Siguiente", "Listo", "Guardar y salir"),
    };
}
