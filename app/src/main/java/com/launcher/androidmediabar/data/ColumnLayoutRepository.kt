package com.launcher.androidmediabar.data

import android.content.Context
import androidx.core.content.edit
import com.launcher.androidmediabar.model.CategoryType
import com.launcher.androidmediabar.model.LayoutSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Persists hidden columns and per-app column assignments. */
class ColumnLayoutRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LayoutSettings> = _settings.asStateFlow()

    fun toggleColumn(type: CategoryType) {
        if (!type.isUserColumn) return
        _settings.update { s -> s.copy(hidden = if (type in s.hidden) s.hidden - type else s.hidden + type) }
        save()
    }

    /** [type] == null restores the automatic column. */
    fun assignApp(packageName: String, type: CategoryType?) {
        _settings.update { s ->
            s.copy(
                assignments = if (type == null || !type.isUserColumn) {
                    s.assignments - packageName
                } else {
                    s.assignments + (packageName to type)
                },
            )
        }
        save()
    }

    private fun save() {
        val s = _settings.value
        prefs.edit {
            putStringSet(KEY_HIDDEN, s.hidden.mapTo(HashSet()) { it.name })
            putStringSet(KEY_ASSIGNMENTS, s.assignments.mapTo(HashSet()) { (pkg, type) -> "$pkg$SEPARATOR${type.name}" })
        }
    }

    private fun read(): LayoutSettings {
        val hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
            .mapNotNullTo(HashSet()) { name -> CategoryType.entries.firstOrNull { it.name == name && it.isUserColumn } }
        val assignments = prefs.getStringSet(KEY_ASSIGNMENTS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val pkg = entry.substringBeforeLast(SEPARATOR, missingDelimiterValue = "")
                val type = CategoryType.entries.firstOrNull { it.name == entry.substringAfterLast(SEPARATOR) }
                if (pkg.isNotEmpty() && type != null && type.isUserColumn) pkg to type else null
            }
            .toMap()
        return LayoutSettings(hidden, assignments)
    }

    private companion object {
        const val PREFS_NAME = "xmb_layout"
        const val KEY_HIDDEN = "hidden_columns"
        const val KEY_ASSIGNMENTS = "app_columns"
        const val SEPARATOR = '|'
    }
}
