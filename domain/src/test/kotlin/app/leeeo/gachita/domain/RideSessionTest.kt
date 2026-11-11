package app.leeeo.gachita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RideSessionTest {
    private val ready = SensorSnapshot(Occupancy.OCCUPIED, Belt.FASTENED, true, true, remainingMinutes = 25)
    private fun checking() = RideSession(sensors = ready).start()
    private fun riding() = checking().update(ready.copy(parked = false))
    private fun arrived() = riding().update(ready.copy(ignitionOn = false), 10)

    @Test fun simulatorStartsWithAnUnfastenedBelt() {
        val state = RideSession(sensors = SimulatedSeatSensorSource().snapshot).start()
        assertFalse(state.canDepart)
        assertEquals(setOf(Alert.CHECK_BELT), state.alerts)
    }

    @Test fun missingDataNeverPassesTheChecklist() {
        val state = RideSession(sensors = SensorSnapshot(parked = true)).start()
        assertFalse(state.canDepart)
        assertEquals(setOf(Alert.CHECK_SEAT, Alert.CHECK_BELT), state.alerts)
    }

    @Test fun rideCannotStartWhileMovingOrParkingUnknown() {
        listOf(false, null).forEach {
            assertFailsWith<IllegalArgumentException> { RideSession(sensors = ready.copy(parked = it)).start() }
        }
    }

    @Test fun physicalDepartureStillTracksAnUnsafeRide() {
        val state = checking().update(ready.copy(parked = false, belt = Belt.UNFASTENED))
        assertEquals(Phase.RIDING, state.phase)
        assertTrue(Alert.CHECK_BELT in state.alerts)
    }

    @Test fun beltAlertClearsOnlyAfterKnownFastening() {
        val unsafe = riding().update(ready.copy(parked = false, belt = Belt.UNKNOWN))
        assertTrue(Alert.CHECK_BELT in unsafe.alerts)
        assertFalse(Alert.CHECK_BELT in unsafe.update(ready.copy(parked = false)).alerts)
    }

    @Test fun etaRemindersAreDeliveredOnceDespiteRouteFluctuation() {
        var state = riding().update(ready.copy(parked = false, remainingMinutes = 10))
        assertTrue(Alert.CARE_TEN_MINUTES in state.alerts)
        state = state.update(state.sensors.copy(remainingMinutes = 12))
        state = state.update(state.sensors.copy(remainingMinutes = 9))
        assertFalse(Alert.CARE_TEN_MINUTES in state.alerts)
        state = state.update(state.sensors.copy(remainingMinutes = 3))
        assertTrue(Alert.CARE_THREE_MINUTES in state.alerts)
        assertFalse(Alert.CARE_THREE_MINUTES in state.tick(1).alerts)
    }

    @Test fun shortRouteDoesNotStackBothReminders() {
        val state = riding().update(ready.copy(parked = false, remainingMinutes = 2))
        assertEquals(setOf(Alert.CARE_THREE_MINUTES), state.alerts)
    }

    @Test fun engineOffStartsTheCheckEvenBeforeDriverDoorOpens() {
        assertEquals(Phase.ARRIVAL, arrived().phase)
        assertTrue(Alert.CHECK_REAR_SEAT in arrived().alerts)
    }

    @Test fun parkedDriverExitAlsoStartsTheCheckWithIgnitionOn() {
        val state = riding().update(ready.copy(driverDoorOpen = true))
        assertEquals(Phase.ARRIVAL, state.phase)
    }

    @Test fun temporaryParkingDoesNotFinishTheRide() {
        assertEquals(Phase.RIDING, riding().update(ready).phase)
    }

    @Test fun escalationStartsAtExactDeadlineAndStaysLatched() {
        assertFalse(arrived().tick(39).escalated)
        val escalated = arrived().tick(40)
        assertTrue(Alert.ESCALATE in escalated.alerts)
        assertTrue(escalated.tick(41).escalated)
    }

    @Test fun emptySeatAloneCannotDismissArrival() {
        val state = arrived().update(ready.copy(ignitionOn = false, occupancy = Occupancy.EMPTY))
        assertFalse(state.canConfirmExit)
        assertFailsWith<IllegalArgumentException> { state.confirmExit() }
    }

    @Test fun rearDoorAloneAndUnknownSeatCannotDismissArrival() {
        listOf(Occupancy.OCCUPIED, Occupancy.UNKNOWN).forEach {
            val state = arrived().update(ready.copy(occupancy = it, rearDoorOpen = true))
            assertFalse(state.canConfirmExit)
        }
    }

    @Test fun doorBeforeArrivalDoesNotCountForExit() {
        val state = checking().update(ready.copy(rearDoorOpen = true))
            .update(ready.copy(parked = false))
            .update(ready.copy(ignitionOn = false, occupancy = Occupancy.EMPTY))
        assertFalse(state.canConfirmExit)
    }

    @Test fun explicitConfirmationCompletesAndResetsNextRide() {
        val state = arrived().tick(40).update(
            ready.copy(ignitionOn = false, occupancy = Occupancy.EMPTY, rearDoorOpen = true),
        )
        assertTrue(state.canConfirmExit)
        assertEquals(Phase.ARRIVAL, state.phase)
        val complete = state.confirmExit()
        assertEquals(Phase.COMPLETE, complete.phase)
        assertTrue(complete.alerts.isEmpty())
        val restarted = complete.update(ready).start()
        assertFalse(restarted.escalated)
        assertFalse(restarted.rearDoorOpenedAfterArrival)
        assertFalse(restarted.tenMinuteReminderSent)
    }

    @Test fun movingBlocksConfirmationEvenAfterExitSignals() {
        val state = arrived().update(ready.copy(occupancy = Occupancy.EMPTY, rearDoorOpen = true, parked = false))
        assertFalse(state.canConfirmExit)
    }

    @Test fun invalidClockAndEtaAreRejected() {
        assertFailsWith<IllegalArgumentException> { arrived().tick(9) }
        assertFailsWith<IllegalArgumentException> { checking().update(ready.copy(remainingMinutes = -1)) }
    }
}
