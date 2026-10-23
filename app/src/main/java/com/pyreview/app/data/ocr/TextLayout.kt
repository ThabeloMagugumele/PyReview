package com.pyreview.app.data.ocr

import kotlin.math.abs
import kotlin.math.roundToInt

data class TextBox(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
) {
    val height: Int get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
}

object TextLayout {
    private const val LOW_CONFIDENCE = 0.7f
    private const val EDGE_MARGIN_PX = 3

    fun toCodeLines(boxes: List<TextBox>, imageHeight: Int): List<CodeLine> {
        val usable = boxes.filter { it.text.isNotBlank() && it.height > 0 }
        if (usable.isEmpty()) return emptyList()

        val medianHeight = usable.map { it.height }.sorted()[usable.size / 2].toFloat()
        val rows = groupIntoRows(usable.sortedBy { it.centerY }, medianHeight)

        val charWidth = rows.flatten()
            .filter { it.text.length >= 3 }
            .map { (it.right - it.left).toFloat() / it.text.length }
            .sorted()
            .let { if (it.isEmpty()) medianHeight * 0.55f else it[it.size / 2] }
            .coerceAtLeast(1f)

        val minLeft = rows.minOf { row -> row.first().left }
        val rawIndents = rows.map { row -> (row.first().left - minLeft) / charWidth }
        val step = indentStep(rawIndents)

        val result = mutableListOf<CodeLine>()
        var previousBottom: Int? = null
        rows.forEachIndexed { i, row ->
            val top = row.minOf { it.top }
            val bottom = row.maxOf { it.bottom }
            previousBottom?.let { if (top - it > medianHeight) result += CodeLine("") }

            val spaces = ((rawIndents[i] / step).roundToInt() * step).coerceAtLeast(0)
            val text = joinRow(row, charWidth).replace("\t", "    ")
            val cutOff = top <= EDGE_MARGIN_PX || bottom >= imageHeight - EDGE_MARGIN_PX
            val uncertain = cutOff || row.any { it.confidence > 0f && it.confidence < LOW_CONFIDENCE }
            result += CodeLine(" ".repeat(spaces) + text, uncertain)
            previousBottom = bottom
        }
        return result
    }

    private fun groupIntoRows(sorted: List<TextBox>, medianHeight: Float): List<List<TextBox>> {
        val rows = mutableListOf<MutableList<TextBox>>()
        for (box in sorted) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.map { it.centerY }.average() - box.centerY) < medianHeight * 0.5f) {
                row += box
            } else {
                rows += mutableListOf(box)
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    private fun joinRow(row: List<TextBox>, charWidth: Float): String = buildString {
        row.forEachIndexed { i, box ->
            if (i > 0) {
                val gap = ((box.left - row[i - 1].right) / charWidth).roundToInt().coerceIn(1, 8)
                append(" ".repeat(gap))
            }
            append(box.text.trim())
        }
    }

    private fun indentStep(rawIndents: List<Float>): Int {
        val smallest = rawIndents.filter { it >= 1f }.minOrNull() ?: return 4
        return if (smallest < 3f) 2 else 4
    }
}
