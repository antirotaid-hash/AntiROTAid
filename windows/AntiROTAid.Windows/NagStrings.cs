using System;
using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// The rotating motivational one-liners shown on the floating overlay card, ported
/// from the phone (AraI18n.nagMessages). One is picked at random each time a card
/// appears, exactly like the phone overlay.
/// </summary>
public static class NagI18n
{
    private static readonly Random _rng = new();

    public static string? Random(string code)
    {
        var list = All.TryGetValue(code, out var v) ? v : All["en"];
        return list.Count == 0 ? null : list[_rng.Next(list.Count)];
    }

    private static readonly Dictionary<string, List<string>> All = new()
    {
        ["ro"] = new() {
            "Salut! Un card rapid și gata.", "Hai să învățăm ceva mic acum.",
            "Un minut pentru tine și mintea ta.", "Pauză bună de scroll — hai la un card.",
            "Doar un card, apoi continui liniștit.", "Mintea ta îți mulțumește pentru asta.",
            "Hai să prindem o idee nouă.", "Un pas mic azi, un zâmbet la examen.",
            "Te-ai descurcat — încă un card?", "Învățăm puțin, fără stres.",
            "Cardul ăsta e ușor, promitem.", "Hai, doar atât. Ești pe drumul bun." },
        ["en"] = new() {
            "Hey! Quick card, then back to it.", "Let's learn one small thing now.",
            "A minute for you and your mind.", "Nice scroll break — here's a card.",
            "Just one card, then carry on.", "Your brain says thanks for this.",
            "Let's catch one fresh idea.", "Small step today, big smile at the exam.",
            "You've got this — one more card?", "A little learning, zero stress.",
            "This card's an easy one, promise.", "Just this. You're on the right track." },
        ["de"] = new() {
            "Hi! Eine schnelle Karte, dann weiter.", "Lass uns kurz etwas Kleines lernen.",
            "Eine Minute für dich und deinen Kopf.", "Schöne Scroll-Pause — hier eine Karte.",
            "Nur eine Karte, dann mach weiter.", "Dein Gehirn sagt danke.",
            "Fangen wir eine frische Idee ein.", "Kleiner Schritt heute, Lächeln in der Prüfung.",
            "Du schaffst das — noch eine Karte?", "Ein bisschen lernen, null Stress.",
            "Diese Karte ist leicht, versprochen.", "Nur das. Du bist auf dem richtigen Weg." },
        ["fr"] = new() {
            "Coucou ! Une carte rapide, puis continue.", "Apprenons une petite chose maintenant.",
            "Une minute pour toi et ton esprit.", "Belle pause scroll — voici une carte.",
            "Juste une carte, puis continue.", "Ton cerveau te dit merci.",
            "Attrapons une idée fraîche.", "Petit pas aujourd'hui, sourire à l'examen.",
            "Tu gères — une carte de plus ?", "Un peu d'apprentissage, zéro stress.",
            "Cette carte est facile, promis.", "Juste ça. Tu es sur la bonne voie." },
        ["es"] = new() {
            "¡Hola! Una tarjeta rápida y sigues.", "Aprendamos algo pequeño ahora.",
            "Un minuto para ti y tu mente.", "Buena pausa del scroll — aquí una tarjeta.",
            "Solo una tarjeta, y continúas.", "Tu cerebro te lo agradece.",
            "Atrapemos una idea fresca.", "Pequeño paso hoy, sonrisa en el examen.",
            "Tú puedes — ¿una tarjeta más?", "Un poco de estudio, cero estrés.",
            "Esta tarjeta es fácil, lo prometo.", "Solo esto. Vas por buen camino." },
    };
}
