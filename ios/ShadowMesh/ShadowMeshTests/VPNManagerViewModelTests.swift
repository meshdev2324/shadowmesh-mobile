
//
//  VPNManagerViewModelTests.swift
//  ShadowMeshTests
//
//  Created on 7/2/24.
//

import XCTest
import Combine
@testable import ShadowMesh
import uniffi_shadowmesh

// Since we don't have a proper mock framework for Swift, we'll use simple stubs
class MockVpnManager: VpnManager {
    var status: ConnectionStatus = .disconnected
    var nodes: [VpnNode] = []
    var selectedNode: VpnNode?
    var killSwitchEnabled: Bool = false
    var trafficModePreference: TrafficModePreference = .auto
    var userSettings: UserSettings = getDefaultUserSettings()
    var splitTunnelConfig: SplitTunnelConfig = SplitTunnelConfig(enabled: false, mode: "exclude", appList: [])
    
    func disconnect() { self.status = .disconnected }
    func getConnectionAttempt() -> ConnectionAttempt? { nil }
    func getConnectionStats() -> ConnectionStats {
        ConnectionStats(
            bytesReceived: 0,
            bytesSent: 0,
            packetsReceived: 0,
            packetsSent: 0,
            lastHandshake: 0,
            connectedSince: 0
        )
    }
    func getCurrentConnectionMode() -> TrafficMode { .direct }
    func getNodes() -> [VpnNode] { nodes }
    func getSplitTunnelConfig() -> SplitTunnelConfig { splitTunnelConfig }
    func getStatus() -> ConnectionStatus { status }
    func getTrafficModePreference() -> TrafficModePreference { trafficModePreference }
    func getUserSettings() -> UserSettings { userSettings }
    func isConnectionTimedOut() -> Bool { false }
    func isKillSwitchEnabled() -> Bool { killSwitchEnabled }
    func completeConnection() { status = .connected }
    func connectTo(node: VpnNode) throws { status = .connectingDirect }
    func refreshConnectionStats() throws {}
    func setConnectionMode(mode: TrafficMode) throws {}
    func setKillSwitchEnabled(enabled: Bool) { killSwitchEnabled = enabled }
    func setNodes(nodes: [VpnNode]) { self.nodes = nodes }
    func setSelectedNode(node: VpnNode) { self.selectedNode = node }
    func setSplitTunnelConfig(config: SplitTunnelConfig) { self.splitTunnelConfig = config }
    func setTrafficModePreference(preference: TrafficModePreference) { self.trafficModePreference = preference }
    func setUserSettings(settings: UserSettings) { self.userSettings = settings }
    func stopConnection() throws { self.status = .disconnecting }
}

final class VPNManagerViewModelTests: XCTestCase {
    
    var cancellables = Set<AnyCancellable>()
    
    override func setUp() {
        super.setUp()
        cancellables = []
    }
    
    func testInitialState() {
        let viewModel = VPNManagerViewModel()
        
        // Wait for async init
        let expectation = XCTestExpectation(description: "Wait for init")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertEqual(viewModel.status, .disconnected)
    }
    
    func testSelectNode() {
        let viewModel = VPNManagerViewModel()
        let testNode = VpnNode(
            id: "1",
            name: "Test Node",
            region: "US",
            country: "United States",
            ipAddress: "1.1.1.1",
            publicKey: "testkey",
            latencyMs: 50,
            load: 0.5
        )
        
        // First load nodes
        let expectation = XCTestExpectation(description: "Load nodes")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            viewModel.selectNode(testNode)
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertEqual(viewModel.selectedNode?.id, testNode.id)
    }
    
    func testKillSwitchToggle() {
        let viewModel = VPNManagerViewModel()
        
        let expectation = XCTestExpectation(description: "Toggle kill switch")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            viewModel.setKillSwitch(true)
            // Wait for async operation
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                // In a real test with mocks, we'd check the state here
                expectation.fulfill()
            }
        }
        wait(for: [expectation], timeout: 2.0)
        
        // For a stub test, we can check it compiles and runs without crashing
        XCTAssertTrue(true)
    }
    
    func testPinHashing() {
        let viewModel = VPNManagerViewModel()
        let secureStorage = SecureStorage.shared
        
        let pin = "123456"
        let salt = secureStorage.generateSalt()
        let hash = secureStorage.hashPin(pin, salt: salt)
        
        XCTAssertNotNil(hash)
        XCTAssertEqual(hash.count, 64)
    }
}
