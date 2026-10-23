using System.Collections.Generic;
using System.Text.Json;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Engine;

/// <summary>
/// Parses the engine's lesson feed (pages → cards) into <see cref="StudyCard"/>s,
/// mirroring the Android <c>parseAraFeed</c> / <c>araCardFrom</c> (AraCardFeed.kt)
/// line-for-line so the Windows deck is identical to the phone's.
/// </summary>
public static class EngineFeed
{
    /// <summary>Flatten every page's cards into a single ordered deck.</summary>
    public static List<StudyCard> Parse(JsonElement feed)
    {
        var outp = new List<StudyCard>();
        if (feed.ValueKind != JsonValueKind.Object
            || !feed.TryGetProperty("pages", out var pages)
            || pages.ValueKind != JsonValueKind.Array)
            return outp;

        foreach (var page in pages.EnumerateArray())
        {
            if (!page.TryGetProperty("cards", out var cards) || cards.ValueKind != JsonValueKind.Array)
                continue;
            foreach (var c in cards.EnumerateArray())
                outp.Add(CardFrom(c));
        }
        return outp;
    }

    /// <summary>How many source units (slides/paragraphs/pages) the feed reports.</summary>
    public static int SectionCount(JsonElement feed) =>
        feed.ValueKind == JsonValueKind.Object && feed.TryGetProperty("page_count", out var pc)
            && pc.ValueKind == JsonValueKind.Number ? pc.GetInt32() : 0;

    // Mirrors araCardFrom(): sourceLabel = "<label> <number>"; title = source.title;
    // body = answer; and for slide_image cards we also carry the media + raw text so
    // the card can render its image (or fall back to the verbatim slide text).
    private static StudyCard CardFrom(JsonElement c)
    {
        string label = "";
        string rawTitle = "";
        if (c.TryGetProperty("source", out var src) && src.ValueKind == JsonValueKind.Object)
        {
            var l = Str(src, "label");
            int n = Int(src, "number", -1);
            label = (l.Length > 0 && n >= 0) ? $"{l} {n}" : l;
            if (src.TryGetProperty("title", out var tt) && tt.ValueKind == JsonValueKind.String)
                rawTitle = tt.GetString() ?? "";
        }
        if (rawTitle == "null") rawTitle = "";

        string cardType = Str(c, "card_type", "lesson_chunk");
        string answer = Str(c, "answer");
        string raw = Str(c, "raw_text_fallback");
        string question = Str(c, "question");

        var card = new StudyCard
        {
            CardId = Str(c, "card_id", "card"),
            CardType = cardType,
            SourceLabel = label.Length > 0 ? label : question.Length > 0 ? question : "Card",
            Title = rawTitle,
            // lesson_chunk carries its prose in `answer`; a slide_image card's `answer`
            // is just the title, so its readable text lives in `raw_text_fallback`.
            Body = cardType == "slide_image" ? (raw.Length > 0 ? raw : answer) : answer,
        };

        if (c.TryGetProperty("media", out var m) && m.ValueKind == JsonValueKind.Object)
        {
            card.ImageBase64 = FirstStr(m, "base64", "data_base64");
            card.ImagePath = FirstStr(m, "image_path", "local_path", "path");
            if (Str(m, "document_type") == "pdf")
            {
                card.PdfDocPath = Str(m, "document_path");
                card.PdfPage = Int(m, "physical_page", 0);
            }
        }
        return card;
    }

    private static string Str(JsonElement o, string key, string dflt = "")
        => o.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String ? (v.GetString() ?? dflt) : dflt;

    private static string FirstStr(JsonElement o, params string[] keys)
    {
        foreach (var k in keys) { var s = Str(o, k); if (s.Length > 0) return s; }
        return "";
    }

    private static int Int(JsonElement o, string key, int dflt)
        => o.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetInt32() : dflt;
}
