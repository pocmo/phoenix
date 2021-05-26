/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.helpers

import io.mockk.MockKMatcherScope
import io.mockk.MockKStubScope
import io.mockk.every
import org.mozilla.fenix.FenixApplication

/**
 * Helper for stubbing parts of our components that are accessed through `Context`.
 *
 * With the switch to Java 11 stubbing those parts started to fail with an exception like:
 * ```
 * Caused by: io.mockk.MockKException: Class cast exception happened.
 * Probably type information was erased.
 * In this case use `hint` before call to specify exact return type of a method.
 * [..}
 * Caused by: java.lang.ClassCastException: class org.mozilla.fenix.FenixApplication cannot be cast
 * to class android.content.Context (org.mozilla.fenix.FenixApplication is in unnamed module of
 * loader 'app'; android.content.Context is in unnamed module of loader
 * org.robolectric.internal.AndroidSandbox$SdkSandboxClassLoader @1e0b4072)
 * at android.content.ContextWrapper.getApplicationContext(ContextWrapper.java)
 * at org.mozilla.fenix.ext.ContextKt.getApplication(Context.kt:29)
 * at org.mozilla.fenix.ext.ContextKt.getComponents(Context.kt:35)
 * ```
 *
 * This helper uses the `hint()` API from mockk to work around that.
 */
fun <T> everyComponents(stubBlock: MockKMatcherScope.() -> T): MockKStubScope<T, T> {
    return every {
        hint(FenixApplication::class)
        stubBlock(this)
    }
}
