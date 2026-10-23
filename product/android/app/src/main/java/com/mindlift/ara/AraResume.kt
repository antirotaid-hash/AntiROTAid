package com.mindlift.ara

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Robust resume model for AntiROTAid (Android Update 1, Phase 3 & 5).
 *
 * The old app stored resume position as a bare card INDEX. That is unsafe once a
 * deck is regenerated, reordered, inserted-into or trimmed: the same index can
 * point at a completely different card. This model persists the index PLUS the
 * [cardId] it pointed at PLUS a deterministic [fingerprint] of the deck it was
 * captured against, so resume can:
 *
 *  - resume exactly when the deck is unchanged,
 *  - remap by CardId when the deck changed but the card still exists,
 *  - reset to 0 when the card is gone (never open an unrelated card at the same
 *    index),
 *  - recover safely when the old index is now out of range.
 *
 * Everything here is PURE (no Android types) so it is directly unit-testable and
 * deterministic on any JVM. [AraStore] owns persistence + legacy migration.
 */
data class AraResume(
    val index: Int,
    val cardId: String,
    val fingerprint: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("idx", index)
        .put("cardId", cardId)
        .put("fp", fingerprint)

    fun toStorage(): String = toJson().toString()

    companion object {
        val EMPTY = AraResume(0, "", "")

        /** Parse persisted JSON. Malformed / missing state returns null (the caller
         *  then falls back to legacy migration or a safe start at 0) — never throws. */
        fun fromStorage(raw: String?): AraResume? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val j = JSONObject(raw)
                AraResume(
                    index = j.optInt("idx", 0).coerceAtLeast(0),
                    cardId = j.optString("cardId", ""),
                    fingerprint = j.optString("fp", ""),
                )
            }.getOrNull()
        }

        /**
         * Deterministic deck fingerprint from each card's stable identity, content
         * and ORDER. Two decks with the same cards in the same order (same text)
         * produce the same fingerprint; any insert/delete/reorder/edit changes it.
         */
        fun fingerprint(cardIds: List<String>, contents: List<String> = emptyList()): String {
            if (cardIds.isEmpty()) return ""
            val md = MessageDigest.getInstance("SHA-256")
            for (i in cardIds.indices) {
                md.update(cardIds[i].toByteArray(Charsets.UTF_8)); md.update(0)
                val c = contents.getOrNull(i) ?: ""
                md.update(c.toByteArray(Charsets.UTF_8)); md.update(30)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Convenience: fingerprint a live deck (cardId + title + body). */
        fun fingerprintCards(cards: List<AraCard>): String =
            fingerprint(cards.map { it.cardId }, cards.map { it.title + "" + it.body })

        /**
         * Resolve the safe start index for the CURRENT deck (Phase 3 rules 1-5).
         *
         * @param saved previously persisted resume (or null)
         * @param cardIds current deck order
         * @param fp current deck fingerprint
         */
        fun resolveIndex(saved: AraResume?, cardIds: List<String>, fp: String): Int {
            if (cardIds.isEmpty()) return 0
            if (saved == null) return 0
            // (1) Unchanged deck: resume at the exact card. Prefer the CardId (it is
            //     authoritative); fall back to the clamped index if the id is blank.
            if (fp.isNotEmpty() && saved.fingerprint == fp) {
                val byId = cardIds.indexOf(saved.cardId)
                if (byId >= 0) return byId
                return saved.index.coerceIn(0, cardIds.size - 1)
            }
            // (2 & 5) Changed / reordered deck but the same CardId still exists:
            //         remap by CardId; never trust the old numeric index alone.
            if (saved.cardId.isNotEmpty()) {
                val byId = cardIds.indexOf(saved.cardId)
                if (byId >= 0) return byId
            }
            // (3 & 4) CardId gone, or old index out of range: reset to 0 rather than
            //         open an unrelated card that merely occupies the same index.
            return 0
        }

        /**
         * Migrate a legacy integer resume (Phase 3 rule 6) into the new model,
         * binding it to the current deck's CardId + fingerprint. Out-of-range old
         * indices are clamped; an empty deck yields EMPTY.
         */
        fun migrateLegacy(oldIndex: Int, cardIds: List<String>, fp: String): AraResume {
            if (cardIds.isEmpty()) return AraResume(0, "", fp)
            val idx = oldIndex.coerceIn(0, cardIds.size - 1)
            return AraResume(idx, cardIds[idx], fp)
        }

        /** Capture the user's current position within the current deck. */
        fun capture(index: Int, cardIds: List<String>, fp: String): AraResume {
            if (cardIds.isEmpty()) return AraResume(0, "", fp)
            val idx = index.coerceIn(0, cardIds.size - 1)
            return AraResume(idx, cardIds[idx], fp)
        }

        /**
         * Remap resume after an individual card is removed (Phase 5): keep the same
         * CardId if it survived; otherwise pick the nearest valid neighbor — the
         * card that now occupies the removed slot, clamped to the last card when the
         * final card was removed. Empty new deck yields EMPTY.
         *
         * @param oldIds deck order BEFORE removal
         * @param newIds deck order AFTER removal
         */
        fun remapAfterRemoval(
            saved: AraResume?,
            removedCardId: String,
            oldIds: List<String>,
            newIds: List<String>,
            newFp: String,
        ): AraResume {
            if (newIds.isEmpty()) return AraResume(0, "", newFp)
            if (saved == null) return AraResume(0, newIds[0], newFp)
            // The resume card is a different, surviving card -> follow it by id.
            if (saved.cardId.isNotEmpty() && saved.cardId != removedCardId) {
                val i = newIds.indexOf(saved.cardId)
                if (i >= 0) return AraResume(i, saved.cardId, newFp)
            }
            // We were sitting on the removed card (or its id vanished): take the slot
            // the removed card occupied, clamped to the new last index.
            val removedPos = oldIds.indexOf(removedCardId).let { if (it >= 0) it else saved.index }
            val idx = removedPos.coerceIn(0, newIds.size - 1)
            return AraResume(idx, newIds[idx], newFp)
        }
    }
}
