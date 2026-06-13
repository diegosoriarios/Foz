package com.example.foz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PrefsManagerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var prefsManager: PrefsManager

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        val testFile = File(tmpFolder.newFolder(), "test_prefs.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        // We still need a Context for preferencesDataStore initialization, 
        // but our refactored PrefsManager will use the passed testDataStore.
        val mockContext: Context = mock()
        prefsManager = PrefsManager(mockContext, testDataStore)
    }

    @Test
    fun setClockUse24h_updatesFlow() = runTest(testDispatcher) {
        prefsManager.setClockUse24h(false)
        assertEquals(false, prefsManager.clockUse24h.first())
        
        prefsManager.setClockUse24h(true)
        assertEquals(true, prefsManager.clockUse24h.first())
    }

    @Test
    fun setAppIconSizeDp_coercesValue() = runTest(testDispatcher) {
        prefsManager.setAppIconSizeDp(10) // below min 24
        assertEquals(24, prefsManager.appIconSizeDp.first())
        
        prefsManager.setAppIconSizeDp(100) // above max 64
        assertEquals(64, prefsManager.appIconSizeDp.first())
        
        prefsManager.setAppIconSizeDp(40) // within range
        assertEquals(40, prefsManager.appIconSizeDp.first())
    }

    @Test
    fun setDrawerPaddingPercent_updatesFlow() = runTest(testDispatcher) {
        prefsManager.setDrawerPaddingPercent(0.2f)
        assertEquals(0.2f, prefsManager.drawerPaddingPercent.first())
    }

    @Test
    fun setAppPinned_managesOrderedList() = runTest(testDispatcher) {
        assertTrue(prefsManager.pinnedApps.first().isEmpty())
        
        prefsManager.setAppPinned("pkg1", true)
        assertEquals(listOf("pkg1"), prefsManager.pinnedApps.first())
        
        prefsManager.setAppPinned("pkg2", true)
        assertEquals(listOf("pkg1", "pkg2"), prefsManager.pinnedApps.first())
        
        prefsManager.setAppPinned("pkg1", false)
        assertEquals(listOf("pkg2"), prefsManager.pinnedApps.first())
    }
}
