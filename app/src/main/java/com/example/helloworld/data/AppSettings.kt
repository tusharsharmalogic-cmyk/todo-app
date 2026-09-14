package com.example.helloworld.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val themeMode: Int = 0,        // 0=System, 1=Light, 2=Dark
    val accentHex: String = "#6750A4",
    val dynamicColor: Boolean = true
)