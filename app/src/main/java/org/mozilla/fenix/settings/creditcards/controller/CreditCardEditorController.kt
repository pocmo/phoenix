/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.creditcards.controller

import android.content.Context
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.NewCreditCardFields
import mozilla.components.concept.storage.UpdatableCreditCardFields
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.settings.creditcards.CreditCardEditorFragment
import org.mozilla.fenix.settings.creditcards.interactor.CreditCardEditorInteractor
import org.mozilla.fenix.ext.components

/**
 * [CreditCardEditorFragment] controller. An interface that handles the view manipulation of the
 * credit card editor.
 */
interface CreditCardEditorController {

    /**
     * @see [CreditCardEditorInteractor.onCancelButtonClicked]
     */
    fun handleCancelButtonClicked()

    /**
     * @see [CreditCardEditorInteractor.onDeleteCardButtonClicked]
     */
    fun handleDeleteCreditCard(guid: String)

    /**
     * @see [CreditCardEditorInteractor.onSaveCreditCard]
     */
    fun handleSaveCreditCard(creditCardFields: NewCreditCardFields)

    /**
     * @see [CreditCardEditorInteractor.onUpdateCreditCard]
     */
    fun handleUpdateCreditCard(guid: String, creditCardFields: UpdatableCreditCardFields)
}

/**
 * The default implementation of [CreditCardEditorController].
 *
 * @param storage An instance of the [AutofillCreditCardsAddressesStorage] for adding and retrieving
 * credit cards.
 * @param lifecycleScope [CoroutineScope] scope to launch coroutines.
 * @param navController [NavController] used for navigation.
 * @param ioDispatcher [CoroutineDispatcher] used for executing async tasks. Defaults to [Dispatchers.IO].
 */
class DefaultCreditCardEditorController(
    context: Context,
    private val storage: AutofillCreditCardsAddressesStorage,
    private val lifecycleScope: CoroutineScope,
    private val navController: NavController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CreditCardEditorController {

    private val metrics = context.components.analytics.metrics

    override fun handleCancelButtonClicked() {
        navController.popBackStack()
    }

    /**
     * Deletes an existing credit card.
     *
     * @param guid the id of the credit card to delete.
     */
    override fun handleDeleteCreditCard(guid: String) {
        lifecycleScope.launch(ioDispatcher) {
            storage.deleteCreditCard(guid)
            metrics.track(Event.CreditCardDelete)

            lifecycleScope.launch(Dispatchers.Main) {
                navController.popBackStack()
            }
        }
    }

    /**
     * Saves a new credit card.
     *
     * @param creditCardFields the field values for the new credit card.
     */
    override fun handleSaveCreditCard(creditCardFields: NewCreditCardFields) {
        lifecycleScope.launch(ioDispatcher) {
            storage.addCreditCard(creditCardFields)
            metrics.track(Event.CreditCardManualSave)

            lifecycleScope.launch(Dispatchers.Main) {
                navController.popBackStack()
            }
        }
    }

    /**
     * Updates an existing credit card.
     *
     * @param guid the id of the credit card to update.
     * @param creditCardFields the updated values for the card.
     */
    override fun handleUpdateCreditCard(guid: String, creditCardFields: UpdatableCreditCardFields) {
        lifecycleScope.launch(ioDispatcher) {
            storage.updateCreditCard(guid, creditCardFields)

            lifecycleScope.launch(Dispatchers.Main) {
                navController.popBackStack()
            }
        }
    }
}
