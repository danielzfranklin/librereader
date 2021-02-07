package org.danielzfranklin.librereader.ui.reader.pagesView

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.danielzfranklin.librereader.databinding.ReaderPagesViewBinding
import org.danielzfranklin.librereader.repo.model.BookPosition
import org.danielzfranklin.librereader.ui.reader.displayModel.BookDisplay
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ReaderPagesView(
    context: Context,
    override val coroutineContext: CoroutineContext,
    private val book: BookDisplay,
    private val position: MutableStateFlow<BookPosition>
) : LinearLayout(context), CoroutineScope {
    private val inflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val binding = ReaderPagesViewBinding.inflate(inflater, this, true)

    private fun createPage(position: BookPosition?, percentTurned: Float) =
        PageView(context).apply {
            style = book.pageDisplay.style
            text = position?.page(book)
            manager = this@ReaderPagesView
            this.percentTurned = percentTurned
            width = book.pageDisplay.width
            height = book.pageDisplay.height
        }

    // NOTE: Order of creation matters
    private var prevPage = createPage(position.value.movedBy(book, -1), 0f)
    private var nextPage = createPage(position.value.movedBy(book, 1), 1f)
    private var currentPage = createPage(position.value, 1f)

    init {
        binding.parent.addView(prevPage, pageLayoutParams)
        binding.parent.addView(nextPage, pageLayoutParams)
        binding.parent.addView(currentPage, pageLayoutParams)
    }

    private val turnState = MutableStateFlow<TurnState>(TurnState.Initial)
    private val pageWidth = book.pageDisplay.width
    val gestureDetector = ReaderPagesGestureDetector(context, turnState, pageWidth.toFloat())

    fun jumpTo(newPosition: BookPosition) {
        prevPage.text = newPosition.movedBy(book, -1)?.page(book)
        currentPage.text = newPosition.page(book)
        nextPage.text = newPosition.movedBy(book, 1)?.page(book)
        position.value = newPosition
    }

    init {
        binding.tapBack.setOnClickListener {
            turnState.value = TurnState.BeganTurnBack
            turnState.value = TurnState.CompletingTurnBack(0f)
        }
        binding.tapNext.setOnClickListener {
            turnState.value = TurnState.BeganTurnForward
            turnState.value = TurnState.CompletingTurnForward(0f)
        }

        // Propagate events to the detector
        binding.tapBack.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        binding.tapNext.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        launch {
            turnState.collect { state ->
                when (state) {
                    is TurnState.BeganTurnBack -> {
                        disableSelection()

                        val toRecycle = prevPage
                        prevPage = nextPage.apply {
                            percentTurned = 0f
                            text = position.value.movedBy(book, -2)?.page(book)
                        }
                        nextPage = currentPage.apply {
                            bringToFront()
                        }
                        currentPage = toRecycle.apply {
                            percentTurned = 0f
                            bringToFront()
                        }
                    }

                    is TurnState.BeganTurnForward -> {
                        disableSelection()
                    }

                    is TurnState.TurningBackwards -> {
                        currentPage.percentTurned = state.percent
                    }

                    is TurnState.TurningForwards -> {
                        currentPage.percentTurned = 1f - state.percent
                    }

                    is TurnState.CompletingTurnBack -> {
                        animateTurn(state.fromPercent, 1f) {
                            currentPage.percentTurned = 1f
                            currentPage.setTextIsSelectable(true)
                            position.value.movedBy(book, -1)?.let { position.value = it }
                        }
                    }

                    is TurnState.CompletingTurnForward -> {
                        animateTurn(1 - state.fromPercent, 0f) {
                            position.value.movedBy(book, 1)?.let { position.value = it }

                            val toRecycle = prevPage
                            prevPage = currentPage
                            currentPage = nextPage.apply {
                                percentTurned = 1f
                                setTextIsSelectable(true)
                                bringToFront()
                            }
                            nextPage = toRecycle.apply {
                                percentTurned = 1f
                                text = position.value.movedBy(book, 1)?.page(book)
                            }
                        }
                    }

                    is TurnState.CancellingTurnBack -> {
                        animateTurn(state.fromPercent, 0f) {
                            val currentPosition = position.value
                            val sectionPages = book.sections[currentPosition.sectionIndex].pages
                            val pageIndex = currentPosition.sectionPageIndex(book)

                            val toRecycle = prevPage
                            prevPage = currentPage.apply {
                                percentTurned = 0f
                            }
                            currentPage = nextPage.apply {
                                percentTurned = 1f
                                setTextIsSelectable(true)
                                bringToFront()
                            }
                            nextPage = toRecycle.apply {
                                percentTurned = 1f
                                text = sectionPages.getOrNull(pageIndex + 2)
                            }
                        }
                    }

                    is TurnState.CancellingTurnForward -> {
                        animateTurn(1 - state.fromPercent, 1f) {
                            currentPage.percentTurned = 1f
                            currentPage.setTextIsSelectable(true)
                        }
                    }
                }
            }
        }

        launch {
            position.collect { position ->
                if (book.isFirstPage(position)) {
                    gestureDetector.disableTurnBackwards()
                    binding.tapBack.isEnabled = false
                } else {
                    gestureDetector.enableTurnBackwards()
                    binding.tapBack.isEnabled = true
                }

                if (book.isLastPage(position)) {
                    gestureDetector.disableTurnForwards()
                    binding.tapNext.isEnabled = false
                } else {
                    gestureDetector.enableTurnForwards()
                    binding.tapNext.isEnabled = true
                }
            }
        }
    }

    /**
     * Not that from and to are the percent the current page is turned, not the percent the current
     * turn is complete.
     */
    private fun animateTurn(from: Float, to: Float, onEnd: () -> Unit) {
        val animator = ObjectAnimator.ofFloat(currentPage, "percentTurned", from, to)
        animator.duration = (pageAnimationTimePerPercent * abs(from - to) * 100).toLong()
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                onEnd()
            }
        })
        animator.start()
    }

    private fun disableSelection() {
        prevPage.setTextIsSelectable(false)
        nextPage.setTextIsSelectable(false)
        currentPage.setTextIsSelectable(false)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        gestureDetector.onWindowVisibilityChanged(visibility)
        super.onWindowVisibilityChanged(visibility)
    }

    companion object {
        private val pageLayoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        private const val pageAnimationTimePerPercent = 3f
    }
}