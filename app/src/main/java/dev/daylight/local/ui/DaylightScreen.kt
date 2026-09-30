package dev.daylight.local.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import dev.daylight.core.VoiceStage

class DaylightScreen(private val context: Context, private val actions: UiActions) {
    private val ui = DaylightUi(context)
    private var currentState: ScreenState? = null
    private val today = TodayPanel(ui, actions)
    private val talk = TalkPanel(ui, actions)
    private val evidence = EvidencePanel(ui, actions)
    private val privacy = PrivacyPanel(ui, actions)
    private val pages = mapOf(Tab.TODAY to today.view, Tab.TALK to talk.view, Tab.EVIDENCE to evidence.view, Tab.PRIVACY to privacy.view)
    private val header = ui.column()
    private val headerRow = ui.row()
    private val brand = ui.row()
    private val brandTitle = ui.text("Ami", 25f, medium = true)
    private val logo = ui.icon(DaylightGlyph.SUN, DaylightColors.mint)
    private val tagline = ui.text("Your day. Your pace. Your space.", 13f, DaylightColors.secondary)
    private val badge = ui.text("", 11f, DaylightColors.mint, medium = true)
    private val status = ui.text("", 13f, DaylightColors.secondary)
    private val statusDot = View(context)
    private val busy = ProgressBar(context, null, android.R.attr.progressBarStyleSmall)
    private val percentage = ui.text("", 12f, DaylightColors.lavender, medium = true)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val stop = ui.button("Stop", danger = true) {
        actions.onStopVoice()
    }
    private val navigation = ui.row()
    private val navigationItems = mutableMapOf<Tab, DaylightNavigationItem>()
    private var stackedHeader = false
    private val shell = DaylightShell(context) { compact, availableWidth -> configureCompactLayout(compact, availableWidth) }
    val root: View = shell

    fun frameSyntheticCapture(tab: Tab): Int {
        check(currentState?.demo == true) { "Only synthetic views may be framed for export." }
        return if (tab == Tab.TALK) talk.frameSyntheticConversation() else 0
    }

    init {
        shell.orientation = LinearLayout.VERTICAL
        shell.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(DaylightColors.background, Color.rgb(15, 21, 40))
        )
        shell.visibility = View.INVISIBLE
        shell.isFocusableInTouchMode = true
        shell.isSaveEnabled = false
        shell.setSaveFromParentEnabled(false)
        shell.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        header.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
        logo.background = ui.rounded(DaylightColors.indigo, 13)
        logo.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7))
        brand.addView(logo, LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)))
        brand.addView(brandTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = ui.dp(10) })
        badge.gravity = Gravity.CENTER
        badge.letterSpacing = 0.07f
        badge.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        headerRow.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(badge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ui.dp(12)
        })
        ui.add(header, headerRow)
        ui.add(header, tagline, top = 6)
        ui.add(shell, header)

        val statusSection = ui.column().apply { setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(8)) }
        val statusRow = ui.row()
        val indicator = FrameLayout(context)
        busy.indeterminateTintList = ColorStateList.valueOf(DaylightColors.mint)
        busy.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        indicator.addView(busy, FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
        statusDot.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        indicator.addView(statusDot, FrameLayout.LayoutParams(ui.dp(7), ui.dp(7), Gravity.CENTER))
        statusRow.addView(indicator, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
        status.apply {
            minimumHeight = ui.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(35, 213, 202, 255)), null, ui.rounded(Color.WHITE, 10)
            )
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            setOnClickListener {
                currentState?.let { state ->
                    AlertDialog.Builder(context)
                        .setTitle(if (state.recording) "Recording · current status" else "Current status")
                        .setMessage(state.status)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(percentage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ui.dp(8)
        })
        statusRow.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ui.dp(8)
        })
        ui.add(statusSection, statusRow)
        progress.max = 100
        progress.progressTintList = ColorStateList.valueOf(DaylightColors.lavender)
        progress.indeterminateTintList = ColorStateList.valueOf(DaylightColors.mint)
        progress.progressBackgroundTintList = ColorStateList.valueOf(DaylightColors.line)
        progress.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        statusSection.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(4)))
        ui.add(shell, statusSection)
        shell.addView(ui.divider())

        val content = FrameLayout(context)
        for ((_, page) in pages) {
            page.visibility = View.GONE
            content.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        shell.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        shell.addView(ui.divider())
        navigation.setBackgroundColor(DaylightColors.background)
        navigation.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        val destinations = listOf(
            Triple(Tab.TODAY, "Today", DaylightGlyph.TODAY),
            Triple(Tab.TALK, "Talk", DaylightGlyph.TALK),
            Triple(Tab.EVIDENCE, "Evidence", DaylightGlyph.EVIDENCE),
            Triple(Tab.PRIVACY, "Privacy", DaylightGlyph.PRIVACY)
        )
        for ((tab, label, glyph) in destinations) {
            val item = DaylightNavigationItem(ui, tab, label, glyph) {
                if (currentState?.tab != tab) actions.onTab(tab)
            }
            navigationItems[tab] = item
            navigation.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ui.dp(3)
                marginEnd = ui.dp(3)
            })
        }
        ui.add(shell, navigation)
        shell.requestFocus()
    }

    fun render(state: ScreenState) {
        val previousTab = currentState?.tab
        val previousDemo = currentState?.demo
        currentState = state
        if (previousDemo != state.demo) {
            updateBadgeText(state.demo)
            badge.setTextColor(if (state.demo) DaylightColors.mint else DaylightColors.lavender)
            badge.background = ui.rounded(if (state.demo) DaylightColors.mintSurface else DaylightColors.indigo, 12, DaylightColors.line)
            badge.contentDescription = if (state.demo) {
                "Demo mode. Synthetic records, not your health data."
            } else {
                "Personal mode. Read-only health access requires separate consent."
            }
        }
        status.showText(state.status)
        status.contentDescription = buildString {
            if (state.recording) append("Recording is active. ")
            else if (state.busy) append("An operation is in progress. ")
            append(state.status)
            append(". Tap for full status.")
        }
        busy.showWhen(state.busy && !state.recording)
        statusDot.showWhen(!state.busy || state.recording)
        statusDot.background = ui.rounded(if (state.recording) DaylightColors.rose else DaylightColors.mint, 4)
        stop.showWhen(state.voiceStage != VoiceStage.IDLE)
        stop.isEnabled = state.voiceStage != VoiceStage.IDLE
        val suppliedProgress = state.downloadProgress
        val hasPercentage = suppliedProgress != null && suppliedProgress in 0..100
        progress.showWhen(state.busy || suppliedProgress != null)
        progress.isIndeterminate = !hasPercentage
        if (hasPercentage) progress.progress = suppliedProgress ?: 0
        percentage.showWhen(hasPercentage && !state.recording)
        if (hasPercentage) percentage.showText("$suppliedProgress%")
        today.render(state)
        talk.render(state)
        evidence.render(state)
        privacy.render(state)
        if (previousTab == Tab.TALK && state.tab != Tab.TALK) {
            val focused = shell.findFocus()
            if (focused is EditText) {
                context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(focused.windowToken, 0)
                focused.clearFocus()
                shell.requestFocus()
            }
        }
        pages.forEach { (tab, page) -> page.showWhen(tab == state.tab) }
        navigationItems.forEach { (tab, item) -> item.select(tab == state.tab) }
        shell.visibility = View.VISIBLE
    }

    fun clearSessionDraft() {
        talk.clearDraft()
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(shell.windowToken, 0)
        shell.requestFocus()
    }

    private fun updateBadgeText(demo: Boolean) {
        badge.showText(when {
            demo && stackedHeader -> "DEMO · SYNTHETIC"
            demo -> "DEMO\nSYNTHETIC"
            stackedHeader -> "PERSONAL MODE"
            else -> "PERSONAL\nMODE"
        })
    }

    private fun configureCompactLayout(compact: Boolean, availableWidth: Int) {
        tagline.showWhen(!compact)
        status.maxLines = if (compact) 1 else 2
        talk.setCompact(compact)
        header.setPadding(ui.dp(20), ui.dp(if (compact) 6 else 12), ui.dp(20), ui.dp(if (compact) 4 else 12))
        navigation.setPadding(ui.dp(8), ui.dp(if (compact) 4 else 8), ui.dp(8), ui.dp(if (compact) 4 else 8))
        val requiredWidth = ui.dp(124) + brandTitle.paint.measureText("Ami") + badge.paint.measureText("SYNTHETIC")
        val stackBrand = availableWidth < requiredWidth
        stackedHeader = stackBrand
        updateBadgeText(currentState?.demo ?: true)
        headerRow.orientation = if (stackBrand) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        brand.layoutParams = LinearLayout.LayoutParams(
            if (stackBrand) ViewGroup.LayoutParams.MATCH_PARENT else 0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (stackBrand) 0f else 1f
        )
        badge.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = if (stackBrand) 0 else ui.dp(12)
            topMargin = if (stackBrand) ui.dp(8) else 0
            gravity = if (stackBrand) Gravity.START else Gravity.CENTER_VERTICAL
        }
    }
}

private class DaylightShell(context: Context, private val onCompact: (Boolean, Int) -> Unit) : LinearLayout(context) {
    private var compact: Boolean? = null
    private var contentWidth = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val nextCompact = availableHeight < 540 * resources.displayMetrics.density
        if (compact != nextCompact || contentWidth != availableWidth) {
            compact = nextCompact
            contentWidth = availableWidth
            onCompact(nextCompact, availableWidth)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

private class DaylightNavigationItem(
    private val ui: DaylightUi,
    private val tab: Tab,
    label: String,
    private val glyph: DaylightGlyph,
    action: () -> Unit
) : LinearLayout(ui.context) {
    private val icon = ui.icon(glyph)
    private val caption = ui.text(label, 12f, medium = true)
    private var active: Boolean? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = ui.dp(64)
        setPadding(ui.dp(2), ui.dp(9), ui.dp(2), ui.dp(9))
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "$label tab"
        caption.gravity = Gravity.CENTER
        caption.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, LayoutParams(ui.dp(23), ui.dp(23)))
        addView(caption, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(5) })
        setOnClickListener { action() }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
                info.isSelected = isSelected
                info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, tab.ordinal, 1, false, isSelected)
            }
        }
    }

    fun select(selected: Boolean) {
        if (active == selected) return
        active = selected
        isSelected = selected
        val foreground = if (selected) DaylightColors.lavender else DaylightColors.secondary
        icon.setImageDrawable(DaylightIcon(glyph, foreground))
        caption.setTextColor(foreground)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(45, 213, 202, 255)),
            ui.rounded(if (selected) DaylightColors.indigo else Color.TRANSPARENT, 16),
            ui.rounded(Color.WHITE, 16)
        )
    }
}
