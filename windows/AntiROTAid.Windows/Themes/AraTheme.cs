using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Media;

namespace AntiROTAid.Windows.Themes;

public enum AraThemeStyle { DarkGold, AmoledBlack, LightClean, NeonFocus, DeepBlue }

/// <summary>
/// The exact ARA brand palette (ported 1:1 from Android `AraBrand`). Compose stores
/// colours as 0xAARRGGBB; WPF uses #AARRGGBB — same byte order, so the values are
/// copied verbatim. Applying a theme rewrites named brushes in the app resources,
/// which every window/control binds via DynamicResource.
/// </summary>
public sealed record AraPalette(
    string BgTop, string BgBottom, string Green, string GreenDeep, string Teal,
    string GreenSoft, string Title, string Body, string Muted, string Danger,
    string Purple, string GlassTop, string GlassBottom, string GlassEdge,
    string GlassEdgeGreen, string Glow, string GlowStrong);

public static class AraTheme
{
    public static AraThemeStyle Active { get; private set; } = AraThemeStyle.DarkGold;

    private static readonly Dictionary<AraThemeStyle, AraPalette> Palettes = new()
    {
        [AraThemeStyle.DarkGold] = new(
            BgTop: "#FF050505", BgBottom: "#FF111111", Green: "#FFFFC107", GreenDeep: "#FFFFD54F",
            Teal: "#FFFF8F00", GreenSoft: "#FF241B05", Title: "#FFF7F7F7", Body: "#FFD7D7DA",
            Muted: "#FF9A9AA0", Danger: "#FFFF6B5E", Purple: "#FFFFC107", GlassTop: "#F1141414",
            GlassBottom: "#F00A0A0A", GlassEdge: "#66FFFFFF", GlassEdgeGreen: "#AAFFC107",
            Glow: "#33FFC107", GlowStrong: "#66FFC107"),

        [AraThemeStyle.AmoledBlack] = new(
            BgTop: "#FF000000", BgBottom: "#FF000000", Green: "#FFFFD54F", GreenDeep: "#FFFFE082",
            Teal: "#FFB2FF59", GreenSoft: "#FF151309", Title: "#FFFFFFFF", Body: "#FFE5E7EB",
            Muted: "#FF9CA3AF", Danger: "#FFFF5252", Purple: "#FFFFD54F", GlassTop: "#FA080808",
            GlassBottom: "#F5000000", GlassEdge: "#44FFFFFF", GlassEdgeGreen: "#99FFD54F",
            Glow: "#22FFD54F", GlowStrong: "#55FFD54F"),

        [AraThemeStyle.LightClean] = new(
            BgTop: "#FFF7FAFC", BgBottom: "#FFEFF6FF", Green: "#FF2563EB", GreenDeep: "#FF1D4ED8",
            Teal: "#FF10B981", GreenSoft: "#FFEFF6FF", Title: "#FF111827", Body: "#FF374151",
            Muted: "#FF6B7280", Danger: "#FFDC2626", Purple: "#FF7C3AED", GlassTop: "#FFFFFFFF",
            GlassBottom: "#FFF8FAFC", GlassEdge: "#FFFFFFFF", GlassEdgeGreen: "#552563EB",
            Glow: "#222563EB", GlowStrong: "#442563EB"),

        [AraThemeStyle.NeonFocus] = new(
            BgTop: "#FF030712", BgBottom: "#FF0B1020", Green: "#FF22D3EE", GreenDeep: "#FF67E8F9",
            Teal: "#FFA855F7", GreenSoft: "#FF0C2630", Title: "#FFF8FAFC", Body: "#FFD1D5DB",
            Muted: "#FF94A3B8", Danger: "#FFFB7185", Purple: "#FFA855F7", GlassTop: "#F10B1020",
            GlassBottom: "#F0060A14", GlassEdge: "#44FFFFFF", GlassEdgeGreen: "#AA22D3EE",
            Glow: "#3322D3EE", GlowStrong: "#6622D3EE"),

        [AraThemeStyle.DeepBlue] = new(
            BgTop: "#FF06111F", BgBottom: "#FF0B172A", Green: "#FF38BDF8", GreenDeep: "#FF7DD3FC",
            Teal: "#FF60A5FA", GreenSoft: "#FF0E2A44", Title: "#FFF1F5F9", Body: "#FFD4DEE9",
            Muted: "#FF94A3B8", Danger: "#FFFF6B6B", Purple: "#FF818CF8", GlassTop: "#F10F1D33",
            GlassBottom: "#F0081324", GlassEdge: "#44FFFFFF", GlassEdgeGreen: "#AA38BDF8",
            Glow: "#3338BDF8", GlowStrong: "#6638BDF8"),
    };

    public static IReadOnlyDictionary<AraThemeStyle, string> Labels => new Dictionary<AraThemeStyle, string>
    {
        [AraThemeStyle.DarkGold] = "Dark Gold",
        [AraThemeStyle.AmoledBlack] = "Amoled Black",
        [AraThemeStyle.LightClean] = "Light Clean",
        [AraThemeStyle.NeonFocus] = "Neon Focus",
        [AraThemeStyle.DeepBlue] = "Deep Blue",
    };

    public static AraPalette Current => Palettes[Active];

    /// <summary>The signature accent colour of a theme, for the Settings theme tiles.</summary>
    public static Color AccentFor(AraThemeStyle style) => C(Palettes[style].Green);
    public static Color BgBottomFor(AraThemeStyle style) => C(Palettes[style].BgBottom);

    private static Color C(string hex) => (Color)ColorConverter.ConvertFromString(hex)!;

    /// <summary>Write the active palette into app resources; call once at startup and on change.</summary>
    public static void Apply(AraThemeStyle style)
    {
        Active = style;
        var p = Palettes[style];
        var res = Application.Current.Resources;

        void Brush(string key, string hex) => res[key] = new SolidColorBrush(C(hex));

        Brush("AraBgTop", p.BgTop);
        Brush("AraBgBottom", p.BgBottom);
        Brush("AraGreen", p.Green);
        Brush("AraGreenDeep", p.GreenDeep);
        Brush("AraTeal", p.Teal);
        Brush("AraGreenSoft", p.GreenSoft);
        Brush("AraTitle", p.Title);
        Brush("AraBody", p.Body);
        Brush("AraMuted", p.Muted);
        Brush("AraDanger", p.Danger);
        Brush("AraPurple", p.Purple);
        Brush("AraGlassTop", p.GlassTop);
        Brush("AraGlassBottom", p.GlassBottom);
        Brush("AraGlassEdge", p.GlassEdge);
        Brush("AraGlassEdgeGreen", p.GlassEdgeGreen);
        Brush("AraGlow", p.Glow);
        Brush("AraGlowStrong", p.GlowStrong);

        // Composite brushes used across the UI.
        res["AraBgGradient"] = new LinearGradientBrush(C(p.BgTop), C(p.BgBottom), 90);
        res["AraBrush"] = new LinearGradientBrush(C(p.Green), C(p.Teal), 0);           // araBrush = green→teal
        res["AraGlassGradient"] = new LinearGradientBrush(C(p.GlassTop), C(p.GlassBottom), 90);
    }
}
