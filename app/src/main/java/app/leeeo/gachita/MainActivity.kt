package app.leeeo.gachita

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.leeeo.gachita.domain.Alert
import app.leeeo.gachita.domain.Belt
import app.leeeo.gachita.domain.Occupancy
import app.leeeo.gachita.domain.Phase
import app.leeeo.gachita.domain.RideSession
import app.leeeo.gachita.domain.SensorSnapshot
import app.leeeo.gachita.domain.SimulatedSeatSensorSource
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF92E2CC),
                background = Color(0xFF101C24),
                surface = Color(0xFF1B2B36),
            )) {
                Surface(modifier = Modifier.fillMaxSize()) { GachitaApp() }
            }
        }
    }
}

private val rideSaver = Saver<RideSession, String>(
    save = { state ->
        JSONObject().apply {
            put("phase", state.phase.name)
            put("occupancy", state.sensors.occupancy.name)
            put("belt", state.sensors.belt.name)
            put("ignition", state.sensors.ignitionOn ?: JSONObject.NULL)
            put("parked", state.sensors.parked ?: JSONObject.NULL)
            put("driverDoor", state.sensors.driverDoorOpen)
            put("rearDoor", state.sensors.rearDoorOpen)
            put("eta", state.sensors.remainingMinutes ?: JSONObject.NULL)
            put("elapsed", state.elapsedSeconds)
            put("arrival", state.arrivalSeconds ?: JSONObject.NULL)
            put("rearSeen", state.rearDoorOpenedAfterArrival)
            put("ten", state.tenMinuteReminderSent)
            put("three", state.threeMinuteReminderSent)
            put("escalated", state.escalated)
            put("alerts", JSONArray(state.alerts.map { it.name }))
        }.toString()
    },
    restore = { text ->
        val json = JSONObject(text)
        RideSession(
            phase = Phase.valueOf(json.getString("phase")),
            sensors = SensorSnapshot(
                occupancy = Occupancy.valueOf(json.getString("occupancy")),
                belt = Belt.valueOf(json.getString("belt")),
                ignitionOn = if (json.isNull("ignition")) null else json.getBoolean("ignition"),
                parked = if (json.isNull("parked")) null else json.getBoolean("parked"),
                driverDoorOpen = json.getBoolean("driverDoor"),
                rearDoorOpen = json.getBoolean("rearDoor"),
                remainingMinutes = if (json.isNull("eta")) null else json.getInt("eta"),
            ),
            elapsedSeconds = json.getLong("elapsed"),
            arrivalSeconds = if (json.isNull("arrival")) null else json.getLong("arrival"),
            rearDoorOpenedAfterArrival = json.getBoolean("rearSeen"),
            tenMinuteReminderSent = json.getBoolean("ten"),
            threeMinuteReminderSent = json.getBoolean("three"),
            escalated = json.getBoolean("escalated"),
            alerts = json.getJSONArray("alerts").let { array ->
                (0 until array.length()).map { Alert.valueOf(array.getString(it)) }.toSet()
            },
        )
    },
)

@Composable
private fun GachitaApp() {
    val source = remember { SimulatedSeatSensorSource() }
    var session by rememberSaveable(stateSaver = rideSaver) {
        mutableStateOf(RideSession(sensors = source.snapshot))
    }
    var recentMessage by rememberSaveable { mutableStateOf("아이와 함께하는 이동을 시작하세요.") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pleosStatus by rememberSaveable { mutableStateOf("Pleos SDK: 미조회 · 좌석/벨트 모의 입력") }
    var pleosReading by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }
    var koreanVoice by remember { mutableStateOf(false) }
    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val result = tts?.setLanguage(Locale.KOREAN)
            koreanVoice = result != null && result >= TextToSpeech.LANG_AVAILABLE
        }
    }
    LaunchedEffect(session.alerts) {
        if (session.alerts.isNotEmpty()) {
            recentMessage = session.alerts.joinToString(" ") { it.message() }
            if (koreanVoice) tts?.speak(recentMessage, TextToSpeech.QUEUE_FLUSH, null, "ride-alert")
        }
    }
    // This is a foreground demo clock, not a vehicle-off background safety service.
    LaunchedEffect(session.phase) {
        if (session.phase == Phase.ARRIVAL) {
            while (true) {
                delay(1_000)
                session = session.tick(session.elapsedSeconds + 1)
            }
        }
    }
    fun simulate(snapshot: SensorSnapshot) {
        pleosStatus = "Pleos SDK: 모의 입력 사용 중"
        source.snapshot = snapshot
        session = session.update(source.snapshot)
    }
    // Diagnostic SDK read lives with the developer simulator so the ride flow keeps the visible area.
    val pleosRow: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = !pleosReading && session.canStart,
                modifier = Modifier.testTag("pleos-read"),
                onClick = {
                    scope.launch {
                        pleosReading = true
                        pleosStatus = "Pleos SDK: 후석 좌측 Capability 및 읽기 확인 중"
                        try {
                            val actual = PleosSeatSensorSource(context).readOnce()
                            pleosStatus = "Pleos SDK 후석 좌측: ${actual.occupancy.label()} / ${actual.belt.label()} · 시연 입력은 모의 유지"
                        } catch (error: TimeoutCancellationException) {
                            pleosStatus = "Pleos SDK: 응답 없음 · 모의 입력 유지"
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            pleosStatus = "Pleos SDK: 연결 불가 (${error.javaClass.simpleName}) · 모의 입력 유지"
                        } finally {
                            pleosReading = false
                        }
                    }
                },
            ) { Text("Pleos 좌석 읽기") }
            Text(pleosStatus, modifier = Modifier.weight(1f).testTag("pleos-status"))
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("꼬마듬", style = MaterialTheme.typography.headlineLarge)
        Text("아이와 타는 날의 돌봄 알림", style = MaterialTheme.typography.titleMedium)
        Text(
            "시뮬레이션 전용 · 실제 차량 센서 / 가족 전송 없음",
            color = MaterialTheme.colorScheme.primary,
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        RidePanel(session, recentMessage, koreanVoice,
                            onStart = { session = session.start() },
                            onConfirm = {
                                session = session.confirmExit()
                                recentMessage = "하차 확인 완료. 다음 이동도 꼬마듬이 함께해요."
                            })
                    }
                    Column(
                        Modifier.width(340.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        pleosRow()
                        SimulatorPanel(session, ::simulate,
                            onAdvance = { session = session.tick(session.elapsedSeconds + 30) },
                            onReset = {
                                session = RideSession(sensors = SimulatedSeatSensorSource().snapshot)
                                recentMessage = "새 시연을 시작합니다."
                            })
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    RidePanel(session, recentMessage, koreanVoice,
                        onStart = { session = session.start() },
                        onConfirm = {
                            session = session.confirmExit()
                            recentMessage = "하차 확인 완료. 다음 이동도 꼬마듬이 함께해요."
                        })
                    pleosRow()
                    SimulatorPanel(session, ::simulate,
                        onAdvance = { session = session.tick(session.elapsedSeconds + 30) },
                        onReset = {
                            session = RideSession(sensors = SimulatedSeatSensorSource().snapshot)
                            recentMessage = "새 시연을 시작합니다."
                        })
                }
            }
        }
    }
}

@Composable
private fun RidePanel(
    session: RideSession,
    recentMessage: String,
    koreanVoice: Boolean,
    onStart: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(session.phase.title(), modifier = Modifier.testTag("phase-${session.phase.name}"),
            style = MaterialTheme.typography.headlineMedium)
        Column(
            Modifier.fillMaxWidth().background(
                if (session.escalated && session.phase == Phase.ARRIVAL) Color(0xFF692C32)
                else Color(0xFF253E49),
            ).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("최근 안내", style = MaterialTheme.typography.labelLarge)
            Text(recentMessage, style = MaterialTheme.typography.titleLarge)
            if (session.phase == Phase.ARRIVAL) {
                Text("하차 미확인 · ${session.elapsedSeconds - requireNotNull(session.arrivalSeconds)}초")
                Text(
                    if (session.escalated) "보호자 알림 단계 (화면 시뮬레이션, 전송 0건)" else "30초 미확인 시 보호자 알림 단계로 전환",
                    modifier = Modifier.testTag(if (session.escalated) "escalated" else "awaiting-exit"),
                )
            }
        }
        if (session.canStart) Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("start")) { Text("아이 동승 시작") }
        if (session.phase == Phase.CHECKING) {
            Text(if (session.canDepart) "탑승 점검 완료. 시뮬레이터에서 출발하세요." else "좌석과 벨트를 확인한 뒤 출발하세요.")
        }
        if (session.phase == Phase.RIDING) Text("주행 중 · 동승자 화면 조작 잠금")
        if (session.phase == Phase.ARRIVAL) {
            Button(onClick = onConfirm, enabled = session.canConfirmExit, modifier = Modifier.fillMaxWidth().testTag("confirm")) {
                Text("아이 하차를 직접 확인했어요")
            }
            Text("주차 + 뒷문 열림 + 빈 좌석 확인 후 누르세요. 센서 미확인은 완료로 처리하지 않습니다.")
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("뒷좌석 · ${session.sensors.occupancy.label()}", style = MaterialTheme.typography.titleLarge)
                Text("안전벨트 · ${session.sensors.belt.label()}", style = MaterialTheme.typography.titleLarge)
                Text("차량 · ${session.sensors.parked.parkedLabel()}  /  시동 · ${session.sensors.ignitionOn.label()}")
                Text("남은 시간 · ${session.sensors.remainingMinutes?.let { "${it}분" } ?: "미확인"}")
                Text("창문 · 차일드락 · 실내 온도: 미연동")
            }
        }
        Text(if (koreanVoice) "음성: Android 한국어 TTS 사용" else "음성: 한국어 엔진 미확인 · 화면 안내 사용")
    }
}

@Composable
private fun SimulatorPanel(
    session: RideSession,
    onSensor: (SensorSnapshot) -> Unit,
    onAdvance: () -> Unit,
    onReset: () -> Unit,
) {
    val sensors = session.sensors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("개발용 시뮬레이터", style = MaterialTheme.typography.titleLarge)
        Text("실차 운전자 조작 화면이 아닙니다. 아래 순서로 시연하세요.")
        SimButton("belt", "1. 벨트 체결 / 해제") {
            onSensor(sensors.copy(belt = if (sensors.belt == Belt.FASTENED) Belt.UNFASTENED else Belt.FASTENED))
        }
        SimButton("depart", "2. 출발", session.canDepart) { onSensor(sensors.copy(parked = false, ignitionOn = true)) }
        SimButton("eta-ten", "3. 도착 10분 전", session.phase == Phase.RIDING) { onSensor(sensors.copy(remainingMinutes = 10)) }
        SimButton("eta-three", "4. 도착 3분 전", session.phase == Phase.RIDING) { onSensor(sensors.copy(remainingMinutes = 3)) }
        SimButton("park", "5. 주차 · 시동 끄기", session.phase == Phase.RIDING) {
            onSensor(sensors.copy(parked = true, ignitionOn = false, remainingMinutes = 0))
        }
        SimButton("driver-exit", "6. 운전석 문 열기", session.phase == Phase.ARRIVAL) { onSensor(sensors.copy(driverDoorOpen = true)) }
        SimButton("advance", "7. 시계 +30초", session.phase == Phase.ARRIVAL, onAdvance)
        SimButton("child-exit", "8. 뒷문 열고 아이 하차", session.phase == Phase.ARRIVAL) {
            onSensor(sensors.copy(rearDoorOpen = true, occupancy = Occupancy.EMPTY))
        }
        SimButton("unknown", "센서 미확인 주입") { onSensor(sensors.copy(occupancy = Occupancy.UNKNOWN, belt = Belt.UNKNOWN)) }
        SimButton("reset", "시연 초기화", onClick = onReset)
    }
}

@Composable
private fun SimButton(id: String, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(id)) { Text(text) }
}

private fun Phase.title() = when (this) {
    Phase.IDLE -> "출발 전, 아이와 함께"
    Phase.CHECKING -> "탑승 점검"
    Phase.RIDING -> "이동 중 돌봄"
    Phase.ARRIVAL -> "아이도 내렸나요?"
    Phase.COMPLETE -> "하차 확인 완료"
}
private fun Occupancy.label() = when (this) {
    Occupancy.UNKNOWN -> "미확인"
    Occupancy.OCCUPIED -> "좌석 점유"
    Occupancy.EMPTY -> "빈 좌석"
}
private fun Belt.label() = when (this) {
    Belt.UNKNOWN -> "미확인"
    Belt.FASTENED -> "체결"
    Belt.UNFASTENED -> "미체결"
}
private fun Boolean?.label() = when (this) { true -> "켜짐"; false -> "꺼짐"; null -> "미확인" }
private fun Boolean?.parkedLabel() = when (this) { true -> "주차 중"; false -> "주행 중"; null -> "미확인" }
private fun Alert.message() = when (this) {
    Alert.CHECK_SEAT -> "아이 탑승 상태를 직접 확인하세요."
    Alert.CHECK_BELT -> "안전벨트를 확인하세요."
    Alert.CARE_TEN_MINUTES -> "10분 안에 도착해요. 아이를 깨울 준비를 해 주세요."
    Alert.CARE_THREE_MINUTES -> "곧 도착해요. 물병과 담요를 챙기세요."
    Alert.CHECK_REAR_SEAT -> "아이도 내렸나요? 뒷좌석을 직접 확인하세요."
    Alert.ESCALATE -> "하차 미확인이 계속됩니다. 보호자 알림 단계입니다."
}
