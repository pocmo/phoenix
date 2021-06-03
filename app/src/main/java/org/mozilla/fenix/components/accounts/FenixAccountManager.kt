/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.accounts

import android.content.Context
import mozilla.components.service.fxa.manager.FxaAccountManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.browser.menu.BrowserMenuItem
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.OAuthAccount
import org.mozilla.fenix.ext.components

/**
 * Component which holds a reference to [FxaAccountManager]. Manages account authentication,
 * profiles, and profile state observers.
 */
open class FenixAccountManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    val accountManager = context.components.backgroundServices.accountManager

    val authenticatedAccount
        get() = accountManager.authenticatedAccount() != null

    val accountProfileEmail
        get() = accountManager.accountProfile()?.email

    /**
     * Check if the current account is signed in and authenticated.
     */
    fun signedInToFxa(): Boolean {
        val account = accountManager.authenticatedAccount()
        val needsReauth = accountManager.accountNeedsReauth()

        return account != null && !needsReauth
    }

    /**
     * Observe account state and updates menus.
     */
    fun observeAccountState(
        menuItems: List<BrowserMenuItem>,
        onMenuBuilderChanged: (BrowserMenuBuilder) -> Unit = {}
    ) {
        context.components.backgroundServices.accountManagerAvailableQueue.runIfReadyOrQueue {
            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                return@runIfReadyOrQueue
            }
            context.components.backgroundServices.accountManager.register(object : AccountObserver {
                override fun onAuthenticationProblems() {
                    lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        onMenuBuilderChanged(BrowserMenuBuilder(menuItems))
                    }
                }

                override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
                    lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        onMenuBuilderChanged(BrowserMenuBuilder(menuItems))
                    }
                }

                override fun onLoggedOut() {
                    lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        onMenuBuilderChanged(BrowserMenuBuilder(menuItems))
                    }
                }
            }, lifecycleOwner)
        }
    }
}
