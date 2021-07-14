/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabstray.browser

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import mozilla.components.concept.tabstray.Tab
import mozilla.components.concept.tabstray.Tabs
import mozilla.components.concept.tabstray.TabsTray
import mozilla.components.feature.tabs.tabstray.TabsFeature
import mozilla.components.support.base.observer.Observable
import mozilla.components.support.base.observer.ObserverRegistry
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.tabstray.TabsTrayInteractor
import org.mozilla.fenix.tabstray.TabsTrayStore
import org.mozilla.fenix.tabstray.ext.filterFromConfig
import java.util.concurrent.TimeUnit

class BrowserTrayList @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    /**
     * The browser tab types we would want to show.
     */
    enum class BrowserTabType { NORMAL, PRIVATE }

    lateinit var browserTabType: BrowserTabType
    lateinit var interactor: TabsTrayInteractor
    lateinit var tabsTrayStore: TabsTrayStore

    private val requireTabSeparator by lazy {
        val concatAdapter = adapter as ConcatAdapter
        ActiveTabSeparator { active, inactive ->
            concatAdapter.browserAdapter.updateTabs(active)
            concatAdapter.inactiveTabsAdapter.submitList(inactive)
        }
    }

    private val tabsFeature by lazy {
        // NB: The use cases here are duplicated because there isn't a nicer
        // way to share them without a better dependency injection solution.
        val selectTabUseCase = SelectTabUseCaseWrapper(
            context.components.analytics.metrics,
            context.components.useCases.tabsUseCases.selectTab
        ) {
            interactor.onBrowserTabSelected()
        }

        val removeTabUseCase = RemoveTabUseCaseWrapper(
            context.components.analytics.metrics
        ) { sessionId ->
            interactor.onDeleteTab(sessionId)
        }

        val tabsAdapter = if (browserTabType == BrowserTabType.NORMAL) {
            requireTabSeparator
        } else {
            adapter as TabsAdapter
        }

        TabsFeature(
            tabsAdapter,
            context.components.core.store,
            selectTabUseCase,
            removeTabUseCase,
            { it.filterFromConfig(browserTabType) },
            { }
        )
    }

    private val swipeToDelete by lazy {
        SwipeToDeleteBinding(tabsTrayStore)
    }

    private val touchHelper by lazy {
        TabsTouchHelper(
            observable = if (browserTabType == BrowserTabType.NORMAL) requireTabSeparator else adapter as TabsAdapter,
            onViewHolderTouched = { swipeToDelete.isSwipeable },
            onViewHolderDraw = { context.components.settings.gridTabView.not() }
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        tabsFeature.start()
        swipeToDelete.start()

        adapter?.onAttachedToRecyclerView(this)

        touchHelper.attachToRecyclerView(this)
    }

    @VisibleForTesting
    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        tabsFeature.stop()
        swipeToDelete.stop()

        // Notify the adapter that it is released from the view preemptively.
        adapter?.onDetachedFromRecyclerView(this)

        touchHelper.attachToRecyclerView(null)
    }
}

class ActiveTabSeparator(
    delegate: ObserverRegistry<TabsTray.Observer> = ObserverRegistry(),
    private val maxInactiveThing: Long = TimeUnit.DAYS.toMillis(4),
    private val onTabSeparated: (Tabs, List<Tab>) -> Unit
) : TabsTray, Observable<TabsTray.Observer> by delegate {

    override fun updateTabs(tabs: Tabs) {
        val now = System.currentTimeMillis()
        val activeTabs = tabs.list.filter { now - it.lastAccess <= maxInactiveThing }
        val inactiveTabs = tabs.list - activeTabs
        val tabTabs = Tabs(activeTabs, tabs.selectedIndex)

        onTabSeparated.invoke(tabTabs, inactiveTabs)
    }

    override fun isTabSelected(tabs: Tabs, position: Int) = false
    override fun onTabsChanged(position: Int, count: Int) = Unit
    override fun onTabsInserted(position: Int, count: Int) = Unit
    override fun onTabsMoved(fromPosition: Int, toPosition: Int) = Unit
    override fun onTabsRemoved(position: Int, count: Int) = Unit
}
