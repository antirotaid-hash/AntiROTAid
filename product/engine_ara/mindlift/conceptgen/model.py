"""Data model for the concept-based generator. Plain dataclasses, JSON-able."""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class KnowledgeUnit:
    unit_id: str
    title: str | None
    source_type: str            # slide | section | paragraph | heading | pasted ...
    kind: str                   # definition | process | comparison | list | formula | heading | paragraph
    raw_text: str
    clean_text: str
    length: int
    density: float              # 0..1 conceptual density
    importance: float           # 0..1
    decorative: bool            # title/agenda/credits/too-short -> no cards

    def to_json(self) -> dict:
        return {"unit_id": self.unit_id, "title": self.title,
                "source_type": self.source_type, "kind": self.kind,
                "length": self.length, "density": round(self.density, 3),
                "importance": round(self.importance, 3), "decorative": self.decorative}


@dataclass
class Relation:
    subject: str                # concept label
    rtype: str                  # is_a|defines|uses|produces|causes|transforms|depends_on|enables|part_of|has_role|contrasts_with|occurs_in...
    object: str                 # concept label / phrase
    evidence: str               # source sentence
    unit_id: str
    confidence: float = 0.7
    verb: str = ""              # original verb surface (diacritics + agreement kept)

    @property
    def rid(self) -> str:
        from .textproc import fold
        return f"{fold(self.subject)}|{self.rtype}|{fold(self.object)}"

    def to_json(self) -> dict:
        return {"subject": self.subject, "relation_type": self.rtype,
                "object": self.object, "evidence": self.evidence,
                "unit_id": self.unit_id, "confidence": round(self.confidence, 3)}


@dataclass
class Concept:
    label: str
    key: str                    # folded, stable id
    ctype: str = "term"         # term | process | entity | formula | law | quantity
    aliases: list[str] = field(default_factory=list)
    unit_ids: list[str] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    definition: str | None = None
    importance: float = 0.0
    confidence: float = 0.6
    from_title: bool = False
    relations: list[Relation] = field(default_factory=list)

    def to_json(self) -> dict:
        return {"id": self.key, "label": self.label, "type": self.ctype,
                "aliases": self.aliases, "unit_ids": self.unit_ids,
                "evidence": self.evidence[:4], "definition": self.definition,
                "importance": round(self.importance, 3),
                "confidence": round(self.confidence, 3),
                "relations": [r.to_json() for r in self.relations]}


@dataclass
class Card:
    card_id: str
    question: str
    answer: str
    card_type: str              # lesson_chunk|slide_image|definition|role|mechanism|...
    unit_id: str
    concept_ids: list[str] = field(default_factory=list)
    relation_ids: list[str] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    confidence: float = 0.6
    coverage_tags: list[str] = field(default_factory=list)
    # AntiROTAid Study/Overlay cards may be visual source-unit cards.
    # Keep media/source metadata in the serialized deck instead of losing it
    # when Card dataclasses are used by Android/Chaquopy.
    media: dict | None = None
    source: dict | None = None

    def to_json(self) -> dict:
        data = {"card_id": self.card_id, "question": self.question,
                "answer": self.answer, "card_type": self.card_type,
                "unit_id": self.unit_id, "concept_ids": self.concept_ids,
                "relation_ids": self.relation_ids, "evidence": self.evidence,
                "confidence": round(self.confidence, 3),
                "coverage_tags": self.coverage_tags}
        if self.media is not None:
            data["media"] = self.media
        if self.source is not None:
            data["source"] = self.source
        return data


@dataclass
class AdaptiveDeck:
    cards: list[Card] = field(default_factory=list)
    units: list[KnowledgeUnit] = field(default_factory=list)
    concepts: list[Concept] = field(default_factory=list)
    mode: str = "full_course"
    sections_total: int = 0
    sections_processed: int = 0
    important_concepts: int = 0
    notes: list[str] = field(default_factory=list)

    def to_json(self) -> dict:
        return {"mode": self.mode, "cards": [c.to_json() for c in self.cards],
                "card_count": len(self.cards),
                "sections_total": self.sections_total,
                "sections_processed": self.sections_processed,
                "important_concepts": self.important_concepts,
                "units": [u.to_json() for u in self.units],
                "notes": self.notes}
