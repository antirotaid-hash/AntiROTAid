using AntiROTAid.Windows;
using Xunit;

namespace AntiROTAid.Windows.Tests;

/// <summary>
/// Localization completeness (Part 6): all five languages resolve, every newly-localized
/// string is present in each, and format strings keep their placeholders. These run in a
/// single collection because <see cref="I18n.Set"/> mutates process-wide state.
/// </summary>
[Collection("i18n")]
public class LocalizationTests
{
    private static readonly string[] Locales = { "ro", "en", "de", "fr", "es" };

    [Fact]
    public void AllFiveLanguagesAreRegistered()
    {
        Assert.Equal(5, I18n.Languages.Count);
        foreach (var code in Locales)
        {
            I18n.Set(code);
            Assert.Equal(code, I18n.Locale);
            Assert.Equal("AntiROTAid", I18n.T.AppName);
        }
    }

    [Fact]
    public void UnknownLocale_FallsBackToEnglish()
    {
        I18n.Set("xx");
        Assert.Equal("en", I18n.Locale);
    }

    [Fact]
    public void FilePickerLabels_LocalizedAndNonEmpty()
    {
        foreach (var code in Locales)
        {
            I18n.Set(code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.FilePickerCourseFiles), code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.FilePickerAllFiles), code);
        }
    }

    [Fact]
    public void UntitledFallbacks_LocalizedAndNonEmpty()
    {
        foreach (var code in Locales)
        {
            I18n.Set(code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.UntitledCourse), code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.UntitledExam), code);
        }
    }

    [Fact]
    public void CardsLabel_KeepsCountPlaceholder_InEveryLanguage()
    {
        foreach (var code in Locales)
        {
            I18n.Set(code);
            Assert.Contains("{0}", I18n.T.CardsLabel);
        }
    }

    [Fact]
    public void CardOf_KeepsBothPlaceholders_InEveryLanguage()
    {
        foreach (var code in Locales)
        {
            I18n.Set(code);
            Assert.Contains("{0}", I18n.T.CardOf);
            Assert.Contains("{1}", I18n.T.CardOf);
        }
    }

    [Fact]
    public void CoreNavStrings_NonEmpty_InEveryLanguage()
    {
        foreach (var code in Locales)
        {
            I18n.Set(code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.NavHome), code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.StartMode), code);
            Assert.False(string.IsNullOrWhiteSpace(I18n.T.PickCourse), code);
        }
    }
}
