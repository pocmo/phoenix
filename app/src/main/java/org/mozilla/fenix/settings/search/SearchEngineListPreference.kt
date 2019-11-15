/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.search

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.navigation.Navigation
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kotlinx.android.synthetic.main.search_engine_radio_button.view.*
import mozilla.components.browser.search.SearchEngine
import mozilla.components.browser.search.provider.SearchEngineList
import org.mozilla.fenix.R
import org.mozilla.fenix.components.searchengine.CustomSearchEngineStore
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings

abstract class SearchEngineListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr), CompoundButton.OnCheckedChangeListener {

    protected lateinit var searchEngineList: SearchEngineList
    protected var searchEngineGroup: RadioGroup? = null

    protected abstract val itemResId: Int

    init {
        layoutResource = R.layout.preference_search_engine_chooser
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        searchEngineGroup = holder!!.itemView.findViewById(R.id.search_engine_group)
        reload(searchEngineGroup!!.context)
    }

    fun reload(context: Context) {
        searchEngineList = context.components.search.provider.installedSearchEngines(context)
        refreshSearchEngineViews(context)
    }

    protected abstract fun onSearchEngineSelected(searchEngine: SearchEngine)
    protected abstract fun updateDefaultItem(defaultButton: CompoundButton)

    private fun refreshSearchEngineViews(context: Context) {
        if (searchEngineGroup == null) {
            // We want to refresh the search engine list of this preference in onResume,
            // but the first time this preference is created onResume is called before onCreateView
            // so searchEngineGroup is not set yet.
            return
        }

        val selectedEngine = context.components.search.provider.getDefaultEngine(context).identifier

        searchEngineGroup!!.removeAllViews()

        val layoutInflater = LayoutInflater.from(context)
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val setupSearchEngineItem: (Int, SearchEngine) -> Unit = { index, engine ->
            val engineId = engine.identifier
            val engineItem = makeButtonFromSearchEngine(
                engine = engine,
                layoutInflater = layoutInflater,
                res = context.resources,
                allowDelete = searchEngineList.list.size > 1)

            engineItem.id = index + (searchEngineList.default?.let { 1 } ?: 0)
            engineItem.tag = engineId
            if (engineId == selectedEngine) {
                updateDefaultItem(engineItem.radio_button)
            }
            searchEngineGroup!!.addView(engineItem, layoutParams)
        }

        searchEngineList.default?.apply {
            setupSearchEngineItem(0, this)
        }

        searchEngineList.list
            .filter { it.identifier != searchEngineList.default?.identifier }
            .forEachIndexed(setupSearchEngineItem)
    }

    private fun makeButtonFromSearchEngine(
        engine: SearchEngine,
        layoutInflater: LayoutInflater,
        res: Resources,
        allowDelete: Boolean
    ): View {
        val wrapper = layoutInflater.inflate(itemResId, null) as ConstraintLayout
        wrapper.setOnClickListener { wrapper.radio_button.isChecked = true }
        wrapper.radio_button.setOnCheckedChangeListener(this)
        wrapper.engine_text.text = engine.name
        wrapper.overflow_menu.isVisible = allowDelete
        wrapper.overflow_menu.setOnClickListener {
            val isCustomSearchEngine = CustomSearchEngineStore.isCustomSearchEngine(context, engine.identifier)
            SearchEngineMenu(
                context = context,
                isCustomSearchEngine = isCustomSearchEngine,
                onItemTapped = {
                    when (it) {
                        is SearchEngineMenu.Item.Edit -> editCustomSearchEngine(engine)
                        is SearchEngineMenu.Item.Delete -> deleteSearchEngine(context, engine)
                    }
                }
            ).menuBuilder.build(context).show(wrapper.overflow_menu)
        }
        val iconSize = res.getDimension(R.dimen.preference_icon_drawable_size).toInt()
        val engineIcon = BitmapDrawable(res, engine.icon)
        engineIcon.setBounds(0, 0, iconSize, iconSize)
        wrapper.engine_icon.setImageDrawable(engineIcon)
        return wrapper
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        searchEngineList.list.forEach { engine ->
            val wrapper: ConstraintLayout = searchEngineGroup?.findViewWithTag(engine.identifier) ?: return

            when (wrapper.radio_button == buttonView) {
                true -> onSearchEngineSelected(engine)
                false -> {
                    wrapper.radio_button.setOnCheckedChangeListener(null)
                    wrapper.radio_button.isChecked = false
                    wrapper.radio_button.setOnCheckedChangeListener(this)
                }
            }
        }
    }

    private fun editCustomSearchEngine(engine: SearchEngine) {
        val directions = SearchEngineFragmentDirections
            .actionSearchEngineFragmentToEditCustomSearchEngineFragment(engine.identifier)
        Navigation.findNavController(searchEngineGroup!!).navigate(directions)
    }

    private fun deleteSearchEngine(context: Context, engine: SearchEngine) {
        val defaultEngine = context.components.search.provider.getDefaultEngine(context)
        context.components.search.provider.uninstallSearchEngine(context, engine)

        if (engine == defaultEngine) {
            context.settings().defaultSearchEngineName = context
                .components
                .search
                .provider
                .getDefaultEngine(context)
                .name
        }

        reload(context)
    }
}
