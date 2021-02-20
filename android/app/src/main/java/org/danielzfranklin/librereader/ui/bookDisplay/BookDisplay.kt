package org.danielzfranklin.librereader.ui.bookDisplay

import android.content.Context
import nl.siegmann.epublib.domain.Book
import org.danielzfranklin.librereader.model.BookID
import org.danielzfranklin.librereader.model.BookPosition

open class BookDisplay(
    private val context: Context,
    val id: BookID,
    val epub: Book,
    val pageDisplay: BookPageDisplay,
) {
    val title = epub.title ?: "Untitled"

    val sections = epub.spine.spineReferences
        .mapIndexed { index, _ -> BookSectionDisplay(context, this, index) }

    val textLength = sections.sumBy { it.textLength }

    private var _pageCount: Int? = null
    fun pageCount(): Int {
        val cached = _pageCount
        return if (cached != null) {
            cached
        } else {
            val computed = sections.sumBy { it.pages().size }
            _pageCount = computed
            computed
        }
    }

    fun isFirstPage(position: BookPosition) =
        position.sectionIndex == 0 && position.sectionPageIndex(this) == 0

    fun isLastPage(position: BookPosition) =
        position.sectionIndex == sections.size - 1 &&
                position.sectionPageIndex(this) == sections[position.sectionIndex].pages().size - 1
}
