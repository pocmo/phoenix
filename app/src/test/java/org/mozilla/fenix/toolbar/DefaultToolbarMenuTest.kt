/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.toolbar

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import mozilla.components.browser.menu.item.BrowserMenuDivider
import mozilla.components.browser.menu.item.BrowserMenuImageTextCheckboxButton
import mozilla.components.browser.menu.item.BrowserMenuItemToolbar
import mozilla.components.browser.menu.item.WebExtensionPlaceholderMenuItem
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.storage.BookmarksStorage
import mozilla.components.feature.webcompat.reporter.WebCompatReporterFeature
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.support.test.rule.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.FeatureFlags
import org.mozilla.fenix.R
import org.mozilla.fenix.components.accounts.FenixAccountManager
import org.mozilla.fenix.components.toolbar.DefaultToolbarMenu
import org.mozilla.fenix.components.toolbar.ToolbarMenu
import org.mozilla.fenix.components.toolbar.ToolbarMenuItems
import org.mozilla.fenix.ext.settings

@ExperimentalCoroutinesApi
class DefaultToolbarMenuTest {

    private lateinit var store: BrowserStore
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var toolbarMenu: DefaultToolbarMenu
    private lateinit var context: Context
    private lateinit var bookmarksStorage: BookmarksStorage
    private lateinit var toolbarMenuItems: ToolbarMenuItems
    private lateinit var accountManager: FenixAccountManager
    private val testDispatcher = TestCoroutineDispatcher()
    private val primaryTextColor: Int = mockk(relaxed = true)
    private val accentBrightTextColor: Int = mockk(relaxed = true)
    private var shouldUseBottomToolbar = true

    @get:Rule
    val coroutinesTestRule = MainCoroutineRule(testDispatcher)

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        lifecycleOwner = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { context.theme } returns mockk(relaxed = true)

        bookmarksStorage = mockk(relaxed = true)
        store = BrowserStore(
            BrowserState(
                tabs = listOf(
                    createTab(url = "https://firefox.com", id = "1"),
                    createTab(url = "https://getpocket.com", id = "2")
                ), selectedTabId = "1"
            )
        )
        accountManager = mockk(relaxed = true)

        toolbarMenuItems = ToolbarMenuItems(
            context,
            store,
            accountManager,
            hasAccountProblem = false,
            onItemTapped = {},
            primaryTextColor,
            accentBrightTextColor
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun createMenu() {
        toolbarMenu = spyk(DefaultToolbarMenu(
            context = context,
            store = store,
            hasAccountProblem = false,
            onItemTapped = { },
            lifecycleOwner = lifecycleOwner,
            bookmarksStorage = bookmarksStorage,
            isPinningSupported = false
        ))

        every { toolbarMenu.updateCurrentUrlIsBookmarked(any()) } returns mockk()
        every { toolbarMenu.shouldShowOpenInApp() } returns mockk()
    }

    @Test
    fun `WHEN the bottom toolbar is set THEN the first item in the list is not the navigation`() {
        every { context.settings().shouldUseBottomToolbar } returns true
        createMenu()

        val menuItems = toolbarMenu.coreMenuItems
        assertNotNull(menuItems)

        val firstItem = menuItems[0]
        val newTabItem = toolbarMenu.newTabItem

        assertEquals(newTabItem, firstItem)
    }

    @Test
    fun `WHEN the top toolbar is set THEN the first item in the list is the navigation`() {
        every { context.settings().shouldUseBottomToolbar } returns false
        createMenu()

        val menuItems = toolbarMenu.coreMenuItems
        assertNotNull(menuItems)

            val firstItem = menuItems[0]
            val navToolbar = toolbarMenu.menuToolbarNavigation

        assertEquals(navToolbar, firstItem)
    }

    @Test
    fun `WHEN the bottom toolbar is set THEN the nav menu should be the last item`() {
        every { context.settings().shouldUseBottomToolbar } returns true

        createMenu()

        val menuItems = toolbarMenu.coreMenuItems
        assertNotNull(menuItems)

            val lastItem = menuItems[menuItems.size - 1]
            val navToolbar = toolbarMenu.menuToolbarNavigation

        assertEquals(navToolbar, lastItem)
    }

    @Test
    fun `WHEN the top toolbar is set THEN settings should be the last item`() {
        every { context.settings().shouldUseBottomToolbar } returns false

        createMenu()

        val menuItems = toolbarMenu.coreMenuItems
        assertNotNull(menuItems)

        val lastItem = menuItems[menuItems.size - 1]
        val settingsItem = toolbarMenu.settingsItem

        assertEquals(settingsItem, lastItem)
    }

    @Test


    val installPwaToHomescreen = toolbarMenuItems.installPwaToHomescreen
    val newTabItem = toolbarMenuItems.newTabItem
    val historyItem = toolbarMenuItems.historyItem
    val downloadsItem = toolbarMenuItems.downloadsItem
    var findInPageItem = toolbarMenuItems.findInPageItem
    var desktopSiteItem = toolbarMenuItems.requestDesktopSiteItem
    var customizeReaderView = toolbarMenuItems.customizeReaderView
    var openInApp = toolbarMenuItems.openInAppItem
    var addToHomeScreenItem = toolbarMenuItems.addToHomeScreenItem
    var addToTopSitesItem = toolbarMenuItems.addToTopSitesItem
    var saveToCollectionItem = toolbarMenuItems.saveToCollectionItem
    var settingsItem = toolbarMenuItems.settingsItem
    var deleteDataOnQuit = toolbarMenuItems.deleteDataOnQuitItem
    var syncedTabsItem = toolbarMenuItems.oldSyncedTabsItem
    var syncSignInItem = toolbarMenuItems.syncMenuItem

    val extensionsItem = WebExtensionPlaceholderMenuItem(
        id = WebExtensionPlaceholderMenuItem.MAIN_EXTENSIONS_MENU_ID
    )

    val reportSiteIssuePlaceholder = WebExtensionPlaceholderMenuItem(
        id = WebCompatReporterFeature.WEBCOMPAT_REPORTER_EXTENSION_ID
    )

    var addEditBookmarksItem = BrowserMenuImageTextCheckboxButton(
        imageResource = R.drawable.ic_bookmarks_menu,
        iconTintColorResource = primaryTextColor,
        label = context.getString(R.string.library_bookmarks),
        labelListener = {},
        primaryStateIconResource = R.drawable.ic_bookmark_outline,
        secondaryStateIconResource = R.drawable.ic_bookmark_filled,
        tintColorResource = accentBrightTextColor,
        primaryLabel = context.getString(R.string.browser_menu_add),
        secondaryLabel = context.getString(R.string.browser_menu_edit),
        isInPrimaryState = { true }
    ) { }

    val menuToolbarNavigation by lazy {
        val back = toolbarMenuItems.backNavButton
        val forward = toolbarMenuItems.forwardNavButton
        val refresh = toolbarMenuItems.refreshNavButton
        val share = toolbarMenuItems.shareItem

        BrowserMenuItemToolbar(listOf(back, forward, share, refresh), isSticky = true)
    }

    val masterList = listOfNotNull(
        menuToolbarNavigation,
        newTabItem,
        BrowserMenuDivider(),
        addEditBookmarksItem,
        historyItem,
        downloadsItem,
        extensionsItem,
        if (FeatureFlags.tabsTrayRewrite) syncSignInItem else syncedTabsItem,
        BrowserMenuDivider(),
        toolbarMenu.getSetDefaultBrowserItem(),
        toolbarMenu.getSetDefaultBrowserItem()?.let { BrowserMenuDivider() },
        findInPageItem,
        desktopSiteItem,
        customizeReaderView,
        openInApp,
        reportSiteIssuePlaceholder,
        BrowserMenuDivider(),
        addToHomeScreenItem,
        installPwaToHomescreen,
        addToTopSitesItem,
        saveToCollectionItem,
        BrowserMenuDivider(),
        settingsItem,
        deleteDataOnQuit,
        if (shouldUseBottomToolbar) BrowserMenuDivider() else null,
        if (shouldUseBottomToolbar) menuToolbarNavigation else null
    )
}
