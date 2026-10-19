package com.example.foz.ui.home.components

import com.example.foz.model.AppInfo

sealed class DrawerItem {
    data class Section(val letter: Char) : DrawerItem()
    data class App(val app: AppInfo) : DrawerItem()
}

internal fun sectionLetterFor(app: AppInfo): Char? {
    val firstChar = app.name.trim().firstOrNull() ?: return null
    val first = firstChar.toString().removeAccents().firstOrNull()?.uppercaseChar() ?: firstChar.uppercaseChar()
    return when {
        first in 'A'..'Z' -> first
        first.isDigit() -> '#'
        else -> null
    }
}

internal fun buildDrawerItems(apps: List<AppInfo>): List<DrawerItem> {
    if (apps.isEmpty()) return emptyList()
    val items = mutableListOf<DrawerItem>()
    var currentSection: Char? = null
    for (app in apps) {
        val letter = sectionLetterFor(app)
        if (letter != null && letter != currentSection) {
            items.add(DrawerItem.Section(letter))
            currentSection = letter
        }
        items.add(DrawerItem.App(app))
    }
    return items
}

internal fun buildLazySectionIndexes(items: List<DrawerItem>): Map<Char, Int> {
    val indexes = linkedMapOf<Char, Int>()
    items.forEachIndexed { index, item ->
        if (item is DrawerItem.Section && item.letter !in indexes) {
            indexes[item.letter] = index
        }
    }
    return indexes
}

internal fun flatAppIndex(items: List<DrawerItem>, lazyIndex: Int): Int {
    if (items.isEmpty()) return lazyIndex
    var flat = 0
    for (i in 0 until lazyIndex.coerceAtMost(items.size)) {
        if (items[i] is DrawerItem.App) flat++
    }
    return flat
}

private fun String.removeAccents(): String {
    return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}
