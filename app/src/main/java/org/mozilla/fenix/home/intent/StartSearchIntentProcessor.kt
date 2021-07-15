/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.intent

import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.navOptions
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.ext.nav

/**
 * When the search widget is tapped, Fenix should open to the search fragment.
 * Tapping the private browsing mode launcher icon should also open to the search fragment.
 * Long pressing home button should also open to the search fragment if fenix is set as the assist app
 */
class StartSearchIntentProcessor(
    private val metrics: MetricController
) : HomeIntentProcessor {

    override fun process(intent: Intent, navController: NavController, out: Intent): Boolean {
        var source :Event.PerformedSearch.SearchAccessPoint?= when (intent.extras?.getString(HomeActivity.OPEN_TO_SEARCH)) {
            SEARCH_WIDGET -> {
                metrics.track(Event.SearchWidgetNewTabPressed)
                Event.PerformedSearch.SearchAccessPoint.WIDGET
            }
            STATIC_SHORTCUT_NEW_TAB -> {
                metrics.track(Event.PrivateBrowsingStaticShortcutTab)
                Event.PerformedSearch.SearchAccessPoint.SHORTCUT
            }
            STATIC_SHORTCUT_NEW_PRIVATE_TAB -> {
                metrics.track(Event.PrivateBrowsingStaticShortcutPrivateTab)
                Event.PerformedSearch.SearchAccessPoint.SHORTCUT
            }
            PRIVATE_BROWSING_PINNED_SHORTCUT -> {
                metrics.track(Event.PrivateBrowsingPinnedShortcutPrivateTab)
                Event.PerformedSearch.SearchAccessPoint.SHORTCUT
            }
            else -> null
        }

        when {
            source != null -> {
                out.removeExtra(HomeActivity.OPEN_TO_SEARCH)
            }
            intent.action == Intent.ACTION_ASSIST -> {
                source = Event.PerformedSearch.SearchAccessPoint.ASSIST
            }
            else -> return false
        }

        val directions = NavGraphDirections.actionGlobalSearchDialog(
                sessionId = null,
                searchAccessPoint = source
        )

        val options = navOptions {
            popUpTo = R.id.homeFragment
        }
        navController.nav(null, directions, options)
        return true
    }

    companion object {
        const val SEARCH_WIDGET = "search_widget"
        const val STATIC_SHORTCUT_NEW_TAB = "static_shortcut_new_tab"
        const val STATIC_SHORTCUT_NEW_PRIVATE_TAB = "static_shortcut_new_private_tab"
        const val PRIVATE_BROWSING_PINNED_SHORTCUT = "private_browsing_pinned_shortcut"
    }
}
