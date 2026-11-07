package com.ztmanager.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.KeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max

/**
 * Plain text editor view with a line number gutter and undo / redo history.
 */
@SuppressLint("ViewConstructor")
class CodeEditText(context: Context) : AppCompatEditText(context) {

    private data class Edit(val start: Int, val before: String, val after: String, val time: Long)

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private var applying = false
    private var pendingBefore = ""
    private var removedNewlines = 0
    private var logicalLines = 1

    // cache for mapping layout lines to logical lines when wrapping
    private var cacheOffset = 0
    private var cacheCount = 0

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterPaint = Paint()
    private val linePaint = Paint()
    private var gutterWidth = 0
    private val gutterPad = dp(6f)

    var onTextEdited: (() -> Unit)? = null
    var autoIndent: Boolean = true
    private var pendingIndent: Pair<Int, String>? = null
    var onCursorMoved: ((Int) -> Unit)? = null
    private var editableKeyListener: KeyListener? = null

    var showLineNumbers: Boolean = true
        set(value) {
            field = value
            updateGutter()
        }

    var readOnly: Boolean = false
        set(value) {
            field = value
            keyListener = if (value) null else editableKeyListener
            setTextIsSelectable(true)
            isFocusable = true
            isFocusableInTouchMode = true
        }

    init {
        gravity = Gravity.TOP or Gravity.START
        typeface = Typeface.MONOSPACE
        background = null
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        numberPaint.typeface = Typeface.MONOSPACE
        numberPaint.textAlign = Paint.Align.RIGHT
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                removedNewlines = countNewlines(s, start, start + count)
                if (!applying) pendingBefore = s.subSequence(start, start + count).toString()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val added = countNewlines(s, start, start + count)
                val oldDigits = logicalLines.toString().length
                logicalLines += added - removedNewlines
                if (logicalLines < 1) logicalLines = 1
                if (start < cacheOffset) {
                    cacheOffset = 0
                    cacheCount = 0
                }
                if (logicalLines.toString().length != oldDigits) post { updateGutter() }
                if (!applying) record(start, pendingBefore, s.subSequence(start, start + count).toString())
                // keep the indentation of the previous line when Enter is pressed
                if (!applying && autoIndent && !readOnly && count == 1 && before == 0 && s[start] == '\n') {
                    var lineStart = start
                    while (lineStart > 0 && s[lineStart - 1] != '\n') lineStart--
                    var end = lineStart
                    while (end < start && (s[end] == ' ' || s[end] == '\t')) end++
                    if (end > lineStart) pendingIndent = (start + 1) to s.subSequence(lineStart, end).toString()
                }
            }

            override fun afterTextChanged(s: Editable) {
                val indent = pendingIndent
                if (indent != null) {
                    pendingIndent = null
                    if (indent.first <= s.length) s.insert(indent.first, indent.second)
                }
                onTextEdited?.invoke()
            }
        })
        editableKeyListener = keyListener
        updateGutter()
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    fun setColors(gutterBg: Int, gutterFg: Int, text: Int, divider: Int) {
        gutterPaint.color = gutterBg
        numberPaint.color = gutterFg
        linePaint.color = divider
        setTextColor(text)
        invalidate()
    }

    fun setFontSizeSp(sp: Float) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        numberPaint.textSize = textSize * 0.85f
        updateGutter()
    }

    fun setWordWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
        cacheOffset = 0
        cacheCount = 0
        requestLayout()
    }

    private fun updateGutter() {
        if (numberPaint.textSize <= 0f) numberPaint.textSize = textSize * 0.85f
        gutterWidth = if (showLineNumbers) {
            val digits = max(2, logicalLines.toString().length)
            (numberPaint.measureText("9".repeat(digits)) + gutterPad * 2).toInt()
        } else 0
        setPadding(gutterWidth + gutterPad, gutterPad, gutterPad, dp(48f))
        invalidate()
    }

    /** Replaces the whole content without recording history. */
    fun setContent(text: String) {
        applying = true
        setText(text)
        applying = false
        undoStack.clear()
        redoStack.clear()
        logicalLines = countNewlines(text, 0, text.length) + 1
        cacheOffset = 0
        cacheCount = 0
        updateGutter()
    }

    private fun countNewlines(s: CharSequence, from: Int, to: Int): Int {
        var n = 0
        for (i in from until to) if (s[i] == '\n') n++
        return n
    }

    private fun record(start: Int, before: String, after: String) {
        redoStack.clear()
        val now = System.currentTimeMillis()
        val last = undoStack.lastOrNull()
        // merge typing of consecutive characters into one undo step
        if (last != null && before.isEmpty() && last.before.isEmpty() && after.length == 1 &&
            after != "\n" && start == last.start + last.after.length && now - last.time < 1500
        ) {
            undoStack[undoStack.lastIndex] = last.copy(after = last.after + after, time = now)
        } else {
            undoStack.addLast(Edit(start, before, after, now))
            if (undoStack.size > 1000) undoStack.removeFirst()
        }
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo() {
        val e = undoStack.removeLastOrNull() ?: return
        val ed = text ?: return
        applying = true
        ed.replace(e.start, (e.start + e.after.length).coerceAtMost(ed.length), e.before)
        applying = false
        redoStack.addLast(e)
        setSelection((e.start + e.before.length).coerceAtMost(ed.length))
    }

    fun redo() {
        val e = redoStack.removeLastOrNull() ?: return
        val ed = text ?: return
        applying = true
        ed.replace(e.start, (e.start + e.before.length).coerceAtMost(ed.length), e.after)
        applying = false
        undoStack.addLast(e)
        setSelection((e.start + e.after.length).coerceAtMost(ed.length))
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        @Suppress("UNNECESSARY_SAFE_CALL")
        onCursorMoved?.invoke(selStart)
    }

    private fun logicalLineAt(offset: Int): Int {
        val t = text ?: return 0
        if (offset < cacheOffset) {
            cacheOffset = 0
            cacheCount = 0
        }
        var count = cacheCount
        for (i in cacheOffset until offset.coerceAtMost(t.length)) if (t[i] == '\n') count++
        cacheOffset = offset
        cacheCount = count
        return count
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showLineNumbers) return
        val layout = layout ?: return
        val t = text ?: return
        val left = scrollX.toFloat()
        val top = scrollY.toFloat()
        canvas.drawRect(left, top, left + gutterWidth, top + height, gutterPaint)
        canvas.drawRect(left + gutterWidth - 1, top, left + gutterWidth.toFloat(), top + height, linePaint)
        if (layout.lineCount == 0) return
        val padTop = totalPaddingTop
        val first = layout.getLineForVertical((scrollY - padTop).coerceAtLeast(0))
        val last = layout.getLineForVertical(scrollY + height - padTop)
        var logical = logicalLineAt(layout.getLineStart(first))
        val x = left + gutterWidth - gutterPad
        for (line in first..last) {
            val start = layout.getLineStart(line)
            val isLineStart = start == 0 || (start - 1 < t.length && t[start - 1] == '\n')
            if (line > first && isLineStart) logical++
            if (isLineStart) {
                val y = layout.getLineBaseline(line) + padTop
                canvas.drawText((logical + 1).toString(), x, y.toFloat(), numberPaint)
            }
        }
    }

    fun lineColOf(offset: Int): Pair<Int, Int> {
        val t = text ?: return 1 to 1
        val o = offset.coerceIn(0, t.length)
        var line = 1
        var lastNl = -1
        for (i in 0 until o) if (t[i] == '\n') {
            line++
            lastNl = i
        }
        return line to (o - lastNl)
    }

    fun offsetOfLine(line: Int): Int {
        val t = text ?: return 0
        if (line <= 1) return 0
        var current = 1
        for (i in t.indices) {
            if (t[i] == '\n') {
                current++
                if (current == line) return i + 1
            }
        }
        return t.length
    }

    val lineTotal: Int get() = logicalLines
}
