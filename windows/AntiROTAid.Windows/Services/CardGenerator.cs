using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// Deterministic, local card generation (the C# reimplementation of the Android
/// engine's card logic for v1 — no Python, no network). It understands the ARA demo
/// format ("Slide N:" blocks with a concept title + bullets) and falls back to a
/// paragraph-per-card split for plain text.
/// </summary>
public static class CardGenerator
{
    private static readonly Regex SlideHeader =
        new(@"^\s*(Slide|Slaid|Diapozitiv)\s*\d+\s*:?\s*$", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    public static List<StudyCard> Generate(string text)
    {
        var cards = ParseSlides(text);
        if (cards.Count > 0) return cards;
        return ParseParagraphs(text);
    }

    /// <summary>Slide-structured lessons: header → concept title → bullet body.</summary>
    private static List<StudyCard> ParseSlides(string text)
    {
        var lines = text.Replace("\r\n", "\n").Split('\n');
        var cards = new List<StudyCard>();
        int slideNo = 0;
        int i = 0;
        while (i < lines.Length)
        {
            if (!SlideHeader.IsMatch(lines[i])) { i++; continue; }
            slideNo++;
            i++;
            // Skip blank lines to the title.
            while (i < lines.Length && lines[i].Trim().Length == 0) i++;
            string title = i < lines.Length ? lines[i].Trim() : $"Slide {slideNo}";
            i++;
            // Body = everything until the next slide header.
            var body = new StringBuilder();
            while (i < lines.Length && !SlideHeader.IsMatch(lines[i]))
            {
                var l = lines[i].TrimEnd();
                if (l.Trim().Length > 0) body.AppendLine(l);
                i++;
            }
            cards.Add(new StudyCard
            {
                SourceLabel = $"Slide {slideNo}",
                Title = title,
                Body = body.ToString().Trim(),
            });
        }
        return cards;
    }

    /// <summary>Plain text: one card per paragraph; first sentence becomes the title.</summary>
    private static List<StudyCard> ParseParagraphs(string text)
    {
        var paras = Regex.Split(text.Replace("\r\n", "\n").Trim(), @"\n\s*\n")
            .Select(p => p.Trim()).Where(p => p.Length > 0).ToList();
        var cards = new List<StudyCard>();
        int n = 0;
        foreach (var p in paras)
        {
            n++;
            var firstSentence = Regex.Match(p, @"^.*?[\.!\?](\s|$)");
            string title = firstSentence.Success ? firstSentence.Value.Trim() : p;
            if (title.Length > 90) title = title[..87].TrimEnd() + "…";
            cards.Add(new StudyCard
            {
                SourceLabel = $"{n}",
                Title = title,
                Body = p,
            });
        }
        return cards;
    }
}
