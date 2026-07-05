package com.shadowmesh.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.content.ComponentName
import android.content.pm.PackageManager
import uniffi.shadowmesh.ApiClient
import uniffi.shadowmesh.ConnectionStats
import uniffi.shadowmesh.ConnectionStatus
import uniffi.shadowmesh.TrafficModePreference
import uniffi.shadowmesh.UserSettings
import uniffi.shadowmesh.VpnManager
import uniffi.shadowmesh.VpnNode
import uniffi.shadowmesh.VpnConfig
import uniffi.shadowmesh.NodeCache
import uniffi.shadowmesh.ActivationRequest
import uniffi.shadowmesh.ActivationResponse
import uniffi.shadowmesh.PoWChallenge
import uniffi.shadowmesh.solvePow
import uniffi.shadowmesh.createApiClient
import uniffi.shadowmesh.createVpnManager
import uniffi.shadowmesh.createNodeCache
import uniffi.shadowmesh.createSecurityLogger
import uniffi.shadowmesh.SecurityEventLogger
import uniffi.shadowmesh.SecurityEventType
import uniffi.shadowmesh.getDefaultUserSettings
import uniffi.shadowmesh.getMockNodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import android.provider.Settings

private const val TAG = "VPNManagerViewModel"

class VPNManagerViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(VPNUiState())
    val uiState: StateFlow<VPNUiState> = _uiState.asStateFlow()

    private val wireguardService = WireGuardService.instance
    private var vpnManager: VpnManager? = null
    private var apiClient: ApiClient? = null
    private var nodeCache: NodeCache? = null
    private var wgKeys: Pair<String, String>? = null
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        Config.PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val json = Json { ignoreUnknownKeys = true }

    companion object {

    }

    init {
        viewModelScope.launch {
            initialize()
            startStatsPolling()
            loadSecuritySettings()
        }
    }

    fun hasAcceptedVpnConsent(): Boolean {
        return securePrefs.getBoolean(Config.KEY_VPN_CONSENT, false)
    }

    fun acceptVpnConsent() {
        securePrefs.edit().putBoolean(Config.KEY_VPN_CONSENT, true).apply()
    }

    private suspend fun initialize() {
        wireguardService.initialize(context)
        val manager = wireguardService.vpnManager
        if (manager == null) {
            _uiState.update { it.copy(errorMessage = "Failed to initialize VPN Manager") }
            return
        }
        
        try {
            val client = createApiClient(Config.DEFAULT_API_URL)
            val cache = createNodeCache(100u, 86400u)
            val logger = createSecurityLogger(
                deviceId = getPersistentDeviceId(context),
                appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0",
                storageDir = context.filesDir.path
            )
            vpnManager = manager
            apiClient = client
            nodeCache = cache

            val isActivated = manager.isActivated()
            val activationCode = securePrefs.getString(Config.KEY_ACTIVATION_CODE, null)

            // Load cached nodes from Rust NodeCache (fallback to mock)
            var nodes = cache.getAll()
            if (nodes.isEmpty()) {
                nodes = getMockNodes()
            }
            var selectedNode = nodes.firstOrNull()
            selectedNode?.let { manager.setSelectedNode(it) }
            manager.setNodes(nodes)

            // Try to fetch fresh nodes from API
            try {
                val freshNodes = client.getNodes()
                if (freshNodes.isNotEmpty()) {
                    nodes = freshNodes
                    selectedNode = nodes.firstOrNull()
                    selectedNode?.let { manager.setSelectedNode(it) }
                    manager.setNodes(nodes)
                    cache.putAll(nodes)
                }
            } catch (e: Exception) {
                // Keep cached nodes on failure
            }

            wgKeys = wireguardService.generateKeyPair()

            _uiState.update {
                it.copy(
                    vpnManager = manager,
                    apiClient = client,
                    securityEventLogger = logger,
                    status = manager.getStatus(),
                    nodes = nodes,
                    selectedNode = selectedNode,
                    isActivated = isActivated,
                    activationCode = activationCode,
                    showActivationScreen = !isActivated,
                    userSettings = manager.getUserSettings(),
                    killSwitchEnabled = manager.isKillSwitchEnabled(),
                    trafficModePreference = manager.getTrafficModePreference(),
                    splitTunnelConfig = manager.getSplitTunnelConfig()
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = e.message)
            }
        }
    }

    fun activate(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActivating = true, errorMessage = null) }
            try {
                val client = apiClient ?: return@launch
                val deviceId = getPersistentDeviceId(context)
                
                // 1. Request Challenge
                val challenge = client.requestActivationChallenge(deviceId)
                
                // 2. Solve PoW
                val solution = solvePow(challenge)
                
                // 3. Activate
                val request = ActivationRequest(
                    activationCode = code,
                    deviceId = deviceId,
                    solution = solution.solution,
                    nonce = solution.nonce,
                    timestamp = solution.timestamp,
                    signature = solution.signature
                )
                
                val response = client.activate(request)
                
                if (response.success) {
                    val token = response.authToken
                    client.setAuthToken(token)
                    vpnManager?.activate(code, token)
                    
                    securePrefs.edit().putString(Config.KEY_ACTIVATION_CODE, code).apply()
                    
                    _uiState.update { 
                        it.copy(
                            isActivated = true,
                            activationCode = code,
                            showActivationScreen = false,
                            isActivating = false
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isActivating = false,
                            errorMessage = response.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isActivating = false,
                        errorMessage = e.message ?: "Activation failed"
                    )
                }
            }
        }
    }

    private fun loadCachedNodes(): List<VpnNode> {
        return nodeCache?.getAll() ?: emptyList()
    }

    private fun cacheNodes(nodes: List<VpnNode>) {
        nodeCache?.putAll(nodes)
    }

    fun selectNode(node: VpnNode) {
        _uiState.update { it.copy(selectedNode = node) }
        vpnManager?.setSelectedNode(node)
    }

    fun loadNodes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNodes = true) }
            try {
                val nodes = try {
                    val freshNodes = apiClient?.getNodes()
                    if (!freshNodes.isNullOrEmpty()) {
                        cacheNodes(freshNodes)
                        freshNodes
                    } else {
                        loadCachedNodes().ifEmpty { getMockNodes() }
                    }
                } catch (e: Exception) {
                    loadCachedNodes().ifEmpty { getMockNodes() }
                }
                _uiState.update {
                    it.copy(nodes = nodes, isLoadingNodes = false)
                }
                vpnManager?.setNodes(nodes)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message, isLoadingNodes = false)
                }
            }
        }
    }

    fun toggleConnection() {
        viewModelScope.launch {
            val currentStatus = _uiState.value.status
            if (currentStatus == ConnectionStatus.DISCONNECTED) {
                connect()
            } else {
                disconnect()
            }
        }
    }

    private suspend fun connect() {
        val currentState = _uiState.value
        val manager = currentState.vpnManager ?: return
        val selectedNode = currentState.selectedNode ?: currentState.nodes.firstOrNull() ?: return

        _uiState.update { it.copy(isConnecting = true, status = ConnectionStatus.CONNECTING_DIRECT) }

        try {
            val keys = wgKeys ?: wireguardService.generateKeyPair()
            wgKeys = keys

            val dnsServers = if (currentState.userSettings.dnsServers.isNotEmpty()) {
                currentState.userSettings.dnsServers
            } else {
                Config.DEFAULT_DNS_SERVERS
            }

            // Fetch dynamic configuration from the mesh API
            val deviceId = this@VPNManagerViewModel.getPersistentDeviceId()
            val config = try {
                apiClient?.getConfig(selectedNode.id, deviceId)
            } catch (e: Exception) {
                null
            }

            val clientAddress = config?.address ?: "10.0.0.2/32"
            val dnsServersFinal = config?.dns ?: if (currentState.userSettings.dnsServers.isNotEmpty()) {
                currentState.userSettings.dnsServers
            } else {
                Config.DEFAULT_DNS_SERVERS
            }
            val mtu = config?.mtu ?: if (selectedNode.region == "CN" || selectedNode.region == "IR") Config.CHINA_IRAN_MTU else Config.DEFAULT_MTU

            manager.initiate_connection(selectedNode, keys.second)

            wireguardService.connect(
                node = selectedNode,
                clientConfig = VpnConfig(
                    privateKey = keys.first,
                    publicKey = keys.second,
                    address = clientAddress,
                    dns = dnsServersFinal,
                    mtu = mtu
                )
            )
            
            manager.complete_connection()

            _uiState.update {
                it.copy(
                    status = manager.getStatus(),
                    isConnecting = false
                )
            }
            startHeartbeat()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    isConnecting = false,
                    errorMessage = e.message
                )
            }
        }
    }

    private suspend fun disconnect() {
        val manager = _uiState.value.vpnManager ?: return
        wireguardService.disconnect()
        manager.disconnect()
        _uiState.update {
            it.copy(status = manager.getStatus())
        }
    }

    private fun startStatsPolling() {
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.status == ConnectionStatus.CONNECTED) {
                    val stats = wireguardService.getStats()
                    _uiState.update { it.copy(connectionStats = stats) }
                }
                delay(2000)
            }
        }
    }

    private fun startHeartbeat() {
        viewModelScope.launch {
            while (_uiState.value.status == ConnectionStatus.CONNECTED) {
                delay(30000) // 30 second heartbeat for production
                try {
                    val code = _uiState.value.activationCode ?: return@launch
                    val deviceId = getPersistentDeviceId(context)
                    
                    val success = apiClient?.heartbeat(code, deviceId) == true
                    if (!success) {
                        Log.w(TAG, "Heartbeat failed, session may be invalid")
                    }
                    
                    // Also refresh nodes periodically
                    val freshNodes = apiClient?.getNodes()
                    if (!freshNodes.isNullOrEmpty()) {
                        cacheNodes(freshNodes)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error: ${e.message}")
                }
            }
        }
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isKillSwitchLoading = true, killSwitchError = null) }
            try {
                vpnManager?.setKillSwitchEnabled(enabled)
                if (enabled) {
                    wireguardService.activateKillSwitch()
                } else {
                    wireguardService.deactivateKillSwitch()
                }
                _uiState.value.securityEventLogger?.logEvent(
                    SecurityEventType.KILL_SWITCH_STATE_CHANGE,
                    "Kill switch ${if (enabled) "enabled" else "disabled"}",
                    true
                )
                _uiState.update { it.copy(killSwitchEnabled = enabled, isKillSwitchLoading = false) }
            } catch (e: Exception) {
                _uiState.value.securityEventLogger?.logEvent(
                    SecurityEventType.KILL_SWITCH_STATE_CHANGE,
                    "Failed to ${if (enabled) "enable" else "disable"} kill switch: ${e.message}",
                    false
                )
                _uiState.update { 
                    it.copy(
                        killSwitchError = "Failed to update kill switch: ${e.message}",
                        isKillSwitchLoading = false,
                        killSwitchEnabled = vpnManager?.isKillSwitchEnabled() ?: false
                    ) 
                }
            }
        }
    }

    fun setTrafficModePreference(preference: TrafficModePreference) {
        viewModelScope.launch {
            vpnManager?.setTrafficModePreference(preference)
            _uiState.update { it.copy(trafficModePreference = preference) }
        }
    }

    fun setSplitTunnelConfig(config: uniffi.shadowmesh.SplitTunnelConfig) {
        viewModelScope.launch {
            vpnManager?.setSplitTunnelConfig(config)
            _uiState.update { it.copy(splitTunnelConfig = config) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFail()
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationFailed() {
                    // Retry allowed, do nothing
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ShadowMesh Security")
            .setSubtitle("Authenticate to access VPN")
            .setNegativeButtonText("Enter PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun verifyPin(pin: String): Boolean {
        val panicSalt = securePrefs.getString(Config.KEY_PANIC_PIN_SALT, null)
        val panicHash = securePrefs.getString(Config.KEY_PANIC_PIN_HASH, null)
        if (panicSalt != null && panicHash != null && hashPinWithSalt(pin, panicSalt) == panicHash) {
            triggerPanicWipe()
            return false
        }
        
        val salt = securePrefs.getString(Config.KEY_PIN_SALT, null)
        val storedHash = securePrefs.getString(Config.KEY_PIN_HASH, null)
        if (storedHash == null || salt == null) {
            // No PIN set, allow anything for now but should force PIN set in prod
            return true
        }
        
        return hashPinWithSalt(pin, salt) == storedHash
    }

    fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        securePrefs.edit()
            .putString(Config.KEY_PIN_HASH, hash)
            .putString(Config.KEY_PIN_SALT, salt)
            .apply()
        _uiState.update { it.copy(isSecurityLockEnabled = true) }
    }

    fun setPanicPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        securePrefs.edit()
            .putString(Config.KEY_PANIC_PIN_HASH, hash)
            .putString(Config.KEY_PANIC_PIN_SALT, salt)
            .apply()
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPinWithSalt(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun loadSecuritySettings() {
        val hasPin = securePrefs.contains(Config.KEY_PIN_HASH)
        _uiState.update { it.copy(isSecurityLockEnabled = hasPin) }
    }

    fun triggerPanicWipe() {
        Log.w(TAG, "!!! PANIC WIPE TRIGGERED !!!")
        // Trigger Rust kill switch
        vpnManager?.setKillSwitchEnabled(true)
        // Clear nodes
        nodeCache?.clear()
        // Clear keys
        wgKeys = null
        // Clear all secure preferences
        securePrefs.edit().clear().apply()
        // Reset state
        _uiState.update { 
            it.copy(
                nodes = emptyList(), 
                selectedNode = null, 
                isSecurityLockEnabled = false,
                killSwitchEnabled = true 
            ) 
        }
    }

    fun toggleCamouflageMode(enabled: Boolean) {
        val pm = context.packageManager
        val mainActivity = ComponentName(context, "com.shadowmesh.app.MainActivity")
        val notesAlias = ComponentName(context, "com.shadowmesh.app.NotesAlias")

        if (enabled) {
            pm.setComponentEnabledSetting(
                notesAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                mainActivity,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            pm.setComponentEnabledSetting(
                mainActivity,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                notesAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun addCustomDNSServer(dns: String) {
        _uiState.update { it.copy(customDNSServers = it.customDNSServers + dns) }
    }

    fun removeCustomDNSServer(dns: String) {
        _uiState.update { it.copy(customDNSServers = it.customDNSServers.filterNot { it == dns }) }
    }

    fun setShowCustomDNSSettings(show: Boolean) {
        _uiState.update { it.copy(showCustomDNSSettings = show) }
    }

    fun setShowVPNGuideModal(show: Boolean) {
        _uiState.update { it.copy(showVPNGuideModal = show) }
    }
}

data class VPNUiState(
    val vpnManager: VpnManager? = null,
    val apiClient: ApiClient? = null,
    val securityEventLogger: SecurityEventLogger? = null,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val selectedNode: VpnNode? = null,
    val nodes: List<VpnNode> = emptyList(),
    val isLoadingNodes: Boolean = false,
    val isConnecting: Boolean = false,
    val isActivated: Boolean = false,
    val activationCode: String? = null,
    val showActivationScreen: Boolean = false,
    val isActivating: Boolean = false,
    val errorMessage: String? = null,
    val userSettings: UserSettings = getDefaultUserSettings(),
    val connectionStats: ConnectionStats = ConnectionStats(0u, 0u, 0u, 0u, 0L, 0L),
    val killSwitchEnabled: Boolean = false,
    val isKillSwitchLoading: Boolean = false,
    val killSwitchError: String? = null,
    val trafficModePreference: TrafficModePreference = TrafficModePreference.AUTO,
    val isSecurityLockEnabled: Boolean = false,
    val splitTunnelConfig: uniffi.shadowmesh.SplitTunnelConfig = uniffi.shadowmesh.SplitTunnelConfig(false, "exclude", emptyList()),
    val customDNSServers: List<String> = emptyList(),
    val showCustomDNSSettings: Boolean = false,
    val showVPNGuideModal: Boolean = false
)

fun getPersistentDeviceId(context: Context): String {
    val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    var deviceId = sharedPrefs.getString(Config.KEY_DEVICE_ID, null)
    if (deviceId == null) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val secureRandom = SecureRandom()
        val randomBytes = ByteArray(16)
        secureRandom.nextBytes(randomBytes)
        val randomPart = randomBytes.joinToString("") { "%02x".format(it) }
        deviceId = "$androidId-$randomPart"
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(deviceId.toByteArray())
        deviceId = hashBytes.joinToString("") { "%02x".format(it) }
        
        sharedPrefs.edit().putString(Config.KEY_DEVICE_ID, deviceId).apply()
    }
    return deviceId
}
