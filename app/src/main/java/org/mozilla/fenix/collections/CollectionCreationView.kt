/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.collections

import android.os.Handler
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionManager
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.component_collection_creation.*
import kotlinx.android.synthetic.main.component_collection_creation.view.*
import mozilla.components.support.ktx.android.view.hideKeyboard
import mozilla.components.support.ktx.android.view.showKeyboard
import org.mozilla.fenix.R
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.increaseTapArea
import org.mozilla.fenix.ext.urlToTrimmedHost
import org.mozilla.fenix.home.sessioncontrol.Tab
import org.mozilla.fenix.home.sessioncontrol.TabCollection

/**
 * TODO
 */
interface CollectionViewInteractor {
    /**
     * todo
     */
    fun saveCollectionName(tabs: List<Tab>, name: String)
    /**
     * todo
     */
    fun renameCollection(collection: TabCollection, name: String)
    /**
     * todo
     */
    fun backPressed(fromStep: SaveCollectionStep)
    /**
     * todo
     */
    fun selectAllTapped()
    /**
     * todo
     */
    fun deselectAllTapped()
    /**
     * todo
     */
    fun close()
    /**
     * todo
     */
    fun selectCollection(collection: TabCollection, tabs: List<Tab>)
    /**
     * todo
     */
    fun saveTabsToCollection(tabs: List<Tab>)
    /**
     * todo
     */
    fun addNewCollection()
}

@SuppressWarnings("LargeClass")
class CollectionCreationView(
    override val containerView: ViewGroup,
    private val interactor: CollectionViewInteractor
) : LayoutContainer {
    val view: View = LayoutInflater.from(containerView.context) // TODO View type?
        .inflate(R.layout.component_collection_creation, containerView, true)

    private val collectionCreationTabListAdapter = CollectionCreationTabListAdapter(interactor)
    private val collectionSaveListAdapter = SaveCollectionListAdapter(interactor)
    private val selectTabsConstraints = ConstraintSet()
    private val selectCollectionConstraints = ConstraintSet()
    private val nameCollectionConstraints = ConstraintSet()
    private val transition = AutoTransition()

    private var selectedCollection: TabCollection? = null
    private var selectedTabs: Set<Tab> = setOf()
    var step: SaveCollectionStep = SaveCollectionStep.SelectTabs
        private set

    init {
        transition.duration = TRANSITION_DURATION

        selectTabsConstraints.clone(collection_constraint_layout)
        selectCollectionConstraints.clone(
            view.context,
            R.layout.component_collection_creation_select_collection
        )
        nameCollectionConstraints.clone(
            view.context,
            R.layout.component_collection_creation_name_collection
        )

        view.bottom_bar_icon_button.apply {
            increaseTapArea(increaseButtonByDps)
        }

        view.name_collection_edittext.filters += InputFilter.LengthFilter(COLLECTION_NAME_MAX_LENGTH)
        view.name_collection_edittext.setOnEditorActionListener { view, actionId, _ ->
            val text = view.text.toString()
            if (actionId == EditorInfo.IME_ACTION_DONE && text.isNotBlank()) {
                when (step) {
                    SaveCollectionStep.NameCollection -> 
                        interactor.saveCollectionName(selectedTabs.toList(), text)
                    SaveCollectionStep.RenameCollection -> 
                        selectedCollection?.let { interactor.renameCollection(it, text) }
                    // TODO if shouldn't be possible to reach the other states, let's log that to telemetry
                    else -> { /* noop (TODO else branch will hopefully be replaced with above mentioned telemetry calls) */ }
                }
            }
            false
        }

        view.tab_list.run {
            adapter = collectionCreationTabListAdapter
            itemAnimator = null
            layoutManager = LinearLayoutManager(containerView.context, RecyclerView.VERTICAL, true)
        }

        view.collections_list.run {
            adapter = collectionSaveListAdapter
            layoutManager = LinearLayoutManager(containerView.context, RecyclerView.VERTICAL, true)
        }
    }
    
    fun update(state: CollectionCreationState) {
        
        fun cacheState() {
            step = state.saveCollectionStep
            selectedTabs = state.selectedTabs
            selectedCollection = state.selectedTabCollection
        }
        
        fun updateForSelectTabs() {
            view.context.components.analytics.metrics.track(Event.CollectionTabSelectOpened)

            view.tab_list.isClickable = true

            back_button.setOnClickListener {
                interactor.backPressed(SaveCollectionStep.SelectTabs)
            }
            val allSelected = state.selectedTabs.size == state.tabs.size
            select_all_button.text =
                if (allSelected) view.context.getString(R.string.create_collection_deselect_all)
                else view.context.getString(R.string.create_collection_select_all)

            view.select_all_button.setOnClickListener {
                if (allSelected) interactor.deselectAllTapped()
                else interactor.selectAllTapped()
            }

            view.bottom_button_bar_layout.setOnClickListener(null)
            view.bottom_button_bar_layout.isClickable = false

            val drawable = view.context.getDrawable(R.drawable.ic_close)
            drawable?.setTint(ContextCompat.getColor(view.context, R.color.photonWhite))
            view.bottom_bar_icon_button.setImageDrawable(drawable)
            view.bottom_bar_icon_button.contentDescription =
                view.context.getString(R.string.create_collection_close)
            view.bottom_bar_icon_button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            view.bottom_bar_icon_button.setOnClickListener {
                interactor.close()
            }

            TransitionManager.beginDelayedTransition(
                view.collection_constraint_layout,
                transition
            )
            val constraint = selectTabsConstraints
            constraint.applyTo(view.collection_constraint_layout)

            collectionCreationTabListAdapter.updateData(state.tabs, state.selectedTabs)

            back_button.text = view.context.getString(R.string.create_collection_select_tabs)

            val selectTabsText = if (state.selectedTabs.isEmpty()) {
                view.context.getString(R.string.create_collection_save_to_collection_empty)
            } else {
                view.context.getString(
                    if (state.selectedTabs.size == 1)
                        R.string.create_collection_save_to_collection_tab_selected else
                        R.string.create_collection_save_to_collection_tabs_selected,
                    state.selectedTabs.size
                )
            }

            view.bottom_bar_text.text = selectTabsText

            save_button.setOnClickListener { _ ->
                if (selectedCollection != null) {
                    interactor.selectCollection(
                        collection = selectedCollection!!,
                        tabs = state.selectedTabs.toList()
                    )
                } else {
                    interactor.saveTabsToCollection(tabs = selectedTabs.toList())
                }
            }

            save_button.visibility = if (state.selectedTabs.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
        fun updateForSelectCollection() {
            view.tab_list.isClickable = false

            save_button.visibility = View.GONE

            view.bottom_bar_text.text =
                view.context.getString(R.string.create_collection_add_new_collection)

            val drawable = view.context.getDrawable(R.drawable.ic_new)
            drawable?.setTint(ContextCompat.getColor(view.context, R.color.photonWhite))
            view.bottom_bar_icon_button.setImageDrawable(drawable)
            view.bottom_bar_icon_button.contentDescription = null
            view.bottom_bar_icon_button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            view.bottom_button_bar_layout.isClickable = true
            view.bottom_button_bar_layout.setOnClickListener {
                interactor.addNewCollection()
            }

            back_button.setOnClickListener {
                interactor.backPressed(SaveCollectionStep.SelectCollection)
            }
            TransitionManager.beginDelayedTransition(
                view.collection_constraint_layout,
                transition
            )
            val constraint = selectCollectionConstraints
            constraint.applyTo(view.collection_constraint_layout)
            back_button.text =
                view.context.getString(R.string.create_collection_select_collection)
        }
        fun updateForNameCollection() {
            view.tab_list.isClickable = false

            collectionCreationTabListAdapter.updateData(state.selectedTabs.toList(), state.selectedTabs, true)
            back_button.setOnClickListener {
                name_collection_edittext.hideKeyboard()
                val handler = Handler()
                handler.postDelayed({
                    interactor.backPressed(SaveCollectionStep.NameCollection)
                }, TRANSITION_DURATION)
            }
            transition.addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { /* noop */ }

                override fun onTransitionEnd(transition: Transition) {
                    view.name_collection_edittext.showKeyboard()
                    transition.removeListener(this)
                }

                override fun onTransitionCancel(transition: Transition) { /* noop */ }
                override fun onTransitionPause(transition: Transition) { /* noop */ }
                override fun onTransitionResume(transition: Transition) { /* noop */ }
            })
            TransitionManager.beginDelayedTransition(
                view.collection_constraint_layout,
                transition
            )
            val constraint = nameCollectionConstraints
            constraint.applyTo(view.collection_constraint_layout)
            name_collection_edittext.setText(
                view.context.getString(
                    R.string.create_collection_default_name,
                    state.tabCollections.size + 1
                )
            )
            name_collection_edittext.setSelection(0, name_collection_edittext.text.length)
            back_button.text =
                view.context.getString(R.string.create_collection_name_collection)
        }
        fun updateForRenameCollection() {
            view.tab_list.isClickable = false

            state.selectedTabCollection?.let { tabCollection ->
                tabCollection.tabs.map { tab ->
                    Tab(
                        tab.id.toString(),
                        tab.url,
                        tab.url.urlToTrimmedHost(view.context),
                        tab.title
                    )
                }.let { tabs ->
                    collectionCreationTabListAdapter.updateData(tabs, tabs.toSet(), true)
                }
            }
            val constraint = nameCollectionConstraints
            constraint.applyTo(view.collection_constraint_layout)
            name_collection_edittext.setText(state.selectedTabCollection?.title)
            name_collection_edittext.setSelection(0, name_collection_edittext.text.length)

            back_button.text =
                view.context.getString(R.string.collection_rename)
            back_button.setOnClickListener {
                name_collection_edittext.hideKeyboard()
                val handler = Handler()
                handler.postDelayed({
                    interactor.backPressed(SaveCollectionStep.RenameCollection)
                }, TRANSITION_DURATION)
            }
            transition.addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { /* noop */ }

                override fun onTransitionEnd(transition: Transition) {
                    view.name_collection_edittext.showKeyboard()
                    transition.removeListener(this)
                }

                override fun onTransitionCancel(transition: Transition) { /* noop */ }
                override fun onTransitionPause(transition: Transition) { /* noop */ }
                override fun onTransitionResume(transition: Transition) { /* noop */ }
            })
            TransitionManager.beginDelayedTransition(
                view.collection_constraint_layout,
                transition
            )
        }
        
        cacheState()
        
        when (step) {
            SaveCollectionStep.SelectTabs -> updateForSelectTabs()
            SaveCollectionStep.SelectCollection -> updateForSelectCollection()
            SaveCollectionStep.NameCollection -> updateForNameCollection()
            SaveCollectionStep.RenameCollection -> updateForRenameCollection()
        }

        collectionSaveListAdapter.updateData(state.tabCollections, state.selectedTabs)
    }

    fun onResumed() {
        if (step == SaveCollectionStep.NameCollection || step == SaveCollectionStep.RenameCollection) {
            view.name_collection_edittext.showKeyboard()
        }
    }

    fun onKey(keyCode: Int, event: KeyEvent?): Boolean {
        return if (event?.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            interactor.backPressed(step)
            true
        } else {
            false
        }
    }

    companion object {
        private const val TRANSITION_DURATION = 200L
        private const val increaseButtonByDps = 16
        private const val COLLECTION_NAME_MAX_LENGTH = 128
    }
}
