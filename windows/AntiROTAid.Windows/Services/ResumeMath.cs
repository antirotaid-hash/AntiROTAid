using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// Pure (no I/O, no engine, no UI) decisions behind the study-progress state machine:
/// deck fingerprinting, reconciling a stored resume position against the current deck,
/// remapping it after a card deletion, and choosing the next course on completion.
/// <para/>
/// This lives apart from <see cref="CourseStore"/> so it can be unit-tested directly and
/// so the rules that make resume/completion correct are in one small, auditable place.
/// </summary>
public static class ResumeMath
{
    /// <summary>Deterministic hash of a deck (card identities + order). A change means the
    /// deck was regenerated and any stored numeric index must be re-validated by CardId.</summary>
    public static string Fingerprint(IReadOnlyList<StudyCard> cards)
    {
        var sb = new StringBuilder();
        sb.Append(cards.Count).Append(';');
        foreach (var c in cards)
        {
            // Prefer the engine card id; fall back to stable content so text decks hash too.
            string key = !string.IsNullOrEmpty(c.CardId) ? c.CardId
                : (c.SourceLabel + "|" + c.Title + "|" + c.Body);
            sb.Append(key).Append('\n');
        }
        var hash = SHA256.HashData(Encoding.UTF8.GetBytes(sb.ToString()));
        return Convert.ToHexString(hash, 0, 8); // 16 hex chars is plenty
    }

    /// <summary>Clamp an index into a valid card slot (0 when the deck is empty).</summary>
    public static int Clamp(int i, int count) => count <= 0 ? 0 : Math.Clamp(i, 0, count - 1);

    /// <summary>Reconcile a stored position against the CURRENT deck: validate the index,
    /// prefer the pinned CardId, and reset safely when the deck fingerprint changed
    /// (regeneration). Mutates and returns <paramref name="st"/>; always leaves it valid.</summary>
    public static CourseResumeState Reconcile(CourseResumeState st, IReadOnlyList<StudyCard> cards)
    {
        string fp = Fingerprint(cards);

        if (cards.Count == 0)
        {
            st.Index = 0; st.CardId = ""; st.DeckFingerprint = fp;
        }
        else if (string.IsNullOrEmpty(st.DeckFingerprint))
        {
            // First reconcile ever (fresh state OR a migrated legacy index with no fingerprint):
            // there is nothing to validate against, so trust the raw index — just clamp and pin
            // it. Resetting to 0 here would throw away an upgrading user's saved position.
            st.Index = Clamp(st.Index, cards.Count);
            st.CardId = cards[st.Index].CardId;
            st.DeckFingerprint = fp;
        }
        else if (st.DeckFingerprint != fp)
        {
            // Deck changed materially (regeneration): it is no longer the same deck the user
            // "completed", so drop the stale completion flag either way. Keep their spot by
            // CardId if that exact card survives, else restart from the top.
            int byId = !string.IsNullOrEmpty(st.CardId) ? IndexOfCardId(cards, st.CardId) : -1;
            st.Index = Clamp(byId >= 0 ? byId : 0, cards.Count);
            st.CardId = cards[st.Index].CardId;
            st.DeckFingerprint = fp;
            st.Completed = false;
            st.CompletedAtUtc = null;
        }
        else
        {
            // Same deck: prefer the pinned CardId, else the clamped index.
            int idx = Clamp(st.Index, cards.Count);
            if (!string.IsNullOrEmpty(st.CardId))
            {
                int byId = IndexOfCardId(cards, st.CardId);
                if (byId >= 0) idx = byId;
            }
            st.Index = idx;
            st.CardId = cards[idx].CardId;
        }
        return st;
    }

    /// <summary>Remap a resume position after the card at <paramref name="removedIndex"/> was
    /// removed from the deck (the new, shorter deck is <paramref name="newCards"/>). Keeps the
    /// user near where they were; clamps when the last card went; clears when the deck empties.</summary>
    public static void RemapAfterDelete(CourseResumeState st, int removedIndex, IReadOnlyList<StudyCard> newCards)
    {
        if (newCards.Count == 0) { st.Index = 0; st.CardId = ""; return; }
        if (st.Index > removedIndex) st.Index--;          // a card before us was removed
        // (st.Index == removedIndex → keep index, which now points at the following card)
        st.Index = Clamp(st.Index, newCards.Count);        // clamp when the last card went
        st.CardId = newCards[st.Index].CardId;
    }

    /// <summary>The next course index for a deterministic, circular advance among the eligible
    /// courses (those with cards). Returns <paramref name="currentIndex"/> when there is one or
    /// zero eligible courses (restart the same one).</summary>
    public static int NextCourseIndex(int currentIndex, int eligibleCount)
    {
        if (eligibleCount <= 1) return currentIndex < 0 ? 0 : currentIndex;
        if (currentIndex < 0) return 0;
        return (currentIndex + 1) % eligibleCount;
    }

    private static int IndexOfCardId(IReadOnlyList<StudyCard> cards, string cardId)
    {
        for (int i = 0; i < cards.Count; i++)
            if (cards[i].CardId.Length > 0 && cards[i].CardId == cardId) return i;
        return -1;
    }
}
