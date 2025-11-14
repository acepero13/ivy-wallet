package com.ivy.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.SharedAccountEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SharedAccountDaoTest {
    private lateinit var database: IvyRoomDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IvyRoomDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndFindById() = runTest {
        val account = createTestSharedAccount()
        database.writeSharedAccountDao.save(account)

        val found = database.sharedAccountDao.findById(account.id)
        assertNotNull(found)
        assertEquals(account.id, found.id)
        assertEquals(account.name, found.name)
    }

    @Test
    fun testFindAll() = runTest {
        val account1 = createTestSharedAccount()
        val account2 = createTestSharedAccount(name = "Test Account 2")

        database.writeSharedAccountDao.save(account1)
        database.writeSharedAccountDao.save(account2)

        val all = database.sharedAccountDao.findAll()
        assertEquals(2, all.size)
    }

    @Test
    fun testFindByOwner() = runTest {
        val ownerUid = "user123"
        val account1 = createTestSharedAccount(owners = """["user123", "user456"]""")
        val account2 = createTestSharedAccount(owners = """["user789"]""")

        database.writeSharedAccountDao.save(account1)
        database.writeSharedAccountDao.save(account2)

        val found = database.sharedAccountDao.findByOwner(ownerUid)
        assertEquals(1, found.size)
        assertEquals(account1.id, found[0].id)
    }

    @Test
    fun testFindBySyncStatus() = runTest {
        val syncedAccount = createTestSharedAccount(isSynced = true)
        val unsyncedAccount = createTestSharedAccount(isSynced = false)

        database.writeSharedAccountDao.save(syncedAccount)
        database.writeSharedAccountDao.save(unsyncedAccount)

        val synced = database.sharedAccountDao.findBySyncStatus(true)
        val unsynced = database.sharedAccountDao.findBySyncStatus(false)

        assertEquals(1, synced.size)
        assertEquals(1, unsynced.size)
        assertTrue(synced[0].isSynced)
        assertTrue(!unsynced[0].isSynced)
    }

    @Test
    fun testFindByRemoteId() = runTest {
        val remoteId = "firebase123"
        val account = createTestSharedAccount(remoteId = remoteId)

        database.writeSharedAccountDao.save(account)

        val found = database.sharedAccountDao.findByRemoteId(remoteId)
        assertNotNull(found)
        assertEquals(remoteId, found.remoteId)
    }

    @Test
    fun testUpdateSyncStatus() = runTest {
        val account = createTestSharedAccount(isSynced = false)
        database.writeSharedAccountDao.save(account)

        database.writeSharedAccountDao.updateSyncStatus(account.id, true)

        val updated = database.sharedAccountDao.findById(account.id)
        assertNotNull(updated)
        assertTrue(updated.isSynced)
    }

    @Test
    fun testUpdateRemoteId() = runTest {
        val account = createTestSharedAccount()
        database.writeSharedAccountDao.save(account)

        val remoteId = "firebase123"
        database.writeSharedAccountDao.updateRemoteId(account.id, remoteId, true)

        val updated = database.sharedAccountDao.findById(account.id)
        assertNotNull(updated)
        assertEquals(remoteId, updated.remoteId)
        assertTrue(updated.isSynced)
    }

    @Test
    fun testDeleteById() = runTest {
        val account = createTestSharedAccount()
        database.writeSharedAccountDao.save(account)

        database.writeSharedAccountDao.deleteById(account.id)

        val found = database.sharedAccountDao.findById(account.id)
        assertNull(found)
    }

    @Test
    fun testDeleteAll() = runTest {
        database.writeSharedAccountDao.save(createTestSharedAccount())
        database.writeSharedAccountDao.save(createTestSharedAccount())

        database.writeSharedAccountDao.deleteAll()

        val all = database.sharedAccountDao.findAll()
        assertTrue(all.isEmpty())
    }

    private fun createTestSharedAccount(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Shared Account",
        currency: String = "USD",
        owners: String = """["user1", "user2"]""",
        createdBy: String = "user1",
        remoteId: String? = null,
        isSynced: Boolean = false
    ) = SharedAccountEntity(
        id = id,
        name = name,
        currency = currency,
        owners = owners,
        createdBy = createdBy,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        remoteId = remoteId,
        isSynced = isSynced
    )
}
