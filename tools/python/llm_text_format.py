"""
Plain-text cleanup for LLM output — Python port of Gyango's Kotlin
AssistantOutput / AssistantParsingPipeline + MessageTextFormatter (UI blocks).

No markdown parsing or rendering: strips artifacts and markdown-like delimiters
so downstream UI (or logs) can show readable plain text.

Kept in sync manually with:
  gyango-core/src/main/java/ai/gyango/assistant/AssistantLlmSanitizer.kt
  gyango-core/src/main/java/ai/gyango/assistant/AssistantParsingPipeline.kt
  gyango-core/src/main/java/ai/gyango/assistant/GyangoOutputEnvelope.kt
  features/chatbot/src/main/java/ai/gyango/chatbot/ui/MessageTextFormatter.kt
"""

from __future__ import annotations

import re
import sys
from typing import Final

# --- AssistantLlmSanitizer parity ---

REASONING_OPEN: Final = "\\thinking>"
REASONING_END: Final = "\\/thinking>"

_COMPLETE_REASONING_BLOCK = re.compile(
    re.escape(REASONING_OPEN) + r"[\s\S]*?" + re.escape(REASONING_END),
    re.DOTALL,
)

_FORBIDDEN_TOKENS: Final[tuple[str, ...]] = (
    "!end_of_the_turn",
    "!start_of_the_turn",
    "<|endoftext|>",
    "<|redacted_im_end|>",
    "<|im_start|>",
    "<start_of_turn>",
    "<end_of_turn>",
    "<start_of_turn>user",
    "<start_of_turn>model",
    "<eos>",
    "<bos>",
    "<|end_of_turn|>",
    "<|start_of_turn|>",
    "<|eot_id|>",
    "<|redacted_end_header_id|>",
    "<|redacted_start_header_id|>",
    "[/INST]",
    "[INST]",
    "<<SYS>>",
    "<</SYS>>",
    "<s>",
    "</s>",
    "model\n",
    "user\n",
    "end_of_turn",
    "start_of_turn",
)

_RE_ANGLE_TAG = re.compile(
    r"<[^>]*(?:turn|end|start|eos|bos|eot)[^>]*>?", re.IGNORECASE
)
_RE_NAMED_TAGS = re.compile(
    r"<(?:end_of_turn|start_of_turn|eot_id|end_header_id)[^>]*>?"
)
_RE_PIPE_TAGS = re.compile(r"<\|[^|]*\|>")
_RE_LEAD_ANGLE = re.compile(r"^<+")
_RE_TRAIL_ANGLE = re.compile(r">+$")
_RE_MULTI_NEWLINE = re.compile(r"\n{3,}")


def _strip_reasoning_artifacts(inp: str) -> str:
    s = _COMPLETE_REASONING_BLOCK.sub("", inp)
    open_idx = s.find(REASONING_OPEN)
    if open_idx >= 0:
        end_idx = s.find(REASONING_END, open_idx + len(REASONING_OPEN))
        if end_idx < 0:
            s = s[:open_idx] + s[open_idx + len(REASONING_OPEN) :]
        else:
            s = s[:open_idx] + s[end_idx + len(REASONING_END) :]
    s = s.replace(REASONING_END, "")
    s = s.replace(REASONING_OPEN, "")
    return s


def sanitize(inp: str) -> str:
    """Mirror AssistantLlmSanitizer.sanitize — drop reasoning blocks and chat control tokens."""
    result = _strip_reasoning_artifacts(inp)
    for tag in _FORBIDDEN_TOKENS:
        result = re.sub(re.escape(tag), "", result, flags=re.IGNORECASE)
    result = _RE_ANGLE_TAG.sub("", result)
    result = _RE_NAMED_TAGS.sub("", result)
    result = _RE_PIPE_TAGS.sub("", result)
    result = _RE_LEAD_ANGLE.sub("", result)
    result = _RE_TRAIL_ANGLE.sub("", result)
    result = _RE_MULTI_NEWLINE.sub("\n\n", result)
    return result.strip()


# --- MessageTextFormatter.readableAssistantText parity ---

_RE_PARA_SPLIT = re.compile(r"\n\n+")
_RE_WS_COLLAPSE = re.compile(r"\s+")
_RE_LONG_NEWLINES = re.compile(r"\n{4,}")
_RE_DIGIT_COLON = re.compile(r"(\d+):(\S)")
_RE_FENCED = re.compile(r"```(?:[\w-]*\n)?(.*?)```", re.DOTALL)
_RE_INLINE_CODE = re.compile(r"`([^`]+)`")
_RE_BOLD_STAR = re.compile(r"\*\*([^*]+)\*\*")
_RE_UNDER = re.compile(r"__(?!_)([^_\n]+)(?<!_)__")
_RE_LINK = re.compile(r"\[([^\]]+)]\([^)]+\)")
_RE_ATX_HEADING = re.compile(r"^#{1,6}\s*", re.MULTILINE)
_RE_BULLET_LINE = re.compile(r"^(\s*)([-*•]|\d+\.)\s+")
_RE_ITALIC_STAR = re.compile(r"(?<!\*)\*([^*\n]+)\*(?!\*)")


def _normalize_markdown_unicode(s: str) -> str:
    return (
        s.replace("\uff0a", "*")
        .replace("\u2217", "*")
        .replace("\ufeff", "")
    )


def _dedupe_repeated_paragraphs(text: str) -> str:
    paras = _RE_PARA_SPLIT.split(text)
    if len(paras) < 2:
        return text

    def norm(p: str) -> str:
        return _RE_WS_COLLAPSE.sub(" ", p.strip().lower())

    out: list[str] = []
    prev_key: str | None = None
    for p in paras:
        key = norm(p)
        if key and key == prev_key:
            continue
        out.append(p)
        prev_key = key if key else None
    return "\n\n".join(out)


def _dedupe_consecutive_duplicate_lines(text: str) -> str:
    lines = text.splitlines()
    out: list[str] = []
    prev_non_blank: str | None = None
    for line in lines:
        t = line.strip()
        if t and t == prev_non_blank:
            continue
        out.append(line)
        prev_non_blank = t if t else prev_non_blank
    return "\n".join(out)


def _normalize_whitespace_for_plain_text(inp: str) -> str:
    s = inp.strip().replace("\r\n", "\n")
    s = _RE_LONG_NEWLINES.sub("\n\n\n", s)
    s = s.replace("\u00a0", " ")
    s = _RE_DIGIT_COLON.sub(r"\1: \2", s)
    return s


def _strip_markdown_like_delimiters(inp: str) -> str:
    s = inp

    def _fence_sub(m: re.Match[str]) -> str:
        return "\n" + m.group(1).strip() + "\n"

    s = _RE_FENCED.sub(_fence_sub, s)
    s = _RE_INLINE_CODE.sub(r"\1", s)
    for _ in range(24):
        nxt = _RE_BOLD_STAR.sub(r"\1", s)
        if nxt == s:
            break
        s = nxt
    s = _RE_UNDER.sub(r"\1", s)
    s = _RE_LINK.sub(r"\1", s)
    s = _RE_ATX_HEADING.sub("", s)
    s = s.replace("**", "")

    lines_out: list[str] = []
    for line in s.splitlines():
        m = _RE_BULLET_LINE.match(line)
        if m:
            rest = line[m.end() :]
            if "*" in rest:
                rest = _RE_BOLD_STAR.sub(r"\1", rest)
                rest = re.sub(r"\*([^*]+)\*", r"\1", rest)
                lines_out.append(line[: m.end()] + rest)
            else:
                lines_out.append(line)
        else:
            l2 = _RE_BOLD_STAR.sub(r"\1", line)
            l2 = _RE_ITALIC_STAR.sub(r"\1", l2)
            lines_out.append(l2)
    return "\n".join(lines_out)


def readable_assistant_text(raw: str) -> str:
    """
    Full on-device bubble pipeline: sanitize, dedupe noise, strip markdown-like tokens.
    Output is plain text only (no markdown semantics).
    """
    s = sanitize(raw)
    s = _normalize_markdown_unicode(s)
    s = _dedupe_repeated_paragraphs(s)
    s = _dedupe_consecutive_duplicate_lines(s)
    s = _normalize_whitespace_for_plain_text(s)
    s = _strip_markdown_like_delimiters(s)
    return s.strip()


# --- AssistantLlmStreamSanitizer parity (subset of tokens, length-sorted) ---

_STREAM_FORBIDDEN: Final[tuple[str, ...]] = tuple(
    sorted(
        (
            "<|redacted_start_header_id|>",
            "<|redacted_end_header_id|>",
            "<|end_of_turn|>",
            "<|start_of_turn|>",
            "<start_of_turn>",
            "<end_of_turn>",
            "!end_of_the_turn",
            "!start_of_the_turn",
            "<|endoftext|>",
            "<|redacted_im_end|>",
            "<|im_start|>",
            "<|eot_id|>",
            "<eos>",
            "<bos>",
            "end_of_turn",
            "start_of_turn",
        ),
        key=len,
        reverse=True,
    )
)


class AssistantLlmStreamSanitizer:
    """Holds a buffer and strips forbidden prefixes chunk-by-chunk (Kotlin parity)."""

    __slots__ = ("_buffer",)

    def __init__(self) -> None:
        self._buffer = ""

    def process(self, chunk: str) -> str:
        self._buffer += chunk
        output_parts: list[str] = []
        remaining = self._buffer

        while remaining:
            full = next(
                (t for t in _STREAM_FORBIDDEN if remaining.lower().startswith(t.lower())),
                None,
            )
            if full is not None:
                remaining = remaining[len(full) :]
                self._buffer = remaining
                continue
            if any(
                t.lower().startswith(remaining.lower()) for t in _STREAM_FORBIDDEN
            ):
                break
            output_parts.append(remaining[0])
            remaining = remaining[1:]
            self._buffer = remaining

        return "".join(output_parts)

    def flush(self) -> str:
        rest = self._buffer
        self._buffer = ""
        return rest


def _main(argv: list[str]) -> int:
    """CLI: stdin → readable_assistant_text, or --sanitize-only for LLM sanitizer only."""
    sanitize_only = "--sanitize-only" in argv
    if sys.stdin.isatty() and not hasattr(sys.stdin, "read"):
        print(
            "Usage: pipe LLM text on stdin, or: python llm_text_format.py [--sanitize-only] < file.txt",
            file=sys.stderr,
        )
        return 1
    raw = sys.stdin.read()
    out = sanitize(raw) if sanitize_only else readable_assistant_text(raw)
    sys.stdout.write(out)
    if out and not out.endswith("\n"):
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(_main(sys.argv))
