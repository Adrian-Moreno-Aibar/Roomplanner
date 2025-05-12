package com.adrianmoreno.roomplanner.util

import java.text.SimpleDateFormat
import java.util.*

object DateParser {

    private val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun parse(dateStr: String): Date {
        return format.parse(dateStr) ?: Date()
    }

    fun format(date: Date): String {
        return format.format(date)
    }
}
