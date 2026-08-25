package com.mealflow.support.dto;

/** Structured citation pointing back to a knowledge source document and chunk. */
public record Citation(String source, String chunkIndex, Double score, String content) {
}
