
//
//  VPNManagerViewModel.swift
//  ShadowMesh
//
//  Created on 6/29/24.
//

import Foundation
import Combine
import SwiftUI
import LocalAuthentication
import uniffi_shadowmesh

@MainActor
class VPNManagerViewModel: ObservableObject {
    // MARK: - Constants
    private let DEFAULT_API_URL = "https://api.shadowmesh.org/api/v1"

    // MARK: - Published State
    @Published var vpnManager: VpnManager?
    @Published var apiClient: ApiClient?
    @Published var status: ConnectionStatus = .disconnected
    @Published var selectedNode: VpnNode?
    @Published var nodes: [VpnNode] = []
    @Published var isLoadingNodes = false
    @Published var isConnecting = false
    @Published var isActivated = false
    @Published var activationCode: String?
    @Published var showActivationScreen = false
    @Published var isActivating = false
    @Published var errorMessage: String?
    @Published var userSettings: UserSettings
    
    private var nodeCache: NodeCache?
    private var securityEventLogger: SecurityEventLogger?
    @Published var connectionStats: ConnectionStats = ConnectionStats(
        bytesReceived: 0,
        bytesSent: 0,
        packetsReceived: 0,
        packetsSent: 0,
        lastHandshake: 0,
        connectedSince: 0
    )
    @Published var killSwitchEnabled: Bool = false
    @Published var isKillSwitchLoading: Bool = false
    @Published var killSwitchError: String? = nil
    @Published var trafficModePreference: TrafficModePreference = .auto
    @Published var isSecurityLockEnabled: Bool = false
    @Published var splitTunnelConfig: SplitTunnelConfig = SplitTunnelConfig(enabled: false, mode: "exclude", appList: [])
    @Published var customDNSServers: [String] = [] // Store custom DNS servers
    @Published var showCustomDNSSettings: Bool = false // Toggle for custom DNS
    @Published var showVPNGuideModal: Bool = false // Toggle for VPN guide modal
    
    // MARK: - Services
    private let wireguardService = WireGuardService.shared
    private let secureStorage = SecureStorage.shared
    private let KEY_PIN_HASH = "pin_hash"
    private let KEY_PANIC_PIN_HASH = "panic_pin_hash"
    private var wgKeys: (privateKey: String, publicKey: String)?
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init() {
        self.userSettings = getDefaultUserSettings()
        Task {
            await initialize()
        }
    }
    
    private func initialize() async {
        await _ = wireguardService.initialize()
        guard let manager = wireguardService.vpnManager else {
            self.errorMessage = "Failed to initialize VPN manager"
            return
        }

        do {
            let client = try createApiClient(baseUrl: DEFAULT_API_URL)
            let cache = try createNodeCache(maxSize: 100, ttlSeconds: 86400)
            let logger = try createSecurityLogger(
                deviceId: getPersistentDeviceId(),
                appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
                storageDir: FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? NSTemporaryDirectory()
            )
            self.vpnManager = manager
            self.apiClient = client
            self.nodeCache = cache
            self.securityEventLogger = logger
            self.userSettings = manager.getUserSettings()
            self.killSwitchEnabled = manager.isKillSwitchEnabled()
            self.trafficModePreference = manager.getTrafficModePreference()
            self.splitTunnelConfig = manager.getSplitTunnelConfig()
            self.status = manager.getStatus()
            self.isActivated = manager.isActivated()
            self.showActivationScreen = !self.isActivated

            // Load saved settings from secure storage
            loadSavedSettings()
            
            // Load nodes
            await loadNodes()
            
            // Generate WireGuard keys
            self.wgKeys = wireguardService.generateKeyPair()

            if self.status == .connected {
                startStatsPolling()
            }
        } catch {
            self.errorMessage = "Failed to initialize VPN manager: \(error.localizedDescription)"
            print("Error initializing VPN manager: \(error)")
        }
    }

    func activate(code: String) async {
        self.isActivating = true
        self.errorMessage = nil

        do {
            guard let client = apiClient else { return }
            let deviceId = getPersistentDeviceId()

            // 1. Request Challenge
            let challenge = try client.requestActivationChallenge(deviceId: deviceId)

            // 2. Solve PoW
            let solution = try solvePow(challenge: challenge)

            // 3. Activate
            let request = ActivationRequest(
                activationCode: code,
                deviceId: deviceId,
                solution: solution.solution,
                nonce: solution.nonce,
                timestamp: solution.timestamp,
                signature: solution.signature
            )

            let response = try client.activate(req: request)

            if response.success {
                let token = response.authToken
                client.setAuthToken(token: token)
                try vpnManager?.activate(code: code, token: token)

                // Save to secure storage
                secureStorage.save("activation_code", value: code)

                self.isActivated = true
                self.activationCode = code
                self.showActivationScreen = false
                self.isActivating = false
            } else {
                self.isActivating = false
                self.errorMessage = response.message
            }
        } catch {
            self.isActivating = false
            self.errorMessage = error.localizedDescription
        }
    }
        }
    }
    
    // MARK: - Persistence
    private func loadSavedSettings() {
        // Load Security Lock state
        self.isSecurityLockEnabled = secureStorage.getBool(forKey: "isSecurityLockEnabled") ?? false
        // Load from SecureStorage
        if let killSwitch = secureStorage.getBool(forKey: "killSwitchEnabled") {
            self.killSwitchEnabled = killSwitch
            vpnManager?.setKillSwitchEnabled(enabled: killSwitch)
        }
        // Load cached nodes from UserDefaults
        loadCachedNodes()
    }
    
    // MARK: - PIN Management
    func setPin(_ pin: String) {
        let salt = secureStorage.generateSalt()
        let hash = secureStorage.hashPin(pin, salt: salt)
        try? secureStorage.setString(hash, forKey: KEY_PIN_HASH)
        try? secureStorage.setString(salt, forKey: secureStorage.KEY_PIN_SALT)
        self.isSecurityLockEnabled = true
        secureStorage.setBool(true, forKey: "isSecurityLockEnabled")
    }
    
    func setPanicPin(_ pin: String) {
        let salt = secureStorage.generateSalt()
        let hash = secureStorage.hashPin(pin, salt: salt)
        try? secureStorage.setString(hash, forKey: KEY_PANIC_PIN_HASH)
        try? secureStorage.setString(salt, forKey: secureStorage.KEY_PANIC_PIN_SALT)
    }
    
    private func saveSettings() {
        secureStorage.setBool(killSwitchEnabled, forKey: "killSwitchEnabled")
        secureStorage.setBool(isSecurityLockEnabled, forKey: "isSecurityLockEnabled")
    }
    
    private func loadCachedNodes() {
        if let cache = nodeCache {
            let cachedNodes = cache.getAll()
            if !cachedNodes.isEmpty {
                self.nodes = cachedNodes
                self.vpnManager?.setNodes(nodes: cachedNodes)
                if let firstNode = cachedNodes.first {
                    self.selectedNode = firstNode
                    self.vpnManager?.setSelectedNode(node: firstNode)
                }
            }
        }
    }
    
    private func cacheNodes(_ nodes: [VpnNode]) {
        nodeCache?.putAll(nodes: nodes)
    }
    
    // MARK: - Node Management
    func loadNodes() async {
        isLoadingNodes = true
        defer { isLoadingNodes = false }
        
        do {
            // Try to load from API first
            if let client = apiClient {
                do {
                    let apiNodes = try client.getNodes()
                    self.nodes = apiNodes
                    self.vpnManager?.setNodes(nodes: apiNodes)
                    cacheNodes(apiNodes)
                } catch {
                    print("API failed, trying cached nodes: \(error)")
                    // Fall back to cached nodes if available, otherwise mock
                    if nodes.isEmpty {
                        let mockNodes = getMockNodes()
                        self.nodes = mockNodes
                        self.vpnManager?.setNodes(nodes: mockNodes)
                    }
                }
            } else {
                // No API client, try cache then mock
                if nodes.isEmpty {
                    let mockNodes = getMockNodes()
                    self.nodes = mockNodes
                    self.vpnManager?.setNodes(nodes: mockNodes)
                }
            }
            
            if let firstNode = nodes.first {
                self.selectedNode = firstNode
                self.vpnManager?.setSelectedNode(node: firstNode)
            }
        } catch {
            self.errorMessage = "Failed to load nodes: \(error.localizedDescription)"
        }
    }
    
    func selectNode(_ node: VpnNode) {
        self.selectedNode = node
        self.vpnManager?.setSelectedNode(node: node)
    }
    
    // MARK: - Connection Management
    func toggleConnection() async {
        if status == .disconnected {
            await connect()
        } else {
            await disconnect()
        }
    }
    
    private func connect() async {
        guard let vpnManager = vpnManager,
              let selectedNode = selectedNode else {
            self.errorMessage = "No node selected"
            return
        }
        
        let keys = wgKeys ?? wireguardService.generateKeyPair()
        self.wgKeys = keys
        
        isConnecting = true
        defer { isConnecting = false }
        
        do {
            // Update status to connecting via VpnManager FSM
            try vpnManager.initiateConnection(node: selectedNode, devicePublicKey: keys.publicKey)
            self.status = vpnManager.getStatus()

            let dnsServers = !userSettings.dnsServers.isEmpty ? userSettings.dnsServers : ["1.1.1.1", "8.8.8.8"]
            
            try await wireguardService.connect(
                node: selectedNode,
                clientConfig: VpnConfig(
                    privateKey: keys.privateKey,
                    publicKey: keys.publicKey,
                    address: "10.0.0.2/32",
                    dns: dnsServers,
                    mtu: 1420
                )
            )
            
            // Complete connection
            vpnManager.completeConnection()
            self.status = vpnManager.getStatus()
            startHeartbeat()
            startStatsPolling()
        } catch {
            self.status = .error
            self.errorMessage = "Connection failed: \(error.localizedDescription)"
            print("Connection error: \(error)")
        }
    }
    
    private func startHeartbeat() {
        Task {
            while status == .connected {
                try? await Task.sleep(nanoseconds: 30_000_000_000) // 30 seconds
                do {
                    guard let client = apiClient, let code = activationCode else { continue }
                    let deviceId = getPersistentDeviceId()

                    let success = try client.heartbeat(code: code, deviceId: deviceId)
                    if !success {
                        print("Heartbeat failed")
                    }

                    if let freshNodes = try apiClient?.getNodes(), !freshNodes.isEmpty {
                        cacheNodes(freshNodes)
                    }
                } catch {
                    print("Heartbeat error: \(error)")
                }
            }
        }
    }
    
    private func disconnect() async {
        guard let vpnManager = vpnManager else { return }
        
        await wireguardService.disconnect()
        vpnManager.disconnect()
        self.status = vpnManager.getStatus()
        stopStatsPolling()
    }

    private var statsTask: Task<Void, Never>?

    private func startStatsPolling() {
        statsTask?.cancel()
        statsTask = Task {
            while !Task.isCancelled {
                if status == .connected {
                    self.connectionStats = wireguardService.getStats()
                }
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }
    }

    private func stopStatsPolling() {
        statsTask?.cancel()
        statsTask = nil
    }
    
    // MARK: - Settings
    func updateUserSettings(_ settings: UserSettings) {
        self.userSettings = settings
        saveSettings()
    }
    
    func setKillSwitch(_ enabled: Bool) {
        Task { @MainActor in
            self.isKillSwitchLoading = true
            self.killSwitchError = nil
            defer { self.isKillSwitchLoading = false }
            
            do {
                vpnManager?.setKillSwitchEnabled(enabled: enabled)
                if enabled {
                    try await wireguardService.activateKillSwitch()
                } else {
                    try await wireguardService.deactivateKillSwitch()
                }
                self.killSwitchEnabled = enabled
                securityEventLogger?.logEvent(
                    eventType: .killSwitchStateChange,
                    details: "Kill switch \(enabled ? "enabled" : "disabled")",
                    success: true
                )
                saveSettings()
            } catch {
                // Rollback on failure
                self.killSwitchError = "Failed to update kill switch: \(error.localizedDescription)"
                self.killSwitchEnabled = vpnManager?.isKillSwitchEnabled() ?? false
                securityEventLogger?.logEvent(
                    eventType: .killSwitchStateChange,
                    details: "Failed to \(enabled ? "enable" : "disable") kill switch: \(error.localizedDescription)",
                    success: false
                )
            }
        }
    }
    
    func setTrafficModePreference(_ preference: TrafficModePreference) {
        self.trafficModePreference = preference
        vpnManager?.setTrafficModePreference(preference: preference)
    }

    func setSecurityLockEnabled(_ enabled: Bool) {
        self.isSecurityLockEnabled = enabled
        saveSettings()
    }
    
    // MARK: - Advanced Security
    func authenticateWithBiometrics(
        onSuccess: @escaping () -> Void,
        onFail: @escaping () -> Void
    ) {
        let context = LAContext()
        var error: NSError?
        
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            let reason = "Authenticate to access ShadowMesh"
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authenticationError in
                DispatchQueue.main.async {
                    if success {
                        onSuccess()
                    } else {
                        onFail()
                    }
                }
            }
        } else {
            // No biometrics available
            onFail()
        }
    }
    
    func triggerPanicWipe() {
        PanicWipeManager.shared.executePanicWipe()

        // Reset local state
        self.nodes = []
        self.selectedNode = nil
        self.status = .disconnected
    }
    
    func toggleCamouflageMode(enabled: Bool) {
        if enabled {
            UIApplication.shared.setAlternateIconName("NotesIcon") { error in
                if let error = error {
                    print("Failed to set alternate icon: \(error.localizedDescription)")
                }
            }
        } else {
            UIApplication.shared.setAlternateIconName(nil) { error in
                if let error = error {
                    print("Failed to restore icon: \(error.localizedDescription)")
                }
            }
        }
    }
}

// MARK: - Type Extensions for SwiftUI
extension ConnectionStatus: CustomStringConvertible {
    public var description: String {
        switch self {
        case .disconnected: return NSLocalizedString("status_ready", comment: "")
        case .connectingDirect: return NSLocalizedString("status_connecting_phase1", comment: "")
        case .connectingFragmented: return NSLocalizedString("status_connecting_phase2", comment: "")
        case .connectingReality: return NSLocalizedString("status_connecting_phase3", comment: "")
        case .connected: return NSLocalizedString("status_active", comment: "")
        case .disconnecting: return NSLocalizedString("status_disconnecting", comment: "")
        case .error: return NSLocalizedString("status_error", comment: "")
        }
    }
}

extension VpnNode: Identifiable {
    public var id: String { id }
}

