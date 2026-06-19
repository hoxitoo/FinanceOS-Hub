package com.financeos.hub.core.analytics

data class WaterfallBar(
    val label   : String,
    val delta   : Long,
    val isTotal : Boolean = false,
)
