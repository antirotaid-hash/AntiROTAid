using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using AntiROTAid.Windows.Engine;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// Local-only course + progress store. Demo courses are loaded fresh from packaged
/// assets each run; user-created text courses and all per-card study progress are
/// persisted as a single JSON file under %AppData%\AntiROTAid. No cloud, no account.
/// </summary>
public sealed class CourseStore
{
    /// <summary>Test-only override so unit tests persist into a temp folder instead of the
    /// real %AppData%\AntiROTAid (never set in production).</summary>
    internal static string? TestDirOverride;

    private static string Dir =>
        TestDirOverride ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "AntiROTAid");
    private static string FilePath => Path.Combine(Dir, "courses.json");

    public sealed record MaterialSettings(string Appearance, string CardSize);

    private sealed class Persisted
    {
        public List<Course> UserCourses { get; set; } = new();
        // courseId -> (cardIndex -> state)
        public Dictionary<string, Dictionary<int, CardState>> Progress { get; set; } = new();
        // courseId -> material settings (persists for demos too, whose Course is rebuilt each run)
        public Dictionary<string, MaterialSettings> Settings { get; set; } = new();
        // courseId -> rich resume/completion state (index + cardId + deck fingerprint + completed).
        public Dictionary<string, CourseResumeState> ResumeStates { get; set; } = new();
        // The course the overlay + Start button target; shared by both UI modes.
        public string ActiveCourseId { get; set; } = "";
        // LEGACY (pre-2026-07): courseId -> int index. Migrated into ResumeStates on load.
        public Dictionary<string, int>? Resume { get; set; }
    }

    private Persisted _data = new();
    private readonly List<Course> _courses = new();

    public IReadOnlyList<Course> Courses => _courses;

    /// <summary>Test-only: seed in-memory courses without the engine/demo loader, so the
    /// resume/completion/active-course flows can be exercised end-to-end.</summary>
    internal void SeedForTests(params Course[] courses)
    {
        foreach (var c in courses) { _courses.Add(c); if (!c.IsDemo) _data.UserCourses.Add(c); }
    }

    // ---- deck fingerprint --------------------------------------------------
    /// <summary>Deterministic hash of a deck (card identities + order). A change means the
    /// deck was regenerated and any stored numeric index must be re-validated by CardId.
    /// Delegates to the pure, unit-tested <see cref="ResumeMath"/>.</summary>
    public static string Fingerprint(IReadOnlyList<StudyCard> cards) => ResumeMath.Fingerprint(cards);

    private static int Clamp(int i, int count) => ResumeMath.Clamp(i, count);

    // ---- active course (shared by Study + overlay) -------------------------
    /// <summary>The course both the Start button and the overlay target. Falls back to the
    /// first course that actually has cards so the mechanic always works.</summary>
    public Course? ActiveCourse =>
        (_courses.FirstOrDefault(c => c.Id == _data.ActiveCourseId && c.CardCount > 0))
        ?? _courses.FirstOrDefault(c => c.CardCount > 0);

    public void SetActiveCourse(Course course)
    {
        if (_data.ActiveCourseId == course.Id) return;
        _data.ActiveCourseId = course.Id;
        Save();
    }

    // ---- resume / completion state machine ---------------------------------
    /// <summary>Reconcile the stored position against the CURRENT deck: validate the index,
    /// prefer the pinned CardId, and reset safely when the deck fingerprint changed
    /// (regeneration). Always leaves a valid, persisted state. Returns it.</summary>
    public CourseResumeState ResumeState(Course c)
    {
        var st = _data.ResumeStates.TryGetValue(c.Id, out var s) ? s : new CourseResumeState();
        ResumeMath.Reconcile(st, c.Cards);
        _data.ResumeStates[c.Id] = st;
        return st;
    }

    /// <summary>The validated card index to OPEN a course at (its resume spot).</summary>
    public int ResumeIndex(Course c) => ResumeState(c).Index;

    /// <summary>Remember the settled visible card so Study + overlay resume there. Saved
    /// immediately (Next/Previous/scroll/close all call this).</summary>
    public void SavePosition(Course c, int index)
    {
        if (c.Cards.Count == 0) return;
        index = Clamp(index, c.Cards.Count);
        var st = ResumeState(c);               // ensures fingerprint/exists
        // Resuming study of a course means it's no longer "completed" — clear the flag (and
        // persist it) even when the card index itself didn't move.
        bool wasCompleted = st.Completed;
        st.Completed = false;
        st.CompletedAtUtc = null;
        if (!wasCompleted && st.Index == index && st.CardId == c.Cards[index].CardId) return;
        st.Index = index;
        st.CardId = c.Cards[index].CardId;
        _data.ResumeStates[c.Id] = st;
        Save();
    }

    /// <summary>Final "Done": mark the course completed, reset its own resume to card 0, then
    /// pick the next eligible course (circular through courses that have cards, in visible
    /// order). Returns the course to open next — the same for Study and overlay. If this is
    /// the only eligible course, it restarts from card 0.</summary>
    public Course CompleteAndAdvance(Course current)
    {
        var st = ResumeState(current);
        st.Completed = true;
        st.CompletedAtUtc = DateTime.UtcNow;
        st.Index = 0;
        st.CardId = current.Cards.Count > 0 ? current.Cards[0].CardId : "";
        _data.ResumeStates[current.Id] = st;

        var eligible = _courses.Where(c => c.CardCount > 0).ToList();
        int curIdx = eligible.FindIndex(c => c.Id == current.Id);
        // Deterministic circular advance (or restart when this is the only eligible course).
        Course next = eligible.Count == 0 ? current
            : eligible[ResumeMath.NextCourseIndex(curIdx, eligible.Count)];
        _data.ActiveCourseId = next.Id;
        Save();
        return next;
    }

    public bool IsCompleted(string courseId) =>
        _data.ResumeStates.TryGetValue(courseId, out var s) && s.Completed;

    public void Load()
    {
        _data = ReadFile();
        MigrateLegacyResume();
        _courses.Clear();
        // Demo courses first (with any saved progress re-applied), then user courses.
        foreach (var demo in DemoLessonLoader.LoadAll())
        {
            ApplySettings(demo);
            ApplyProgress(demo);
            _courses.Add(demo);
        }
        foreach (var uc in _data.UserCourses)
        {
            ApplySettings(uc);
            // File imports never persist their slide/figure base64 (see StudyCard). If a
            // persisted card lost its image, re-extract the deck from the stored file so the
            // images come back — card ids are content-deterministic, so the resume holds.
            if (uc.SourceType == "file" && uc.Cards.Any(card => card.IsImage && !card.HasImage))
                RestoreFileDeck(uc);
            ApplyProgress(uc);
            _courses.Add(uc);
        }
        // Re-validate every stored position against the freshly loaded decks (clamps bad
        // indexes, drops fingerprints for regenerated decks). Never throws on bad data.
        foreach (var c in _courses)
            if (_data.ResumeStates.ContainsKey(c.Id)) { try { ResumeState(c); } catch { } }
    }

    /// <summary>One-time migration: convert the old flat <c>courseId → int index</c> map into
    /// the richer <see cref="CourseResumeState"/>. Indexes are validated later by ResumeState;
    /// malformed/missing legacy data is ignored, never fatal.</summary>
    private void MigrateLegacyResume()
    {
        if (_data.Resume is not { Count: > 0 }) { _data.Resume = null; return; }
        foreach (var (courseId, index) in _data.Resume)
        {
            if (_data.ResumeStates.ContainsKey(courseId)) continue; // new format wins
            _data.ResumeStates[courseId] = new CourseResumeState { Index = Math.Max(0, index) };
        }
        _data.Resume = null;   // drop the legacy field; save rewrites in the new format
        Save();
    }

    /// <summary>Re-extract a file course's deck from its stored local file so slide/figure
    /// images (which we deliberately never persist as base64) render again. Card ids are
    /// content-deterministic, so the fingerprint and resume position are unchanged. No-op if
    /// the file is gone or the engine is unavailable — the persisted (image-less) cards stand.</summary>
    private void RestoreFileDeck(Course c)
    {
        if (string.IsNullOrEmpty(c.LocalPath) || !File.Exists(c.LocalPath)) return;

        // Path-only: the engine reads the stored file itself (no Base64 transport).
        var cards = FromEngine(EngineBridge.Shared.CallData("lesson_feed_file", new
        {
            filename = c.FileName,
            payload = "",
            encoding = "base64",
            include_media = true,
            local_path = c.LocalPath,
            card_size = string.IsNullOrEmpty(c.CardSize) ? "compact" : c.CardSize,
            appearance = c.Appearance ?? "",
        }), out int slides);

        if (cards is { Count: > 0 })
        {
            c.Cards = cards;
            if (slides > 0) c.SlideCount = slides;
        }
    }

    private void ApplySettings(Course c)
    {
        if (_data.Settings.TryGetValue(c.Id, out var s))
        {
            c.Appearance = s.Appearance;
            c.CardSize = s.CardSize;
        }
    }

    /// <summary>The engine's type-aware appearance/split options for a material (what the
    /// "How cards appear" card shows). Mirrors AraEngine.materialOptions.</summary>
    public JsonElement? MaterialOptions(Course c)
    {
        string stype = c.SourceType switch
        {
            "file" => SourceTypeFromFile(c.FileName),
            _ => c.SourceText.Contains("Slide 1:", StringComparison.OrdinalIgnoreCase) ? "slides"
               : c.SourceText.Contains("Page 1:", StringComparison.OrdinalIgnoreCase) ? "pdf"
               : "pasted",
        };
        return EngineBridge.Shared.CallData("material_options", new { source_type = stype });
    }

    private static string SourceTypeFromFile(string fileName)
    {
        var ext = fileName.Contains('.') ? fileName[(fileName.LastIndexOf('.') + 1)..].ToLowerInvariant() : "";
        return ext switch
        {
            "pdf" => "pdf",
            "pptx" or "ppt" => "slides",
            "docx" or "doc" => "docx",
            "png" or "jpg" or "jpeg" or "webp" or "gif" or "bmp" => "image",
            _ => "pasted",
        };
    }

    /// <summary>Re-split a material with new appearance/card-size settings (re-calls the
    /// engine exactly like the phone), persist the choice, and reset that deck's progress
    /// since the card indices change. Returns false if the deck could not be rebuilt.</summary>
    public bool ApplyMaterialSettings(Course c, string appearance, string cardSize)
    {
        List<StudyCard>? cards = null;
        int slides = 0;
        if (!string.IsNullOrEmpty(c.LocalPath) && File.Exists(c.LocalPath))
        {
            // Path-only re-split: the engine reads the stored file itself (no Base64).
            cards = FromEngine(EngineBridge.Shared.CallData("lesson_feed_file", new
            {
                filename = c.FileName,
                payload = "",
                encoding = "base64",
                include_media = true,
                local_path = c.LocalPath,
                card_size = cardSize,
                appearance,
            }), out slides);
        }
        else if (!string.IsNullOrWhiteSpace(c.SourceText))
        {
            string stype = c.SourceText.Contains("Slide 1:", StringComparison.OrdinalIgnoreCase) ? "slides"
                : c.SourceText.Contains("Page 1:", StringComparison.OrdinalIgnoreCase) ? "pdf" : "pasted";
            // lesson_feed accepts only card_size (no appearance kwarg).
            cards = FromEngine(EngineBridge.Shared.CallData("lesson_feed", new
            {
                text = c.SourceText, source_type = stype, card_size = cardSize,
            }), out slides);
        }

        c.Appearance = appearance;
        c.CardSize = cardSize;
        _data.Settings[c.Id] = new MaterialSettings(appearance, cardSize);

        if (cards is { Count: > 0 })
        {
            c.Cards = cards;
            if (slides > 0) c.SlideCount = slides;
            // Deck regenerated: per-card Known/Repeat indices no longer line up → clear them
            // safely, and let ResumeState remap by CardId or reset to card 0 (+drop Completed).
            _data.Progress.Remove(c.Id);
            foreach (var card in c.Cards) card.State = CardState.New;
            ResumeState(c);   // recompute fingerprint + validate/reset the resume position
        }
        Save();
        return cards is { Count: > 0 };
    }

    public Course? Get(string id) => _courses.FirstOrDefault(c => c.Id == id);

    /// <summary>Create a course from pasted/typed text and persist it. Cards come from
    /// the REAL engine (engine_ara) exactly like the phone; if the engine is unavailable
    /// we fall back to the local deterministic splitter so Courses still works.</summary>
    public Course AddFromText(string name, string text)
    {
        // Mirror AraEngine.load(): pick the source_type from the text shape.
        string stype = text.Contains("Slide 1:", StringComparison.OrdinalIgnoreCase) ? "slides"
            : text.Contains("Page 1:", StringComparison.OrdinalIgnoreCase) ? "pdf"
            : "pasted";
        int slides = 0;
        var cards = FromEngine(EngineBridge.Shared.CallData("lesson_feed", new
        {
            text,
            source_type = stype,
        }), out slides) ?? CardGenerator.Generate(text);

        var c = new Course
        {
            Name = string.IsNullOrWhiteSpace(name) ? I18n.T.UntitledCourse : name.Trim(),
            FileName = "text",
            SourceType = "text",
            IsDemo = false,
            SourceText = text,
            SlideCount = slides > 0 ? slides : cards.Count,
            Cards = cards,
        };
        _data.UserCourses.Add(c);
        _courses.Add(c);
        Save();
        return c;
    }

    /// <summary>Import a local file (PDF / PPTX / DOCX / image) into a course. Synchronous
    /// entry point (kept for non-UI callers); UI handlers should use <see cref="AddFromFileAsync"/>
    /// so the engine work doesn't block the window.</summary>
    public Course? AddFromFile(string name, string path)
    {
        var c = BuildFromFile(name, path);
        if (c is null) return null;
        Register(c);
        return c;
    }

    /// <summary>Import off the UI thread: the file copy + engine extraction (the slow part)
    /// run on a worker thread; only the fast collection update + save happen back on the
    /// caller's context. Prevents the "window not responding" freeze on large files.</summary>
    public async System.Threading.Tasks.Task<Course?> AddFromFileAsync(string name, string path)
    {
        var c = await System.Threading.Tasks.Task.Run(() => BuildFromFile(name, path));
        if (c is null) return null;
        Register(c);   // resumes on the caller's (UI) context, so no _courses race
        return c;
    }

    private void Register(Course c)
    {
        _data.UserCourses.Add(c);
        _courses.Add(c);
        Save();
    }

    /// <summary>The heavy, shared-state-free part of an import: validate the file, copy it into
    /// app storage, run the engine, and build a (not-yet-registered) Course. Returns null if the
    /// file is not a real file of its claimed type or the engine produced no cards.</summary>
    private Course? BuildFromFile(string name, string path)
    {
        if (!File.Exists(path)) return null;
        // Reject a file whose real bytes don't match its extension (e.g. a "bad.pdf" that is
        // not actually a PDF) BEFORE importing, instead of creating a broken visual card.
        if (!FileSignature.MatchesExtension(path)) return null;

        // Copy the file into our own materials folder (like the phone's filesDir), so PDF
        // cards keep rendering even if the user moves/deletes the original. File.Copy streams
        // it — we never hold the whole file (or a Base64 string of it) in memory.
        string stablePath = path;
        bool ownedCopy = false;
        try
        {
            var matDir = Path.Combine(Dir, "materials");
            Directory.CreateDirectory(matDir);
            stablePath = Path.Combine(matDir, $"{Guid.NewGuid():N}{Path.GetExtension(path)}");
            File.Copy(path, stablePath, overwrite: true);
            ownedCopy = true;
        }
        catch { stablePath = path; }   // fall back to the original path (H-02: rendered from there)

        // Pass ONLY the local path (empty payload): the engine opens and reads the file
        // itself, so no Base64 is encoded in C# or shipped over the pipe.
        var data = EngineBridge.Shared.CallData("lesson_feed_file", new
        {
            filename = Path.GetFileName(path),
            payload = "",
            encoding = "base64",
            include_media = true,
            local_path = stablePath,
            card_size = "compact",
            appearance = "",
        });
        var cards = FromEngine(data, out int slides);
        if (cards is null || cards.Count == 0)
        {
            // Import failed: don't leave an orphaned copy behind (H-03).
            if (ownedCopy) { try { File.Delete(stablePath); } catch { } }
            return null;
        }

        return new Course
        {
            Name = string.IsNullOrWhiteSpace(name) ? Path.GetFileNameWithoutExtension(path) : name.Trim(),
            FileName = Path.GetFileName(path),
            SourceType = "file",
            IsDemo = false,
            LocalPath = stablePath,
            SlideCount = slides > 0 ? slides : cards.Count,
            Cards = cards,
        };
    }

    /// <summary>Parse an engine feed envelope's data into cards (null if no usable feed).</summary>
    private static List<StudyCard>? FromEngine(JsonElement? data, out int slides)
    {
        slides = 0;
        if (data is not { } feed) return null;
        var cards = EngineFeed.Parse(feed);
        if (cards.Count == 0) return null;
        slides = EngineFeed.SectionCount(feed);
        return cards;
    }

    /// <summary>Delete a course and EVERY trace of it: cards, per-card state, settings, resume/
    /// completion state, deck fingerprint, and the active-course reference if it pointed here.
    /// Callers (overlay controller) read ActiveCourse afterwards, so no stale ref survives.</summary>
    public void Delete(string id)
    {
        // Grab the imported-file copy path BEFORE we drop the course, so we can also remove
        // the material from disk — the privacy text promises deletion leaves nothing behind.
        var localPath = _courses.FirstOrDefault(c => c.Id == id)?.LocalPath;

        _courses.RemoveAll(c => c.Id == id);
        _data.UserCourses.RemoveAll(c => c.Id == id);
        _data.Progress.Remove(id);
        _data.Settings.Remove(id);
        _data.ResumeStates.Remove(id);
        if (_data.ActiveCourseId == id)
            _data.ActiveCourseId = _courses.FirstOrDefault(c => c.CardCount > 0)?.Id ?? "";
        Save();

        DeleteOwnedFile(localPath);   // only if it lives in our own materials folder
        App.Exams.RemoveMaterialFromAll(id);        // drop ghost references from every exam
        App.Controller?.NotifyCourseDeleted(id);    // close an overlay still showing this course
    }

    /// <summary>Delete an imported material copy, but ONLY when it lives inside our own
    /// %AppData%\AntiROTAid\materials folder — never touch a file the user still owns
    /// elsewhere on disk (e.g. if the stable-copy step had fallen back to the original path).</summary>
    private static void DeleteOwnedFile(string? path)
    {
        if (string.IsNullOrEmpty(path)) return;
        try
        {
            var materials = Path.GetFullPath(Path.Combine(Dir, "materials")) + Path.DirectorySeparatorChar;
            var full = Path.GetFullPath(path);
            if (full.StartsWith(materials, StringComparison.OrdinalIgnoreCase) && File.Exists(full))
                File.Delete(full);
        }
        catch { /* best-effort cleanup */ }
    }

    /// <summary>Remove a single card from a deck and keep every derived state consistent:
    /// persist the deck, drop that card's learning state, recompute the fingerprint, and
    /// remap the resume position (nearest valid card; clamp when the last card is removed;
    /// clean empty state when the deck empties). Persists — not just the in-memory list.</summary>
    public void DeleteCard(Course c, int index)
    {
        if (index < 0 || index >= c.Cards.Count) return;
        c.Cards.RemoveAt(index);

        // Rebuild per-card learning state around the removed slot (indices shift down).
        if (_data.Progress.TryGetValue(c.Id, out var map))
        {
            var rebuilt = new Dictionary<int, CardState>();
            foreach (var (i, st) in map)
            {
                if (i == index) continue;
                rebuilt[i > index ? i - 1 : i] = st;
            }
            if (rebuilt.Count > 0) _data.Progress[c.Id] = rebuilt; else _data.Progress.Remove(c.Id);
        }

        // Remap the resume position around the removed card.
        if (_data.ResumeStates.TryGetValue(c.Id, out var rs))
            ResumeMath.RemapAfterDelete(rs, index, c.Cards);
        // Persist the mutated deck (user courses) + recompute the fingerprint so a later
        // resume stays valid. Demos rebuild from assets each run, so their deletion is
        // per-session; the resume state still reconciles by fingerprint next load.
        ResumeState(c);
        Save();
    }

    /// <summary>Record a card's study state (known / repeat) and persist.</summary>
    public void SetCardState(Course course, int index, CardState state)
    {
        if (index < 0 || index >= course.Cards.Count) return;
        course.Cards[index].State = state;
        if (!_data.Progress.TryGetValue(course.Id, out var map))
            _data.Progress[course.Id] = map = new Dictionary<int, CardState>();
        map[index] = state;
        Save();
    }

    private void ApplyProgress(Course c)
    {
        if (!_data.Progress.TryGetValue(c.Id, out var map)) return;
        foreach (var (idx, state) in map)
            if (idx >= 0 && idx < c.Cards.Count) c.Cards[idx].State = state;
    }

    private static Persisted ReadFile()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<Persisted>(File.ReadAllText(FilePath)) ?? new Persisted();
        }
        catch { /* corrupt → defaults */ }
        return new Persisted();
    }

    private void Save()
    {
        try
        {
            AtomicFile.Write(FilePath, JsonSerializer.Serialize(_data, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { /* best-effort */ }
    }
}
