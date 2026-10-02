package com.sentinel.core.logs

// Room maps GROUP BY query results into this class by matching column aliases
// (name, count) to field names exactly.
data class StatRow(val name: String, val count: Int)
