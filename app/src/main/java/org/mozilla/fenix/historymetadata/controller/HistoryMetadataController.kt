/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.historymetadata.controller

import androidx.navigation.NavController
import mozilla.components.concept.storage.HistoryMetadataKey
import mozilla.components.feature.tabs.TabsUseCases
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.historymetadata.HistoryMetadataContainer
import org.mozilla.fenix.historymetadata.interactor.HistoryMetadataInteractor
import org.mozilla.fenix.home.HomeFragmentAction
import org.mozilla.fenix.home.HomeFragmentDirections
import org.mozilla.fenix.home.HomeFragmentStore
import org.mozilla.fenix.utils.Settings

/**
 * TODO
 */
interface HistoryMetadataController {

    /**
     * @see [HistoryMetadataInteractor.onHistoryMetadataItemClicked]
     */
    fun handleHistoryMetadataItemClicked(url: String, historyMetadata: HistoryMetadataKey)

    /**
     * @see [HistoryMetadataInteractor.onHistoryMetadataShowAllClicked]
     */
    fun handleHistoryShowAllClicked()

    /**
     * @see [HistoryMetadataInteractor.onToggleHistoryMetadataContainerExpanded]
     */
    fun handleToggleHistoryMetadataContainerExpanded(historyMetadataContainer: HistoryMetadataContainer)
}

/**
 * TODO
 */
class DefaultHistoryMetadataController(
    private val activity: HomeActivity,
    private val settings: Settings,
    private val homeFragmentStore: HomeFragmentStore,
    private val addTabUseCase: TabsUseCases.AddNewTabUseCase,
    private val navController: NavController
) : HistoryMetadataController {

    override fun handleHistoryMetadataItemClicked(
        url: String,
        historyMetadata: HistoryMetadataKey
    ) {
        val tabId = addTabUseCase.invoke(
            url = url,
            selectTab = true,
            startLoading = true,
            historyMetadata = historyMetadata
        )

        if (settings.openNextTabInDesktopMode) {
            activity.handleRequestDesktopMode(tabId)
        }

        activity.openToBrowser(BrowserDirection.FromHome)
    }

    override fun handleHistoryShowAllClicked() {
        navController.nav(R.id.homeFragment, HomeFragmentDirections.actionGlobalHistoryFragment())
    }

    override fun handleToggleHistoryMetadataContainerExpanded(historyMetadataContainer: HistoryMetadataContainer) {
        homeFragmentStore.dispatch(
            HomeFragmentAction.HistoryMetadataExpanded(
                historyMetadataContainer
            )
        )
    }
}
