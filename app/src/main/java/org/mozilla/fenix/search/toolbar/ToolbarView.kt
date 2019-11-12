/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.search.toolbar

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import kotlinx.android.extensions.LayoutContainer
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.toolbar.ToolbarAutocompleteFeature
import mozilla.components.support.ktx.android.util.dpToPx
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.getColorFromAttr
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.search.SearchFragmentState

/**
 * Interface for the Toolbar Interactor. This interface is implemented by objects that want
 * to respond to user interaction on the [BrowserToolbarView]
 */
interface ToolbarInteractor {

    /**
     * Called when a user hits the return key while [BrowserToolbarView] has focus.
     * @param url the text inside the [BrowserToolbarView] when committed
     */
    fun onUrlCommitted(url: String)

    /**
     * Called when a user removes focus from the [BrowserToolbarView]
     */
    fun onEditingCanceled()

    /**
     * Called whenever the text inside the [BrowserToolbarView] changes
     * @param text the current text displayed by [BrowserToolbarView]
     */
    fun onTextChanged(text: String)
}

/**
 * View that contains and configures the BrowserToolbar to only be used in its editing mode.
 */
class ToolbarView(
    private val container: ViewGroup,
    private val interactor: ToolbarInteractor,
    private val historyStorage: HistoryStorage?,
    private val isPrivate: Boolean
) : LayoutContainer {

    override val containerView: View?
        get() = container

    val view: BrowserToolbar = if (container.context.settings().shouldUseBottomToolbar) {
        LayoutInflater.from(container.context)
            .inflate(R.layout.component_bottom_browser_toolbar, container, true)
            .findViewById(R.id.toolbar_bottom)
    } else {
        if (container.context.settings().shouldUseFixedToolbar) {
            LayoutInflater.from(container.context)
                .inflate(R.layout.component_browser_bottom_toolbar, container, true)
                .findViewById(R.id.toolbar_top_fixed)
        } else {
            LayoutInflater.from(container.context)
                .inflate(R.layout.component_browser_bottom_toolbar, container, true)
                .findViewById(R.id.toolbar_top)
        }
    }

    private var isInitialized = false

    init {
        view.apply {
            editMode()

            elevation = TOOLBAR_ELEVATION_IN_DP.dpToPx(resources.displayMetrics).toFloat()

            setOnUrlCommitListener {
                interactor.onUrlCommitted(it)
                false
            }

            background = null

            layoutParams.height = CoordinatorLayout.LayoutParams.MATCH_PARENT

            edit.hint = context.getString(R.string.search_hint)

            edit.colors = edit.colors.copy(
                text = container.context.getColorFromAttr(R.attr.primaryText),
                hint = container.context.getColorFromAttr(R.attr.secondaryText),
                suggestionBackground = ContextCompat.getColor(
                    container.context,
                    R.color.suggestion_highlight_color
                ),
                clear = container.context.getColorFromAttr(R.attr.primaryText)
            )

            edit.setUrlBackground(
                ContextCompat.getDrawable(container.context, R.drawable.search_url_background))

            private = isPrivate

            setOnEditListener(object : mozilla.components.concept.toolbar.Toolbar.OnEditListener {
                override fun onCancelEditing(): Boolean {
                    interactor.onEditingCanceled()
                    // We need to return false to not show display mode
                    return false
                }
                override fun onTextChanged(text: String) {
                    url = text
                    this@ToolbarView.interactor.onTextChanged(text)
                }
            })
        }

        ToolbarAutocompleteFeature(view).apply {
            addDomainProvider(ShippedDomainsProvider().also { it.initialize(view.context) })
            historyStorage?.also(::addHistoryStorageProvider)
        }
    }

    fun update(searchState: SearchFragmentState) {
        if (!isInitialized) {
            view.url = searchState.pastedText ?: searchState.query

            /* Only set the search terms if pasted text is null so that the search term doesn't
            overwrite pastedText when view enters `editMode` */
            if (searchState.pastedText.isNullOrEmpty()) {
                view.setSearchTerms(searchState.session?.searchTerms.orEmpty())
            }

            view.editMode()
            isInitialized = true
        }

        val iconSize = container.resources.getDimensionPixelSize(R.dimen.preference_icon_drawable_size)

        val scaledIcon = Bitmap.createScaledBitmap(
            searchState.searchEngineSource.searchEngine.icon,
            iconSize,
            iconSize,
            true)

        val icon = BitmapDrawable(container.resources, scaledIcon)

        view.edit.setIcon(icon, searchState.searchEngineSource.searchEngine.name)
    }

    companion object {
        private const val TOOLBAR_ELEVATION_IN_DP = 16
    }
}
