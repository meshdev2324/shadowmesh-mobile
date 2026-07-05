
//
//  SecureStorageTests.swift
//  ShadowMeshTests
//
//  Created on 7/2/24.
//

import XCTest
@testable import ShadowMesh

final class SecureStorageTests: XCTestCase {
    
    var secureStorage: SecureStorage!
    
    override func setUp() {
        super.setUp()
        secureStorage = SecureStorage.shared
        
        // Clear any existing test values before each test
        clearTestKeys()
    }
    
    override func tearDown() {
        clearTestKeys()
        secureStorage = nil
        super.tearDown()
    }
    
    private func clearTestKeys() {
        try? secureStorage.removeValue(forKey: "test_string_key")
        try? secureStorage.removeValue(forKey: "test_bool_key")
        try? secureStorage.removeValue(forKey: "pin_hash")
        try? secureStorage.removeValue(forKey: "pin_salt")
    }
    
    func testSetAndGetString() {
        let testValue = "Hello, ShadowMesh!"
        let testKey = "test_string_key"
        
        XCTAssertNoThrow(try secureStorage.setString(testValue, forKey: testKey))
        
        let retrieved = try? secureStorage.getString(forKey: testKey)
        XCTAssertEqual(retrieved, testValue)
    }
    
    func testSetAndGetBool() {
        let testKey = "test_bool_key"
        
        secureStorage.setBool(true, forKey: testKey)
        XCTAssertEqual(secureStorage.getBool(forKey: testKey), true)
        
        secureStorage.setBool(false, forKey: testKey)
        XCTAssertEqual(secureStorage.getBool(forKey: testKey), false)
    }
    
    func testGenerateSaltIsRandom() {
        let salt1 = secureStorage.generateSalt()
        let salt2 = secureStorage.generateSalt()
        
        XCTAssertNotEqual(salt1, salt2)
        XCTAssertEqual(salt1.count, 64) // SHA256 is 32 bytes, hex is 64 chars
    }
    
    func testHashPinIsConsistent() {
        let pin = "123456"
        let salt = secureStorage.generateSalt()
        
        let hash1 = secureStorage.hashPin(pin, salt: salt)
        let hash2 = secureStorage.hashPin(pin, salt: salt)
        
        XCTAssertEqual(hash1, hash2)
    }
    
    func testHashPinDifferentPinDifferentHash() {
        let salt = secureStorage.generateSalt()
        
        let hash1 = secureStorage.hashPin("123456", salt: salt)
        let hash2 = secureStorage.hashPin("654321", salt: salt)
        
        XCTAssertNotEqual(hash1, hash2)
    }
    
    func testGetPersistentDeviceIdIsConsistent() {
        let id1 = secureStorage.getPersistentDeviceId()
        let id2 = secureStorage.getPersistentDeviceId()
        
        XCTAssertEqual(id1, id2)
        XCTAssertEqual(id1.count, 64) // SHA256 hex length
    }
    
    func testRemoveValue() {
        let testKey = "test_string_key"
        
        try? secureStorage.setString("test", forKey: testKey)
        XCTAssertNotNil(try? secureStorage.getString(forKey: testKey))
        
        XCTAssertNoThrow(try secureStorage.removeValue(forKey: testKey))
        XCTAssertNil(try? secureStorage.getString(forKey: testKey))
    }
}
