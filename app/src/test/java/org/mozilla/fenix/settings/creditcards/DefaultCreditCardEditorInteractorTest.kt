/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.creditcards

import androidx.navigation.NavController
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import mozilla.components.concept.storage.CreditCard
import mozilla.components.concept.storage.CreditCardNumber
import mozilla.components.concept.storage.NewCreditCardFields
import mozilla.components.concept.storage.UpdatableCreditCardFields
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import mozilla.components.support.utils.CreditCardNetworkType
import org.junit.Before
import org.junit.Test
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.settings.creditcards.controller.CreditCardEditorController
import org.mozilla.fenix.settings.creditcards.controller.DefaultCreditCardEditorController
import org.mozilla.fenix.settings.creditcards.interactor.DefaultCreditCardEditorInteractor

@ExperimentalCoroutinesApi
class DefaultCreditCardEditorInteractorTest {

    private val storage: AutofillCreditCardsAddressesStorage = mockk(relaxed = true)
    private val navController: NavController = mockk(relaxed = true)
    private val metrics: MetricController = mockk(relaxed = true)
    private val testCoroutineScope = TestCoroutineScope()

    private lateinit var controller: CreditCardEditorController
    private lateinit var interactor: DefaultCreditCardEditorInteractor

    @Before
    fun setup() {
        controller = spyk(
            DefaultCreditCardEditorController(
                storage,
                testCoroutineScope,
                navController,
                metrics
            )
        )

        interactor = DefaultCreditCardEditorInteractor(controller)
    }

    @Test
    fun onCancelButtonClicked() {
        interactor.onCancelButtonClicked()
        verify { controller.handleCancelButtonClicked() }
    }

    @Test
    fun onDeleteCardButtonClicked() {
        val creditCard = CreditCard(
            guid = "id",
            billingName = "Banana Apple",
            encryptedCardNumber = CreditCardNumber.Encrypted("4111111111111110"),
            cardNumberLast4 = "1110",
            expiryMonth = 1,
            expiryYear = 2030,
            cardType = CreditCardNetworkType.AMEX.cardName,
            timeCreated = 1L,
            timeLastUsed = 1L,
            timeLastModified = 1L,
            timesUsed = 1L
        )
        interactor.onDeleteCardButtonClicked(creditCard.guid)
        verify { controller.handleDeleteCreditCard(creditCard.guid) }
    }

    @Test
    fun onSaveButtonClicked() {
        val creditCardFields = NewCreditCardFields(
            billingName = "Banana Apple",
            plaintextCardNumber = CreditCardNumber.Plaintext("4111111111111112"),
            cardNumberLast4 = "1112",
            expiryMonth = 1,
            expiryYear = 2030,
            cardType = CreditCardNetworkType.DISCOVER.cardName
        )
        interactor.onSaveCreditCard(creditCardFields)
        verify { controller.handleSaveCreditCard(creditCardFields) }
    }

    @Test
    fun onUpdateCreditCard() {
        val guid = "id"
        val creditCardFields = UpdatableCreditCardFields(
            billingName = "Banana Apple",
            cardNumber = CreditCardNumber.Encrypted("4111111111111112"),
            cardNumberLast4 = "1112",
            expiryMonth = 1,
            expiryYear = 2034,
            cardType = CreditCardNetworkType.DISCOVER.cardName
        )
        interactor.onUpdateCreditCard(guid, creditCardFields)
        verify { controller.handleUpdateCreditCard(guid, creditCardFields) }
    }
}
