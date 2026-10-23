package com.mindlift.ara

/**
 * Localized file-validation & deck-change messages (Android Update 1, Phase 8).
 *
 * Kept as a small, standalone table keyed by [Lang] — deliberately NOT part of the
 * large `Tr` data class (whose 251-argument constructor cannot be loaded by the
 * plain JVM used for unit tests) — so these strings are directly unit-testable and
 * every engine validation code maps to a real message in all five languages.
 */
object AraImportMessages {

    private class M(
        val ro: String, val en: String, val de: String, val fr: String, val es: String,
    ) {
        fun of(lang: Lang): String = when (lang) {
            Lang.RO -> ro; Lang.EN -> en; Lang.DE -> de; Lang.FR -> fr; Lang.ES -> es
        }
    }

    private val INVALID = M(
        "Fișierul nu este valid sau nu are formatul așteptat. Încarcă un fișier corect.",
        "That file isn't valid or isn't the expected format. Please pick a proper file.",
        "Diese Datei ist ungültig oder hat nicht das erwartete Format. Bitte wähle eine passende Datei.",
        "Ce fichier n'est pas valide ou n'a pas le format attendu. Choisis un fichier correct.",
        "Ese archivo no es válido o no tiene el formato esperado. Elige un archivo correcto.")
    private val CORRUPT = M(
        "Fișierul pare corupt și nu poate fi citit. Exportă-l din nou și reîncearcă.",
        "The file looks corrupted and can't be read. Export it again and retry.",
        "Die Datei scheint beschädigt und kann nicht gelesen werden. Exportiere sie erneut und versuche es nochmal.",
        "Le fichier semble corrompu et ne peut pas être lu. Réexporte-le puis réessaie.",
        "El archivo parece dañado y no se puede leer. Expórtalo de nuevo e inténtalo otra vez.")
    private val EMPTY = M(
        "Fișierul este gol. Alege un fișier cu conținut.",
        "The file is empty. Choose a file that has some content.",
        "Die Datei ist leer. Wähle eine Datei mit Inhalt.",
        "Le fichier est vide. Choisis un fichier qui contient du contenu.",
        "El archivo está vacío. Elige un archivo con contenido.")
    private val NO_CONTENT = M(
        "Nu am găsit text sau conținut citibil în fișier.",
        "No readable text or content was found in the file.",
        "In der Datei wurde kein lesbarer Text oder Inhalt gefunden.",
        "Aucun texte ou contenu lisible n'a été trouvé dans le fichier.",
        "No se encontró texto ni contenido legible en el archivo.")
    private val UNSUPPORTED = M(
        "Formatul fișierului nu este acceptat. Folosește TXT, PDF, DOCX, PPTX sau imagine.",
        "That file format isn't supported. Use TXT, PDF, DOCX, PPTX or an image.",
        "Dieses Dateiformat wird nicht unterstützt. Verwende TXT, PDF, DOCX, PPTX oder ein Bild.",
        "Ce format de fichier n'est pas pris en charge. Utilise TXT, PDF, DOCX, PPTX ou une image.",
        "Ese formato de archivo no es compatible. Usa TXT, PDF, DOCX, PPTX o una imagen.")
    private val TOO_LARGE = M(
        "Fișierul este prea mare. Încarcă-l pe bucăți mai mici.",
        "The file is too large. Import it in smaller parts.",
        "Die Datei ist zu groß. Importiere sie in kleineren Teilen.",
        "Le fichier est trop volumineux. Importe-le en parties plus petites.",
        "El archivo es demasiado grande. Impórtalo en partes más pequeñas.")
    private val FAILED = M(
        "Importul a eșuat. Nu s-a pierdut nimic — reîncearcă.",
        "Import failed. Nothing was lost — please try again.",
        "Import fehlgeschlagen. Es ging nichts verloren — bitte erneut versuchen.",
        "Échec de l'import. Rien n'a été perdu — réessaie.",
        "La importación falló. No se perdió nada: inténtalo de nuevo.")
    private val DECK_RESET = M(
        "Cursul s-a schimbat, așa că am repornit de la primul card.",
        "The course changed, so we restarted you from the first card.",
        "Der Kurs hat sich geändert, daher haben wir bei der ersten Karte neu begonnen.",
        "Le cours a changé, nous avons donc repris à la première carte.",
        "El curso cambió, así que reiniciamos desde la primera tarjeta.")

    /** Map a stable engine validation code to its message kind, or null if the code
     *  is not a file-validation failure. */
    private fun kindFor(code: String?): M? = when (code) {
        "invalid_file", "invalid_pdf", "invalid_docx", "invalid_pptx",
        "invalid_zip", "invalid_image" -> INVALID
        "corrupt_file" -> CORRUPT
        "empty_document", "empty_txt" -> EMPTY
        "no_readable_content" -> NO_CONTENT
        "unsupported_format" -> UNSUPPORTED
        "file_too_large" -> TOO_LARGE
        else -> null
    }

    /** Localized message for a validation code, or null for non-validation codes. */
    fun forCode(code: String?, lang: Lang): String? = kindFor(code)?.of(lang)

    fun invalidFile(lang: Lang) = INVALID.of(lang)
    fun corruptFile(lang: Lang) = CORRUPT.of(lang)
    fun emptyDoc(lang: Lang) = EMPTY.of(lang)
    fun noReadableContent(lang: Lang) = NO_CONTENT.of(lang)
    fun unsupportedFormat(lang: Lang) = UNSUPPORTED.of(lang)
    fun fileTooLarge(lang: Lang) = TOO_LARGE.of(lang)
    fun importFailed(lang: Lang) = FAILED.of(lang)
    fun deckReset(lang: Lang) = DECK_RESET.of(lang)

    /** All message kinds (for completeness tests). */
    internal val ALL: List<(Lang) -> String> = listOf(
        ::invalidFile, ::corruptFile, ::emptyDoc, ::noReadableContent,
        ::unsupportedFormat, ::fileTooLarge, ::importFailed, ::deckReset,
    )

    /** Every engine validation code that must resolve to a localized message. */
    internal val CODES = listOf(
        "invalid_file", "invalid_pdf", "invalid_docx", "invalid_pptx", "invalid_zip",
        "invalid_image", "corrupt_file", "empty_document", "empty_txt",
        "no_readable_content", "unsupported_format", "file_too_large",
    )
}
