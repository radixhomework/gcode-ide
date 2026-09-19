"""Tokenizer unit tests (task 3.1)."""

import pytest

from app.parsing.parser import TokenizeError, strip_comments, tokenize


def test_semicolon_comment_stripped():
    assert tokenize("G1 X10 ; first pass") == tokenize("G1 X10")


def test_parenthesized_comments_stripped():
    assert tokenize("(safe) G1 (inner) X5") == tokenize("G1 X5")


def test_strip_comments_keeps_code():
    assert strip_comments("G1 X10 ; cut").strip() == "G1 X10"


def test_mixed_case_normalized():
    words = tokenize("n10 g1 x10 Y20 f600")
    assert [w.letter for w in words] == ["N", "G", "X", "Y", "F"]
    assert [w.value for w in words] == [10, 1, 10, 20, 600]


def test_signed_and_decimal_numbers():
    words = tokenize("G1 X-5.5 Y+2 Z.5")
    assert [w.value for w in words] == [1, -5.5, 2, 0.5]


def test_no_space_between_letter_and_number():
    words = tokenize("G1X10Y20")
    assert [(w.letter, w.value) for w in words] == [("G", 1), ("X", 10), ("Y", 20)]


def test_word_without_number_raises():
    with pytest.raises(TokenizeError):
        tokenize("G1 X")


def test_stray_character_raises():
    with pytest.raises(TokenizeError):
        tokenize("%")


def test_blank_and_comment_only_lines_tokenize_empty():
    assert tokenize("") == []
    assert tokenize("   ") == []
    assert tokenize("; just a comment") == []
    assert tokenize("(block)") == []
