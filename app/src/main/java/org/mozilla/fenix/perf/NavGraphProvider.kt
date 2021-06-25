/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.perf

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.mozilla.fenix.R

/**
 * This class asynchronously loads the navigation graph XML. This is a performance optimization:
 * large nav graphs take ~29ms to inflate on the Moto G5 (#16900). This also seemingly prevents the
 * HomeFragment layout XML from being unnecessarily inflated when it isn't used, improving perf by
 * ~148ms on the Moto G5 for VIEW start up (#18245) though it was unintentional and we may wish to
 * implement more intentional for that.
 *
 * The general flow of this object is:
 *
 * Right after HomeActivity contentView inflation
 *     Start inflation on IO
 * If navGraph is needed
 *     block on Main Thread until inflation completes
 *     setNavGraph on MainThread
 * Else
 *     setNavGraph on MainThread when ready (default case)
 *
 *
 * This class is defined as an Object, rather than as a class instance in our Components, because
 * it needs to be called by the [NavController] extension function which can't easily access Components.
 *
 * To use this class properly, [inflateNavGraphAsync] must be called first before
 * [blockForNavGraphInflation] using the same [NavController] instance.
 */
object NavGraphProvider {

    // We want to store member state on the NavController. However, there is no way to do this.
    // Instead, we store state as part of a map: NavController instance -> State. In order to
    // garbage collect our data when the NavController is no longer relevant, we use a WeakHashMap.
    private val map = WeakHashMap<NavController, DeferredNavGraphContainer>()

    private data class DeferredNavGraphContainer(
        var inflationJob: Deferred<NavGraph>? = null,
        var isGraphSet: Boolean = false
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun inflateNavGraphAsync(navController: NavController, lifecycleScope: LifecycleCoroutineScope) {
        // Avoid redundant work: blockForNavGraphInflation may be called before this in edge cases.
        // See the comment there for details.
        if (map[navController] != null) {
            return
        }

        val navControllerGraph = DeferredNavGraphContainer()
        map[navController] = navControllerGraph

        val inflationJob: Deferred<NavGraph> = lifecycleScope.async(Dispatchers.IO) {
            navController.inflateHomeActivityNavGraph()
        }
        navControllerGraph.inflationJob = inflationJob

        // Once the inflation is complete, we want to set the graph. If it's needed sooner than this
        // coroutine is scheduled, blockForNavGraphInflation will block the main thread and will
        // handle setting the nav graph there.
        //
        // Note: setNavGraph must be called on the main thread due to assertions in NavController.setGraph.
        //
        // We use undispatched as a micro-optimization to avoid putting an additional coroutine at the
        // end of the run queue.
        lifecycleScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED) {
            awaitInflationAndSetNavGraphIfNotAlreadySet(navController, navControllerGraph)
        }
    }

    /**
     * The job should block the main thread if it isn't completed so that the NavGraph can be loaded
     * before any navigation is done.
     *
     * [inflateNavGraphAsync] must be called before this method.
     *
     * @throws IllegalStateException if [inflateNavGraphAsync] wasn't called first for this [NavController]
     */
    fun blockForNavGraphInflation(navController: NavController) {
        val navControllerGraph = map[navController]

        // Ideally, we'd assert this function is called after inflateNavGraphAsync. However, when we
        // switch to using private tabs, we restart HomeActivity which causes the fragment create
        // code to be called before setContentView returns. That means this function is called
        // before inflateNavGraphAsync. As such, instead of asserting, we force the inflation to
        // happen in these edge cases.
        if (navControllerGraph == null) {
            navController.graph = navController.inflateHomeActivityNavGraph()
            map[navController] = DeferredNavGraphContainer(null, true)
            return
        }

        runBlockingIncrement {
            awaitInflationAndSetNavGraphIfNotAlreadySet(navController, navControllerGraph)
        }
    }

    private suspend fun awaitInflationAndSetNavGraphIfNotAlreadySet(
        navController: NavController,
        deferredNavGraphContainer: DeferredNavGraphContainer
    ) {
        val finishedGraph = deferredNavGraphContainer.inflationJob!!.await()
        if (!deferredNavGraphContainer.isGraphSet) {
            navController.graph = finishedGraph
            deferredNavGraphContainer.isGraphSet = true
        }
    }
}

private fun NavController.inflateHomeActivityNavGraph() = navInflater.inflate(R.navigation.nav_graph)
