package com.example.apptempleate

import java.text.BreakIterator
import java.util.Locale

object SentenceSegmenter {

    /**
     * Splits a raw user message into distinct sentences in original order.
     * Uses Java BreakIterator with fallback to punctuation/newline boundaries.
     */
    fun segment(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<String>()

        try {
            val iterator = BreakIterator.getSentenceInstance(Locale.US)
            iterator.setText(trimmed)
            var start = iterator.first()
            var end = iterator.next()

            while (end != BreakIterator.DONE) {
                val sentence = trimmed.substring(start, end).trim()
                if (sentence.isNotEmpty()) {
                    // In case sentence contains newline-separated statements
                    val subLines = sentence.split(Regex("[\r\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
                    results.addAll(subLines)
                }
                start = end
                end = iterator.next()
            }
        } catch (_: Exception) {
            // Fallback: Split on punctuation boundaries
            val parts = trimmed.split(Regex("(?<=[.!?])\\s+|[\r\n]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            results.addAll(parts)
        }

        return if (results.isNotEmpty()) results else listOf(trimmed)
    }
}
