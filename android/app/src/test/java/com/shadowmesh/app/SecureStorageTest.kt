
package com.shadowmesh.app

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SecureStorageTest {

    private lateinit var context: Context
    private lateinit var encryptedPrefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        encryptedPrefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { encryptedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `set should call putString on encrypted prefs`() {
        val key = "test_key"
        val value = "test_value"
        
        val secureStorage = SecureStorage.getInstance(context)
        
        // Use reflection to inject mock encryptedPrefs
        val field = SecureStorage::class.java.getDeclaredField("_encryptedPrefs")
        field.isAccessible = true
        field.set(secureStorage, lazyOf(encryptedPrefs))

        secureStorage.set(key, value)
        
        verify { editor.putString(key, value) }
        verify { editor.apply() }
    }
    
    @Test
    fun `get should call getString on encrypted prefs`() {
        val key = "test_key"
        val expectedValue = "test_value"
        every { encryptedPrefs.getString(key, null) } returns expectedValue
        
        val secureStorage = SecureStorage.getInstance(context)
        
        // Use reflection to inject mock encryptedPrefs
        val field = SecureStorage::class.java.getDeclaredField("_encryptedPrefs")
        field.isAccessible = true
        field.set(secureStorage, lazyOf(encryptedPrefs))

        val result = secureStorage.get(key)
        
        assertEquals(expectedValue, result)
        verify { encryptedPrefs.getString(key, null) }
    }
    
    @Test
    fun `remove should call remove on encrypted prefs`() {
        val key = "test_key"
        
        val secureStorage = SecureStorage.getInstance(context)
        
        // Use reflection to inject mock encryptedPrefs
        val field = SecureStorage::class.java.getDeclaredField("_encryptedPrefs")
        field.isAccessible = true
        field.set(secureStorage, lazyOf(encryptedPrefs))

        secureStorage.remove(key)
        
        verify { editor.remove(key) }
        verify { editor.apply() }
    }

    @Test
    fun `generateSalt returns 64 character hex string`() {
        val secureStorage = SecureStorage.getInstance(context)
        val salt = secureStorage.generateSalt()
        
        assertNotNull(salt)
        assertEquals(64, salt.length)
    }

    @Test
    fun `hashPin returns consistent hash for same pin and salt`() {
        val secureStorage = SecureStorage.getInstance(context)
        val pin = "123456"
        val salt = secureStorage.generateSalt()
        
        val hash1 = secureStorage.hashPin(pin, salt)
        val hash2 = secureStorage.hashPin(pin, salt)
        
        assertEquals(hash1, hash2)
        assertNotNull(hash1)
        assertEquals(64, hash1.length)
    }

    @Test
    fun `hashPin returns different hashes for different pins`() {
        val secureStorage = SecureStorage.getInstance(context)
        val salt = secureStorage.generateSalt()
        
        val hash1 = secureStorage.hashPin("123456", salt)
        val hash2 = secureStorage.hashPin("654321", salt)
        
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `getPersistentDeviceId generates and stores a new id`() {
        every { encryptedPrefs.getString("device_id", null) } returns null
        
        val secureStorage = SecureStorage.getInstance(context)
        
        // Use reflection to inject mock encryptedPrefs
        val field = SecureStorage::class.java.getDeclaredField("_encryptedPrefs")
        field.isAccessible = true
        field.set(secureStorage, lazyOf(encryptedPrefs))

        val id1 = secureStorage.getPersistentDeviceId()
        
        assertNotNull(id1)
        assertEquals(64, id1.length)
        verify { editor.putString("device_id", any()) }
    }

    @Test
    fun `getPersistentDeviceId returns existing id if present`() {
        val expectedId = "a".repeat(64)
        every { encryptedPrefs.getString("device_id", null) } returns expectedId
        
        val secureStorage = SecureStorage.getInstance(context)
        
        // Use reflection to inject mock encryptedPrefs
        val field = SecureStorage::class.java.getDeclaredField("_encryptedPrefs")
        field.isAccessible = true
        field.set(secureStorage, lazyOf(encryptedPrefs))

        val id = secureStorage.getPersistentDeviceId()
        
        assertEquals(expectedId, id)
        verify(exactly = 0) { editor.putString(any(), any()) }
    }
}
