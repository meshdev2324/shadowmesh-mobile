package com.shadowmesh.app

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import uniffi.shadowmesh.*
import android.content.Context
import android.content.SharedPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class VPNManagerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var viewModel: VPNManagerViewModel
    private lateinit var vpnManager: VpnManager
    private lateinit var apiClient: ApiClient
    private lateinit var nodeCache: NodeCache
    private lateinit var securityEventLogger: SecurityEventLogger

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        vpnManager = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        nodeCache = mockk(relaxed = true)
        securityEventLogger = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        
        mockkStatic("uniffi.shadowmesh.ShadowmeshKt")
        every { getDefaultUserSettings() } returns UserSettings(emptyList(), false, TrafficModePreference.AUTO)
        every { createVpnManager(any()) } returns vpnManager
        every { createApiClient(any()) } returns apiClient
        every { createNodeCache(any(), any()) } returns nodeCache
        every { createSecurityEventLogger(any()) } returns securityEventLogger
        every { getMockNodes() } returns emptyList()

        viewModel = VPNManagerViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should be disconnected`() = runTest {
        every { vpnManager.getStatus() } returns ConnectionStatus.DISCONNECTED
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
    }

    @Test
    fun `selectNode updates selectedNode in state`() = runTest {
        val node = VpnNode("1", "Test", "reg", "US", "1.1.1.1", "key", 0u, 0.0)
        every { vpnManager.getStatus() } returns ConnectionStatus.DISCONNECTED
        
        viewModel.selectNode(node)
        
        assertEquals(node, viewModel.uiState.value.selectedNode)
    }

    @Test
    fun `setKillSwitch should update manager and state`() = runTest {
        viewModel.setKillSwitch(true)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { vpnManager.setKillSwitchEnabled(true) }
        assertEquals(true, viewModel.uiState.value.killSwitchEnabled)
    }

    @Test
    fun `setTrafficMode updates state`() = runTest {
        viewModel.setTrafficMode(TrafficModePreference.HYBRID)
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify { vpnManager.setTrafficModePreference(TrafficModePreference.HYBRID) }
        assertEquals(TrafficModePreference.HYBRID, viewModel.uiState.value.trafficModePreference)
    }

    @Test
    fun `loadNodes fetches and updates state`() = runTest {
        val testNodes = listOf(
            VpnNode("1", "Test 1", "reg", "US", "1.1.1.1", "key", 0u, 0.0))
        every { vpnManager.getNodes() } returns testNodes
        
        viewModel.loadNodes()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(testNodes, viewModel.uiState.value.nodes)
    }
}
