package com.funrisestudio.stepprogress

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.*

class StepProgressView : ViewGroup, View.OnClickListener {

    private val colorDefault = ContextCompat.getColor(context, R.color.colorGrey)

    private val stepProgress = StepProgress()

    private var nodeCheckDrawable: Int = R.drawable.ic_check
    private var nodeSelectDrawable: Int = R.drawable.ic_check
    private var nodeUnselectDrawable: Int = R.drawable.ic_check

    private var textNodeSelectColor = colorDefault
    private var textNodeUnselectColor = colorDefault

    private var arcSelectColor = colorDefault
    private var arcUnselectColor = colorDefault

    private var textNodeTitleColor = colorDefault

    private lateinit var arcSelectDrawable: Drawable
    private lateinit var arcUnselectDrawable: ColorDrawable

    private var nodes: List<String> = listOf()
    private var titles: List<String> = listOf()

    private var stepsCount = 1
    private var titlesEnabled = false
    private var nodeHeight = -1f
    private var textNodeTitleSize = 30
    private var textNodeSize = 35
    private var textTitlePadding = SViewUtils.toPx(5f, context)
    private var arcHeight = SViewUtils.toPx(2f, context)
    private var arcPadding = SViewUtils.toPx(10f, context)
    private val minSpacingLength = SViewUtils.toPx(10, context)
    private val nodeDefaultRatio = 0.1
    private val arcsMaxRatio = 0.60
    private val arcTransitionDuration = 200

    var onStepSelected: ((Int) -> Unit)? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.StepProgressView, 0, 0).apply {
            try {
                //common setup
                stepsCount = getInteger(R.styleable.StepProgressView_stepsCount, stepsCount)
                if (stepsCount < 0) {
                    throw IllegalStateException("Steps count can't be a negative number")
                }

                nodeCheckDrawable = getResourceId(R.styleable.StepProgressView_nodeCheckDrawable, nodeCheckDrawable)
                nodeSelectDrawable = getResourceId(R.styleable.StepProgressView_nodeSelectDrawable, nodeSelectDrawable)
                nodeUnselectDrawable = getResourceId(R.styleable.StepProgressView_nodeUnselectDrawable, nodeUnselectDrawable)

                textNodeSelectColor = getColor(R.styleable.StepProgressView_textNodeSelectColor, textNodeSelectColor)
                textNodeUnselectColor = getColor(R.styleable.StepProgressView_textNodeUnselectColor, textNodeUnselectColor)

                arcSelectColor = getColor(R.styleable.StepProgressView_arcSelectColor, arcSelectColor)
                arcUnselectColor = getColor(R.styleable.StepProgressView_arcUnselectColor, arcUnselectColor)

                //node setup
                nodeHeight = getDimension(R.styleable.StepProgressView_nodeHeight, nodeHeight)
                //arc setup
                arcHeight = getDimension(R.styleable.StepProgressView_arcWidth, arcHeight)
                arcPadding = getDimension(R.styleable.StepProgressView_arcPadding, arcPadding)
                //titles setup
                titlesEnabled = getBoolean(R.styleable.StepProgressView_titlesEnabled, titlesEnabled)
                textTitlePadding = getDimension(R.styleable.StepProgressView_textTitlePadding, textTitlePadding)
                textNodeTitleSize = getDimensionPixelSize(R.styleable.StepProgressView_textNodeTitleSize, textNodeTitleSize)
                textNodeSize = getDimensionPixelSize(R.styleable.StepProgressView_textNodeSize, textNodeSize)
                textNodeTitleColor = getColor(R.styleable.StepProgressView_textNodeTitleColor, textNodeTitleColor)
            } finally {
                recycle()
            }
        }
        init()
    }

    private fun init() {
        arcSelectDrawable = ColorDrawable(arcSelectColor)
        arcUnselectDrawable = ColorDrawable(arcUnselectColor)

        if (titlesEnabled) {
            nodes = getDefaultTitles()
            titles = getDefaultTitles()
        }
        stepProgress.reset()
        createViews()
    }

    private fun createViews() {
        if (stepsCount == 0) {
            return
        }
        removeAllViews()
        for (i in 0 until stepsCount) {
            if (titlesEnabled) {
                addView(textViewForStepTitle(i))
            }
            addView(textViewForStep(i, i == 0))
            if (i != stepsCount - 1) {
                addView(arcView(i))
            }
        }
    }

    //Global variables to save measuring results
    private var titleTextMaxHeight = 0
    private var titleTextMaxWidth = 0
    private var textOverflow = 0

    //flag to show if overflow mode was apply to view
    //overflow happen when requested nodes size does not fit into layout
    private var hasNodeOverflow = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        //Calculates width where views could be actually drawn
        val w = if (wMode != MeasureSpec.EXACTLY) {
            //respect margins if measure spec is not exact
            wSize - paddingStart - paddingEnd - marginStart - marginEnd
        } else {
            wSize - paddingStart - paddingEnd
        }
        val nodeSize = getNodeSize(w, heightMeasureSpec)
        val arcWidth = if (stepsCount > 1) {
            getArcWidth(widthMeasureSpec, w, nodeSize)
        } else {
            0
        }
        resolveTextOverflow(nodeSize)
        children.forEach {
            when {
                //Measure titles for step (node) views
                //Text takes a width equal to node size + allowed overflow size
                it is TextView && (it.tag as String == STEP_TITLE_TAG) -> {
                    val wSpecToMeasure = MeasureSpec.makeMeasureSpec(nodeSize + textOverflow, MeasureSpec.AT_MOST)
                    val hSpecToMeasure = MeasureSpec.makeMeasureSpec(hSize, MeasureSpec.AT_MOST)
                    it.measure(wSpecToMeasure, hSpecToMeasure)
                    //save measuring results to use them onLayout
                    val titleTextHeight = it.measuredHeight
                    val titleTextWidth = it.measuredWidth
                    if (titleTextHeight > titleTextMaxHeight) {
                        titleTextMaxHeight = titleTextHeight + textTitlePadding.toInt()
                    }
                    if (titleTextWidth > titleTextMaxWidth) {
                        titleTextMaxWidth = titleTextWidth
                    }
                }
                else -> {
                    //If children is not a text view (text view is used for nodes and titles)
                    //than it is a view for arc
                    //
                    //measures arc view
                    val arcWSpec = MeasureSpec.makeMeasureSpec(arcWidth, MeasureSpec.EXACTLY)
                    val arcHSpec =
                        MeasureSpec.makeMeasureSpec(arcHeight.toInt(), MeasureSpec.EXACTLY)
                    if (!hasNodeOverflow) {
                        (it.layoutParams as LinearLayout.LayoutParams).setMargins(
                            arcPadding.toInt(), 0, arcPadding.toInt(), 0
                        )
                    } else {
                        //remove margin if view is in overflow mode to escape case when arc is smaller than its margins
                        (it.layoutParams as LinearLayout.LayoutParams).setMargins(0)
                    }
                    it.measure(arcWSpec, arcHSpec)
                }
            }
        }
        //Measure node (step) views. Text view is uses to draw nodes
        children.filter { it is TextView && (it.tag as String != STEP_TITLE_TAG) }.forEach {
            //Resolve margins to fit node view with text vertically
            val nodeActualSize = if (hMode != MeasureSpec.EXACTLY) {
                (it.layoutParams as LinearLayout.LayoutParams).setMargins(
                    0, titleTextMaxHeight, 0, 0
                )
                nodeSize
            } else {
                (it.layoutParams as LinearLayout.LayoutParams).setMargins(
                    titleTextMaxHeight / 2, titleTextMaxHeight, titleTextMaxHeight / 2, 0
                )
                nodeSize - titleTextMaxHeight
            }
            val nodeViewSizeSpec = MeasureSpec.makeMeasureSpec(nodeActualSize, MeasureSpec.EXACTLY)
            it.measure(nodeViewSizeSpec, nodeViewSizeSpec)
        }
        //Resolve desired width and height that step progress want to take to fit all views
        //If width or height mode are exact, parameters from specs are used
        val desiredH = if (hMode != MeasureSpec.EXACTLY) {
            nodeSize + paddingTop + paddingBottom + titleTextMaxHeight
        } else {
            hSize
        }
        val desiredW = if (wMode != MeasureSpec.EXACTLY) {
            nodeSize * stepsCount + arcWidth * (stepsCount - 1) + paddingStart + paddingEnd + textOverflow
        } else {
            wSize
        }
        setMeasuredDimension(desiredW, desiredH)
    }

    //Text could be bigger than node view
    //Text overflow margin is calculated to fit text in a node view
    private fun resolveTextOverflow(nodeSize: Int) {
        textOverflow = if (titlesEnabled) {
            nodeSize
        } else {
            0
        }
    }

    //Calculate optimal node size to fit view width.
    //If node with default or exact size does not fit layout, it scales down to fit available width
    private fun getNodeSize(width: Int, heightMeasureSpec: Int): Int {
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        return if (hMode == MeasureSpec.AT_MOST || hMode == MeasureSpec.UNSPECIFIED) {
            val nodeDefaultSize = if (nodeHeight != -1f) {
                nodeHeight.toInt()
            } else {
                (width * nodeDefaultRatio).toInt()
            }
            //If node fits take it default size
            //If not calculate the maximal size that the node could take respecting view width
            if (nodeSizeFits(width, nodeDefaultSize)) {
                hasNodeOverflow = false
                nodeDefaultSize
            } else {
                hasNodeOverflow = true
                maximalNodeSize(width)
            }
        } else {
            hSize
        }
    }

    //Calculate optimal arcs size for steps
    //Arcs take all remaining space that is not taken by node or margins
    //If WRAP_CONTENT width is set for layout arc size could be reduced
    //to respect optimal proportions for nodes and arcs
    private fun getArcWidth(widthMeasureSpec: Int, width: Int, nodeSize: Int): Int {
        //include padding for titles
        val sCount = if (titlesEnabled) {
            stepsCount + 1
        } else {
            stepsCount
        }
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val arcsCount = stepsCount - 1
        val allArcsWidth = (width - sCount * nodeSize)
        val isArcsWidthAppropriate = allArcsWidth <= width * arcsMaxRatio
        return if (isArcsWidthAppropriate || (!isArcsWidthAppropriate && wMode == MeasureSpec.EXACTLY)) {
            allArcsWidth / arcsCount
        } else {
            (width * arcsMaxRatio).toInt() / arcsCount
        }
    }

    private fun nodeSizeFits(width: Int, desiredSize: Int): Boolean {
        //include padding for titles
        val sCount = if (titlesEnabled) {
            stepsCount + 1
        } else {
            stepsCount
        }
        return (width - desiredSize * sCount) >= minSpacingLength * (stepsCount - 1)
    }

    private fun maximalNodeSize(width: Int): Int {
        //include padding for titles
        val sCount = if (titlesEnabled) {
            stepsCount + 1
        } else {
            stepsCount
        }
        return (width - minSpacingLength * (stepsCount - 1)) / sCount
    }

    //Arranges all created views in a proper order from left to right
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        //calculate insets
        var left = paddingStart + textOverflow / 2
        val top = paddingTop
        val step: View
        try {
            //Take a node view to calculate it margins
            step = children.first {
                it is TextView && it.tag as String != STEP_TITLE_TAG
            }
        } catch (e: NoSuchElementException) {
            e.printStackTrace()
            return
        }
        //Width of a node view
        val stepWidth = step.measuredWidth + step.paddingStart + step.paddingEnd
        //Layout children sequentially
        children.forEach {
            when {
                //step
                it is TextView && (it.tag as String != STEP_TITLE_TAG) -> {

                    val centerPadding = (stepWidth - it.measuredWidth) / 2

                    it.layout(
                        left + it.marginLeft + centerPadding,
                        top + 0,
                        left + it.measuredWidth + centerPadding,
                        top + it.measuredHeight
                    )

                    left += it.measuredWidth + it.marginRight + it.marginLeft
                }
                //step title
                it is TextView && (it.tag as String == STEP_TITLE_TAG) -> {

                    val centerPadding = (stepWidth - it.measuredWidth) / 2
                    val stepTitleTop = measuredHeight - paddingBottom - it.measuredHeight

                    it.layout(
                        left + centerPadding,
                        stepTitleTop + 0,
                        left + it.measuredWidth + centerPadding,
                        stepTitleTop + it.measuredHeight
                    )
                }
                //arc
                else -> {

                    val arcTop = top + (step.measuredHeight - it.measuredHeight) / 2

                    it.layout(
                        left + it.marginStart,
                        arcTop + 0,
                        left + it.measuredWidth - it.marginEnd,
                        arcTop + it.measuredHeight
                    )

                    left += it.measuredWidth
                }
            }
        }
    }

    private fun textViewForStepTitle(stepPosition: Int): TextView {
        return TextView(context).apply {
            text = titles[stepPosition]
            gravity = Gravity.TOP or Gravity.CENTER
            layoutParams = getDefaultElementLayoutParams()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textNodeTitleSize.toFloat())
            setTextColor(textNodeTitleColor)
            setOnClickListener(this@StepProgressView)
            tag = STEP_TITLE_TAG
        }
    }

    private fun textViewForStep(stepPosition: Int, isActive: Boolean): TextView {
        return TextView(context).apply {
            text = nodes[stepPosition]

            if (isActive) {
                setTextColor(textNodeSelectColor)
                setBackgroundResource(nodeSelectDrawable)
            } else {
                setTextColor(textNodeUnselectColor)
                setBackgroundResource(nodeUnselectDrawable)
            }

            gravity = Gravity.CENTER
            layoutParams = getDefaultElementLayoutParams()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textNodeSize.toFloat())
            setOnClickListener(this@StepProgressView)
            tag = NODE_TAG_PREFIX + stepPosition
        }
    }


    private fun getDefaultElementLayoutParams(
        width: Int = LayoutParams.WRAP_CONTENT,
        height: Int = LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height)
    }

    private fun arcView(position: Int): ArcView {
        return ArcView(context).apply {
            background = TransitionDrawable(arrayOf(arcUnselectDrawable, arcSelectDrawable))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            tag = ARC_TAG_PREFIX + position
        }
    }

    override fun onClick(v: View?) {
        if (v != null && v.tag is String) {
            val tagStr = v.tag as String
            val prefixEnd = tagStr.indexOf(NODE_TAG_PREFIX)
            if (prefixEnd != -1) {
                val numberIndex = prefixEnd + NODE_TAG_PREFIX.length
                val selectedStepIndex = tagStr.substring(numberIndex).toInt()
                stepProgress.selectStep(selectedStepIndex)
                onStepSelected?.invoke(selectedStepIndex)
            }
        }
    }

    private fun changeStepStateView(stepNumber: Int, newState: StepState) {
        val view = findViewWithTag<TextView>(NODE_TAG_PREFIX + stepNumber)
        view?.let {
            when (newState) {
                StepState.DONE -> {
                    it.textSize = 0f
                    it.setTextColor(textNodeSelectColor)
                    it.setBackgroundResource(nodeCheckDrawable)
                }
                StepState.SELECTED_DONE -> {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textNodeTitleSize.toFloat())
                    it.setTextColor(textNodeSelectColor)
                    it.setBackgroundResource(nodeSelectDrawable)
                }
                StepState.SELECTED_UNDONE -> {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textNodeTitleSize.toFloat())
                    it.setTextColor(textNodeSelectColor)
                    it.setBackgroundResource(nodeSelectDrawable)
                }
                else -> {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textNodeTitleSize.toFloat())
                    it.setTextColor(textNodeUnselectColor)
                    it.setBackgroundResource(nodeUnselectDrawable)
                }
            }
        }
    }

    private fun animateProgressArc(arcNumber: Int, activated: Boolean) {
        val view = findViewWithTag<ArcView>(ARC_TAG_PREFIX + arcNumber)
        if (activated) {
            if (!view.isHighlighted) {
                view.isHighlighted = true
                (view.background as TransitionDrawable).startTransition(arcTransitionDuration)
            }
        } else {
            if (view.isHighlighted) {
                view.isHighlighted = false
                (view.background as TransitionDrawable).resetTransition()
            }
        }
    }

    /**
     * Set number of steps for progress
     * @param stepsCount steps number
     */
    @Throws(IllegalStateException::class)
    fun setStepsCount(stepsCount: Int) {
        if (stepsCount < 0) {
            throw IllegalStateException("Steps count can't be a negative number")
        }
        this.stepsCount = stepsCount
        if (titles.size != stepsCount) {
            nodes = getDefaultTitles()
            titles = getDefaultTitles()
        }
        resetView()
        invalidate()
    }

    /**
     * Go to next step
     * @param isCurrentDone if true marks current selected step as done
     * @return true if all steps are finished
     */
    fun nextStep(isCurrentDone: Boolean): Boolean {
        return stepProgress.nextStep(isCurrentDone) == 1
    }

    /**
     * Mark current selected step as done
     */
    fun markCurrentAsDone() {
        stepProgress.markCurrentStepDone()
    }

    /**
     * Mark current selected step as undone
     */
    fun markCurrentAsUndone() {
        stepProgress.markCurrentStepUndone()
    }

    /**
     * Set title for each step
     * @param titles list of titles to apply to step views. Size should be the same as steps count
     */
    fun setStepTitles(titles: List<String>) {
        this.titles = titles
        resetView()
        invalidate()
    }

    /**
     * Set value for each step
     * @param notes list of value to apply to step views. Size should be the same as steps count
     */
    fun setStepValues(notes: List<String>) {
        this.nodes = notes
        resetView()
        invalidate()
    }

    /**
     * Checks if step is finished
     * @param stepPosition step position to check
     */
    fun isStepDone(stepPosition: Int = -1): Boolean {
        return stepProgress.isStepDone(stepPosition)
    }

    /**
     * Checks if all steps of a progress are finished
     * @return true if all steps marked as finished
     */
    fun isProgressFinished(): Boolean {
        return stepProgress.isAllDone()
    }

    /**
     * Change title text padding
     */
    fun setTextTitlePadding(size: Float) {
        textTitlePadding = size
        requestLayout()
    }

    /**
     * Change node title text size
     */
    fun setNodeTitleSize(size: Int) {
        textNodeTitleSize = size
        resetView()
    }

    /**
     * Change text node  size
     */
    fun setNodeTextSize(size: Int) {
        textNodeSize = size
        resetView()
    }

    /**
     * Change node height
     */
    fun setNodeHeight(size: Float) {
        nodeHeight = size
        requestLayout()
    }


    /**
     * Change node title color
     */
    fun setNodeTitleColor(@ColorInt color: Int) {
        textNodeTitleColor = color
        resetView()
    }

    private fun getDefaultTitles(): List<String> {
        return mutableListOf<String>().apply {
            for (i in 0 until stepsCount) {
                add("Step ${i + 1}")
            }
        }
    }

    private fun resetView() {
        stepProgress.reset()
        createViews()
    }

    private inner class StepProgress {

        private var currentStep = 0

        private var stepsStates: Array<StepState> = getInitialStepProgress()

        fun reset() {
            currentStep = 0
            stepsStates = getInitialStepProgress()
        }

        private fun getInitialStepProgress(): Array<StepState> {
            return Array(stepsCount) {
                if (it == 0) {
                    StepState.SELECTED_UNDONE
                } else {
                    StepState.UNDONE
                }
            }
        }

        fun selectStep(stepNumber: Int) {
            if (stepNumber == currentStep) {
                return
            }
            val oldStep = currentStep
            currentStep = stepNumber
            val currentStepState = stepsStates[currentStep]
            val newOldStepState = if (isStepDone(oldStep)) {
                StepState.DONE
            } else {
                StepState.UNDONE
            }
            val newCurrentStepState = if (currentStepState == StepState.DONE) {
                StepState.SELECTED_DONE
            } else {
                StepState.SELECTED_UNDONE
            }
            changeStepState(oldStep, newOldStepState)
            changeStepState(currentStep, newCurrentStepState)
        }

        fun nextStep(isCurrentDone: Boolean): Int {
            if (isAllDone()) {
                return 1
            }
            val nextStep = currentStep + 1
            if (isCurrentDone) {
                changeStepState(currentStep, StepState.DONE)
            }
            if (nextStep < stepsCount) {
                val nextStepState = stepsStates[nextStep]
                val newNextStepState = if (nextStepState == StepState.DONE) {
                    StepState.SELECTED_DONE
                } else {
                    StepState.SELECTED_UNDONE
                }
                changeStepState(nextStep, newNextStepState)
                currentStep = nextStep
            }
            return if (isAllDone()) {
                1
            } else {
                0
            }
        }

        fun markCurrentStepDone() {
            if (isStepDone()) {
                return
            }
            changeStepState(currentStep, StepState.SELECTED_DONE)
        }

        fun markCurrentStepUndone() {
            if (isStepUndone()) {
                return
            }
            changeStepState(currentStep, StepState.SELECTED_UNDONE)
        }

        private fun updateArcsState() {
            var allDone = true
            for (i in 0..stepsStates.size - 2) {
                if (isStepDone(i) && allDone) {
                    animateProgressArc(i, true)
                } else {
                    allDone = false
                    animateProgressArc(i, false)
                }
            }
        }

        fun isStepDone(stepNumber: Int = -1): Boolean {
            val stepState = if (stepNumber == -1) {
                stepsStates[currentStep]
            } else {
                stepsStates[stepNumber]
            }
            return stepState == StepState.DONE || stepState == StepState.SELECTED_DONE
        }

        private fun isStepUndone(stepNumber: Int = -1): Boolean {
            val stepState = if (stepNumber == -1) {
                stepsStates[currentStep]
            } else {
                stepsStates[stepNumber]
            }
            return stepState == StepState.UNDONE || stepState == StepState.SELECTED_UNDONE
        }

        private fun changeStepState(stepNumber: Int, newState: StepState) {
            stepsStates[stepNumber] = newState
            changeStepStateView(stepNumber, newState)
            updateArcsState()
        }

        fun isAllDone(): Boolean {
            stepsStates.forEach {
                if (it == StepState.UNDONE || it == StepState.SELECTED_UNDONE) {
                    return false
                }
            }
            return true
        }

    }

    companion object {

        private const val NODE_TAG_PREFIX = "stn_"

        private const val ARC_TAG_PREFIX = "atn_"

        private const val STEP_TITLE_TAG = "step_title"

    }
}
