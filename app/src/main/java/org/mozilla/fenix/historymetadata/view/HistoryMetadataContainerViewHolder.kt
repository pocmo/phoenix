/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.historymetadata.view

import android.view.View
import kotlinx.android.synthetic.main.history_metadata_container_group.*
import org.mozilla.fenix.R
import org.mozilla.fenix.historymetadata.HistoryMetadataContainer
import org.mozilla.fenix.historymetadata.interactor.HistoryMetadataInteractor
import org.mozilla.fenix.utils.view.ViewHolder

/**
 * TODO
 */
class HistoryMetadataContainerViewHolder(
    view: View,
    private val interactor: HistoryMetadataInteractor
) : ViewHolder(view) {

    fun bind(historyMetadataContainer: HistoryMetadataContainer) {
        history_metadata_container_title.text = historyMetadataContainer.title

        itemView.isActivated = historyMetadataContainer.expanded

        itemView.setOnClickListener {
            interactor.onToggleHistoryMetadataContainerExpanded(historyMetadataContainer)
        }
    }

    companion object {
        const val LAYOUT_ID = R.layout.history_metadata_container_group
    }
}
