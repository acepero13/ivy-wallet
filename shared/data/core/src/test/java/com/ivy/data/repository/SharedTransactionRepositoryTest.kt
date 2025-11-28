package com.ivy.data.repository

import com.ivy.base.TestDispatchersProvider
import com.ivy.data.db.dao.read.SharedTransactionDao
import com.ivy.data.db.dao.write.WriteSharedTransactionDao
import com.ivy.data.db.entity.SharedTransactionEntity
import com.ivy.data.db.entity.SharedTransactionType as EntityTransactionType
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.fake.fakeRepositoryMemoFactory
import com.ivy.data.repository.mapper.SharedTransactionMapper
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SharedTransactionRepositoryTest {
    private val sharedTransactionDao = mockk<SharedTransactionDao>()
    private val writeSharedTransactionDao = mockk<WriteSharedTransactionDao>(relaxed = true)

    private lateinit var repository: SharedTransactionRepository

    @Before
    fun setup() {
        repository = SharedTransactionRepository(
            mapper = SharedTransactionMapper(),
            sharedTransactionDao = sharedTransactionDao,
            writeSharedTransactionDao = writeSharedTransactionDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = fakeRepositoryMemoFactory(),
            transactionRepository = dagger.Lazy { mockk(relaxed = true) },
            sharedAccountRepository = dagger.Lazy { mockk(relaxed = true) },
        )
    }

    @Test
    fun `find by id - null entity`() = runTest {
        // given
        val transactionId = SharedTransactionId(UUID.randomUUID())
        coEvery { sharedTransactionDao.findById(transactionId.value) } returns null

        // when
        val res = repository.findById(transactionId)

        // then
        res shouldBe null
    }

    @Test
    fun `find by id - valid expense entity`() = runTest {
        // given
        val transactionId = SharedTransactionId(UUID.randomUUID())
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        coEvery { sharedTransactionDao.findById(transactionId.value) } returns SharedTransactionEntity(
            id = transactionId.value,
            sharedAccountId = sharedAccountId.value,
            type = EntityTransactionType.EXPENSE,
            amount = 100.0,
            title = "Groceries",
            description = "Weekly shopping",
            categoryId = null,
            time = now,
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            updatedBy = "user1",
            deleted = false,
            remoteId = null,
            isSynced = false
        )

        // when
        val res = repository.findById(transactionId)

        // then
        res shouldBe SharedTransaction(
            id = transactionId,
            sharedAccountId = sharedAccountId,
            type = SharedTransactionType.EXPENSE,
            amount = 100.0,
            title = NotBlankTrimmedString.unsafe("Groceries"),
            description = NotBlankTrimmedString.unsafe("Weekly shopping"),
            category = null,
            time = now,
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            updatedBy = "user1",
            deleted = false
        )
    }

    @Test
    fun `find by shared account id - returns transactions`() = runTest {
        // given
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        coEvery { sharedTransactionDao.findBySharedAccountId(sharedAccountId.value) } returns listOf(
            SharedTransactionEntity(
                id = UUID.randomUUID(),
                sharedAccountId = sharedAccountId.value,
                type = EntityTransactionType.EXPENSE,
                amount = 50.0,
                title = "Lunch",
                description = null,
                categoryId = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false,
                remoteId = null,
                isSynced = false
            )
        )

        // when
        val res = repository.findBySharedAccountId(sharedAccountId)

        // then
        res.size shouldBe 1
        res[0].type shouldBe SharedTransactionType.EXPENSE
        res[0].amount shouldBe 50.0
    }

    @Test
    fun `save - creates new transaction`() = runTest {
        // given
        val transactionId = SharedTransactionId(UUID.randomUUID())
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val transaction = SharedTransaction(
            id = transactionId,
            sharedAccountId = sharedAccountId,
            type = SharedTransactionType.EXPENSE,
            amount = 75.50,
            title = NotBlankTrimmedString.unsafe("Coffee"),
            description = null,
            category = null,
            time = now,
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            updatedBy = "user1",
            deleted = false
        )

        // when
        repository.save(transaction)

        // then
        coVerify {
            writeSharedTransactionDao.save(
                match { entity ->
                    entity.id == transactionId.value &&
                    entity.sharedAccountId == sharedAccountId.value &&
                    entity.type == EntityTransactionType.EXPENSE &&
                    entity.amount == 75.50 &&
                    entity.title == "Coffee"
                }
            )
        }
    }

    @Test
    fun `save - handles null title and description`() = runTest {
        // given
        val transactionId = SharedTransactionId(UUID.randomUUID())
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val transaction = SharedTransaction(
            id = transactionId,
            sharedAccountId = sharedAccountId,
            type = SharedTransactionType.INCOME,
            amount = 100.0,
            title = null,
            description = null,
            category = null,
            time = now,
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            updatedBy = "user1",
            deleted = false
        )

        // when
        repository.save(transaction)

        // then
        coVerify {
            writeSharedTransactionDao.save(
                match { entity ->
                    entity.title == null &&
                    entity.description == null
                }
            )
        }
    }

    @Test
    fun `deleteById - deletes transaction`() = runTest {
        // given
        val transactionId = SharedTransactionId(UUID.randomUUID())

        // when
        repository.deleteById(transactionId)

        // then
        coVerify {
            writeSharedTransactionDao.deleteById(transactionId.value)
        }
    }

    @Test
    fun `updateSyncStatus - marks transaction as synced`() = runTest {
        // given
        val transactionId = SharedTransactionId(UUID.randomUUID())

        // when
        repository.updateSyncStatus(transactionId, true)

        // then
        coVerify {
            writeSharedTransactionDao.updateSyncStatus(
                transactionId.value,
                true
            )
        }
    }

    // ==================== Flow-based Query Tests ====================

    @Test
    fun `observeBySharedAccountId - emits transactions as Flow`() = runTest {
        // given
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val entities = listOf(
            SharedTransactionEntity(
                id = UUID.randomUUID(),
                sharedAccountId = sharedAccountId.value,
                type = EntityTransactionType.EXPENSE,
                amount = 50.0,
                title = "Lunch",
                description = null,
                categoryId = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false,
                remoteId = null,
                isSynced = false
            ),
            SharedTransactionEntity(
                id = UUID.randomUUID(),
                sharedAccountId = sharedAccountId.value,
                type = EntityTransactionType.INCOME,
                amount = 100.0,
                title = "Salary",
                description = null,
                categoryId = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false,
                remoteId = null,
                isSynced = false
            )
        )

        every { sharedTransactionDao.observeBySharedAccountId(sharedAccountId.value, false) } returns flowOf(entities)

        // when
        val flow = repository.observeBySharedAccountId(sharedAccountId, deleted = false)
        val res = flow.first()

        // then
        res.size shouldBe 2
        res[0].type shouldBe SharedTransactionType.EXPENSE
        res[0].amount shouldBe 50.0
        res[1].type shouldBe SharedTransactionType.INCOME
        res[1].amount shouldBe 100.0
    }

    @Test
    fun `observeBySharedAccountId - emits empty list when no transactions`() = runTest {
        // given
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        every { sharedTransactionDao.observeBySharedAccountId(sharedAccountId.value, false) } returns flowOf(emptyList())

        // when
        val flow = repository.observeBySharedAccountId(sharedAccountId, deleted = false)
        val res = flow.first()

        // then
        res shouldBe emptyList()
    }

    @Test
    fun `observeBySharedAccountId - filters deleted transactions when requested`() = runTest {
        // given
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val activeEntities = listOf(
            SharedTransactionEntity(
                id = UUID.randomUUID(),
                sharedAccountId = sharedAccountId.value,
                type = EntityTransactionType.EXPENSE,
                amount = 50.0,
                title = "Active",
                description = null,
                categoryId = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false,
                remoteId = null,
                isSynced = false
            )
        )

        every { sharedTransactionDao.observeBySharedAccountId(sharedAccountId.value, deleted = false) } returns flowOf(activeEntities)

        // when
        val flow = repository.observeBySharedAccountId(sharedAccountId, deleted = false)
        val res = flow.first()

        // then
        res.size shouldBe 1
        res[0].deleted shouldBe false
    }

    @Test
    fun `observeBySharedAccountId - handles malformed entities gracefully`() = runTest {
        // given
        val sharedAccountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val entities = listOf(
            SharedTransactionEntity(
                id = UUID.randomUUID(),
                sharedAccountId = sharedAccountId.value,
                type = EntityTransactionType.EXPENSE,
                amount = 50.0,
                title = "Valid",
                description = null,
                categoryId = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false,
                remoteId = null,
                isSynced = false
            )
        )

        every { sharedTransactionDao.observeBySharedAccountId(sharedAccountId.value, false) } returns flowOf(entities)

        // when
        val flow = repository.observeBySharedAccountId(sharedAccountId, deleted = false)
        val res = flow.first()

        // then
        // Should only include valid entities
        res.size shouldBe 1
    }
}
