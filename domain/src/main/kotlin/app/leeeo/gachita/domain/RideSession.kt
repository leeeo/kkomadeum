package app.leeeo.gachita.domain

enum class Occupancy { UNKNOWN, OCCUPIED, EMPTY }
enum class Belt { UNKNOWN, FASTENED, UNFASTENED }
enum class Phase { IDLE, CHECKING, RIDING, ARRIVAL, COMPLETE }
enum class Alert { CHECK_SEAT, CHECK_BELT, CARE_TEN_MINUTES, CARE_THREE_MINUTES, CHECK_REAR_SEAT, ESCALATE }

data class SensorSnapshot(
    val occupancy: Occupancy = Occupancy.UNKNOWN,
    val belt: Belt = Belt.UNKNOWN,
    val ignitionOn: Boolean? = null,
    val parked: Boolean? = null,
    val driverDoorOpen: Boolean = false,
    val rearDoorOpen: Boolean = false,
    val remainingMinutes: Int? = null,
)

data class RideSession(
    val phase: Phase = Phase.IDLE,
    val sensors: SensorSnapshot = SensorSnapshot(),
    val elapsedSeconds: Long = 0,
    val arrivalSeconds: Long? = null,
    val rearDoorOpenedAfterArrival: Boolean = false,
    val tenMinuteReminderSent: Boolean = false,
    val threeMinuteReminderSent: Boolean = false,
    val escalated: Boolean = false,
    val alerts: Set<Alert> = emptySet(),
) {
    val canStart: Boolean
        get() = (phase == Phase.IDLE || phase == Phase.COMPLETE) && sensors.parked == true

    val canDepart: Boolean
        get() = phase == Phase.CHECKING &&
            sensors.occupancy == Occupancy.OCCUPIED && sensors.belt == Belt.FASTENED

    val canConfirmExit: Boolean
        get() = phase == Phase.ARRIVAL && sensors.parked == true &&
            sensors.occupancy == Occupancy.EMPTY && rearDoorOpenedAfterArrival

    fun start(): RideSession {
        require(canStart) { "A ride must start while parked." }
        return RideSession(phase = Phase.CHECKING, sensors = sensors, elapsedSeconds = elapsedSeconds)
            .refreshAlerts()
    }

    fun update(snapshot: SensorSnapshot, nowSeconds: Long = elapsedSeconds): RideSession {
        require(nowSeconds >= elapsedSeconds) { "The session clock must be monotonic." }
        require(snapshot.remainingMinutes == null || snapshot.remainingMinutes >= 0)
        var next = copy(sensors = snapshot, elapsedSeconds = nowSeconds)
        if (phase == Phase.CHECKING && snapshot.parked == false) {
            next = next.copy(phase = Phase.RIDING)
        }
        if (next.phase == Phase.RIDING &&
            (snapshot.ignitionOn == false || (snapshot.parked == true && snapshot.driverDoorOpen))
        ) {
            next = next.copy(
                phase = Phase.ARRIVAL,
                arrivalSeconds = nowSeconds,
                rearDoorOpenedAfterArrival = snapshot.rearDoorOpen,
            )
        }
        if (next.phase == Phase.ARRIVAL) {
            next = next.copy(
                rearDoorOpenedAfterArrival = next.rearDoorOpenedAfterArrival || snapshot.rearDoorOpen,
            )
            val arrival = requireNotNull(next.arrivalSeconds)
            if (nowSeconds - arrival >= ESCALATION_SECONDS) next = next.copy(escalated = true)
        }
        return next.refreshAlerts()
    }

    fun tick(nowSeconds: Long): RideSession = update(sensors, nowSeconds)

    fun confirmExit(): RideSession {
        require(canConfirmExit) { "Exit requires parking, an empty seat and a rear-door check." }
        return copy(phase = Phase.COMPLETE, alerts = emptySet())
    }

    private fun refreshAlerts(): RideSession {
        val active = mutableSetOf<Alert>()
        var ten = tenMinuteReminderSent
        var three = threeMinuteReminderSent
        if (phase == Phase.CHECKING || phase == Phase.RIDING) {
            if (sensors.occupancy != Occupancy.OCCUPIED) active += Alert.CHECK_SEAT
            if (sensors.belt != Belt.FASTENED) active += Alert.CHECK_BELT
        }
        if (phase == Phase.RIDING) {
            // Reminder latches prevent repeated delivery when ETA fluctuates.
            val eta = sensors.remainingMinutes
            if (eta != null && eta <= 3 && !three) {
                active += Alert.CARE_THREE_MINUTES
                three = true
                ten = true
            } else if (eta != null && eta <= 10 && !ten) {
                active += Alert.CARE_TEN_MINUTES
                ten = true
            }
        }
        if (phase == Phase.ARRIVAL) {
            active += Alert.CHECK_REAR_SEAT
            if (escalated) active += Alert.ESCALATE
        }
        return copy(alerts = active, tenMinuteReminderSent = ten, threeMinuteReminderSent = three)
    }

    companion object {
        const val ESCALATION_SECONDS = 30L
    }
}

interface SeatSensorSource {
    val snapshot: SensorSnapshot
}

class SimulatedSeatSensorSource(
    override var snapshot: SensorSnapshot = SensorSnapshot(
        occupancy = Occupancy.OCCUPIED,
        belt = Belt.UNFASTENED,
        ignitionOn = true,
        parked = true,
        remainingMinutes = 25,
    ),
) : SeatSensorSource
