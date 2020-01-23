/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import kotlinx.coroutines.Job
import mozilla.components.support.base.log.Log

/**
 * A queue of paused `Job`s whose execution is resumed when Fenix is visually complete.
 *
 * This queue contains paused `Job`s. Each Job in the queue will be restarted once Fenix
 * is visually complete (as long as it has not be cancelled since it was added to the queue).
 * Each Job will execute on the thread on which it was originally scheduled.
 *
 * This is threadsafe.
 */
object OnVisualCompletenessExecutionQueue {
    internal var jobs = mutableListOf<Job>()
    internal var hasExecuted = false
    private const val visualCompletenessExecutionQueueLogTag = "OnVisualCompletenessExecutionQueue"

    /**
     * Add a paused Job to the queue
     *
     * Add a paused Job to the queue. If the job is not paused, it will *not* be added
     * to the queue.
     *
     * @param j: The paused Job to add to the queue.
     * @return true if [j] was added to the queue; false otherwise
     */
    @Synchronized
    fun add(j: Job) {
        if (hasExecuted) {
            Log.log(
                Log.Priority.WARN,
                visualCompletenessExecutionQueueLogTag,
                null,
                "Queue already executed. Not adding the Job."
            )
            throw VisualCompletenessExecutionQueueException("Queue already executed.")
        }
        if (j.isActive || j.isCompleted) {
            Log.log(
                Log.Priority.WARN,
                visualCompletenessExecutionQueueLogTag,
                null,
                "Not adding a Job that is active or complete."
            )
            throw VisualCompletenessExecutionQueueException("Job is active or complete.")
        }
        jobs.add(j)
    }

    /**
     * Restart all paused jobs in the queue
     *
     * Restart all the paused jobs in the queue. When all the jobs have been started,
     * clear the queue.
     */
    @Synchronized
    fun start() {
        jobs.forEach { if (!it.isCancelled) { it.start() } }
        jobs.clear()
        hasExecuted = true
    }

    /**
     * Reset the state of the queue and clear its jobs
     *
     * This is for testing only.
     */
    @Synchronized
    internal fun reset() {
        jobs.clear()
        hasExecuted = false
    }
}

class VisualCompletenessExecutionQueueException(val failure: String) : Exception()
