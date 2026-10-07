package com.rentnest.app.domain.model

enum class AppMode { CUSTOMER, ADMIN }
enum class ThemePref { SYSTEM, LIGHT, DARK }

val AppMode.audience: Audience get() = if (this == AppMode.CUSTOMER) Audience.CUSTOMER else Audience.ADMIN

data class SessionState(
    val onboarded: Boolean = false,
    val loggedIn: Boolean = false,
    val modeChosen: Boolean = false,
    val mode: AppMode = AppMode.CUSTOMER,
    val theme: ThemePref = ThemePref.SYSTEM,
    val recentItemIds: List<Long> = emptyList(),
)
