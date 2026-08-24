package com.example.model

enum class DiagnosticStatus {
    HEALTHY,   // Green: Working normally
    WARNING,   // Yellow: Optional item unconfigured or minor non-blocking issue
    ERROR      // Red: Service disabled, permission missing, or API failed
}

data class DiagnosticItem(
    val id: String,
    val componentName: String,
    val status: DiagnosticStatus,
    val plainDescription: String,
    val technicalDetails: String? = null,
    val suggestedFix: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class SystemHealthState(
    val overallStatus: DiagnosticStatus = DiagnosticStatus.HEALTHY,
    val items: List<DiagnosticItem> = emptyList(),
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val healthyCount: Int = 0
) {
    val hasIssues: Boolean get() = errorCount > 0 || warningCount > 0
    val summaryText: String
        get() = when {
            errorCount > 0 -> "$errorCount issue${if (errorCount > 1) "s" else ""} detected"
            warningCount > 0 -> "$warningCount notice${if (warningCount > 1) "s" else ""}"
            else -> "All systems operational"
        }
}
