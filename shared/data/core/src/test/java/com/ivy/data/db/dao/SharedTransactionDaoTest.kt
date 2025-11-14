package com.ivy.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.SharedAccountEntity
import com.ivy.data.db.entity.SharedTransactionEntity
import com.ivy.data.db.entity.SharedTransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SharedTransactionDaoTest {
    private lateinit var database: IvyRoomDatabase
    private lateinit var testSharedAccountId: UUID

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IvyRoomDatabase::class.java
        ).allowMainThreadQueries().build()

        // Create a test shared account
        testSharedAccountId = UUID.randomUUID()
        database.writeSharedAccountDao.save(
            SharedAccountEntity(
                id = testSharedAccountId,
                name = "Test Account",
                currency = "USD",
                owners = """["user1", "user2"]""",
                createdBy = "user1",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndFindById() = runTest {
        val transaction = createTestTransaction()
        database.writeSharedTransactionDao.save(transaction)

        val found = database.sharedTransactionDao.findById(transaction.id)
        assertNotNull(found)
        assertEquals(transaction.id, found.id)
        assertEquals(transaction.amount, found.amount)
    }

    @Test
    fun testFindBySharedAccountId() = runTest {
        val transaction1 = createTestTransaction()
        val transaction2 = createTestTransaction(deleted = true)

        database.writeSharedTransactionDao.save(transaction1)
        database.writeSharedTransactionDao.save(transaction2)

        val active = database.sharedTransactionDao.findBySharedAccountId(testSharedAccountId, false)
        val deleted = database.sharedTransactionDao.findBySharedAccountId(testSharedAccountId, true)

        assertEquals(1, active.size)
        assertEquals(1, deleted.size)
        assertTrue(!active[0].deleted)
        assertTrue(deleted[0].deleted)
    }

    @Test
    fun testFindBySharedAccountAndUser() = runTest {
        val userId = "user1"
        val transaction1 = createTestTransaction(createdBy = userId)
        val transaction2 = createTestTransaction(createdBy = "user2")

        database.writeSharedTransactionDao.save(transaction1)
        database.writeSharedTransactionDao.save(transaction2)

        val found = database.sharedTransactionDao.findBySharedAccountAndUser(
            testSharedAccountId,
            userId,
            false
        )

        assertEquals(1, found.size)
        assertEquals(userId, found[0].createdBy)
    }

    @Test
    fun testFindBySharedAccountAndTimeRange() = runTest {
        val now = Instant.now()
        val yesterday = now.minus(1, ChronoUnit.DAYS)
        val tomorrow = now.plus(1, ChronoUnit.DAYS)

        val transaction1 = createTestTransaction(time = yesterday)
        val transaction2 = createTestTransaction(time = now)
        val transaction3 = createTestTransaction(time = tomorrow)

        database.writeSharedTransactionDao.save(transaction1)
        database.writeSharedTransactionDao.save(transaction2)
        database.writeSharedTransactionDao.save(transaction3)

        val found = database.sharedTransactionDao.findBySharedAccountAndTimeRange(
            testSharedAccountId,
            yesterday.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            false
        )

        assertEquals(2, found.size)
    }

    @Test
    fun testFindBySyncStatus() = runTest {
        val synced = createTestTransaction(isSynced = true)
        val unsynced = createTestTransaction(isSynced = false)

        database.writeSharedTransactionDao.save(synced)
        database.writeSharedTransactionDao.save(unsynced)

        val syncedList = database.sharedTransactionDao.findBySyncStatus(true)
        val unsyncedList = database.sharedTransactionDao.findBySyncStatus(false)

        assertEquals(1, syncedList.size)
        assertEquals(1, unsyncedList.size)
    }

    @Test
    fun testFindByRemoteId() = runTest {
        val remoteId = "firebase123"
        val transaction = createTestTransaction(remoteId = remoteId)

        database.writeSharedTransactionDao.save(transaction)

        val found = database.sharedTransactionDao.findByRemoteId(remoteId)
        assertNotNull(found)
        assertEquals(remoteId, found.remoteId)
    }

    @Test
    fun testCountBySharedAccountId() = runTest {
        database.writeSharedTransactionDao.save(createTestTransaction())
        database.writeSharedTransactionDao.save(createTestTransaction())
        database.writeSharedTransactionDao.save(createTestTransaction(deleted = true))

        val activeCount = database.sharedTransactionDao.countBySharedAccountId(testSharedAccountId, false)
        val deletedCount = database.sharedTransactionDao.countBySharedAccountId(testSharedAccountId, true)

        assertEquals(2, activeCount)
        assertEquals(1, deletedCount)
    }

    @Test
    fun testMarkAsDeleted() = runTest {
        val transaction = createTestTransaction()
        database.writeSharedTransactionDao.save(transaction)

        database.writeSharedTransactionDao.markAsDeleted(transaction.id, true)

        val updated = database.sharedTransactionDao.findById(transaction.id)
        assertNotNull(updated)
        assertTrue(updated.deleted)
    }

    @Test
    fun testUpdateSyncStatus() = runTest {
        val transaction = createTestTransaction(isSynced = false)
        database.writeSharedTransactionDao.save(transaction)

        database.writeSharedTransactionDao.updateSyncStatus(transaction.id, true)

        val updated = database.sharedTransactionDao.findById(transaction.id)
        assertNotNull(updated)
        assertTrue(updated.isSynced)
    }

    @Test
    fun testUpdateRemoteId() = runTest {
        val transaction = createTestTransaction()
        database.writeSharedTransactionDao.save(transaction)

        val remoteId = "firebase123"
        database.writeSharedTransactionDao.updateRemoteId(transaction.id, remoteId, true)

        val updated = database.sharedTransactionDao.findById(transaction.id)
        assertNotNull(updated)
        assertEquals(remoteId, updated.remoteId)
        assertTrue(updated.isSynced)
    }

    @Test
    fun testDeleteById() = runTest {
        val transaction = createTestTransaction()
        database.writeSharedTransactionDao.save(transaction)

        database.writeSharedTransactionDao.deleteById(transaction.id)

        val found = database.sharedTransactionDao.findById(transaction.id)
        assertNull(found)
    }

    @Test
    fun testDeleteBySharedAccountId() = runTest {
        database.writeSharedTransactionDao.save(createTestTransaction())
        database.writeSharedTransactionDao.save(createTestTransaction())

        database.writeSharedTransactionDao.deleteBySharedAccountId(testSharedAccountId)

        val found = database.sharedTransactionDao.findBySharedAccountId(testSharedAccountId, false)
        assertTrue(found.isEmpty())
    }

    @Test
    fun testCascadeDeleteOnAccountDeletion() = runTest {
        val transaction = createTestTransaction()
        database.writeSharedTransactionDao.save(transaction)

        // Delete the parent shared account
        database.writeSharedAccountDao.deleteById(testSharedAccountId)

        // Transaction should also be deleted due to CASCADE
        val found = database.sharedTransactionDao.findById(transaction.id)
        assertNull(found)
    }

    private fun createTestTransaction(
        id: UUID = UUID.randomUUID(),
        type: SharedTransactionType = SharedTransactionType.EXPENSE,
        amount: Double = 100.0,
        createdBy: String = "user1",
        time: Instant = Instant.now(),
        deleted: Boolean = false,
        remoteId: String? = null,
        isSynced: Boolean = false
    ) = SharedTransactionEntity(
        id = id,
        sharedAccountId = testSharedAccountId,
        type = type,
        amount = amount,
        title = "Test Transaction",
        description = "Test Description",
        categoryId = null,
        time = time,
        createdBy = createdBy,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        updatedBy = createdBy,
        deleted = deleted,
        remoteId = remoteId,
        isSynced = isSynced
    )
}
