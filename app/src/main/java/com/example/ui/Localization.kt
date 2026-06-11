package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

object Loc {
    // English-Persian translation helper
    fun t(fa: String, en: String, isEnglish: Boolean): String {
        return if (isEnglish) en else fa
    }
}
