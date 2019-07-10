package org.mozilla.fenix.search

import android.content.Context
import androidx.navigation.NavController
import mozilla.components.browser.search.SearchEngine
import mozilla.components.support.ktx.kotlin.isUrl
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.search.awesomebar.AwesomeBarInteractor
import org.mozilla.fenix.search.toolbar.ToolbarInteractor

class SearchInteractor(
    private val context: Context,
    private val navController: NavController,
    private val store: SearchStore
) : AwesomeBarInteractor, ToolbarInteractor {

    override fun onUrlCommitted(url: String) {
        if (url.isNotBlank()) {
            (context as HomeActivity).openToBrowserAndLoad(
                searchTermOrURL = url,
                newTab = store.state.session == null,
                from = BrowserDirection.FromSearch,
                engine = store.state.searchEngineSource.searchEngine
            )

            val event = if (url.isUrl()) {
                Event.EnteredUrl(false)
            } else {
                createSearchEvent(store.state.searchEngineSource.searchEngine, false)
            }

            context.components.analytics.metrics.track(event)
        }
    }

    override fun onEditingCanceled() {
        navController.navigateUp()
    }

    override fun onTextChanged(text: String) {
        store.dispatch(SearchAction.UpdateQuery(text))
    }

    override fun onUrlTapped(url: String) {
        (context as HomeActivity).openToBrowserAndLoad(
            searchTermOrURL = url,
            newTab = store.state.session == null,
            from = BrowserDirection.FromSearch
        )

        context.components.analytics.metrics.track(Event.EnteredUrl(false))
    }

    override fun onSearchTermsTapped(searchTerms: String) {
        (context as HomeActivity).openToBrowserAndLoad(
            searchTermOrURL = searchTerms,
            newTab = store.state.session == null,
            from = BrowserDirection.FromSearch,
            engine = store.state.searchEngineSource.searchEngine,
            forceSearch = true
        )

        val event = createSearchEvent(store.state.searchEngineSource.searchEngine, true)
        context.components.analytics.metrics.track(event)
    }

    override fun onSearchShortcutEngineSelected(searchEngine: SearchEngine) {
        store.dispatch(SearchAction.SearchShortcutEngineSelected(searchEngine))
        context.components.analytics.metrics.track(Event.SearchShortcutSelected(searchEngine.name))
    }

    override fun onClickSearchEngineSettings() {
        val directions = SearchFragmentDirections.actionSearchFragmentToSearchEngineFragment()
        navController.navigate(directions)
    }

    private fun createSearchEvent(engine: SearchEngine, isSuggestion: Boolean): Event.PerformedSearch {
        val isShortcut = engine != context.components.search.searchEngineManager.defaultSearchEngine

        val engineSource =
            if (isShortcut) Event.PerformedSearch.EngineSource.Shortcut(engine)
            else Event.PerformedSearch.EngineSource.Default(engine)

        val source =
            if (isSuggestion) Event.PerformedSearch.EventSource.Suggestion(engineSource)
            else Event.PerformedSearch.EventSource.Action(engineSource)

        return Event.PerformedSearch(source)
    }
}
