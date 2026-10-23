package com.mindlift.ara

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android Update 1 — reliability unit tests (Phase 10).
 *
 * Real, executed JVM unit tests over the PURE reliability logic: resume model,
 * deck fingerprint, course-deletion key manifest, exam-reference cleanup, and
 * five-language completeness. No device/Context required, so they run with plain
 * `testAraDebugUnitTest`.
 *
 * Numbering mirrors the task's 31-item plan (items covered here noted per test).
 */
class AraReliabilityTest {

    private fun card(id: String, title: String = id, body: String = "body-$id") =
        AraCard(cardId = id, cardType = "lesson_chunk", sourceLabel = "S", title = title, body = body)

    private fun deck(vararg ids: String) = ids.map { card(it) }

    // #1 — Old numeric resume state migrates successfully.
    @Test fun legacyIntegerResumeMigrates() {
        val cards = deck("a", "b", "c", "d")
        val ids = cards.map { it.cardId }
        val fp = AraResume.fingerprintCards(cards)
        val migrated = AraResume.migrateLegacy(2, ids, fp)
        assertEquals(2, migrated.index)
        assertEquals("c", migrated.cardId)
        assertEquals(fp, migrated.fingerprint)
        // And it resolves back to the same card.
        assertEquals(2, AraResume.resolveIndex(migrated, ids, fp))
    }

    // #2 — Unchanged deck preserves the exact current card.
    @Test fun unchangedDeckResumesExactly() {
        val cards = deck("a", "b", "c", "d", "e")
        val ids = cards.map { it.cardId }
        val fp = AraResume.fingerprintCards(cards)
        val saved = AraResume.capture(3, ids, fp)
        assertEquals(3, AraResume.resolveIndex(saved, ids, fp))
    }

    // #3 — Changed deck with matching CardId remaps correctly.
    @Test fun changedDeckWithMatchingIdRemaps() {
        val oldCards = deck("a", "b", "c")
        val saved = AraResume.capture(2, oldCards.map { it.cardId }, AraResume.fingerprintCards(oldCards))
        // A card was inserted at the front; "c" now sits at index 3.
        val newCards = deck("x", "a", "b", "c")
        val newIds = newCards.map { it.cardId }
        val newFp = AraResume.fingerprintCards(newCards)
        assertNotEquals(saved.fingerprint, newFp)
        assertEquals(3, AraResume.resolveIndex(saved, newIds, newFp))
    }

    // #4 — Changed deck without matching CardId resets to index 0.
    @Test fun changedDeckWithoutMatchingIdResetsToZero() {
        val oldCards = deck("a", "b", "c")
        val saved = AraResume.capture(2, oldCards.map { it.cardId }, AraResume.fingerprintCards(oldCards))
        val newCards = deck("p", "q", "r", "s")   // "c" is gone
        val newIds = newCards.map { it.cardId }
        val newFp = AraResume.fingerprintCards(newCards)
        assertEquals(0, AraResume.resolveIndex(saved, newIds, newFp))
    }

    // #5 — New deck shorter than old index recovers safely (no crash, valid index).
    @Test fun shorterDeckRecoversSafely() {
        val oldCards = deck("a", "b", "c", "d", "e", "f")
        val saved = AraResume.capture(5, oldCards.map { it.cardId }, AraResume.fingerprintCards(oldCards))
        val newCards = deck("a", "b")             // index 5 no longer exists; "f" gone
        val newIds = newCards.map { it.cardId }
        val newFp = AraResume.fingerprintCards(newCards)
        val idx = AraResume.resolveIndex(saved, newIds, newFp)
        assertTrue(idx in newIds.indices)
        assertEquals(0, idx)                       // id gone -> reset, never an unrelated card
    }

    // #6 — Reordered deck resumes by CardId (not by stale index).
    @Test fun reorderedDeckResumesByCardId() {
        val oldCards = deck("a", "b", "c", "d")
        val saved = AraResume.capture(1, oldCards.map { it.cardId }, AraResume.fingerprintCards(oldCards)) // "b"
        val newCards = deck("d", "c", "b", "a")   // reversed
        val newIds = newCards.map { it.cardId }
        val newFp = AraResume.fingerprintCards(newCards)
        assertEquals(2, AraResume.resolveIndex(saved, newIds, newFp))   // "b" now at index 2
    }

    // #7 — Final-card Done resets the course to index 0 (pure semantic of resetResume).
    @Test fun finalCardDoneResetsToZero() {
        val cards = deck("a", "b", "c")
        val ids = cards.map { it.cardId }
        val fp = AraResume.fingerprintCards(cards)
        // resetResume() persists AraResume.EMPTY; resolving EMPTY against any deck = 0.
        assertEquals(0, AraResume.resolveIndex(AraResume.EMPTY, ids, fp))
    }

    // #8 — App restart preserves valid resume state (re-resolving persisted JSON).
    @Test fun restartPreservesResumeViaRoundTrip() {
        val cards = deck("a", "b", "c", "d")
        val ids = cards.map { it.cardId }
        val fp = AraResume.fingerprintCards(cards)
        val saved = AraResume.capture(2, ids, fp)
        val roundTripped = AraResume.fromStorage(saved.toStorage())!!
        assertEquals(saved, roundTripped)
        assertEquals(2, AraResume.resolveIndex(roundTripped, ids, fp))
    }

    @Test fun malformedResumeStateDoesNotCrash() {
        assertNull(AraResume.fromStorage(null))
        assertNull(AraResume.fromStorage(""))
        assertNull(AraResume.fromStorage("{ not json"))
        // A missing-fields json parses to a safe default, never throws.
        val partial = AraResume.fromStorage("{\"idx\":4}")!!
        assertEquals(4, partial.index)
        assertEquals("", partial.cardId)
    }

    @Test fun fingerprintIsDeterministicAndOrderSensitive() {
        val a = AraResume.fingerprintCards(deck("a", "b", "c"))
        val b = AraResume.fingerprintCards(deck("a", "b", "c"))
        val reordered = AraResume.fingerprintCards(deck("c", "b", "a"))
        val edited = AraResume.fingerprintCards(listOf(card("a"), card("b"), card("c", body = "CHANGED")))
        assertEquals(a, b)
        assertNotEquals(a, reordered)
        assertNotEquals(a, edited)
    }

    // #15 — Removing the current card selects a valid neighboring card.
    @Test fun removingCurrentCardSelectsNeighbor() {
        val old = deck("a", "b", "c", "d")
        val saved = AraResume.capture(1, old.map { it.cardId }, AraResume.fingerprintCards(old)) // on "b"
        val newDeck = old.filter { it.cardId != "b" }        // a, c, d
        val r = AraResume.remapAfterRemoval(saved, "b", old.map { it.cardId },
            newDeck.map { it.cardId }, AraResume.fingerprintCards(newDeck))
        assertEquals("c", r.cardId)                          // the card that took the slot
        assertEquals(1, r.index)
    }

    // #16 — Removing the final card clamps correctly.
    @Test fun removingFinalCardClamps() {
        val old = deck("a", "b", "c")
        val saved = AraResume.capture(2, old.map { it.cardId }, AraResume.fingerprintCards(old)) // on "c"
        val newDeck = old.filter { it.cardId != "c" }        // a, b
        val r = AraResume.remapAfterRemoval(saved, "c", old.map { it.cardId },
            newDeck.map { it.cardId }, AraResume.fingerprintCards(newDeck))
        assertEquals(1, r.index)                             // clamped to new last
        assertEquals("b", r.cardId)
    }

    // #17 — Removing all cards enters a safe empty state.
    @Test fun removingAllCardsSafeEmptyState() {
        val old = deck("a")
        val saved = AraResume.capture(0, old.map { it.cardId }, AraResume.fingerprintCards(old))
        val r = AraResume.remapAfterRemoval(saved, "a", old.map { it.cardId }, emptyList(), "")
        assertEquals(AraResume(0, "", ""), r)
        assertEquals(0, AraResume.resolveIndex(r, emptyList(), ""))
    }

    // #9 / #13 — Course deletion key manifest covers resume, hidden, per-card state.
    @Test fun deletionKeyManifestIsComplete() {
        val keys = AraCourseCleanup.keysFor("custom_curs.pdf")
        // resume (new + legacy)
        assertTrue(keys.contains("resume_custom_curs.pdf"))
        assertTrue(keys.contains("resumeidx_custom_curs.pdf"))
        // hidden-card state
        assertTrue(keys.contains("hiddencards_custom_curs.pdf"))
        // per-card seen/known/repeat/scroll state
        assertTrue(keys.contains("cardprog_custom_curs.pdf"))
        // cached deck ids + overlay unit cursor
        assertTrue(keys.contains("cardids_custom_curs.pdf"))
        assertTrue(keys.contains("unit_idx_custom_curs.pdf"))
        // progress, deadline, passes, pace, settings, unit count
        listOf("prog_", "deadline_", "passes_", "pace_", "matset_", "units_").forEach {
            assertTrue("missing $it", keys.contains(it + "custom_curs.pdf"))
        }
    }

    // #10 — Course deletion removes generated cache (prefix match logic).
    @Test fun feedCachePurgeMatchesOnlyThisCourse() {
        val stored = "curs.pdf"
        assertTrue(AraCourseCleanup.matchesFeedCache("curs.pdf_1234_567_true_compact_auto.json", stored))
        assertFalse(AraCourseCleanup.matchesFeedCache("altcurs.pdf_9_9_true_c_a.json", stored))
    }

    // #12 — Course deletion removes Exam.MaterialIds references.
    @Test fun examMaterialReferencesAreStripped() {
        val exams = """
            [{"id":"e1","name":"Ex","date_ms":0,"material_ids":["custom_a","custom_b"]},
             {"id":"e2","name":"Ex2","date_ms":0,"material_ids":["custom_b","custom_c"]}]
        """.trimIndent()
        val cleaned = AraCourseCleanup.stripMaterialFromExams(exams, "custom_b")
        assertFalse(cleaned.contains("custom_b"))
        assertTrue(cleaned.contains("custom_a"))
        assertTrue(cleaned.contains("custom_c"))
        // Malformed input never throws.
        assertEquals("[]", AraCourseCleanup.stripMaterialFromExams("{bad", "x"))
    }

    // #30 — All five languages contain every new key (non-blank), and codes map.
    @Test fun allFiveLanguagesHaveNewMessages() {
        val langs = listOf(Lang.RO, Lang.EN, Lang.DE, Lang.FR, Lang.ES)
        // Every new message kind must be present and non-blank in all five languages.
        for (lang in langs) {
            AraImportMessages.ALL.forEach { msg ->
                assertTrue("blank message for $lang", msg(lang).isNotBlank())
            }
        }
        // Every engine validation code resolves to a localized message in each language.
        for (lang in langs) {
            AraImportMessages.CODES.forEach { code ->
                assertTrue("unmapped code $code for $lang",
                    AraImportMessages.forCode(code, lang)?.isNotBlank() == true)
            }
        }
        // The five languages must be genuinely different (not accidental copies of EN).
        AraImportMessages.ALL.forEach { msg ->
            val distinct = langs.map { msg(it) }.toSet()
            assertTrue("messages not distinct across languages", distinct.size >= 4)
        }
    }
}
