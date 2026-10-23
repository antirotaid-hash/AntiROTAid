using System.Collections.Generic;
using AntiROTAid.Windows;
using AntiROTAid.Windows.Models;
using AntiROTAid.Windows.Services;
using AntiROTAid.Windows.Themes;
using Xunit;

namespace AntiROTAid.Windows.Tests;

/// <summary>Persisted models + a couple of wiring guarantees behind the repairs.</summary>
public class ModelTests
{
    [Fact]
    public void Settings_FreshInstall_Defaults()
    {
        var s = new Settings();
        Assert.False(s.LanguageChosen);          // first-run picker not answered yet
        Assert.False(s.TutorialSeen);            // first-run tutorial not shown yet (Part 7)
        Assert.Equal(AraThemeStyle.DarkGold, s.Theme);
        Assert.Equal(5, s.IntervalMinutes);
    }

    [Fact]
    public void CourseResumeState_Defaults_AreCleanStart()
    {
        var st = new CourseResumeState();
        Assert.Equal(0, st.Index);
        Assert.Equal("", st.CardId);
        Assert.Equal("", st.DeckFingerprint);
        Assert.False(st.Completed);
        Assert.Null(st.CompletedAtUtc);
    }

    [Fact]
    public void StudyCard_HasImage_TrueForBase64()
        => Assert.True(new StudyCard { CardType = "slide_image", ImageBase64 = "AAAA" }.HasImage);

    [Fact]
    public void StudyCard_HasImage_TrueForImagePath()
        => Assert.True(new StudyCard { CardType = "slide_image", ImagePath = @"C:\x.png" }.HasImage);

    [Fact]
    public void StudyCard_HasImage_TrueForPdfPage()
        => Assert.True(new StudyCard { CardType = "slide_image", PdfDocPath = @"C:\x.pdf", PdfPage = 1 }.HasImage);

    [Fact]
    public void StudyCard_HasImage_FalseWhenNoMedia()
        => Assert.False(new StudyCard { CardType = "slide_image" }.HasImage);

    [Fact]
    public void StudyCard_PdfPageZero_IsNotRenderable()
        => Assert.False(new StudyCard { CardType = "slide_image", PdfDocPath = @"C:\x.pdf", PdfPage = 0 }.HasImage);

    [Fact]
    public void CourseStore_Fingerprint_DelegatesToResumeMath()
    {
        var deck = new List<StudyCard> { new() { CardId = "a" }, new() { CardId = "b" } };
        Assert.Equal(ResumeMath.Fingerprint(deck), CourseStore.Fingerprint(deck));
    }
}
