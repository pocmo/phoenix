/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.historymetadata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.HistoryMetadataStorage
import mozilla.components.support.base.feature.LifecycleAwareFeature
import org.mozilla.fenix.home.HomeFragmentAction
import org.mozilla.fenix.home.HomeFragmentStore

/**
 * TODO
 *
 * @param homeStore
 * @param historyMetadataStorage
 * @param scope
 */
class HistoryMetadataFeature(
    private val homeStore: HomeFragmentStore,
    private val historyMetadataStorage: HistoryMetadataStorage,
    private val scope: CoroutineScope
) : LifecycleAwareFeature {

    private var job: Job? = null

    override fun start() {
        job = scope.launch(Dispatchers.IO) {
            val historyMetadata = historyMetadataStorage.getHistoryMetadataSince(Long.MIN_VALUE)
                .filter { it.totalViewTime > 0 }

            homeStore.dispatch(HomeFragmentAction.HistoryMetadataChange(historyMetadata))
        }
    }

    override fun stop() {
        job?.cancel()
    }
}
