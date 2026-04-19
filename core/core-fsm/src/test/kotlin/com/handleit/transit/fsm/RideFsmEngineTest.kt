package com.handleit.transit.fsm

import app.cash.turbine.test
import com.handleit.transit.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RideFsmEngineTest {

    private lateinit var engine: RideFsmEngine
    private val logs = mutableListOf<TransitionLog>()

    private val stop = Stop("S1", "Test Stop", 28.5035, -81.3775)
    private val route = Route("111", "111", "Test Route", 3)
    private val arrival = BusArrival("111", "111", "T1", "V1", "Downtown", 280L, true)
    private val candidate = TripCandidate(
        trip = Trip("T1", "111", "WD"),
        route = route,
        vehicle = null,
        routeAlignmentScore = 0.90f,
        gtfsConfidence = 0.92f,
        stopsRemaining = emptyList(),
        nextStop = null,
        destinationStop = stop,
    )

    @Before
    fun setup() {
        logs.clear()
        engine = RideFsmEngine(onTransition = { logs.add(it) })
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(engine.current is RideState.Idle)
    }

    @Test
    fun `RouteSelected transitions Idle to WaitingAtStop`() = runTest {
        engine.state.test {
            assertEquals(RideState.Idle, awaitItem())
            engine.process(RideEvent.RouteSelected(route, stop, null))
            val next = awaitItem()
            assertTrue(next is RideState.WaitingAtStop)
            assertEquals(stop, (next as RideState.WaitingAtStop).stop)
        }
    }

    @Test
    fun `ETA T_5MIN transitions WaitingAtStop to BusApproaching`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 280L, EtaThreshold.T_5MIN))
        assertTrue(engine.current is RideState.BusApproaching)
    }

    @Test
    fun `ETA T_90SEC transitions directly to BoardingWindow`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        val state = engine.current
        assertTrue(state is RideState.BoardingWindow)
        assertEquals(AlertLevel.STRONG, (state as RideState.BoardingWindow).level)
    }

    @Test
    fun `high confidence TripMatch transitions BoardingWindow to OnBus`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        engine.process(RideEvent.TripMatchUpdated(candidate))
        assertTrue(engine.current is RideState.OnBus)
    }

    @Test
    fun `low confidence TripMatch does NOT trigger OnBus`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        val lowConfidence = candidate.copy(gtfsConfidence = 0.50f, routeAlignmentScore = 0.40f)
        engine.process(RideEvent.TripMatchUpdated(lowConfidence))
        assertTrue(engine.current is RideState.BoardingWindow)
    }

    @Test
    fun `Reset always returns to Idle from any state`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        engine.process(RideEvent.TripMatchUpdated(candidate))
        assertTrue(engine.current is RideState.OnBus)
        engine.process(RideEvent.Reset)
        assertTrue(engine.current is RideState.Idle)
    }

    @Test
    fun `ExitConfirmed transitions OnBus to TripComplete`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        engine.process(RideEvent.TripMatchUpdated(candidate))
        engine.process(RideEvent.ExitConfirmed)
        assertTrue(engine.current is RideState.TripComplete)
    }

    @Test
    fun `TripDismissed transitions TripComplete to Idle`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        engine.process(RideEvent.TripMatchUpdated(candidate))
        engine.process(RideEvent.ExitConfirmed)
        engine.process(RideEvent.TripDismissed)
        assertTrue(engine.current is RideState.Idle)
    }

    @Test
    fun `missed bus returns to WaitingAtStop`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        engine.process(RideEvent.EtaThresholdCrossed(arrival, 85L, EtaThreshold.T_90SEC))
        // Arrivals update with bus departed (secsToArrival = -120)
        val departed = arrival.copy(secsToArrival = -120L)
        engine.process(RideEvent.ArrivalsUpdated(listOf(departed)))
        assertTrue(engine.current is RideState.WaitingAtStop)
    }

    @Test
    fun `transitions are logged`() {
        engine.process(RideEvent.RouteSelected(route, stop, null))
        assertTrue(logs.isNotEmpty())
        assertEquals("Idle", logs.first().fromState)
        assertEquals("WaitingAtStop", logs.first().toState)
    }
}
