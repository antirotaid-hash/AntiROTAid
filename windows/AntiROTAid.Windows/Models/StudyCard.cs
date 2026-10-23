using System.Text.Json.Serialization;

namespace AntiROTAid.Windows.Models;

/// <summary>Per-card study state, mirroring the Android card feed (known / repeat).</summary>
public enum CardState { New, Known, Repeat }

/// <summary>
/// One study card. The fields mirror the Android <c>AraCard</c> (see AraCardFeed.kt):
/// the deck is produced by the REAL engine (engine_ara) exactly like the phone, so a
/// card can be a text chunk (<c>lesson_chunk</c>) OR a slide/image card
/// (<c>slide_image</c>) carrying an embedded image or an on-device PDF page.
/// </summary>
public sealed class StudyCard
{
    public string CardId { get; set; } = "";
    public string CardType { get; set; } = "lesson_chunk";  // lesson_chunk | slide_image
    public string Title { get; set; } = "";
    public string Body { get; set; } = "";                  // formatted text (bullets / headers)
    public string SourceLabel { get; set; } = "";           // e.g. "Slide 3"
    public CardState State { get; set; } = CardState.New;

    // ---- visual (slide_image) payload, populated for file imports ----
    // Base64 image bytes are a per-session render convenience only: we NEVER persist them
    // (a slide deck would bloat the state file by tens of MB). The import is remembered by
    // its stable on-disk file path instead, and the deck is re-extracted from that file on
    // load, so the images come back without ever being written to courses.json.
    [JsonIgnore]
    public string ImageBase64 { get; set; } = "";           // embedded slide/figure image
    public string ImagePath { get; set; } = "";             // on-device image file
    public string PdfDocPath { get; set; } = "";            // on-device PDF to render a page from
    public int PdfPage { get; set; }                        // 1-based physical PDF page

    public bool IsImage => CardType == "slide_image";
    public bool HasImage => ImageBase64.Length > 0 || ImagePath.Length > 0
        || (PdfDocPath.Length > 0 && PdfPage >= 1);
}
