package com.lorongtea.mobile.ui.common

import java.util.Locale

internal fun money(cents: Long): String = String.format(Locale.ROOT, "RM %d.%02d", cents / 100, cents % 100)
