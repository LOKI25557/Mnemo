package com.mnemo.core.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DatabaseConcurrencyWipeTest {

    @Before
    fun setUp() {
        MnemoDatabase.finishWipe()
    }

    @After
    fun tearDown() {
        MnemoDatabase.finishWipe()
    }

    @Test
    fun testQueryInProgressWhenWipingStarts_waitsForCompletion() = runBlocking {
        val queryStarted = CompletableDeferred<Unit>()
        val queryFinished = CompletableDeferred<Boolean>()

        // 1. Launch a query that holds active operation
        val queryJob = async {
            MnemoDatabase.withOperation {
                queryStarted.complete(Unit)
                delay(100) // Simulate query I/O
                queryFinished.complete(true)
                "query_result"
            }
        }

        queryStarted.await()
        assertEquals(1, MnemoDatabase.getActiveOperationCount())

        // 2. Start wipe concurrently — should wait for query to complete
        val wipeSuccess = MnemoDatabase.startWipe(timeoutMs = 1000L)
        assertTrue("Wipe should succeed after active operation completes", wipeSuccess)
        assertTrue("Query must have finished before wipe finished draining", queryFinished.isCompleted)
        assertEquals(0, MnemoDatabase.getActiveOperationCount())
        assertEquals("query_result", queryJob.await())

        MnemoDatabase.finishWipe()
    }

    @Test
    fun testNewQueryArrivingDuringWiping_isRejected() = runBlocking {
        // Start wipe
        val wipeStarted = MnemoDatabase.startWipe()
        assertTrue(wipeStarted)
        assertTrue(MnemoDatabase.isWiping())

        // Any new operation must be rejected immediately
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                MnemoDatabase.withOperation {
                    "should_fail"
                }
            }
        }

        MnemoDatabase.finishWipe()
        assertFalse(MnemoDatabase.isWiping())
    }

    @Test
    fun testTwoSimultaneousWipeRequests_secondRejectedSafely() = runBlocking {
        // First wipe acquires gate
        val firstWipe = MnemoDatabase.startWipe()
        assertTrue("First wipe must succeed", firstWipe)

        // Second simultaneous wipe attempt
        val secondWipe = MnemoDatabase.startWipe()
        assertFalse("Second simultaneous wipe must be safely rejected without deadlock", secondWipe)

        MnemoDatabase.finishWipe()
    }

    @Test
    fun testFailedWipe_resetsGateSafely() = runBlocking {
        // Simulate a wipe attempt where active operation does not drain within timeout
        val blockForever = CompletableDeferred<Unit>()
        val longJob = async {
            MnemoDatabase.withOperation {
                blockForever.await()
            }
        }

        delay(20)
        assertEquals(1, MnemoDatabase.getActiveOperationCount())

        // Wipe with short timeout should fail safely
        val wipeResult = MnemoDatabase.startWipe(timeoutMs = 50L)
        assertFalse("Wipe must report false on timeout", wipeResult)
        assertFalse("isWiping must be reset to false on failure", MnemoDatabase.isWiping())

        // Cleanup
        blockForever.complete(Unit)
        longJob.await()
    }

    @Test
    fun testReopeningAfterSuccessfulWipe_initializesCleanly() = runBlocking {
        val wipeStarted = MnemoDatabase.startWipe()
        assertTrue(wipeStarted)

        // Conclude wipe
        MnemoDatabase.finishWipe()
        assertFalse(MnemoDatabase.isWiping())

        // New operation after wipe executes cleanly
        val result = MnemoDatabase.withOperation {
            "fresh_operation_succeeded"
        }
        assertEquals("fresh_operation_succeeded", result)
    }
}
