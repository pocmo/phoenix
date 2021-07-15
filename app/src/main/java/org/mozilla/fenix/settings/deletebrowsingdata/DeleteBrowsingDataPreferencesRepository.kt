/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.deletebrowsingdata

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.R
import org.mozilla.fenix.dataStore
import org.mozilla.fenix.ext.getPreferenceKey
import java.io.IOException

data class DeleteBrowsingDataPreferences(
    val deleteOpenTabs: Boolean,
    val deleteBrowsingHistory: Boolean,
    val deleteCookies: Boolean,
    val deleteCache: Boolean,
    val deleteSitePermissions: Boolean,
    val deleteDownloads: Boolean
)

/**
 * Class that handles saving and retrieving preferences for [DeleteBrowsingDataFragment].
 */
class DeleteBrowsingDataPreferencesRepository(context: Context) {

    private val logger = Logger("DeleteBrowsingDataPreferencesRepository")
    private val dataStore: DataStore<Preferences> = context.dataStore

    private val deleteOpenTabsKey =
        booleanPreferencesKey(context.getPreferenceKey(R.string.pref_key_delete_open_tabs_now))
    private val deleteBrowsingHistoryKey =
        booleanPreferencesKey(context.getPreferenceKey(R.string.pref_key_delete_browsing_history_now))
    private val deleteCookiesKey =
        booleanPreferencesKey(context.getPreferenceKey(R.string.pref_key_delete_cookies_now))
    private val deleteCacheKey =
        booleanPreferencesKey(context.getPreferenceKey(R.string.pref_key_delete_caches_now))
    private val deleteSitePermissionsKey =
        booleanPreferencesKey(context.getPreferenceKey(R.string.pref_key_delete_permissions_now))
    private val deleteDownloadsKey =
        booleanPreferencesKey(context.getPreferenceKey(R.string.pref_key_delete_downloads_now))

    /**
     * Get delete browsing data preference flow.
     */
    val deleteBrowsingDataPreferencesFlow: Flow<DeleteBrowsingDataPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                logger.error("Error reading preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val deleteOpenTabs = preferences[deleteOpenTabsKey] ?: true
            val deleteBrowsingHistory = preferences[deleteBrowsingHistoryKey] ?: true
            val deleteCookies = preferences[deleteCookiesKey] ?: true
            val deleteCache = preferences[deleteCacheKey] ?: true
            val deleteSitePermissions = preferences[deleteSitePermissionsKey] ?: true
            val deleteDownloads = preferences[deleteDownloadsKey] ?: true

            DeleteBrowsingDataPreferences(
                deleteOpenTabs = deleteOpenTabs,
                deleteBrowsingHistory = deleteBrowsingHistory,
                deleteCookies = deleteCookies,
                deleteCache = deleteCache,
                deleteSitePermissions = deleteSitePermissions,
                deleteDownloads = deleteDownloads
            )
        }

    /**
     * Enable or disable delete open tabs.
     */
    suspend fun enableDeleteOpenTabs(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[deleteOpenTabsKey] = enable
        }
    }

    /**
     * Enable or disable delete browsing history.
     */
    suspend fun enableDeleteBrowsingHistory(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[deleteBrowsingHistoryKey] = enable
        }
    }

    /**
     * Enable or disable delete cookies.
     */
    suspend fun enableDeleteCookies(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[deleteCookiesKey] = enable
        }
    }

    /**
     * Enable or disable delete cache.
     */
    suspend fun enableDeleteCache(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[deleteCacheKey] = enable
        }
    }

    /**
     * Enable or disable delete site permissions.
     */
    suspend fun enableDeleteSitePermissions(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[deleteSitePermissionsKey] = enable
        }
    }

    /**
     * Enable or disable delete downloads.
     */
    suspend fun enableDeleteDownloads(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[deleteDownloadsKey] = enable
        }
    }
}
