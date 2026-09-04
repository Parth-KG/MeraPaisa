package com.kg.merapaisa

/** Which of the two lists is on screen. */
enum class Tab { Active, Settled }

/**
 * Everything the main screen remembers between frames. It lives in the ViewModel rather than
 * in `remember`, so a rotation no longer throws away the typed amount, the selected person,
 * the current tab or a half-finished split.
 */
data class MainUiState(
    val tab: Tab = Tab.Active,
    val selectedId: Long? = null,
    val input: String = "",
    val note: String = "",
    val showNote: Boolean = false,
    val showAddDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val editingPersonId: Long? = null,
    val historyPersonId: Long? = null,
    val pendingDeleteId: Long? = null,
    val pendingReminderId: Long? = null,
    val split: SplitFlowState? = null
)

/** The multi-step split flow, absent when it is not running. */
data class SplitFlowState(
    val step: Int = 0,
    val amount: String = "",
    val note: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val includeMe: Boolean = false
)
