/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.content.res.TypedArray
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_PERCENT
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.mozilla.fenix.R

import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Preference based on android.preference.SeekBarPreference but uses support preference as a base.
 * It contains a title and a [SeekBar] and a SeekBar value [TextView] and an Example [TextView].
 * The actual preference layout is customizable by setting `android:layout` on the
 * preference widget layout or `seekBarPreferenceStyle` attribute.
 *
 * The [SeekBar] within the preference can be defined adjustable or not by setting `adjustable` attribute.
 * If adjustable, the preference will be responsive to DPAD left/right keys.
 * Otherwise, it skips those keys.
 *
 * The [SeekBar] value view can be shown or disabled by setting `showSeekBarValue`
 * attribute to true or false, respectively.
 *
 * Other [SeekBar] specific attributes (e.g. `title, summary, defaultValue, min, max`)
 * can be set directly on the preference widget layout.
 */
class TextPercentageSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    internal var trackingTouch: Boolean = false
    private var seekBar: SeekBar? = null
    private var seekBarValueTextView: TextView? = null
    private var exampleTextTextView: TextView? = null

    /**
     * Whether the SeekBar should respond to the left/right keys
     */
    private val isAdjustable: Boolean

    /**
     * Whether the SeekBarPreference should continuously save the SeekBar value while it is being dragged.
     */
    private val updatesContinuously: Boolean

    /**
     * The lower bound set on the [SeekBar].
     */
    private var min: Int

    /**
     * The upper bound set on the [SeekBar].
     */
    private var max: Int

    /**
     * The amount of increment change via each arrow key click. This value is derived from
     * user's specified increment value if it's not zero. Otherwise, the default value is picked
     * from the default mKeyProgressIncrement value in [android.widget.AbsSeekBar].
     * @return The amount of increment on the [SeekBar] performed after each user's arrow
     * key press
     */
    private var seekBarIncrement: Int = 0
        set(seekBarIncrement) {
            if (seekBarIncrement != field) {
                field = abs(seekBarIncrement).coerceAtMost(max - min)
                notifyChanged()
            }
        }

    /**
     * The current value of the [SeekBar] in [min]..[max]
     */
    internal var seekBarValue: Int = 0
        set(value) {
            val coercedValue = value.coerceIn(min..max)

            if (coercedValue != field) {
                field = coercedValue
                updateLabelValue(field)
                updateExampleTextValue(field)
                persistInt(field)
            }
        }

    /**
     * Whether the current [SeekBar] value is displayed to the user.
     */
    private val showSeekBarValue: Boolean

    init {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.SeekBarPreference, defStyleAttr, defStyleRes
        )

        min = a.getInt(R.styleable.SeekBarPreference_min, 0)
        max = a.getInt(R.styleable.SeekBarPreference_android_max, SEEK_BAR_MAX)
        seekBarIncrement = a.getInt(R.styleable.SeekBarPreference_seekBarIncrement, 0)
        isAdjustable = a.getBoolean(R.styleable.SeekBarPreference_adjustable, true)
        showSeekBarValue = a.getBoolean(R.styleable.SeekBarPreference_showSeekBarValue, false)
        updatesContinuously = a.getBoolean(
            R.styleable.SeekBarPreference_updatesContinuously,
            false
        )
        a.recycle()
    }

    /**
     * Listener reacting to the user pressing DPAD left/right keys if `adjustable` attribute is
     * set to true; it transfers the key presses to the [SeekBar] to be handled accordingly.
     */
    private val seekBarKeyListener = View.OnKeyListener { _, keyCode, event ->
        if (event.action != KeyEvent.ACTION_DOWN) {
            false
        } else if (!isAdjustable && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // Right or left keys are pressed when in non-adjustable mode; Skip the keys.
            false
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // We don't want to propagate the click keys down to the SeekBar view since it will
            // create the ripple effect for the thumb.
            false
        } else {
            seekBar!!.onKeyDown(keyCode, event)
        }
    }

    /**
     * Listener reacting to the [SeekBar] changing value by the user
     */
    private val mSeekBarChangeListener = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser && (updatesContinuously || !trackingTouch)) {
                syncValue(seekBar)
            } else {
                // We always want to update the text while the seekbar is being dragged
                updateLabelValue(progress + min)
                updateExampleTextValue(progress + min)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            trackingTouch = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            trackingTouch = false
            if (seekBar.progress + min != seekBarValue) {
                syncValue(seekBar)
            }
        }
    }

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
        seekBar = view.findViewById(R.id.seekbar) as SeekBar
        view.itemView.setOnKeyListener(seekBarKeyListener)
        val seekBar = seekBar!!
        exampleTextTextView = view.findViewById(R.id.sampleText) as TextView
        seekBarValueTextView = view.findViewById(R.id.seekbar_value) as TextView
        if (showSeekBarValue) {
            seekBarValueTextView!!.isVisible = true
        } else {
            seekBarValueTextView!!.isVisible = false
            seekBarValueTextView = null
        }

        seekBar.setOnSeekBarChangeListener(mSeekBarChangeListener)
        seekBar.max = max - min
        // If the increment is not zero, use that. Otherwise, use the default keyProgressIncrement
        // in SeekBar when it's zero. This default increment value is set by SeekBar
        // after setting max. That's why it's important to set keyProgressIncrement after
        // setting max since that can change the increment value.
        if (seekBarIncrement != 0) {
            seekBar.keyProgressIncrement = seekBarIncrement
        } else {
            seekBarIncrement = seekBar.keyProgressIncrement
        }

        seekBar.progress = seekBarValue - min
        updateExampleTextValue(seekBarValue)
        updateLabelValue(seekBarValue)
        seekBar.isEnabled = isEnabled
        seekBar.thumbOffset = seekBar.thumb.intrinsicWidth.div(2 * PI).roundToInt()
    }

    override fun onSetInitialValue(initialValue: Any?) {
        this.seekBarValue = getPersistedInt((initialValue as Int?) ?: 0)
        notifyChanged()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInt(index, 0)
    }

    /**
     * Persist the [SeekBar]'s SeekBar value if callChangeListener returns true, otherwise
     * set the [SeekBar]'s value to the stored value.
     */
    internal fun syncValue(seekBar: SeekBar) {
        val seekBarValue = min + seekBar.progress
        if (seekBarValue != this.seekBarValue) {
            if (callChangeListener(seekBarValue)) {
                this.seekBarValue = seekBarValue
            } else {
                seekBar.progress = this.seekBarValue - min
                updateLabelValue(this.seekBarValue)
                updateExampleTextValue(this.seekBarValue)
            }
        }
    }

    /**
     * Attempts to update the TextView label that displays the current value.
     *
     * @param labelValue the value to display next to the [SeekBar]
     */
    internal fun updateLabelValue(labelValue: Int) {
        seekBarValueTextView?.let {
            val value = labelValue * STEP_SIZE + MIN_VALUE
            val decimalValue = (value / DECIMAL_CONVERSION).toDouble()
            val percentage = NumberFormat.getPercentInstance().format(decimalValue)
            it.text = percentage
        }
        seekBar?.accessibilityDelegate = object :
            View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.rangeInfo =
                    AccessibilityNodeInfo.RangeInfo.obtain(
                        RANGE_TYPE_PERCENT,
                        MIN_VALUE.toFloat(),
                        SEEK_BAR_MAX.toFloat(),
                        convertCurrentValue(info.rangeInfo.current)
                    )
            }
        }
    }

    /**
     * Attempts to update the example TextView text with text scale size.
     *
     * @param textValue the value of text size
     */
    internal fun updateExampleTextValue(textValue: Int) {
        val value = textValue * STEP_SIZE + MIN_VALUE
        val decimal = value / DECIMAL_CONVERSION
        val textSize = TEXT_SIZE * decimal
        exampleTextTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        if (isPersistent) {
            // No need to save instance state since it's persistent
            return superState
        }

        // Save the instance state
        val myState = SavedState(superState)
        myState.mSeekBarValue = seekBarValue
        myState.min = min
        myState.max = max
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state?.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }

        val savedState = state as SavedState

        // Restore the instance state
        super.onRestoreInstanceState(savedState.superState)
        seekBarValue = savedState.mSeekBarValue
        min = savedState.min
        max = savedState.max
        notifyChanged()
    }

    /**
     * SavedState, a subclass of [Preference.BaseSavedState], will store the state of this preference.
     *
     *
     * It is important to always call through to super methods.
     */
    private class SavedState : BaseSavedState {

        internal var mSeekBarValue: Int = 0
        internal var min: Int = 0
        internal var max: Int = 0

        internal constructor(source: Parcel) : super(source) {

            // Restore the click counter
            mSeekBarValue = source.readInt()
            min = source.readInt()
            max = source.readInt()
        }

        internal constructor(superState: Parcelable) : super(superState)

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)

            // Save the click counter
            dest.writeInt(mSeekBarValue)
            dest.writeInt(min)
            dest.writeInt(max)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState {
                return SavedState(source)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    private fun convertCurrentValue(current: Float): Float {
        return current * STEP_SIZE + MIN_VALUE.toFloat()
    }

    companion object {
        private const val STEP_SIZE = 5
        private const val MIN_VALUE = 50
        private const val DECIMAL_CONVERSION = 100f
        private const val TEXT_SIZE = 16f
        private const val SEEK_BAR_MAX = 100
    }
}
