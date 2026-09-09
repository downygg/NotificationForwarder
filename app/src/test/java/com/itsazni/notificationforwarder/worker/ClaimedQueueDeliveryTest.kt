package com.itsazni.notificationforwarder.worker

import com.itsazni.notificationforwarder.data.QueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ClaimedQueueDeliveryTest {
    private fun item(id: Long) = QueueItem(
        id = id,
        packageName = "com.example.$id",
        appName = "Example",
        title = "Title",
        text = "Text",
        postedAt = 1L,
        notificationKey = "key-$id"
    )

    @Test
    fun `two concurrent attempts for same item send exactly once`() = runBlocking {
        val claimed = ConcurrentHashMap.newKeySet<Long>()
        val sends = AtomicInteger(0)
        val target = item(123)

        val results = listOf(1, 2).map {
            async(Dispatchers.Default) {
                processWithAtomicClaim(
                    item = target,
                    claim = { claimed.add(it) },
                    deliver = { sends.incrementAndGet(); DeliveryOutcome(success = true) },
                    onSuccess = {},
                    onFailure = { _, _ -> }
                )
            }
        }.awaitAll()

        assertEquals(1, results.count { it.claimed })
        assertEquals(1, sends.get())
    }

    @Test
    fun `retry all overlap with retry item is protected by same claim`() = runBlocking {
        assertSingleSendForOverlappingTriggers()
    }

    @Test
    fun `automatic retry overlap with retry item is protected by same claim`() = runBlocking {
        assertSingleSendForOverlappingTriggers()
    }

    @Test
    fun `automatic retry overlap with retry all is protected by same claim`() = runBlocking {
        assertSingleSendForOverlappingTriggers()
    }

    @Test
    fun `double retry all is protected by same claim`() = runBlocking {
        assertSingleSendForOverlappingTriggers()
    }

    @Test
    fun `different items can be processed independently`() = runBlocking {
        val claimed = ConcurrentHashMap.newKeySet<Long>()
        val sends = AtomicInteger(0)
        val results = listOf(item(101), item(102)).map { target ->
            async(Dispatchers.Default) {
                processWithAtomicClaim(
                    item = target,
                    claim = { claimed.add(it) },
                    deliver = { sends.incrementAndGet(); DeliveryOutcome(success = true) },
                    onSuccess = {},
                    onFailure = { _, _ -> }
                )
            }
        }.awaitAll()

        assertTrue(results.all { it.claimed })
        assertEquals(2, sends.get())
    }

    @Test
    fun `failed claim performs no delivery`() = runBlocking {
        val sends = AtomicInteger(0)
        val result = processWithAtomicClaim(
            item = item(123),
            claim = { false },
            deliver = { sends.incrementAndGet(); DeliveryOutcome(success = true) },
            onSuccess = {},
            onFailure = { _, _ -> }
        )

        assertFalse(result.claimed)
        assertEquals(0, sends.get())
    }

    @Test
    fun `manual failure remains retryable without resetting attempt history`() = runBlocking {
        var failedItem: QueueItem? = null
        val original = item(123).copy(attemptCount = 4)
        val result = processWithAtomicClaim(
            item = original,
            claim = { true },
            deliver = { DeliveryOutcome(success = false, message = "HTTP 500") },
            onSuccess = {},
            onFailure = { queueItem, _ -> failedItem = queueItem }
        )

        assertTrue(result.shouldRetryWorker)
        assertEquals(4, failedItem?.attemptCount)
    }

    private suspend fun assertSingleSendForOverlappingTriggers() {
        val claimed = ConcurrentHashMap.newKeySet<Long>()
        val sends = AtomicInteger(0)
        val target = item(123)
        val results = listOf(1, 2).map {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).async {
                processWithAtomicClaim(
                    item = target,
                    claim = { claimed.add(it) },
                    deliver = { sends.incrementAndGet(); DeliveryOutcome(success = true) },
                    onSuccess = {},
                    onFailure = { _, _ -> }
                )
            }
        }.awaitAll()
        assertEquals(1, results.count { it.claimed })
        assertEquals(1, sends.get())
    }
}
