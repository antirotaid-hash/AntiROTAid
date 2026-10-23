using System;

namespace AntiROTAid.Windows.Models;

/// <summary>
/// Persisted study position for one course (the Windows equivalent of the phone's
/// resume/progress). Replaces the old flat <c>courseId → int index</c> map: it also
/// pins the exact <see cref="CardId"/> (so a resume survives deck reordering) and a
/// <see cref="DeckFingerprint"/> (so a regenerated deck can't silently open an
/// unrelated card at the same numeric index). Completion is tracked so the final
/// "Done" advances deterministically instead of trapping the user on the last card.
/// </summary>
public sealed class CourseResumeState
{
    /// <summary>Validated card index the user last settled on (clamped on load).</summary>
    public int Index { get; set; }

    /// <summary>The id of the card at <see cref="Index"/> when it was saved. Preferred over
    /// the raw index when the card still exists after a reorder/regeneration.</summary>
    public string CardId { get; set; } = "";

    /// <summary>Deterministic hash of the deck (ids/content/order) this position belongs to.
    /// A mismatch means the deck was regenerated and the index must be re-validated.</summary>
    public string DeckFingerprint { get; set; } = "";

    /// <summary>True once the whole deck was completed (final "Done"). Cleared when the deck
    /// changes materially.</summary>
    public bool Completed { get; set; }

    /// <summary>When the course was last completed (UTC), or null if never.</summary>
    public DateTime? CompletedAtUtc { get; set; }
}
