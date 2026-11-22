package app.motonav.ui.theme

import androidx.compose.ui.graphics.Color

// Γεννιέται από replica/design/tokens.json (gen). Άλλαξε τα tokens, όχι αυτό το αρχείο.

object Tokens {
    // Μπορντό για το κουμπί «Τέλος πλοήγησης» (ίδιο σε όλα τα θέματα).
    val stop = Color(0xFF8B1E2D)
    val onStop = Color(0xFFFFFFFF)
    val stopBorder = Color(0xFFC2465A)

    val dark = BasePalette(
        bg = Color(0xFF0E0F13),
        surface = Color(0xFF17191F),
        surface2 = Color(0xFF20232B),
        surface3 = Color(0xFF2A2E38),
        border = Color(0xFF2E323C),
        borderInput = Color(0xFF6E7684),
        text = Color(0xFFF2F3F5),
        textMuted = Color(0xFFA4AAB6),
        danger = Color(0xFFFF6B6B),
        onDanger = Color(0xFF16090A),
        success = Color(0xFF4ADE80),
        warning = Color(0xFFFBBF24),
        info = Color(0xFF7DD3FC),
        isDark = true,
    )
    val amoled = BasePalette(
        bg = Color(0xFF000000),
        surface = Color(0xFF0F1014),
        surface2 = Color(0xFF191B21),
        surface3 = Color(0xFF23262E),
        border = Color(0xFF272A33),
        borderInput = Color(0xFF6E7684),
        text = Color(0xFFF2F3F5),
        textMuted = Color(0xFFA4AAB6),
        danger = Color(0xFFFF6B6B),
        onDanger = Color(0xFF16090A),
        success = Color(0xFF4ADE80),
        warning = Color(0xFFFBBF24),
        info = Color(0xFF7DD3FC),
        isDark = true,
    )
    val light = BasePalette(
        bg = Color(0xFFFFFFFF),
        surface = Color(0xFFF3F4F6),
        surface2 = Color(0xFFE9EBEF),
        surface3 = Color(0xFFDDE0E6),
        border = Color(0xFFD3D7DE),
        borderInput = Color(0xFF79808C),
        text = Color(0xFF111318),
        textMuted = Color(0xFF545A66),
        danger = Color(0xFFC62828),
        onDanger = Color(0xFFFFFFFF),
        success = Color(0xFF1B7F45),
        warning = Color(0xFF9A6200),
        info = Color(0xFF0369A1),
        isDark = false,
    )
    val accents = listOf(
        AccentPreset("Μωβ", dark = Color(0xFFA78BFA), light = Color(0xFF6D28D9)),
        AccentPreset("Κόκκινο", dark = Color(0xFFFF7A7A), light = Color(0xFFC62828)),
        AccentPreset("Πορτοκαλί", dark = Color(0xFFFB923C), light = Color(0xFFB45309)),
        AccentPreset("Κίτρινο", dark = Color(0xFFFACC15), light = Color(0xFF8A6A00)),
        AccentPreset("Πράσινο", dark = Color(0xFF4ADE80), light = Color(0xFF15803D)),
        AccentPreset("Κυανό", dark = Color(0xFF22D3EE), light = Color(0xFF0E7490)),
        AccentPreset("Μπλε", dark = Color(0xFF60A5FA), light = Color(0xFF1D4ED8)),
        AccentPreset("Ροζ", dark = Color(0xFFF472B6), light = Color(0xFFBE185D)),
        AccentPreset("Λευκό/Γκρι", dark = Color(0xFFE5E7EB), light = Color(0xFF374151)),
    )
}
