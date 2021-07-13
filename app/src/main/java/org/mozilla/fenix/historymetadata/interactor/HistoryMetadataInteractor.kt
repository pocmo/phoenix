/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.historymetadata.interactor

import mozilla.components.concept.storage.HistoryMetadataKey
import org.mozilla.fenix.historymetadata.HistoryMetadataContainer

/**
 * Interface for history metadata related actions in the Home screen.
 */
interface HistoryMetadataInteractor {

    /**
     * TODO
     *
     * @param url
     */
    fun onHistoryMetadataItemClicked(url: String, historyMetadata: HistoryMetadataKey)

    /**
     * Called when a user clicks on the "Show all" button besides the history metadata
     * collection.
     */
    fun onHistoryMetadataShowAllClicked()

    /**
     * TODO
     */
    fun onToggleHistoryMetadataContainerExpanded(historyMetadataContainer: HistoryMetadataContainer)
}
