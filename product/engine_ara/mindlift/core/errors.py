"""Minimal shared errors used by AntiROTAid offline ingestion/card generation."""
from __future__ import annotations

class ValidationError(ValueError):
    """User-facing validation error with optional public message and context.

    ``code`` is a STABLE machine code (e.g. "invalid_pdf", "empty_txt",
    "file_too_large") that the Android layer maps to a localized message in each
    of the five supported languages. ``public_message`` is a Romanian fallback
    used only when the UI has no localized string for the code.
    """
    def __init__(self, message: str, *, public_message: str | None = None,
                 field_name: str | None = None, context: dict | None = None,
                 code: str | None = None):
        super().__init__(message)
        self.public_message = public_message or message
        self.field_name = field_name
        self.context = context or {}
        self.code = code or "invalid_file"

    def to_json(self) -> dict:
        return {
            "error": str(self),
            "code": self.code,
            "public_message": self.public_message,
            "field_name": self.field_name,
            "context": self.context,
        }
