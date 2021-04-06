/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.top.sites.DefaultTopSitesStorage
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.BrowserAnimator
import org.mozilla.fenix.browser.BrowserFragmentDirections
import org.mozilla.fenix.browser.readermode.ReaderModeController
import org.mozilla.fenix.collections.SaveCollectionStep
import org.mozilla.fenix.components.FenixSnackbar
import org.mozilla.fenix.components.TabCollectionStorage
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.components.toolbar.ToolbarMenu.Item
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.getRootView
import org.mozilla.fenix.ext.navigateBlockingForAsyncNavGraph
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.navigateSafe
import org.mozilla.fenix.ext.openSetDefaultBrowserOption
import org.mozilla.fenix.settings.deletebrowsingdata.deleteAndQuit
import org.mozilla.fenix.utils.Do
import org.mozilla.fenix.utils.Settings

/**
 * An interface that handles events from the BrowserToolbar menu, triggered by the Interactor
 */
interface BrowserToolbarMenuController {
    fun handleToolbarItemInteraction(item: Item)
}

@Suppress("LargeClass", "ForbiddenComment")
class DefaultBrowserToolbarMenuController(
    private val store: BrowserStore,
    private val activity: HomeActivity,
    private val navController: NavController,
    private val metrics: MetricController,
    private val settings: Settings,
    private val readerModeController: ReaderModeController,
    private val sessionFeature: ViewBoundFeatureWrapper<SessionFeature>,
    private val findInPageLauncher: () -> Unit,
    private val browserAnimator: BrowserAnimator,
    private val swipeRefresh: SwipeRefreshLayout,
    private val customTabSessionId: String?,
    private val openInFenixIntent: Intent,
    private val bookmarkTapped: (String, String) -> Unit,
    private val scope: CoroutineScope,
    private val tabCollectionStorage: TabCollectionStorage,
    private val topSitesStorage: DefaultTopSitesStorage,
    private val browserStore: BrowserStore
) : BrowserToolbarMenuController {

    private val currentSession
        get() = store.state.findCustomTabOrSelectedTab(customTabSessionId)

    // We hold onto a reference of the inner scope so that we can override this with the
    // TestCoroutineScope to ensure sequential execution. If we didn't have this, our tests
    // would fail intermittently due to the async nature of coroutine scheduling.
    @VisibleForTesting
    internal var ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Suppress("ComplexMethod", "LongMethod")
    override fun handleToolbarItemInteraction(item: Item) {
        val sessionUseCases = activity.components.useCases.sessionUseCases
        val customTabUseCases = activity.components.useCases.customTabsUseCases
        trackToolbarItemInteraction(item)

        Do exhaustive when (item) {
            is Item.InstallPwaToHomeScreen -> {
                settings.installPwaOpened = true
                MainScope().launch {
                    with(activity.components.useCases.webAppUseCases) {
                        if (isInstallable()) {
                            addToHomescreen()
                        } else {
                            val directions =
                                BrowserFragmentDirections.actionBrowserFragmentToCreateShortcutFragment()
                            navController.navigateSafe(R.id.browserFragment, directions)
                        }
                    }
                }
            }
            is Item.OpenInFenix -> {
                customTabSessionId?.let {
                    // Stop the SessionFeature from updating the EngineView and let it release the session
                    // from the EngineView so that it can immediately be rendered by a different view once
                    // we switch to the actual browser.
                    sessionFeature.get()?.release()

                    // Turn this Session into a regular tab and then select it
                    customTabUseCases.migrate(customTabSessionId, select = true)

                    // Switch to the actual browser which should now display our new selected session
                    activity.startActivity(openInFenixIntent.apply {
                        // We never want to launch the browser in the same task as the external app
                        // activity. So we force a new task here. IntentReceiverActivity will do the
                        // right thing and take care of routing to an already existing browser and avoid
                        // cloning a new one.
                        flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                    })

                    // Close this activity (and the task) since it is no longer displaying any session
                    activity.finishAndRemoveTask()
                }
            }
            is Item.OpenInApp -> {
                settings.openInAppOpened = true

                val appLinksUseCases = activity.components.useCases.appLinksUseCases
                val getRedirect = appLinksUseCases.appLinkRedirect
                currentSession?.let {
                    val redirect = getRedirect.invoke(it.content.url)
                    redirect.appIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appLinksUseCases.openAppLink.invoke(redirect.appIntent)
                }
            }
            is Item.Quit -> {
                // We need to show the snackbar while the browsing data is deleting (if "Delete
                // browsing data on quit" is activated). After the deletion is over, the snackbar
                // is dismissed.
                val snackbar: FenixSnackbar? = activity.getRootView()?.let { v ->
                    FenixSnackbar.make(
                        view = v,
                        duration = Snackbar.LENGTH_LONG,
                        isDisplayedWithBrowserToolbar = true
                    )
                        .setText(v.context.getString(R.string.deleting_browsing_data_in_progress))
                }

                deleteAndQuit(activity, scope, snackbar)
            }
            is Item.CustomizeReaderView -> {
                readerModeController.showControls()
                metrics.track(Event.ReaderModeAppearanceOpened)
            }
            is Item.Back -> {
                if (item.viewHistory) {
                    navController.navigateBlockingForAsyncNavGraph(
                        BrowserFragmentDirections.actionGlobalTabHistoryDialogFragment(
                            activeSessionId = customTabSessionId
                        )
                    )
                } else {
                    currentSession?.let {
                        sessionUseCases.goBack.invoke(it.id)
                    }
                }
            }
            is Item.Forward -> {
                if (item.viewHistory) {
                    navController.navigateBlockingForAsyncNavGraph(
                        BrowserFragmentDirections.actionGlobalTabHistoryDialogFragment(
                            activeSessionId = customTabSessionId
                        )
                    )
                } else {
                    currentSession?.let {
                        sessionUseCases.goForward.invoke(it.id)
                    }
                }
            }
            is Item.Reload -> {
                val flags = if (item.bypassCache) {
                    LoadUrlFlags.select(LoadUrlFlags.BYPASS_CACHE)
                } else {
                    LoadUrlFlags.none()
                }

                currentSession?.let {
                    sessionUseCases.reload.invoke(it.id, flags = flags)
                }
            }
            is Item.Stop -> {
                currentSession?.let {
                    sessionUseCases.stopLoading.invoke(it.id)
                }
            }
            is Item.Share -> {
                val directions = NavGraphDirections.actionGlobalShareFragment(
                    data = arrayOf(
                        ShareData(
                            url = getProperUrl(currentSession),
                            title = currentSession?.content?.title
                        )
                    ),
                    showPage = true
                )
                navController.navigateBlockingForAsyncNavGraph(directions)
            }
            is Item.Settings -> browserAnimator.captureEngineViewAndDrawStatically {
                val directions = BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
                navController.nav(R.id.browserFragment, directions)
            }
            is Item.SyncedTabs -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionBrowserFragmentToSyncedTabsFragment()
                )
            }
            is ToolbarMenu.Item.SyncAccount -> {
                val directions = if (item.signedIn) {
                    BrowserFragmentDirections.actionGlobalAccountSettingsFragment()
                } else {
                    BrowserFragmentDirections.actionGlobalTurnOnSync()
                }
                browserAnimator.captureEngineViewAndDrawStatically {
                    navController.nav(
                        R.id.browserFragment,
                        directions
                    )
                }
            }
            is Item.RequestDesktop -> {
                currentSession?.let {
                    sessionUseCases.requestDesktopSite.invoke(
                        item.isChecked,
                        it.id
                    )
                }
            }
            is Item.AddToTopSites -> {
                scope.launch {
                    val context = swipeRefresh.context
                    val numPinnedSites =
                        topSitesStorage.cachedTopSites.filter { it.type != TopSite.Type.FRECENT }.size

                    if (numPinnedSites >= settings.topSitesMaxLimit) {
                        AlertDialog.Builder(swipeRefresh.context).apply {
                            setTitle(R.string.top_sites_max_limit_title)
                            setMessage(R.string.top_sites_max_limit_content_2)
                            setPositiveButton(R.string.top_sites_max_limit_confirmation_button) { dialog, _ ->
                                dialog.dismiss()
                            }
                            create()
                        }.show()
                    } else {
                        ioScope.launch {
                            currentSession?.let {
                                with(activity.components.useCases.topSitesUseCase) {
                                    addPinnedSites(it.content.title, it.content.url)
                                }
                            }
                        }.join()

                        FenixSnackbar.make(
                            view = swipeRefresh,
                            duration = Snackbar.LENGTH_SHORT,
                            isDisplayedWithBrowserToolbar = true
                        )
                            .setText(
                                context.getString(R.string.snackbar_added_to_top_sites)
                            )
                            .show()
                    }
                }
            }
            is Item.AddToHomeScreen -> {
                settings.installPwaOpened = true
                MainScope().launch {
                    with(activity.components.useCases.webAppUseCases) {
                        if (isInstallable()) {
                            addToHomescreen()
                        } else {
                            val directions =
                                BrowserFragmentDirections.actionBrowserFragmentToCreateShortcutFragment()
                            navController.navigateSafe(R.id.browserFragment, directions)
                        }
                    }
                }
            }
            is Item.FindInPage -> {
                findInPageLauncher()
                metrics.track(Event.FindInPageOpened)
            }
            is Item.AddonsManager -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalAddonsManagementFragment()
                )
            }
            is Item.SaveToCollection -> {
                metrics
                    .track(Event.CollectionSaveButtonPressed(TELEMETRY_BROWSER_IDENTIFIER))

                currentSession?.let { currentSession ->
                    val directions =
                        BrowserFragmentDirections.actionGlobalCollectionCreationFragment(
                            tabIds = arrayOf(currentSession.id),
                            selectedTabIds = arrayOf(currentSession.id),
                            saveCollectionStep = if (tabCollectionStorage.cachedTabCollections.isEmpty()) {
                                SaveCollectionStep.NameCollection
                            } else {
                                SaveCollectionStep.SelectCollection
                            }
                        )
                    navController.nav(R.id.browserFragment, directions)
                }
            }
            is Item.Bookmark -> {
                store.state.selectedTab?.let {
                    getProperUrl(it)?.let { url -> bookmarkTapped(url, it.content.title) }
                }
            }
            is Item.Bookmarks -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalBookmarkFragment(BookmarkRoot.Mobile.id)
                )
            }
            is Item.History -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalHistoryFragment()
                )
            }
            is Item.Downloads -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalDownloadsFragment()
                )
            }
            is Item.NewTab -> {
                navController.navigateBlockingForAsyncNavGraph(
                    BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)
                )
            }
            is Item.SetDefaultBrowser -> {
                metrics.track(Event.SetDefaultBrowserToolbarMenuClicked)
                activity.openSetDefaultBrowserOption()
            }
        }
    }

    private fun getProperUrl(currentSession: SessionState?): String? {
        return currentSession?.id?.let {
            val currentTab = browserStore.state.findTab(it)
            if (currentTab?.readerState?.active == true) {
                currentTab.readerState.activeUrl
            } else {
                currentSession.content.url
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun trackToolbarItemInteraction(item: Item) {
        val eventItem = when (item) {
            is Item.OpenInFenix -> Event.BrowserMenuItemTapped.Item.OPEN_IN_FENIX
            is Item.InstallPwaToHomeScreen ->
                Event.BrowserMenuItemTapped.Item.ADD_TO_HOMESCREEN
            is Item.Quit -> Event.BrowserMenuItemTapped.Item.QUIT
            is Item.OpenInApp -> Event.BrowserMenuItemTapped.Item.OPEN_IN_APP
            is Item.CustomizeReaderView ->
                Event.BrowserMenuItemTapped.Item.READER_MODE_APPEARANCE
            is Item.Back -> Event.BrowserMenuItemTapped.Item.BACK
            is Item.Forward -> Event.BrowserMenuItemTapped.Item.FORWARD
            is Item.Reload -> Event.BrowserMenuItemTapped.Item.RELOAD
            is Item.Stop -> Event.BrowserMenuItemTapped.Item.STOP
            is Item.Share -> Event.BrowserMenuItemTapped.Item.SHARE
            is Item.Settings -> Event.BrowserMenuItemTapped.Item.SETTINGS
            is Item.RequestDesktop ->
                if (item.isChecked) {
                    Event.BrowserMenuItemTapped.Item.DESKTOP_VIEW_ON
                } else {
                    Event.BrowserMenuItemTapped.Item.DESKTOP_VIEW_OFF
                }
            is Item.FindInPage -> Event.BrowserMenuItemTapped.Item.FIND_IN_PAGE
            is Item.SaveToCollection ->
                Event.BrowserMenuItemTapped.Item.SAVE_TO_COLLECTION
            is Item.AddToTopSites ->
                Event.BrowserMenuItemTapped.Item.ADD_TO_TOP_SITES
            is Item.AddToHomeScreen ->
                Event.BrowserMenuItemTapped.Item.ADD_TO_HOMESCREEN
            is Item.SyncedTabs -> Event.BrowserMenuItemTapped.Item.SYNC_TABS
            is Item.SyncAccount -> Event.BrowserMenuItemTapped.Item.SYNC_ACCOUNT
            is Item.Bookmark -> Event.BrowserMenuItemTapped.Item.BOOKMARK
            is Item.AddonsManager ->
                Event.BrowserMenuItemTapped.Item.ADDONS_MANAGER
            is Item.Bookmarks -> Event.BrowserMenuItemTapped.Item.BOOKMARKS
            is Item.History -> Event.BrowserMenuItemTapped.Item.HISTORY
            is Item.Downloads -> Event.BrowserMenuItemTapped.Item.DOWNLOADS
            is Item.NewTab -> Event.BrowserMenuItemTapped.Item.NEW_TAB
            is Item.SetDefaultBrowser ->
                Event.BrowserMenuItemTapped.Item.SET_DEFAULT_BROWSER
        }

        metrics.track(Event.BrowserMenuItemTapped(eventItem))
    }

    companion object {
        internal const val TELEMETRY_BROWSER_IDENTIFIER = "browserMenu"
    }
}
