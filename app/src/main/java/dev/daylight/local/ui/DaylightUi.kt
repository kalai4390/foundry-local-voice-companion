package dev.daylight.local.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object DaylightColors {
    val background = Color.rgb(10, 15, 29)
    val surface = Color.rgb(20, 28, 49)
    val elevated = Color.rgb(29, 38, 63)
    val line = Color.rgb(57, 69, 96)
    val text = Color.rgb(244, 245, 253)
    val secondary = Color.rgb(187, 198, 221)
    val muted = Color.rgb(158, 173, 200)
    val lavender = Color.rgb(213, 202, 255)
    val mint = Color.rgb(172, 236, 212)
    val blue = Color.rgb(168, 206, 255)
    val amber = Color.rgb(255, 218, 161)
    val rose = Color.rgb(255, 185, 197)
    val indigo = Color.rgb(42, 39, 75)
    val mintSurface = Color.rgb(24, 48, 48)
    val dangerSurface = Color.rgb(62, 33, 47)
}

internal class DaylightUi(val context: Context) {
    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun text(
        value: String,
        size: Float = 15f,
        color: Int = DaylightColors.text,
        medium: Boolean = false,
        heading: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        setLineSpacing(dp(3).toFloat(), 1.06f)
        isAccessibilityHeading = heading
        isSaveEnabled = false
    }

    fun eyebrow(value: String, color: Int = DaylightColors.mint): TextView =
        text(value, 11f, color, medium = true).apply { letterSpacing = 0.1f }

    fun column(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun rounded(fill: Int, radius: Int = 20, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    fun card(fill: Int = DaylightColors.surface): LinearLayout = column().apply {
        background = rounded(fill, stroke = DaylightColors.line)
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    fun add(parent: LinearLayout, child: View, top: Int = 0, bottom: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        })
    }

    fun icon(glyph: DaylightGlyph, color: Int = DaylightColors.lavender): ImageView =
        ImageView(context).apply {
            setImageDrawable(DaylightIcon(glyph, color))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusable = false
        }

    fun button(
        label: String,
        primary: Boolean = false,
        danger: Boolean = false,
        action: () -> Unit
    ): Button = Button(context).apply {
        text = label
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        setSingleLine(false)
        includeFontPadding = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = dp(48)
        minimumWidth = dp(48)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        val foreground = when {
            primary -> DaylightColors.background
            danger -> DaylightColors.rose
            else -> DaylightColors.lavender
        }
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(foreground, DaylightColors.muted)
        ))
        val fill = when {
            primary -> DaylightColors.lavender
            danger -> DaylightColors.dangerSurface
            else -> DaylightColors.elevated
        }
        val backgrounds = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_enabled), rounded(fill, 14, if (primary) null else DaylightColors.line))
            addState(intArrayOf(), rounded(DaylightColors.surface, 14, DaylightColors.line))
        }
        background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(45, 213, 202, 255)),
            backgrounds,
            rounded(Color.WHITE, 14)
        )
        isSaveEnabled = false
        setOnClickListener { if (isEnabled) action() }
    }

    fun flow(): DaylightFlow = DaylightFlow(context, dp(8))

    fun divider(): View = View(context).apply {
        setBackgroundColor(DaylightColors.line)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

internal fun TextView.showText(value: String) {
    if (text.toString() != value) text = value
}

internal fun View.showWhen(visible: Boolean) {
    val nextVisibility = if (visible) View.VISIBLE else View.GONE
    if (visibility != nextVisibility) visibility = nextVisibility
}

internal class DaylightColumn(context: Context) : LinearLayout(context) {
    private val maximumWidth = (720 * resources.displayMetrics.density).roundToInt()

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val boundedSpec = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(maximumWidth, MeasureSpec.AT_MOST)
        } else {
            MeasureSpec.makeMeasureSpec(min(availableWidth, maximumWidth), MeasureSpec.getMode(widthMeasureSpec))
        }
        super.onMeasure(boundedSpec, heightMeasureSpec)
    }
}

internal class DaylightScroll(context: Context, ui: DaylightUi) : ScrollView(context) {
    val content = DaylightColumn(context)

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        clipToPadding = false
        isSaveEnabled = false
        content.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(28))
        addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
    }

    fun isNearEnd(distance: Int): Boolean =
        content.height - scrollY - height + paddingBottom + paddingTop <= distance

    fun moveToEnd() {
        post { scrollTo(0, max(0, content.height - height + paddingTop + paddingBottom)) }
    }
}

internal class DaylightFlow(context: Context, private val spacing: Int) : ViewGroup(context) {
    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            max(0, MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
        }
        var rowWidth = 0
        var rowHeight = 0
        var contentWidth = 0
        var contentHeight = 0
        var childState = 0
        for (childIndex in 0 until childCount) {
            val child = getChildAt(childIndex)
            if (child.visibility == GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val gap = if (rowWidth == 0) 0 else spacing
            if (rowWidth > 0 && rowWidth + gap + child.measuredWidth > availableWidth) {
                contentWidth = max(contentWidth, rowWidth)
                contentHeight += rowHeight + spacing
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += (if (rowWidth == 0) 0 else spacing) + child.measuredWidth
            rowHeight = max(rowHeight, child.measuredHeight)
            childState = combineMeasuredStates(childState, child.measuredState)
        }
        contentWidth = max(contentWidth, rowWidth)
        contentHeight += rowHeight
        setMeasuredDimension(
            resolveSizeAndState(contentWidth + paddingLeft + paddingRight, widthMeasureSpec, childState),
            resolveSizeAndState(contentHeight + paddingTop + paddingBottom, heightMeasureSpec, childState shl MEASURED_HEIGHT_STATE_SHIFT)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val availableWidth = right - left - paddingLeft - paddingRight
        val rightToLeft = layoutDirection == LAYOUT_DIRECTION_RTL
        var rowWidth = 0
        var rowTop = paddingTop
        var rowHeight = 0
        for (childIndex in 0 until childCount) {
            val child = getChildAt(childIndex)
            if (child.visibility == GONE) continue
            val gap = if (rowWidth == 0) 0 else spacing
            if (rowWidth > 0 && rowWidth + gap + child.measuredWidth > availableWidth) {
                rowTop += rowHeight + spacing
                rowWidth = 0
                rowHeight = 0
            }
            if (rowWidth > 0) rowWidth += spacing
            val childLeft = if (rightToLeft) {
                right - left - paddingRight - rowWidth - child.measuredWidth
            } else {
                paddingLeft + rowWidth
            }
            child.layout(childLeft, rowTop, childLeft + child.measuredWidth, rowTop + child.measuredHeight)
            rowWidth += child.measuredWidth
            rowHeight = max(rowHeight, child.measuredHeight)
        }
    }
}
