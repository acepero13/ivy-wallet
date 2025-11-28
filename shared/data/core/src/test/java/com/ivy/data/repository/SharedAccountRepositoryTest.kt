package com.ivy.data.repository

import com.ivy.base.TestDispatchersProvider
import com.ivy.data.db.dao.read.SharedAccountDao
import com.ivy.data.db.dao.write.WriteSharedAccountDao
import com.ivy.data.db.entity.SharedAccountEntity
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.fake.fakeRepositoryMemoFactory
import com.ivy.data.repository.mapper.SharedAccountMapper
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

class SharedAccountRepositoryTest {
    private val sharedAccountDao = mockk<SharedAccountDao>()
    private val writeSharedAccountDao = mockk<WriteSharedAccountDao>(relaxed = true)

    private lateinit var repository: SharedAccountRepository

    @Before
    fun setup() {
        repository = SharedAccountRepository(
            mapper = SharedAccountMapper(),
            sharedAccountDao = sharedAccountDao,
            writeSharedAccountDao = writeSharedAccountDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = fakeRepositoryMemoFactory(),
        )
    }

    @Test
    fun `find by id - null entity`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        coEvery { sharedAccountDao.findById(accountId.value) } returns null

        // when
        val res = repository.findById(accountId)

        // then
        res shouldBe null
    }

    @Test
    fun `find by id - valid entity`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        coEvery { sharedAccountDao.findById(accountId.value) } returns SharedAccountEntity(
            id = accountId.value,
            name = "Family Account",
            currency = "USD",
            owners = """["user1","user2"]""",
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null,
            remoteId = null,
            isSynced = false
        )

        // when
        val res = repository.findById(accountId)

        // then
        res shouldBe SharedAccount(
            id = accountId,
            name = NotBlankTrimmedString.unsafe("Family Account"),
            currency = AssetCode.unsafe("USD"),
            owners = listOf("user1", "user2"),
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null
        )
    }

    @Test
    fun `find all - returns accounts`() = runTest {
        // given
        val now = Instant.now()

        coEvery { sharedAccountDao.findAll() } returns listOf(
            SharedAccountEntity(
                id = UUID.randomUUID(),
                name = "Account 1",
                currency = "USD",
                owners = """["user1"]""",
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null,
                remoteId = null,
                isSynced = false
            ),
            SharedAccountEntity(
                id = UUID.randomUUID(),
                name = "Account 2",
                currency = "EUR",
                owners = """["user2"]""",
                createdBy = "user2",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null,
                remoteId = null,
                isSynced = false
            )
        )

        // when
        val res = repository.findAll()

        // then
        res.size shouldBe 2
        res[0].name.value shouldBe "Account 1"
        res[1].name.value shouldBe "Account 2"
    }

    @Test
    fun `save - creates new account`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val account = SharedAccount(
            id = accountId,
            name = NotBlankTrimmedString.unsafe("New Account"),
            currency = AssetCode.unsafe("USD"),
            owners = listOf("user1"),
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null
        )

        // when
        repository.save(account)

        // then
        coVerify {
            writeSharedAccountDao.save(
                match { entity ->
                    entity.id == accountId.value &&
                    entity.name == "New Account" &&
                    entity.currency == "USD" &&
                    entity.owners.contains("user1")
                }
            )
        }
    }

    @Test
    fun `deleteById - deletes account`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())

        // when
        repository.deleteById(accountId)

        // then
        coVerify {
            writeSharedAccountDao.deleteById(accountId.value)
        }
    }

    @Test
    fun `updateSyncStatus - marks account as synced`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())

        // when
        repository.updateSyncStatus(accountId, true)

        // then
        coVerify {
            writeSharedAccountDao.updateSyncStatus(
                accountId.value,
                true
            )
        }
    }

    // ==================== Flow-based Query Tests ====================

    @Test
    fun `observeById - emits account as Flow`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val entity = SharedAccountEntity(
            id = accountId.value,
            name = "Test Account",
            currency = "USD",
            owners = """["user1"]""",
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null,
            remoteId = null,
            isSynced = false
        )

        every { sharedAccountDao.observeById(accountId.value) } returns flowOf(entity)

        // when
        val flow = repository.observeById(accountId)
        val res = flow.first()

        // then
        res?.name?.value shouldBe "Test Account"
        res?.currency?.code shouldBe "USD"
        res?.owners shouldBe listOf("user1")
    }

    @Test
    fun `observeById - emits null when account does not exist`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        every { sharedAccountDao.observeById(accountId.value) } returns flowOf(null)

        // when
        val flow = repository.observeById(accountId)
        val res = flow.first()

        // then
        res shouldBe null
    }

    @Test
    fun `observeAll - emits all accounts as Flow`() = runTest {
        // given
        val now = Instant.now()

        val entities = listOf(
            SharedAccountEntity(
                id = UUID.randomUUID(),
                name = "Account 1",
                currency = "USD",
                owners = """["user1"]""",
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null,
                remoteId = null,
                isSynced = false
            ),
            SharedAccountEntity(
                id = UUID.randomUUID(),
                name = "Account 2",
                currency = "EUR",
                owners = """["user2"]""",
                createdBy = "user2",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null,
                remoteId = null,
                isSynced = false
            )
        )

        every { sharedAccountDao.observeAll() } returns flowOf(entities)

        // when
        val flow = repository.observeAll()
        val res = flow.first()

        // then
        res.size shouldBe 2
        res[0].name.value shouldBe "Account 1"
        res[1].name.value shouldBe "Account 2"
    }

    @Test
    fun `observeAll - emits empty list when no accounts`() = runTest {
        // given
        every { sharedAccountDao.observeAll() } returns flowOf(emptyList())

        // when
        val flow = repository.observeAll()
        val res = flow.first()

        // then
        res shouldBe emptyList()
    }

    @Test
    fun `observeAll - sorts accounts by createdAt descending`() = runTest {
        // given
        val now = Instant.now()
        val earlier = now.minusSeconds(3600)

        val entities = listOf(
            SharedAccountEntity(
                id = UUID.randomUUID(),
                name = "Newer Account",
                currency = "USD",
                owners = """["user1"]""",
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null,
                remoteId = null,
                isSynced = false
            ),
            SharedAccountEntity(
                id = UUID.randomUUID(),
                name = "Older Account",
                currency = "EUR",
                owners = """["user2"]""",
                createdBy = "user2",
                createdAt = earlier,
                updatedAt = earlier,
                linkedAccountId = null,
                remoteId = null,
                isSynced = false
            )
        )

        every { sharedAccountDao.observeAll() } returns flowOf(entities)

        // when
        val flow = repository.observeAll()
        val res = flow.first()

        // then
        // Should be sorted by createdAt descending (newest first)
        res.size shouldBe 2
        res[0].name.value shouldBe "Newer Account"
        res[0].createdAt shouldBe now
        res[1].name.value shouldBe "Older Account"
        res[1].createdAt shouldBe earlier
    }
}
