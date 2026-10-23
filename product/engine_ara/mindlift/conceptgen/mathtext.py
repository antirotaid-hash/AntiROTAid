"""Offline, deterministic math-text normaliser for extracted PDF/TeX text.

TeX/Beamer PDFs lose the 2-D layout of mathematics when their text is
extracted: an ``\\overrightarrow`` leaves a stray ``"− − →"`` stroke line
*before* the vector, sub/superscripts collapse onto the baseline (``"p 1"``
instead of ``p₁``, ``"R 2"`` instead of ``ℝ²``) and a determinant prints as
bare digit rows. The raw result reads as garbled soup (``"AP=r PB"``).

This module repairs the common, convention-driven cases with pure stdlib
regex — no per-document word/formula list, no network, no LaTeX engine:

  * vector arrows   →  vec(AP)
  * number sets     →  ℝ², ℝⁿ, ℚ³, ℤ …
  * complexity      →  O(n³)
  * indices         →  p₁, q₂, Pₙ, α₁
  * determinants    →  det[ 1 1 1 ; p₁ q₁ r₁ ; p₂ q₂ r₂ ]

Every rule is keyed on math *convention* (sets carry a dimension exponent,
named points carry an index, an over-arrow binds the next point pair), never
on a particular document, author or formula. Anything it cannot confidently
reshape is left untouched; :func:`math_density` lets the caller fall back to
the rendered slide image for formula-dominated pages.
"""
from __future__ import annotations

import re

# --- script translation tables ------------------------------------------------
# Only characters with a real Unicode sub/superscript are mapped; unknown chars
# pass through unchanged so we never emit tofu.
_SUP = {"0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴", "5": "⁵", "6": "⁶",
        "7": "⁷", "8": "⁸", "9": "⁹", "n": "ⁿ", "m": "ᵐ", "d": "ᵈ", "i": "ⁱ",
        "k": "ᵏ", "+": "⁺", "-": "⁻"}
_SUB = {"0": "₀", "1": "₁", "2": "₂", "3": "₃", "4": "₄", "5": "₅", "6": "₆",
        "7": "₇", "8": "₈", "9": "₉", "n": "ₙ", "m": "ₘ", "i": "ᵢ", "j": "ⱼ",
        "k": "ₖ", "+": "₊", "-": "₋"}
_SET = {"R": "ℝ", "Q": "ℚ", "Z": "ℤ"}  # only sets common as geometric spaces


def _sup(s: str) -> str:
    return "".join(_SUP.get(c, c) for c in s)


def _sub(s: str) -> str:
    return "".join(_SUB.get(c, c) for c in s)


def _plain_script(s: str, table: dict, sign: str) -> str:
    """Render an already-plain (non-LaTeX) sub/superscript: Unicode glyphs when
    every character maps, else a readable ``^(..)``/``_(..)`` fallback."""
    s = (s or "").strip()
    if not s:
        return ""
    if all(c in table for c in s):
        return "".join(table[c] for c in s)
    if len(s) == 1:
        return sign + s
    return f"{sign}({s})"


def to_superscript(s: str) -> str:
    """Public: render plain text as a superscript (for OMML / structured math)."""
    return _plain_script(s, _SUP, "^")


def to_subscript(s: str) -> str:
    """Public: render plain text as a subscript (for OMML / structured math)."""
    return _plain_script(s, _SUB, "_")


# --- 1. vector over-arrows ----------------------------------------------------
# A line with NO alphanumerics that contains a rightwards arrow is the leftover
# stroke of \overrightarrow{..} / \vec{..}; TeX prints it just BEFORE the vector
# name on its own line. We drop the stroke and tag the following point pair.
_ALNUM = re.compile(r"[0-9A-Za-zĂÂÎȘȚăâîșț]")
_ARROW = re.compile(r"[→←↔⇒⇐]")  # → ← ↔ ⇒ ⇐
_VEC_HEAD = re.compile(r"^([A-Z]{1,3})\b")


def _attach_vectors(lines: list[str]) -> list[str]:
    out: list[str] = []
    pending = False
    for ln in lines:
        s = ln.strip()
        if s and not _ALNUM.search(s) and _ARROW.search(s):
            pending = True            # stroke line: consume, mark next token
            continue
        if pending:
            ln = _VEC_HEAD.sub(lambda m: f"vec({m.group(1)})", ln, count=1)
            pending = False
        out.append(ln)
    return out


# --- 2. determinant / matrix blocks ------------------------------------------
# After an "… =" line that names a determinant (∆ / det / |…|), TeX dumps each
# matrix row on its own short line. Collapse them to det[ r ; r ; r ].
_TRIGGER = re.compile(r"=\s*$")
_DET_HINT = re.compile(r"[∆Δ]|\bdet\b|\|")
_ROW = re.compile(r"^[\s]*[0-9A-Za-zα-ωΑ-Ω₀-₉][0-9A-Za-zα-ωΑ-Ω₀-₉\s.+\-]{0,30}$")
_ROW_BAD = re.compile(r"[←→⇔•=]|[a-zăâîșț]{3,}")  # prose / operators / bullets


def _is_matrix_row(s: str) -> bool:
    s = s.strip()
    if not s or s == "." or not _ROW.match(s):
        return False
    if _ROW_BAD.search(s):
        return False
    return 2 <= len(s.split()) <= 6


def _collapse_determinants(lines: list[str]) -> list[str]:
    out: list[str] = []
    i = 0
    n = len(lines)
    while i < n:
        ln = lines[i]
        if _TRIGGER.search(ln.strip()) and _DET_HINT.search(ln):
            rows: list[str] = []
            j = i + 1
            # TeX leaves blank lines (former form-feed strokes) around the matrix;
            # skip them while a row is still reachable, but stop at real prose.
            while j < n:
                if not lines[j].strip():
                    j += 1
                    continue
                if _is_matrix_row(lines[j]):
                    rows.append(" ".join(lines[j].split()))
                    j += 1
                    continue
                break
            if len(rows) >= 2:
                out.append(ln.rstrip() + " det[ " + " ; ".join(rows) + " ]")
                # a lone "." closing the determinant is now redundant
                if j < n and lines[j].strip() == ".":
                    j += 1
                i = j
                continue
        out.append(ln)
        i += 1
    return out


# --- 3. scalar sub/superscript & set rules (whole-text regex) -----------------
# Number set carrying a dimension: R 2 / Rn / R d -> ℝ² / ℝⁿ / ℝᵈ. Requires the
# trailing dimension token so a bare point named R/Q/Z is left alone. Only a
# horizontal gap is allowed: a newline means the digit belongs to the NEXT line
# (e.g. a "3." algorithm step), never to the letter ending the previous one.
_SET_DIM = re.compile(r"(?<![A-Za-z])([RQZ])[ \t]*([0-9nmd])(?![0-9A-Za-z])")
# Complexity O(n 3) / O(n2) -> O(n³).
_BIGO = re.compile(r"\bO\([ \t]*([a-z])[ \t]*([0-9])[ \t]*\)")
# Indexed symbol: a standalone single letter (latin/greek) + one digit -> sub.
# The negative look-behind keeps it from firing inside ordinary words; the
# horizontal-only gap keeps it from eating a next-line enumerator (L\n5. -> L₅).
_INDEX = re.compile(r"(?<![0-9A-Za-zα-ωΑ-Ω])([A-Za-zα-ωΑ-Ω])[ \t]?([0-9])(?![0-9A-Za-z])")


def _scalar_rules(text: str) -> str:
    text = _SET_DIM.sub(lambda m: _SET[m.group(1)] + _sup(m.group(2)), text)
    text = _BIGO.sub(lambda m: f"O({m.group(1)}{_sup(m.group(2))})", text)
    text = _INDEX.sub(lambda m: m.group(1) + _sub(m.group(2)), text)
    return text


# --- 4. literal LaTeX source commands -----------------------------------------
# A LaTeX-generated PDF usually RENDERS its math, so extraction yields the
# garbled-unicode form handled above. But two cases leak raw TeX *source* into
# the card text and must be decoded: (a) a slide/PDF where a math run failed to
# render and the control words survive verbatim, and (b) an imported ``.tex``
# file read as plain text. This pass turns the convention-driven subset of TeX
# math into readable Unicode — offline, no LaTeX engine, no per-document list.
_GREEK = {
    "alpha": "α", "beta": "β", "gamma": "γ", "delta": "δ", "epsilon": "ε",
    "varepsilon": "ε", "zeta": "ζ", "eta": "η", "theta": "θ", "vartheta": "ϑ",
    "iota": "ι", "kappa": "κ", "lambda": "λ", "mu": "μ", "nu": "ν", "xi": "ξ",
    "pi": "π", "varpi": "ϖ", "rho": "ρ", "varrho": "ϱ", "sigma": "σ",
    "varsigma": "ς", "tau": "τ", "upsilon": "υ", "phi": "φ", "varphi": "φ",
    "chi": "χ", "psi": "ψ", "omega": "ω",
    "Gamma": "Γ", "Delta": "Δ", "Theta": "Θ", "Lambda": "Λ", "Xi": "Ξ",
    "Pi": "Π", "Sigma": "Σ", "Upsilon": "Υ", "Phi": "Φ", "Psi": "Ψ", "Omega": "Ω",
}
_SYM = {
    "times": "×", "cdot": "·", "div": "÷", "pm": "±", "mp": "∓", "ast": "∗",
    "leq": "≤", "le": "≤", "geq": "≥", "ge": "≥", "neq": "≠", "ne": "≠",
    "ll": "≪", "gg": "≫", "approx": "≈", "equiv": "≡", "cong": "≅", "sim": "∼",
    "simeq": "≃", "propto": "∝", "infty": "∞", "partial": "∂", "nabla": "∇",
    "forall": "∀", "exists": "∃", "nexists": "∄", "in": "∈", "notin": "∉",
    "ni": "∋", "subset": "⊂", "subseteq": "⊆", "supset": "⊃", "supseteq": "⊇",
    "cup": "∪", "cap": "∩", "emptyset": "∅", "varnothing": "∅", "setminus": "∖",
    "to": "→", "rightarrow": "→", "leftarrow": "←", "longrightarrow": "→",
    "Rightarrow": "⇒", "Leftarrow": "⇐", "leftrightarrow": "↔",
    "Leftrightarrow": "⇔", "iff": "⇔", "implies": "⇒", "mapsto": "↦",
    "land": "∧", "wedge": "∧", "lor": "∨", "vee": "∨", "neg": "¬", "lnot": "¬",
    "oplus": "⊕", "otimes": "⊗", "circ": "∘", "bullet": "•", "star": "⋆",
    "dots": "…", "ldots": "…", "cdots": "⋯", "vdots": "⋮", "angle": "∠",
    "perp": "⊥", "parallel": "∥", "prime": "′", "ell": "ℓ", "hbar": "ℏ",
    "Re": "ℜ", "Im": "ℑ", "aleph": "ℵ", "deg": "°", "degree": "°",
    "prod": "∏", "sum": "∑", "int": "∫", "iint": "∬", "oint": "∮",
    "sqrt": "√", "surd": "√", "lfloor": "⌊", "rfloor": "⌋", "lceil": "⌈",
    "rceil": "⌉", "langle": "⟨", "rangle": "⟩", "backslash": "\\",
    "leftrightarrows": "⇄", "cdotp": "·", "ldotp": ".",
}
# Blackboard-bold sets carry their own glyphs.
_BB = {"R": "ℝ", "Q": "ℚ", "Z": "ℤ", "N": "ℕ", "C": "ℂ", "H": "ℍ",
       "P": "ℙ", "F": "𝔽", "E": "𝔼", "K": "𝕂", "D": "𝔻"}
# Word operators that should survive as plain text (not dropped as unknown).
_OPNAMES = {"lim", "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln",
            "exp", "max", "min", "sup", "inf", "det", "dim", "ker", "deg",
            "gcd", "lcm", "arg", "mod", "sgn", "tr", "rank"}
# Font/no-op wrappers whose braces we unwrap, keeping the inner content.
_FONT_CMDS = {"mathrm", "mathbf", "mathsf", "mathit", "mathtt", "mathcal",
              "mathscr", "mathfrak", "boldsymbol", "bm", "operatorname",
              "text", "textrm", "textbf", "textit", "textsf", "texttt",
              "mbox", "hbox", "rm", "bf", "it", "displaystyle", "textstyle",
              "scriptstyle", "limits", "nolimits", "!", ",", ";", ":", " ",
              "left", "right", "bigl", "bigr", "Bigl", "Bigr", "big", "Big",
              "biggl", "biggr", "Biggl", "Biggr", "quad", "qquad"}

_LATEX_SIGNAL = re.compile(r"\\[A-Za-z]|\\[\[\](){}]|\$|[\^_]\{|[\^_]\\?[A-Za-z0-9]")


def _take_group(s: str, i: int) -> tuple[str, int]:
    """Read the LaTeX argument at ``s[i:]``: a ``{...}`` group (brace-matched) or
    a single token (one char, or one ``\\command``). Leading spaces are skipped."""
    n = len(s)
    while i < n and s[i] == " ":
        i += 1
    if i >= n:
        return "", i
    if s[i] == "{":
        depth, j = 0, i
        while j < n:
            if s[j] == "{":
                depth += 1
            elif s[j] == "}":
                depth -= 1
                if depth == 0:
                    return s[i + 1:j], j + 1
            j += 1
        return s[i + 1:], n
    if s[i] == "\\":
        m = re.match(r"\\([A-Za-z]+|.)", s[i:])
        if m:
            return s[i:i + m.end()], i + m.end()
    return s[i], i + 1


def _script(arg: str, table: dict) -> str:
    """Render a sub/superscript argument: Unicode glyphs when every character
    maps, otherwise a parenthesised fallback so nothing is silently dropped."""
    inner = _convert_latex(arg).strip()
    if inner and all(c in table for c in inner):
        return "".join(table[c] for c in inner)
    if len(inner) == 1:
        return ("^" if table is _SUP else "_") + inner
    return ("^(" if table is _SUP else "_(") + inner + ")"


def _convert_latex(s: str) -> str:
    out: list[str] = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == "\\":
            m = re.match(r"\\([A-Za-z]+)", s[i:])
            if not m:                       # escaped symbol: \{ \} \% \_ \$ \&
                nxt = s[i + 1] if i + 1 < n else ""
                out.append(" " if nxt in ",;!: " else nxt)  # \, \; \! thin spaces
                i += 2
                continue
            name = m.group(1)
            i += m.end()
            if i < n and s[i] == " ":       # a control word eats one trailing space
                i += 1
            if name in ("frac", "tfrac", "dfrac", "cfrac"):
                a, i = _take_group(s, i)
                b, i = _take_group(s, i)
                out.append("(" + _convert_latex(a) + ")/(" + _convert_latex(b) + ")")
            elif name in ("sqrt", "surd"):
                if i < n and s[i] == "[":
                    j = s.find("]", i)
                    deg = s[i + 1:j] if j != -1 else ""
                    i = j + 1 if j != -1 else i
                    a, i = _take_group(s, i)
                    out.append(_sup(deg.strip()) + "√(" + _convert_latex(a) + ")")
                else:
                    a, i = _take_group(s, i)
                    out.append("√(" + _convert_latex(a) + ")")
            elif name == "mathbb":
                a, i = _take_group(s, i)
                out.append("".join(_BB.get(ch, ch) for ch in a.strip()))
            elif name in ("vec", "overrightarrow", "overline", "bar", "hat",
                          "widehat", "tilde", "widetilde", "dot", "ddot",
                          "acute", "grave", "check", "breve", "underline"):
                a, i = _take_group(s, i)
                inner = _convert_latex(a)
                out.append(f"vec({inner})" if name in ("vec", "overrightarrow") else inner)
            elif name in _FONT_CMDS:
                # spacing macros carry no argument; font/size wrappers unwrap one.
                if name in ("!", ",", ";", ":", " ", "quad", "qquad",
                            "displaystyle", "textstyle", "scriptstyle",
                            "limits", "nolimits", "left", "right", "bigl",
                            "bigr", "Bigl", "Bigr", "big", "Big", "biggl",
                            "biggr", "Biggl", "Biggr"):
                    out.append(" ")
                else:
                    a, i = _take_group(s, i)
                    out.append(_convert_latex(a))
            elif name in ("begin", "end"):
                _env, i = _take_group(s, i)     # drop environment delimiter
            elif name in _GREEK:
                out.append(_GREEK[name])
            elif name in _SYM:
                out.append(_SYM[name])
            elif name in _OPNAMES:
                out.append(name)
            else:
                out.append("")                  # unknown control word: drop quietly
        elif c in "^_":
            arg, j = _take_group(s, i + 1)
            out.append(_script(arg, _SUP if c == "^" else _SUB))
            i = j
        elif c == "$":
            out.append(" ")
            i += 1
        elif c == "&":
            out.append("  ")
            i += 1
        elif c == "~":
            out.append(" ")
            i += 1
        elif c == "{" or c == "}":
            i += 1                              # stray grouping brace
        else:
            out.append(c)
            i += 1
    return "".join(out)


def normalize_latex_commands(text: str) -> str:
    """Decode literal LaTeX math source (``\\frac``, ``\\sqrt``, ``^{}``, greek,
    operators) into readable Unicode. Safe on prose without TeX signals."""
    if not text or not _LATEX_SIGNAL.search(text):
        return text
    text = re.sub(r"\\\[|\\\]|\\\(|\\\)", " ", text)   # display/inline math fences
    text = text.replace(r"\\", "\n")                   # TeX line break
    text = re.sub(r"%.*", "", text)                    # strip TeX line comments
    out = []
    for ln in text.splitlines():
        out.append(_convert_latex(ln) if _LATEX_SIGNAL.search(ln) else ln)
    text = "\n".join(out)
    # Restore the spaces TeX control words swallowed, around relational/binary
    # operators only (never around '/' or letters), then collapse runs.
    text = re.sub(r"\s*([≤≥≠≈≡∈∉⊂⊆⊃⊇∪∩→←↔⇒⇐⇔×÷±∓·∧∨])\s*", r" \1 ", text)
    text = re.sub(r"[ \t]{2,}", " ", text)
    return "\n".join(ln.strip() for ln in text.splitlines())


def normalize_math_text(text: str) -> str:
    """Repair extracted TeX/PDF mathematics. Safe on plain prose (no-op)."""
    if not text:
        return text
    text = normalize_latex_commands(text)
    lines = text.splitlines()
    lines = _attach_vectors(lines)
    lines = _collapse_determinants(lines)
    return _scalar_rules("\n".join(lines))


# --- density gate -------------------------------------------------------------
_MATH_CHARS = re.compile(r"[∈∉⊂⊆⊇⊃∪∩≤≥≠≈≡⇔⇒∧∨±×÷∆Δ∑∏∫√∂∞→←↔₀-₉⁰-⁹ℝℚℤ]")


def math_density(text: str) -> float:
    """Fraction of non-space characters that are mathematical symbols.

    A formula-dominated slide (high density) reads better as the rendered slide
    image than as reconstructed text; callers can route on this.
    """
    body = re.sub(r"\s+", "", text or "")
    if not body:
        return 0.0
    return sum(bool(_MATH_CHARS.match(c)) for c in body) / len(body)
