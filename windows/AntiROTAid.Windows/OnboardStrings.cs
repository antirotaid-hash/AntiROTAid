using System.Collections.Generic;

namespace AntiROTAid.Windows;

/// <summary>
/// First-run onboarding text (language pick → disclaimer → privacy/consent),
/// ported verbatim from the Android ara flavor (AraI18n.kt) so the Windows
/// first-run is identical in all 5 languages (RO/EN/DE/FR/ES).
/// </summary>
public sealed record OnboardText(
    string LangPickTitle, string LangPickLead, string Continue,
    string WarnTitle, string WarnBody,
    string PrivacyBrand, string PrivacyTitle, string PrivacyLead,
    string ConsentAgree, string ConsentContinue,
    (string T, string B)[] Policy);

public static class Onboard
{
    public static OnboardText For(string code) =>
        All.TryGetValue(code, out var v) ? v : All["en"];

    private static readonly Dictionary<string, OnboardText> All = new()
    {
        ["ro"] = new(
            LangPickTitle: "Alege limba",
            LangPickLead: "În ce limbă vrei aplicația? O poți schimba oricând din Setări.",
            Continue: "Continuă",
            WarnTitle: "Disclaimer",
            WarnBody: "AntiROTAid este doar un posibil ajutor — nu înlocuiește școala/facultatea, nu vindecă dependența de dispozitiv și nu garantează note mari. Pentru rezultate reale și note bune, cel mai bine rămâne să mergi la școală/facultate, să înveți, să-ți iei notițe și să participi la activități. Aplicația doar te ajută să recuperezi câteva minute din timpul petrecut pe dispozitiv.",
            PrivacyBrand: "AntiROTAid",
            PrivacyTitle: "Confidențialitate",
            PrivacyLead: "Pe scurt: nimic nu pleacă de pe dispozitivul tău.",
            ConsentAgree: "Am citit și am înțeles.",
            ConsentContinue: "Continuă",
            Policy: new[]
            {
                ("Ce prelucrează", "Doar setările tale locale (limbă, temă) și dacă modul este pornit sau oprit."),
                ("Unde rămâne", "Totul stă pe acest dispozitiv. Nimic NU este trimis nicăieri."),
                ("Fără internet", "Aplicația funcționează complet offline. Nu trimite și nu primește date."),
                ("Fără cont, fără reclame", "Nu cere cont, e-mail sau parolă. Fără reclame. Nu se colectează nimic despre tine."),
                ("Control total", "Poți opri modul oricând. Când dezinstalezi aplicația, setările tale locale dispar odată cu ea."),
            }),

        ["en"] = new(
            LangPickTitle: "Choose your language",
            LangPickLead: "Which language do you want the app in? You can change it anytime in Settings.",
            Continue: "Continue",
            WarnTitle: "Disclaimer",
            WarnBody: "AntiROTAid is only a possible helper — it does not replace school, it does not cure device addiction, and it does not guarantee good grades. For real results and good marks, the best path is still going to school, studying, taking notes and joining activities. The app only helps you reclaim a few minutes of your device time.",
            PrivacyBrand: "AntiROTAid",
            PrivacyTitle: "Privacy",
            PrivacyLead: "In short: nothing leaves your device.",
            ConsentAgree: "I have read and understood.",
            ConsentContinue: "Continue",
            Policy: new[]
            {
                ("What it processes", "Only your local settings (language, theme) and whether the mode is on or off."),
                ("Where it stays", "Everything stays on this device. Nothing is sent anywhere."),
                ("No internet", "The app works fully offline. It sends and receives no data."),
                ("No account, no ads", "No account, email or password. No ads. Nothing about you is collected."),
                ("Full control", "You can stop the mode anytime. When you uninstall the app, your local settings go with it."),
            }),

        ["de"] = new(
            LangPickTitle: "Sprache wählen",
            LangPickLead: "In welcher Sprache willst du die App? Du kannst sie jederzeit in den Einstellungen ändern.",
            Continue: "Weiter",
            WarnTitle: "Disclaimer",
            WarnBody: "AntiROTAid ist nur eine mögliche Hilfe — sie ersetzt nicht die Schule, heilt keine Gerätesucht und garantiert keine guten Noten. Für echte Ergebnisse und gute Noten bleibt es am besten, zur Schule zu gehen, zu lernen, Notizen zu machen und an Aktivitäten teilzunehmen. Die App hilft dir nur, ein paar Minuten deiner Gerätezeit zurückzuholen.",
            PrivacyBrand: "AntiROTAid",
            PrivacyTitle: "Datenschutz",
            PrivacyLead: "Kurz gesagt: nichts verlässt dein Gerät.",
            ConsentAgree: "Ich habe es gelesen und verstanden.",
            ConsentContinue: "Weiter",
            Policy: new[]
            {
                ("Was verarbeitet wird", "Nur deine lokalen Einstellungen (Sprache, Design) und ob der Modus an oder aus ist."),
                ("Wo es bleibt", "Alles bleibt auf diesem Gerät. Es wird nichts irgendwohin gesendet."),
                ("Kein Internet", "Die App funktioniert komplett offline. Sie sendet und empfängt keine Daten."),
                ("Kein Konto, keine Werbung", "Kein Konto, keine E-Mail, kein Passwort. Keine Werbung. Es wird nichts über dich gesammelt."),
                ("Volle Kontrolle", "Du kannst den Modus jederzeit stoppen. Wenn du die App deinstallierst, verschwinden deine lokalen Einstellungen mit ihr."),
            }),

        ["fr"] = new(
            LangPickTitle: "Choisis ta langue",
            LangPickLead: "Dans quelle langue veux-tu l'appli ? Tu peux la changer à tout moment dans les Réglages.",
            Continue: "Continuer",
            WarnTitle: "Disclaimer",
            WarnBody: "AntiROTAid n'est qu'une aide possible — elle ne remplace pas l'école, ne guérit pas la dépendance à l'appareil et ne garantit pas de bonnes notes. Pour de vrais résultats et de bonnes notes, le mieux reste d'aller en cours, d'étudier, de prendre des notes et de participer aux activités. L'appli t'aide seulement à récupérer quelques minutes de ton temps passé sur l'appareil.",
            PrivacyBrand: "AntiROTAid",
            PrivacyTitle: "Confidentialité",
            PrivacyLead: "En bref : rien ne quitte ton appareil.",
            ConsentAgree: "J'ai lu et compris.",
            ConsentContinue: "Continuer",
            Policy: new[]
            {
                ("Ce qui est traité", "Seulement tes réglages locaux (langue, thème) et si le mode est activé ou non."),
                ("Où ça reste", "Tout reste sur cet appareil. Rien n'est envoyé nulle part."),
                ("Sans internet", "L'appli fonctionne entièrement hors ligne. Elle n'envoie ni ne reçoit aucune donnée."),
                ("Sans compte, sans pub", "Aucun compte, e-mail ou mot de passe. Pas de pub. Rien n'est collecté sur toi."),
                ("Contrôle total", "Tu peux arrêter le mode à tout moment. Quand tu désinstalles l'appli, tes réglages locaux partent avec elle."),
            }),

        ["es"] = new(
            LangPickTitle: "Elige tu idioma",
            LangPickLead: "¿En qué idioma quieres la app? Puedes cambiarlo cuando quieras en Ajustes.",
            Continue: "Continuar",
            WarnTitle: "Disclaimer",
            WarnBody: "AntiROTAid es solo una posible ayuda — no sustituye a la escuela, no cura la adicción al dispositivo ni garantiza buenas notas. Para resultados reales y buenas notas, lo mejor sigue siendo ir a clase, estudiar, tomar apuntes y participar en las actividades. La app solo te ayuda a recuperar unos minutos del tiempo que pasas en el dispositivo.",
            PrivacyBrand: "AntiROTAid",
            PrivacyTitle: "Privacidad",
            PrivacyLead: "En resumen: nada sale de tu dispositivo.",
            ConsentAgree: "He leído y entendido.",
            ConsentContinue: "Continuar",
            Policy: new[]
            {
                ("Qué procesa", "Solo tus ajustes locales (idioma, tema) y si el modo está activado o no."),
                ("Dónde se queda", "Todo se queda en este dispositivo. No se envía nada a ningún sitio."),
                ("Sin internet", "La app funciona totalmente sin conexión. No envía ni recibe datos."),
                ("Sin cuenta, sin anuncios", "Sin cuenta, correo ni contraseña. Sin anuncios. No se recopila nada sobre ti."),
                ("Control total", "Puedes detener el modo cuando quieras. Cuando desinstalas la app, tus ajustes locales se van con ella."),
            }),
    };
}
