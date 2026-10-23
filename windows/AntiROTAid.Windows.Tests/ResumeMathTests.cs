using System.Collections.Generic;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Services;
using Xunit;

namespace AntiROTAid.Windows.Tests;

/// <summary>
/// The heart of the repair: the study-progress state machine. These exercise the pure
/// <see cref="ResumeMath"/> decisions — fingerprinting, reconciling a saved position
/// against the current deck, remapping after a delete, and the circular course advance —
/// with no engine, no files, no UI.
/// </summary>
public class ResumeMathTests
{
    private static StudyCard Card(string id, string title = "", string body = "") =>
        new() { CardId = id, Title = title, Body = body };

    private static List<StudyCard> Deck(params string[] ids)
    {
        var d = new List<StudyCard>();
        foreach (var id in ids) d.Add(Card(id));
        return d;
    }

    // ---- Fingerprint ----
    [Fact]
    public void Fingerprint_SameDeck_SameHash()
    {
        Assert.Equal(ResumeMath.Fingerprint(Deck("a", "b", "c")),
                     ResumeMath.Fingerprint(Deck("a", "b", "c")));
    }

    [Fact]
    public void Fingerprint_Reorder_DifferentHash()
    {
        Assert.NotEqual(ResumeMath.Fingerprint(Deck("a", "b", "c")),
                        ResumeMath.Fingerprint(Deck("a", "c", "b")));
    }

    [Fact]
    public void Fingerprint_CountChange_DifferentHash()
    {
        Assert.NotEqual(ResumeMath.Fingerprint(Deck("a", "b")),
                        ResumeMath.Fingerprint(Deck("a", "b", "c")));
    }

    [Fact]
    public void Fingerprint_TextCards_FallBackToContent()
    {
        // Cards without an engine id must still hash by their content, not collide.
        var a = new List<StudyCard> { new() { Title = "T1", Body = "B1" } };
        var b = new List<StudyCard> { new() { Title = "T1", Body = "B2" } };
        Assert.NotEqual(ResumeMath.Fingerprint(a), ResumeMath.Fingerprint(b));
    }

    [Fact]
    public void Fingerprint_EmptyDeck_IsStable()
    {
        Assert.Equal(ResumeMath.Fingerprint(Deck()), ResumeMath.Fingerprint(Deck()));
    }

    // ---- Clamp ----
    [Fact]
    public void Clamp_EmptyDeck_IsZero() => Assert.Equal(0, ResumeMath.Clamp(5, 0));

    [Fact]
    public void Clamp_OverflowsToLast() => Assert.Equal(2, ResumeMath.Clamp(9, 3));

    [Fact]
    public void Clamp_NegativeToZero() => Assert.Equal(0, ResumeMath.Clamp(-4, 3));

    // ---- Reconcile ----
    [Fact]
    public void Reconcile_EmptyDeck_ResetsToZero()
    {
        var st = new CourseResumeState { Index = 3, CardId = "x", Completed = true };
        ResumeMath.Reconcile(st, Deck());
        Assert.Equal(0, st.Index);
        Assert.Equal("", st.CardId);
    }

    [Fact]
    public void Reconcile_FirstTime_PinsCardIdAndFingerprint()
    {
        var deck = Deck("a", "b", "c");
        var st = new CourseResumeState { Index = 1 };   // no fingerprint yet
        ResumeMath.Reconcile(st, deck);
        Assert.Equal(1, st.Index);
        Assert.Equal("b", st.CardId);
        Assert.Equal(ResumeMath.Fingerprint(deck), st.DeckFingerprint);
    }

    [Fact]
    public void Reconcile_SameDeck_ClampsOutOfRangeIndex()
    {
        var deck = Deck("a", "b", "c");
        var st = new CourseResumeState { Index = 9, DeckFingerprint = ResumeMath.Fingerprint(deck) };
        ResumeMath.Reconcile(st, deck);
        Assert.Equal(2, st.Index);
        Assert.Equal("c", st.CardId);
    }

    [Fact]
    public void Reconcile_SameDeck_PrefersCardIdOverStaleIndex()
    {
        var deck = Deck("a", "b", "c");
        // Same fingerprint, but the pinned card "c" is authoritative even if Index says 0.
        var st = new CourseResumeState { Index = 0, CardId = "c", DeckFingerprint = ResumeMath.Fingerprint(deck) };
        ResumeMath.Reconcile(st, deck);
        Assert.Equal(2, st.Index);
    }

    [Fact]
    public void Reconcile_Regenerated_RemapsByCardId()
    {
        var oldDeck = Deck("a", "b", "c");
        var st = new CourseResumeState { Index = 2, CardId = "c", DeckFingerprint = ResumeMath.Fingerprint(oldDeck) };
        var newDeck = Deck("x", "c", "y", "z");   // regenerated: "c" moved to index 1
        ResumeMath.Reconcile(st, newDeck);
        Assert.Equal(1, st.Index);
        Assert.Equal("c", st.CardId);
        Assert.Equal(ResumeMath.Fingerprint(newDeck), st.DeckFingerprint);
    }

    [Fact]
    public void Reconcile_Regenerated_CardGone_ResetsAndClearsCompleted()
    {
        var oldDeck = Deck("a", "b", "c");
        var st = new CourseResumeState
        {
            Index = 2, CardId = "c", Completed = true,
            DeckFingerprint = ResumeMath.Fingerprint(oldDeck),
        };
        var newDeck = Deck("x", "y");             // "c" no longer exists
        ResumeMath.Reconcile(st, newDeck);
        Assert.Equal(0, st.Index);
        Assert.False(st.Completed);
        Assert.Null(st.CompletedAtUtc);
    }

    [Fact]
    public void Reconcile_Regenerated_ClearsCompleted_EvenWhenCardSurvives()
    {
        // A materially changed deck is no longer the deck the user "completed" — the flag
        // must drop even when we can still remap their spot by CardId.
        var oldDeck = Deck("a", "b", "c");
        var st = new CourseResumeState
        {
            Index = 1, CardId = "b", Completed = true,
            DeckFingerprint = ResumeMath.Fingerprint(oldDeck),
        };
        var newDeck = Deck("b", "c", "d", "e");   // "b" still exists, deck changed
        ResumeMath.Reconcile(st, newDeck);
        Assert.Equal(0, st.Index);                 // remapped to surviving "b"
        Assert.Equal("b", st.CardId);
        Assert.False(st.Completed);
        Assert.Null(st.CompletedAtUtc);
    }

    // ---- RemapAfterDelete ----
    [Fact]
    public void Remap_DeleteBeforePosition_DecrementsIndex()
    {
        var st = new CourseResumeState { Index = 3 };
        ResumeMath.RemapAfterDelete(st, removedIndex: 1, Deck("a", "c", "d", "e")); // was 5, removed idx1
        Assert.Equal(2, st.Index);
    }

    [Fact]
    public void Remap_DeleteAtPosition_KeepsIndex_NowNextCard()
    {
        var st = new CourseResumeState { Index = 2 };
        var newDeck = Deck("a", "b", "d", "e");   // removed the card that was at index 2
        ResumeMath.RemapAfterDelete(st, removedIndex: 2, newDeck);
        Assert.Equal(2, st.Index);
        Assert.Equal("d", st.CardId);
    }

    [Fact]
    public void Remap_DeleteAfterPosition_IndexUnchanged()
    {
        var st = new CourseResumeState { Index = 1 };
        ResumeMath.RemapAfterDelete(st, removedIndex: 3, Deck("a", "b", "c"));
        Assert.Equal(1, st.Index);
    }

    [Fact]
    public void Remap_DeleteLastCard_ClampsDown()
    {
        var st = new CourseResumeState { Index = 3 };   // last of a 4-card deck
        var newDeck = Deck("a", "b", "c");              // last removed
        ResumeMath.RemapAfterDelete(st, removedIndex: 3, newDeck);
        Assert.Equal(2, st.Index);
        Assert.Equal("c", st.CardId);
    }

    [Fact]
    public void Remap_DeleteIntoEmpty_Clears()
    {
        var st = new CourseResumeState { Index = 0, CardId = "a" };
        ResumeMath.RemapAfterDelete(st, removedIndex: 0, Deck());
        Assert.Equal(0, st.Index);
        Assert.Equal("", st.CardId);
    }

    // ---- NextCourseIndex (circular advance) ----
    [Fact]
    public void Next_AdvancesToFollowing() => Assert.Equal(2, ResumeMath.NextCourseIndex(1, 4));

    [Fact]
    public void Next_WrapsAtEnd() => Assert.Equal(0, ResumeMath.NextCourseIndex(3, 4));

    [Fact]
    public void Next_SingleCourse_Restarts() => Assert.Equal(0, ResumeMath.NextCourseIndex(0, 1));

    [Fact]
    public void Next_NegativeCurrent_ReturnsZero() => Assert.Equal(0, ResumeMath.NextCourseIndex(-1, 3));
}
