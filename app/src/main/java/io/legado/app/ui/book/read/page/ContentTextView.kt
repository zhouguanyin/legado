package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.entities.*
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.ImageProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.utils.activity
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.toastOnUi
import kotlin.math.min

/**
 * 阅读内容视图
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var selectAble = context.getPrefBoolean(PreferKey.textSelectAble, true)
    var upView: ((TextPage) -> Unit)? = null
    private val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = RectF()
    val selectStart = TextPos(0, 0, 0)
    val selectEnd = TextPos(0, 0, 0)
    var textPage: TextPage = TextPage()
        private set
    private var drawVisibleImageOnly = false
    private var cacheIncreased = false
    private val increaseSize = 8 * 1024 * 1024
    private val maxCacheSize = 256 * 1024 * 1024

    //滚动参数
    private val pageFactory: TextPageFactory get() = callBack.pageFactory
    private var pageOffset = 0

    //绘制图片的paint
    private val imagePaint by lazy {
        Paint().apply {
            isAntiAlias = AppConfig.useAntiAlias
        }
    }

    init {
        callBack = activity as CallBack
    }

    /**
     * 设置内容
     */
    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        imagePaint.isAntiAlias = AppConfig.useAntiAlias
        invalidate()
    }

    /**
     * 更新绘制区域
     */
    fun upVisibleRect() {
        visibleRect.set(
            ChapterProvider.paddingLeft.toFloat(),
            ChapterProvider.paddingTop.toFloat(),
            ChapterProvider.visibleRight.toFloat(),
            ChapterProvider.visibleBottom.toFloat()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ChapterProvider.upViewSize(w, h)
        upVisibleRect()
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipRect(visibleRect)
        drawPage(canvas)
        drawVisibleImageOnly = false
        cacheIncreased = false
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        textPage.textLines.forEach { textLine ->
            draw(canvas, textPage, textLine, relativeOffset)
        }
        if (!callBack.isScroll) return
        //滚动翻页
        if (!pageFactory.hasNext()) return
        val textPage1 = relativePage(1)
        relativeOffset = relativeOffset(1)
        textPage1.textLines.forEach { textLine ->
            draw(canvas, textPage1, textLine, relativeOffset)
        }
        if (!pageFactory.hasNextPlus()) return
        relativeOffset = relativeOffset(2)
        if (relativeOffset < ChapterProvider.visibleHeight) {
            val textPage2 = relativePage(2)
            textPage2.textLines.forEach { textLine ->
                draw(canvas, textPage2, textLine, relativeOffset)
            }
        }
    }

    /**
     * 绘制页面
     */
    private fun draw(
        canvas: Canvas,
        textPage: TextPage,
        textLine: TextLine,
        relativeOffset: Float,
    ) {
        val lineTop = textLine.lineTop + relativeOffset
        val lineBase = textLine.lineBase + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset
        drawChars(canvas, textPage, textLine, lineTop, lineBase, lineBottom)
    }

    /**
     * 绘制文字
     */
    private fun drawChars(
        canvas: Canvas,
        textPage: TextPage,
        textLine: TextLine,
        lineTop: Float,
        lineBase: Float,
        lineBottom: Float,
    ) {
        val textPaint = if (textLine.isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = if (textLine.isReadAloud) context.accentColor else ReadBookConfig.textColor
        val linePaint = Paint()
        linePaint.strokeWidth = textPaint.textSize / 21
        linePaint.color = textColor
        val reviewCountPaint = TextPaint()
        reviewCountPaint.textSize = textPaint.textSize * 0.6F
        reviewCountPaint.color = textColor
        textLine.textChars.forEach {
            when (it.style) {
                0 -> {
                    textPaint.color = textColor
                    if (it.isSearchResult) {
                        textPaint.color = context.accentColor
                    }
                    canvas.drawText(it.charData, it.start, lineBase, textPaint)
                }
                1 -> drawImage(canvas, textPage, textLine, it, lineTop, lineBottom)
                2 -> {
                    if (textLine.reviewCount <= 0) return@forEach
                    canvas.drawLine(
                        it.start,
                        lineBase - textPaint.textSize * 2 / 5,
                        it.start + textPaint.textSize / 6,
                        lineBase - textPaint.textSize / 4,
                        linePaint
                    )
                    canvas.drawLine(
                        it.start,
                        lineBase - textPaint.textSize * 0.38F,
                        it.start + textPaint.textSize / 6,
                        lineBase - textPaint.textSize * 0.55F,
                        linePaint
                    )
                    canvas.drawLine(
                        it.start + textPaint.textSize / 6,
                        lineBase - textPaint.textSize / 4,
                        it.start + textPaint.textSize / 6,
                        lineBase,
                        linePaint
                    )
                    canvas.drawLine(
                        it.start + textPaint.textSize / 6,
                        lineBase - textPaint.textSize * 0.55F,
                        it.start + textPaint.textSize / 6,
                        lineBase - textPaint.textSize * 0.8F,
                        linePaint
                    )
                    canvas.drawLine(
                        it.start + textPaint.textSize / 6,
                        lineBase,
                        it.start + textPaint.textSize * 1.6F,
                        lineBase,
                        linePaint
                    )
                    canvas.drawLine(
                        it.start + textPaint.textSize / 6,
                        lineBase - textPaint.textSize * 0.8F,
                        it.start + textPaint.textSize * 1.6F,
                        lineBase - textPaint.textSize * 0.8F,
                        linePaint
                    )
                    canvas.drawLine(
                        it.start + textPaint.textSize * 1.6F,
                        lineBase - textPaint.textSize * 0.8F,
                        it.start + textPaint.textSize * 1.6F,
                        lineBase,
                        linePaint
                    )
                    if (textLine.reviewCount < 100) canvas.drawText(
                        textLine.reviewCount.toString(),
                        it.start + textPaint.textSize * 0.87F -
                                StaticLayout.getDesiredWidth(
                                    textLine.reviewCount.toString(),
                                    reviewCountPaint
                                ) / 2,
                        lineBase - textPaint.textSize / 6,
                        reviewCountPaint
                    )
                    else canvas.drawText(
                        "99+",
                        it.start + textPaint.textSize * 0.35F,
                        lineBase - textPaint.textSize / 6,
                        reviewCountPaint
                    )
                }
            }
            if (it.selected) {
                canvas.drawRect(it.start, lineTop, it.end, lineBottom, selectedPaint)
            }
        }
    }

    /**
     * 绘制图片
     */
    @Suppress("UNUSED_PARAMETER")
    private fun drawImage(
        canvas: Canvas,
        textPage: TextPage,
        textLine: TextLine,
        textChar: TextColumn,
        lineTop: Float,
        lineBottom: Float
    ) {

        val book = ReadBook.book ?: return
        val isVisible = when {
            lineTop > 0 -> lineTop < height
            lineTop < 0 -> lineBottom > 0
            else -> true
        }
        if (drawVisibleImageOnly && !isVisible) {
            return
        }
        if (drawVisibleImageOnly &&
            isVisible &&
            !cacheIncreased &&
            ImageProvider.isTriggerRecycled() &&
            !ImageProvider.isImageAlive(book, textChar.charData)
        ) {
            val newSize = ImageProvider.bitmapLruCache.maxSize() + increaseSize
            if (newSize < maxCacheSize) {
                ImageProvider.bitmapLruCache.resize(newSize)
                AppLog.put("图片缓存不够大，自动扩增至${(newSize / 1024 / 1024)}MB。")
                cacheIncreased = true
            }
            return
        }
        val bitmap = ImageProvider.getImage(
            book,
            textChar.charData,
            (textChar.end - textChar.start).toInt(),
            (lineBottom - lineTop).toInt()
        ) {
            if (!drawVisibleImageOnly && isVisible) {
                drawVisibleImageOnly = true
                invalidate()
            }
        } ?: return

        val rectF = if (textLine.isImage) {
            RectF(textChar.start, lineTop, textChar.end, lineBottom)
        } else {
            /*以宽度为基准保持图片的原始比例叠加，当div为负数时，允许高度比字符更高*/
            val h = (textChar.end - textChar.start) / bitmap.width * bitmap.height
            val div = (lineBottom - lineTop - h) / 2
            RectF(textChar.start, lineTop + div, textChar.end, lineBottom - div)
        }
        kotlin.runCatching {
            canvas.drawBitmap(bitmap, null, rectF, imagePaint)
        }.onFailure { e ->
            context.toastOnUi(e.localizedMessage)
        }
    }

    /**
     * 滚动事件
     */
    fun scroll(mOffset: Int) {
        if (mOffset == 0) return
        pageOffset += mOffset
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
        } else if (pageOffset > 0) {
            pageFactory.moveToPrev(false)
            textPage = pageFactory.curPage
            pageOffset -= textPage.height.toInt()
            upView?.invoke(textPage)
            contentDescription = textPage.text
        } else if (pageOffset < -textPage.height) {
            pageOffset += textPage.height.toInt()
            pageFactory.moveToNext(false)
            textPage = pageFactory.curPage
            upView?.invoke(textPage)
            contentDescription = textPage.text
        }
        invalidate()
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        pageOffset = 0
    }

    /**
     * 长按
     */
    fun longPress(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touch(x, y) { _, textPos, _, _, textColumn ->
            if (textColumn.style == 2) return@touch
            if (textColumn.style == 1) {
                callBack.onImageLongPress(x, y, textColumn.charData)
            } else {
                if (!selectAble) return@touch
                textColumn.selected = true
                invalidate()
                select(textPos)
            }
        }
    }

    /**
     * 单击
     * @return true:已处理, false:未处理
     */
    fun click(x: Float, y: Float): Boolean {
        var handled = false
        touch(x, y) { _, textPos, textPage, textLine, textColumn ->
            if (textColumn.style == 2) {
                context.toastOnUi("Button Pressed!")
                handled = true
            }
        }
        return handled
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touch(x, y) { _, textPos, _, _, textColumn ->
            if (textColumn.style == 2) return@touch
            textColumn.selected = true
            invalidate()
            select(textPos)
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touch(x, y) { relativeOffset, textPos, _, textLine, textChar ->
            if (selectStart.compare(textPos) != 0) {
                if (textPos.compare(selectEnd) <= 0) {
                    selectStart.upData(pos = textPos)
                    upSelectedStart(
                        textChar.start,
                        textLine.lineBottom + relativeOffset,
                        textLine.lineTop + relativeOffset
                    )
                    upSelectChars()
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touch(x, y) { relativeOffset, textPos, _, textLine, textChar ->
            if (textPos.compare(selectEnd) != 0) {
                if (textPos.compare(selectStart) >= 0) {
                    selectEnd.upData(textPos)
                    upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                    upSelectChars()
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * @param touched 回调
     */
    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            textColumn: TextColumn
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.textLines.withIndex()) {
                if (textLine.isTouch(x, y, relativeOffset)) {
                    for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                        if (textChar.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textChar
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(relativePagePos: Int, lineIndex: Int, charIndex: Int) {
        selectStart.relativePagePos = relativePagePos
        selectStart.lineIndex = lineIndex
        selectStart.charIndex = charIndex
        val textLine = relativePage(relativePagePos).getLine(lineIndex)
        val textChar = textLine.getTextChar(charIndex)
        upSelectedStart(
            textChar.start,
            textLine.lineBottom + relativeOffset(relativePagePos),
            textLine.lineTop + relativeOffset(relativePagePos)
        )
        upSelectChars()
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectEnd.relativePagePos = relativePage
        selectEnd.lineIndex = lineIndex
        selectEnd.charIndex = charIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        val textChar = textLine.getTextChar(charIndex)
        upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset(relativePage))
        upSelectChars()
    }

    private fun upSelectChars() {
        val last = if (callBack.isScroll) 2 else 0
        val textPos = TextPos(0, 0, 0)
        for (relativePos in 0..last) {
            textPos.relativePagePos = relativePos
            for ((lineIndex, textLine) in relativePage(relativePos).textLines.withIndex()) {
                textPos.lineIndex = lineIndex
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    textPos.charIndex = charIndex
                    if (textChar.style == 2) continue
                    textChar.selected =
                        textPos.compare(selectStart) >= 0 && textPos.compare(selectEnd) <= 0
                    textChar.isSearchResult = textChar.selected && callBack.isSelectingSearchResult
                }
            }
        }
        invalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) = callBack.apply {
        upSelectedStart(x, y + headerHeight, top + headerHeight)
    }

    private fun upSelectedEnd(x: Float, y: Float) = callBack.apply {
        upSelectedEnd(x, y + headerHeight)
    }

    fun cancelSelect(fromSearchExit: Boolean = false) {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            relativePage(relativePos).textLines.forEach { textLine ->
                textLine.textChars.forEach {
                    it.selected = false
                    if (fromSearchExit) it.isSearchResult = false
                }
            }
        }
        invalidate()
        callBack.onCancelSelect()
    }

    fun getSelectedText(): String {
        val textPos = TextPos(0, 0, 0)
        val builder = StringBuilder()
        for (relativePos in selectStart.relativePagePos..selectEnd.relativePagePos) {
            val textPage = relativePage(relativePos)
            textPos.relativePagePos = relativePos
            textPage.textLines.forEachIndexed { lineIndex, textLine ->
                textPos.lineIndex = lineIndex
                textLine.textChars.forEachIndexed { charIndex, textChar ->
                    textPos.charIndex = charIndex
                    val compareStart = textPos.compare(selectStart)
                    val compareEnd = textPos.compare(selectEnd)
                    if (compareStart >= 0 && compareEnd <= 0) {
                        builder.append(textChar.charData)
                        if (
                            textLine.isParagraphEnd
                            && charIndex == textLine.charSize - 1
                            && compareEnd != 0
                        ) {
                            builder.append("\n")
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart.relativePagePos)
        page.getTextChapter()?.let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getPosByLineColumn(selectStart.lineIndex, selectStart.charIndex)
                    chapterName = chapter.title
                    bookText = getSelectedText()
                }
            }
        }
        return null
    }

    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset.toFloat()
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    fun relativePage(relativePos: Int): TextPage {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPlusPage
        }
    }

    interface CallBack {
        val headerHeight: Int
        val pageFactory: TextPageFactory
        val isScroll: Boolean
        var isSelectingSearchResult: Boolean
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onImageLongPress(x: Float, y: Float, src: String)
        fun onCancelSelect()
    }
}
