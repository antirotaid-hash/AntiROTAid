using System;
using System.IO;
using System.Linq;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Services;
using Xunit;

namespace AntiROTAid.Windows.Tests;

/// <summary>
/// End-to-end store flows the pre-Store audit demanded as mandatory tests: resume survives
/// exit+reopen (incl. a course opened at card 0), and final "Done" advances/restarts with the
/// right completion + active-course state. Uses the CourseStore test seam (temp dir + seed),
/// so no engine, no demo loader, no real user data touched.
/// </summary>
[Collection("store")]
public class StoreFlowTests : IDisposable
{
    private readonly string _dir;

    public StoreFlowTests()
    {
        _dir = Path.Combine(Path.GetTempPath(), "ARA_test_" + Guid.NewGuid().ToString("N"));
        CourseStore.TestDirOverride = _dir;
    }

    public void Dispose()
    {
        CourseStore.TestDirOverride = null;
        try { Directory.Delete(_dir, recursive: true); } catch { }
    }

    private static Course CourseWith(string id, int n)
    {
        var c = new Course { Id = id, Name = id, IsDemo = false, SourceType = "file" };
        for (int i = 0; i < n; i++) c.Cards.Add(new StudyCard { CardId = $"{id}_{i}", Title = $"Card {i}" });
        return c;
    }

    // Audit-required test #2: new course at card 0 → go to card 5 → exit → reopen → card 5.
    [Fact]
    public void Resume_SurvivesExitAndReopen_FromCardZero()
    {
        var store = new CourseStore();
        var c = CourseWith("c1", 8);
        store.SeedForTests(c);

        Assert.Equal(0, store.ResumeIndex(c));   // opens at card 0
        store.SavePosition(c, 5);                 // user studies to card 5, then exits
        Assert.Equal(5, store.ResumeIndex(c));    // reopen → card 5, not 1
    }

    // Audit-required test #3: Done on a single course → reset to card 1, advance restarts it;
    // studying again clears Completed and the new position persists.
    [Fact]
    public void Done_SingleCourse_RestartsAndResumeWorksAgain()
    {
        var store = new CourseStore();
        var c = CourseWith("c1", 6);
        store.SeedForTests(c);
        store.SavePosition(c, 4);

        var next = store.CompleteAndAdvance(c);
        Assert.Same(c, next);                     // only course → restart it
        Assert.True(store.IsCompleted("c1"));
        Assert.Equal(0, store.ResumeIndex(c));    // reset to card 0

        store.SavePosition(c, 2);                  // study again
        Assert.False(store.IsCompleted("c1"));     // resuming clears "completed"
        Assert.Equal(2, store.ResumeIndex(c));
    }

    [Fact]
    public void Done_TwoCourses_AdvancesToNextAndSetsActive()
    {
        var store = new CourseStore();
        var a = CourseWith("a", 3);
        var b = CourseWith("b", 3);
        store.SeedForTests(a, b);

        var next = store.CompleteAndAdvance(a);
        Assert.Same(b, next);
        Assert.Same(b, store.ActiveCourse);
        Assert.True(store.IsCompleted("a"));
    }

    [Fact]
    public void DeleteCard_ShiftsResumeAndPersists()
    {
        var store = new CourseStore();
        var c = CourseWith("c1", 5);
        store.SeedForTests(c);
        store.SavePosition(c, 3);

        store.DeleteCard(c, 1);                    // a card before the position is removed
        Assert.Equal(4, c.CardCount);
        Assert.Equal(2, store.ResumeIndex(c));     // 3 → 2
    }

    [Fact]
    public void ActiveCourse_DefaultsToFirstWithCards_WhenNoneSaved()
    {
        var store = new CourseStore();
        var empty = CourseWith("empty", 0);
        var real = CourseWith("real", 4);
        store.SeedForTests(empty, real);
        Assert.Same(real, store.ActiveCourse);
    }
}

/// <summary>The magic-byte guard that rejects a file whose bytes don't match its extension.</summary>
public class FileSignatureTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "ARA_sig_" + Guid.NewGuid().ToString("N"));

    public FileSignatureTests() => Directory.CreateDirectory(_dir);
    public void Dispose() { try { Directory.Delete(_dir, true); } catch { } }

    private string Make(string ext, byte[] bytes)
    {
        var p = Path.Combine(_dir, "f." + ext);
        File.WriteAllBytes(p, bytes);
        return p;
    }

    [Fact]
    public void FakePdf_IsRejected()
        => Assert.False(FileSignature.MatchesExtension(Make("pdf", System.Text.Encoding.ASCII.GetBytes("hello not a pdf"))));

    [Fact]
    public void RealPdf_IsAccepted()
        => Assert.True(FileSignature.MatchesExtension(Make("pdf", System.Text.Encoding.ASCII.GetBytes("%PDF-1.4\n..."))));

    [Fact]
    public void RealPptx_ZipMagic_IsAccepted()
        => Assert.True(FileSignature.MatchesExtension(Make("pptx", new byte[] { 0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0 })));

    [Fact]
    public void PlainText_AlwaysAllowed()
        => Assert.True(FileSignature.MatchesExtension(Make("txt", System.Text.Encoding.ASCII.GetBytes("anything"))));
}
