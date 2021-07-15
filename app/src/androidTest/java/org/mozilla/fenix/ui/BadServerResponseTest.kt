/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui

import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.mozilla.fenix.helpers.HomeActivityTestRule
import org.mozilla.fenix.helpers.MockWebServerHelper
import org.mozilla.fenix.ui.robots.homeScreen

class BadServerResponseTest {
    private val activityTestRule = HomeActivityTestRule()
    private var mockWebServer = MockWebServer()

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun serverResponse400StartupTest() {
        mockWebServer = MockWebServerHelper.createErrorResponseMockWebServer(400)
        mockWebServer.start()

        activityTestRule.launchActivity(null)

        homeScreen {
        }.dismissOnboarding()

        homeScreen {
        }.openSearch {
        }.openBrowser {}
    }

    @Test
    fun serverResponse500StartupTest() {
        mockWebServer = MockWebServerHelper.createErrorResponseMockWebServer(500)
        mockWebServer.start()

        activityTestRule.launchActivity(null)

        homeScreen {
        }.dismissOnboarding()

        homeScreen {
        }.openSearch {
        }.openBrowser {}
    }

    @Test
    fun badJSONResponseStartupTest() {
        val body = "{badJsonResponse}"
        val header = Pair("Content-Type", "application/json; charset=utf-8")

        mockWebServer = MockWebServerHelper.createErrorResponseMockWebServer(200, body, header)
        mockWebServer.start()

        activityTestRule.launchActivity(null)

        homeScreen {
        }.dismissOnboarding()

        homeScreen {
        }.openSearch {
        }.openBrowser {}
    }

    @Test
    fun badHeaderResponseStartupTest() {
        val body = "{badResponse}"
        val header = Pair("badName", "badValue")

        mockWebServer = MockWebServerHelper.createErrorResponseMockWebServer(200, body, header)
        mockWebServer.start()

        activityTestRule.launchActivity(null)

        homeScreen {
        }.dismissOnboarding()

        homeScreen {
        }.openSearch {
        }.openBrowser {}
    }
}
