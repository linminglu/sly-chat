package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.cond
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import rx.observers.TestSubscriber
import rx.subjects.BehaviorSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactJobRunnerImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        class MockJobFactory : ContactJobFactory {
            val jobs = ArrayList<ContactJob>()
            val deferreds = ArrayList<Deferred<Unit, Exception>>()
            override fun create(): ContactJob {
                val job = mock<ContactJob>()
                jobs.add(job)

                val d = deferred<Unit, Exception>()
                deferreds.add(d)

                whenever(job.run(any())).thenReturn(d.promise)

                return job
            }
        }
    }

    val factory: ContactJobFactory = mock()
    val contactJob: ContactJob = mock()
    val jobDeferred = deferred<Unit, Exception>()

    val networkStatus: BehaviorSubject<Boolean> = BehaviorSubject.create()

    fun createRunner(isNetworkAvailable: Boolean = false): ContactJobRunnerImpl {
        networkStatus.onNext(isNetworkAvailable)

        whenever(factory.create()).thenReturn(contactJob)
        whenever(contactJob.run(any())).thenReturn(jobDeferred.promise)

        return ContactJobRunnerImpl(
            networkStatus,
            factory
        )
    }

    fun doLocalSync(runner: ContactJobRunnerImpl) {
        runner.withCurrentJob { doLocalSync() }
    }

    @Test
    fun `it should run a sync job if no pending operations are running and the network is available`() {
        val runner = createRunner(true)

        doLocalSync(runner)

        verify(contactJob).run(any())
    }

    @Test
    fun `it should not run a sync job if no pending operations are running and the network is unavailable`() {
        val runner = createRunner()

        doLocalSync(runner)

        verify(contactJob, never()).run(any())
    }

    @Test
    fun `it should run an operation if no sync job is running`() {
        val runner = createRunner()

        var wasRun = false
        runner.runOperation {
            wasRun = true
            Promise.ofSuccess(Unit)
        }

        assertTrue(wasRun, "Operation was not run")
    }

    @Test
    fun `it should run pending operations after a sync has completed`() {
        val runner = createRunner(true)

        doLocalSync(runner)

        jobDeferred.resolve(Unit)

        var wasRun = false
        runner.runOperation {
            wasRun = true
            Promise.ofSuccess(Unit)
        }

        assertTrue(wasRun, "Operation was not run")
    }

    @Test
    fun `it should run a sync job after available pending operations are complete`() {
        val runner = createRunner(true)

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        doLocalSync(runner)

        d.resolve(Unit)

        verify(contactJob).run(any())
    }

    @Test
    fun `it should queue operations if one is already running`() {
        val runner = createRunner(true)

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        var wasRun = false
        runner.runOperation {
            wasRun = true
            Promise.ofSuccess(Unit)
        }

        assertFalse(wasRun, "Operation wasn't queued")
    }

    @Test
    fun `it should not run a queued sync job if the network comes back online and an operation is pending`() {
        val runner = createRunner(false)

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        doLocalSync(runner)

        networkStatus.onNext(true)

        verify(contactJob, never()).run(any())
    }

    @Test
    fun `it should run the next operation after an operation fails`() {
        val runner = createRunner()

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        var wasRun = false
        runner.runOperation {
            wasRun = true
            Promise.ofSuccess(Unit)
        }

        d.reject(RuntimeException("test error"))

        assertTrue(wasRun, "Operation wasn't run")
    }

    @Test
    fun `it should run a queued sync job when the network comes back online`() {
        val runner = createRunner()

        doLocalSync(runner)

        networkStatus.onNext(true)

        verify(contactJob).run(any())
    }

    //operation -> sync job
    @Test
    fun `it should not run a queued sync job when no network is available`() {
        val runner = createRunner()

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        doLocalSync(runner)

        d.resolve(Unit)

        verify(contactJob, never()).run(any())
    }

    @Test
    fun `it should queue a sync job if one is already running`() {
        val factory = MockJobFactory()

        val runner = ContactJobRunnerImpl(
            Observable.just(true),
            factory
        )

        doLocalSync(runner)
        doLocalSync(runner)

        verify(factory.jobs[0]).run(any())
        assertEquals(1, factory.jobs.size, "More than one concurrent job created")
    }

    @Test
    fun `it should run a queued sync job after the current one is complete if no operations are pending`() {
        val factory = MockJobFactory()

        val runner = ContactJobRunnerImpl(
            Observable.just(true),
            factory
        )

        doLocalSync(runner)
        doLocalSync(runner)

        factory.deferreds[0].resolve(Unit)

        assertEquals(2, factory.jobs.size, "Second job not created")
        verify(factory.jobs[1]).run(any())
    }

    @Test
    fun `it should emit a running event when a sync begins`() {
        val runner = createRunner(true)

        val testSubscriber = TestSubscriber<ContactJobInfo>()

        runner.running.subscribe(testSubscriber)

        doLocalSync(runner)

        val events = testSubscriber.onNextEvents

        assertThat(events)
            .haveExactly(1, cond("isRunning") { it.isRunning })
            .`as`("Running events")
    }

    @Test
    fun `it should emit a a stopped event when a sync ends`() {
        val factory = MockJobFactory()
        val runner = ContactJobRunnerImpl(
            Observable.just(true),
            factory
        )

        val testSubscriber = TestSubscriber<ContactJobInfo>()

        runner.running.subscribe(testSubscriber)

        doLocalSync(runner)

        factory.deferreds[0].resolve(Unit)

        val events = testSubscriber.onNextEvents

        assertThat(events)
            .haveExactly(1, cond("!isRunning") { !it.isRunning })
            .`as`("Running events")
    }
}
