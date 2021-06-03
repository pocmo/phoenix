/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.browser.menu.WebExtensionBrowserMenuBuilder
import mozilla.components.browser.menu.item.BrowserMenuDivider
import mozilla.components.browser.menu.item.BrowserMenuImageText
import mozilla.components.browser.menu.item.BrowserMenuImageTextCheckboxButton
import mozilla.components.browser.menu.item.BrowserMenuItemToolbar
import mozilla.components.browser.menu.item.WebExtensionPlaceholderMenuItem
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.storage.BookmarksStorage
import mozilla.components.feature.webcompat.reporter.WebCompatReporterFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.toolbar.ToolbarMenu.Item
import org.mozilla.fenix.components.accounts.FenixAccountManager
import org.mozilla.fenix.experiments.ExperimentBranch
import org.mozilla.fenix.experiments.FeatureId
import org.mozilla.fenix.ext.asActivity
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.ext.withExperiment
import org.mozilla.fenix.theme.ThemeManager
import org.mozilla.fenix.utils.BrowsersCache

/**
 * Builds the toolbar object used with the 3-dot menu in the browser fragment.
 * @param context the [Context].
 * @param store reference to the application's [BrowserStore].
 * @param hasAccountProblem If true, there was a problem signing into the Firefox account.
 * @param shouldReverseItems If true, reverse the menu items.
 * @param onItemTapped Called when a menu item is tapped.
 * @param lifecycleOwner View lifecycle owner used to determine when to cancel UI jobs.
 * @param bookmarksStorage Used to check if a page is bookmarked.
 * @param isPinningSupported Whether pinning is supported for install or add to homescreen.
 * @param onMenuBuilderChanged Defines behavior when menu items have changed.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@ExperimentalCoroutinesApi
open class DefaultToolbarMenu(
    private val context: Context,
    private val store: BrowserStore,
    hasAccountProblem: Boolean = false,
    private val onItemTapped: (Item) -> Unit = {},
    private val lifecycleOwner: LifecycleOwner,
    private val bookmarksStorage: BookmarksStorage,
    val isPinningSupported: Boolean,
    onMenuBuilderChanged: (BrowserMenuBuilder) -> Unit = {},
) : ToolbarMenu {

    private var isCurrentUrlBookmarked = false
    private var isBookmarkedJob: Job? = null

    private val shouldDeleteDataOnQuit = context.settings().shouldDeleteBrowsingDataOnQuit
    private val shouldUseBottomToolbar = context.settings().shouldUseBottomToolbar
    private val accountManager = FenixAccountManager(context, lifecycleOwner)

    private val selectedSession: TabSessionState?
        get() = store.state.selectedTab

    @ColorRes
    internal val primaryTextColor = ThemeManager.resolveAttribute(R.attr.primaryText, context)

    @ColorRes
    internal val accentTextColor =
        ThemeManager.resolveAttribute(R.attr.menuItemButtonTintColor, context)

    internal val toolbarMenuItems = ToolbarMenuItems(
        context,
        store,
        accountManager,
        hasAccountProblem,
        onItemTapped,
        primaryTextColor,
        accentTextColor
    )

    override val menuBuilder by lazy {
        WebExtensionBrowserMenuBuilder(
            items = coreMenuItems,
            endOfMenuAlwaysVisible = shouldUseBottomToolbar,
            store = store,
            style = WebExtensionBrowserMenuBuilder.Style(
                webExtIconTintColorResource = primaryTextColor,
                addonsManagerMenuItemDrawableRes = R.drawable.ic_addons_extensions
            ),
            onAddonsManagerTapped = {
                onItemTapped.invoke(Item.AddonsManager)
            },
            appendExtensionSubMenuAtStart = shouldUseBottomToolbar
        )
    }

    override val menuToolbarNavigation by lazy {
        val back = toolbarMenuItems.backNavButton
        val forward = toolbarMenuItems.forwardNavButton
        val refresh = toolbarMenuItems.refreshNavButton
        val share = toolbarMenuItems.shareItem

        registerForIsBookmarkedUpdates()

        BrowserMenuItemToolbar(listOf(back, forward, share, refresh), isSticky = true)
    }

    // Predicates that need to be repeatedly called as the session changes
    @VisibleForTesting(otherwise = PRIVATE)
    fun canAddToHomescreen(): Boolean =
        selectedSession != null && isPinningSupported &&
                !context.components.useCases.webAppUseCases.isInstallable()

    @VisibleForTesting(otherwise = PRIVATE)
    fun canInstall(): Boolean =
        selectedSession != null && isPinningSupported &&
                context.components.useCases.webAppUseCases.isInstallable()

    @VisibleForTesting(otherwise = PRIVATE)
    fun shouldShowOpenInApp(): Boolean = selectedSession?.let { session ->
        val appLink = context.components.useCases.appLinksUseCases.appLinkRedirect
        appLink(session.content.url).hasExternalApp()
    } ?: false

    @VisibleForTesting(otherwise = PRIVATE)
    fun shouldShowReaderViewCustomization(): Boolean = selectedSession?.let {
        store.state.findTab(it.id)?.readerState?.active
    } ?: false
    // End of predicates //

    internal val installPwaToHomescreen = toolbarMenuItems.installPwaToHomescreen
    internal val newTabItem = toolbarMenuItems.newTabItem
    internal val historyItem = toolbarMenuItems.historyItem
    internal val downloadsItem = toolbarMenuItems.downloadsItem
    internal var findInPageItem = toolbarMenuItems.findInPageItem
    internal var desktopSiteItem = toolbarMenuItems.requestDesktopSiteItem
    internal var customizeReaderView = toolbarMenuItems.customizeReaderView
    internal var openInApp = toolbarMenuItems.openInAppItem
    internal var addToHomeScreenItem = toolbarMenuItems.addToHomeScreenItem
    internal var addToTopSitesItem = toolbarMenuItems.addToTopSitesItem
    internal var saveToCollectionItem = toolbarMenuItems.saveToCollectionItem
    internal var settingsItem = toolbarMenuItems.settingsItem
    internal var deleteDataOnQuit = toolbarMenuItems.deleteDataOnQuitItem
    internal var syncSignInItem = toolbarMenuItems.syncMenuItem

    internal val extensionsItem = WebExtensionPlaceholderMenuItem(
        id = WebExtensionPlaceholderMenuItem.MAIN_EXTENSIONS_MENU_ID
    )

    internal val reportSiteIssuePlaceholder = WebExtensionPlaceholderMenuItem(
        id = WebCompatReporterFeature.WEBCOMPAT_REPORTER_EXTENSION_ID
    )

    internal var addEditBookmarksItem = BrowserMenuImageTextCheckboxButton(
        imageResource = R.drawable.ic_bookmarks_menu,
        iconTintColorResource = primaryTextColor,
        label = context.getString(R.string.library_bookmarks),
        labelListener = {
            onItemTapped.invoke(Item.Bookmarks)
        },
        primaryStateIconResource = R.drawable.ic_bookmark_outline,
        secondaryStateIconResource = R.drawable.ic_bookmark_filled,
        tintColorResource = accentTextColor,
        primaryLabel = context.getString(R.string.browser_menu_add),
        secondaryLabel = context.getString(R.string.browser_menu_edit),
        isInPrimaryState = { !isCurrentUrlBookmarked }
    ) {
        handleBookmarkItemTapped()
    }

    internal val coreMenuItems by lazy {
        // Predicates that are called once, during screen init
        val shouldShowSaveToCollection = (context.asActivity() as? HomeActivity)
            ?.browsingModeManager?.mode == BrowsingMode.Normal

        val menuItems =
            listOfNotNull(
                if (shouldUseBottomToolbar) null else menuToolbarNavigation,
                newTabItem,
                BrowserMenuDivider(),
                addEditBookmarksItem,
                historyItem,
                downloadsItem,
                extensionsItem,
                syncSignInItem,
                BrowserMenuDivider(),
                getSetDefaultBrowserItem(),
                getSetDefaultBrowserItem()?.let { BrowserMenuDivider() },
                findInPageItem,
                desktopSiteItem,
                customizeReaderView.apply { visible = ::shouldShowReaderViewCustomization },
                openInApp.apply { visible = ::shouldShowOpenInApp },
                reportSiteIssuePlaceholder,
                BrowserMenuDivider(),
                addToHomeScreenItem.apply { visible = ::canAddToHomescreen },
                installPwaToHomescreen.apply { visible = ::canInstall },
                addToTopSitesItem,
                if (shouldShowSaveToCollection) saveToCollectionItem else null,
                BrowserMenuDivider(),
                settingsItem,
                if (shouldDeleteDataOnQuit) deleteDataOnQuit else null,
                if (shouldUseBottomToolbar) BrowserMenuDivider() else null,
                if (shouldUseBottomToolbar) menuToolbarNavigation else null
            )

        menuItems
    }

    init {
        onMenuBuilderChanged(BrowserMenuBuilder(coreMenuItems))

        accountManager.observeAccountState(
            menuItems = coreMenuItems,
            onMenuBuilderChanged = onMenuBuilderChanged
        )
    }

    private fun handleBookmarkItemTapped() {
        if (!isCurrentUrlBookmarked) isCurrentUrlBookmarked = true
        onItemTapped.invoke(Item.Bookmark)
    }

    @VisibleForTesting
    internal fun registerForIsBookmarkedUpdates() {
        store.flowScoped(lifecycleOwner) { flow ->
            flow.mapNotNull { state -> state.selectedTab }
                .ifAnyChanged { tab ->
                    arrayOf(
                        tab.id,
                        tab.content.url
                    )
                }
                .collect {
                    isCurrentUrlBookmarked = false
                    updateCurrentUrlIsBookmarked(it.content.url)
                }
        }
    }

    @VisibleForTesting
    internal fun updateCurrentUrlIsBookmarked(newUrl: String) {
        isBookmarkedJob?.cancel()
        isBookmarkedJob = lifecycleOwner.lifecycleScope.launch {
            isCurrentUrlBookmarked = bookmarksStorage
                .getBookmarksWithUrl(newUrl)
                .any { it.url == newUrl }
        }
    }

    internal fun getSetDefaultBrowserItem(): BrowserMenuImageText? {
        val experiments = context.components.analytics.experiments
        val browsers = BrowsersCache.all(context)

        return experiments.withExperiment(FeatureId.DEFAULT_BROWSER) { experimentBranch ->
            if (experimentBranch == ExperimentBranch.DEFAULT_BROWSER_TOOLBAR_MENU &&
                !browsers.isFirefoxDefaultBrowser
            ) {
                return@withExperiment BrowserMenuImageText(
                    label = context.getString(R.string.preferences_set_as_default_browser),
                    imageResource = R.mipmap.ic_launcher
                ) {
                    onItemTapped.invoke(Item.SetDefaultBrowser)
                }
            } else {
                null
            }
        }
    }
}
