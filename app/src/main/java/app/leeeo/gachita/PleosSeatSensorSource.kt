package app.leeeo.gachita

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import app.leeeo.gachita.domain.Belt
import app.leeeo.gachita.domain.Occupancy
import app.leeeo.gachita.domain.SeatSensorSource
import app.leeeo.gachita.domain.SensorSnapshot
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Reads one rear-left snapshot; it never writes vehicle properties. */
class PleosSeatSensorSource(private val context: Context) : SeatSensorSource {
    override var snapshot = SensorSnapshot()
        private set

    suspend fun readOnce(): SensorSnapshot = withTimeout(5_000) {
        val vehicle = Vehicle(context)
        val area = SeatArea(SeatValue.ROW_2_LEFT)
        val readable = setOf(AccessType.READ, AccessType.READ_WRITE)
        try {
            vehicle.initialize()
            val seat = vehicle.getSeat()
            val occupied = suspendCancellableCoroutine<Boolean?> { continuation ->
                val failed: (Exception) -> Unit = {
                    if (continuation.isActive) continuation.resumeWithException(it)
                }
                seat.checkSeatOccupancyCapabilities(
                    onComplete = { capability, areas ->
                        if (continuation.isActive) {
                            if (capability.accessType !in readable || area !in areas) {
                                continuation.resume(null)
                            } else {
                                seat.getCurrentSeatOccupancy(
                                    area,
                                    onComplete = { if (continuation.isActive) continuation.resume(it) },
                                    onFailed = failed,
                                )
                            }
                        }
                    },
                    onFailed = failed,
                )
            }
            val buckled = suspendCancellableCoroutine<Boolean?> { continuation ->
                val failed: (Exception) -> Unit = {
                    if (continuation.isActive) continuation.resumeWithException(it)
                }
                seat.checkSeatBeltBuckledCapabilities(
                    onComplete = { capability, areas ->
                        if (continuation.isActive) {
                            if (capability.accessType !in readable || area !in areas) {
                                continuation.resume(null)
                            } else {
                                seat.getCurrentSeatBeltBuckledState(
                                    area,
                                    onComplete = { if (continuation.isActive) continuation.resume(it) },
                                    onFailed = failed,
                                )
                            }
                        }
                    },
                    onFailed = failed,
                )
            }
            snapshot = SensorSnapshot(
                occupancy = when (occupied) { true -> Occupancy.OCCUPIED; false -> Occupancy.EMPTY; null -> Occupancy.UNKNOWN },
                belt = when (buckled) { true -> Belt.FASTENED; false -> Belt.UNFASTENED; null -> Belt.UNKNOWN },
            )
            snapshot
        } finally {
            vehicle.release()
        }
    }
}
