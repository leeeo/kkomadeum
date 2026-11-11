# Pleos / Android Automotive 공개 조사

조사일: 2026-10-23. 공개 문서와 익명 HTTP 응답만 사용했다. 계정·개발자 등록·신청은 하지 않았다.
이 PC에는 `wmux` 실행 파일이 없어 공개 HTTP 조회로 대체했다.

## 앱 모델과 배포

[Pleos 빠른 시작](https://document.pleos.ai/en/docs/pleos-only/getting-started)은 Kotlin Android Empty Activity,
minSdk 28 이상과 Maven 의존성 사용을 안내한다.
[Vehicle SDK 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)의 구조는
`Vehicle(context)` → `initialize()` → 도메인별 비동기 API → `release()`다.
SDK가 Pleos Connect 안의 Vehicle Service에 연결하므로, 일반 Android 기기나 Google AAOS 이미지가 같은 서비스를 제공한다고 볼 수 없다.

공개 Maven 저장소: <https://nexus-playground.pleos.ai/repository/maven-releases/>.
[metadata](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml)에서
`Vehicle:2.3.2`를 확인했다. [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom)은 AAR이며,
익명 AAR HEAD 응답은 200, 길이는 983,759바이트다. 문서 예제의 2.3.1 POM은 조사 시점 404였다.
이 저장소는 확인된 2.3.2를 고정한다.
다운로드한 AAR의 manifest는 Vehicle Service 최소 버전 `2.3.13` / 코드 `65`를 요구한다.
`ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한과 서비스 검색용 query도 라이브러리에서 병합된다.
공개 에뮬레이터 2.1.0과 이 서비스 버전의 호환 여부는 별도 실행 검증 대상이다.

## 신호와 권한

| 신호 | Pleos 공개 API | 문서상 읽기 권한 | 이번 판단 |
|---|---|---|---|
| 좌석 점유 | `getCurrentSeatOccupancy(SeatArea, (Boolean?) -> Unit, (Exception) -> Unit)` | `pleos.car.permission.CAR_SEATS`, normal | API 존재 확인. 아동 식별 의미는 아님 |
| 벨트 | `getCurrentSeatBeltBuckledState(...)` | `CAR_SEATS`, normal | 차량 안전벨트 상태. 카시트 자체 하네스와 다름 |
| 도어 위치 | `getCurrentDoorPosition(DoorArea, (Int?) -> Unit, ...)` | `pleos.car.permission.CAR_DOORS`, normal | API 존재 확인. 차종별 위치값 의미/지원 검사 필요 |
| 시동 | Pleos 전용 상세 메서드 미확인 | 미확인 | 일반 driving/odometer 도메인 이름에서 시동 OFF를 추측하지 않음 |
| 기어 P / 주차 | Pleos 전용 상세 메서드 미확인 | 미확인 | 주차 브레이크와 기어 P를 동일시하지 않음 |

근거:

- [착좌 조회](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-occupancy)
- [벨트 조회](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-belt-buckled-state)
- [도어 위치 조회](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-door-position)
- [착좌 구독](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-occupancy)
- [착좌 Capability](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-occupancy-capabilities)
- [Capability 안내](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability): `NONE / READ / WRITE / READ_WRITE`

`normal`은 모든 차량·좌석에 센서가 있다는 뜻이 아니다. 지원 좌석 목록과 읽기 capability를 먼저 확인하고,
미지원·null·권한 오류는 미확인으로 남겨야 한다. 공모전 및 App Market 권한 심사 조건도 별도 확인 대상이다.

## SDK와 에뮬레이터 공개 다운로드

[공식 설치 문서](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)는
Pleos Connect Emulator 2.1.0, Android API 34, Gradle 8 이상을 안내한다.
Windows/Linux SDK Update Site:
<https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml>.
공개 XML의 패키지는 `system-images;android-34;pleos_car_emulator;x86_64`,
ZIP 크기는 4,087,751,964바이트이며 익명 HEAD 200이다.
**SDK와 에뮬레이터 파일 다운로드 자체에 가입 장벽은 확인되지 않았다.**
이는 특정 SDK/Vehicle Service 버전 조합의 런타임 호환성이나 실제 차량 사용 권한을 검증한 결과는 아니다.

이번 검증용 이미지는 Google 공개 `system-images;android-34-ext9;android-automotive;x86_64`다.
Pleos 전용 Vehicle Service가 없으므로 SDK 실신호 성공 검증과 동일하게 보고하지 않는다.

## 표준 AAOS와 다른 점

[Android 14 AOSP VehiclePropertyIds](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-release/car-lib/src/android/car/VehiclePropertyIds.java)에서
`SEAT_OCCUPANCY`, `SEAT_BELT_BUCKLED`는 `CONTROL_CAR_SEATS`, `DOOR_POS`는 `CONTROL_CAR_DOORS`의
signature/privileged 권한에 묶인다. 일반 sideload 앱에 있다고 가정할 수 없다.
`IGNITION_STATE`, `PARKING_BRAKE_ON`은 normal `Car.PERMISSION_POWERTRAIN` 읽기 경로가 있으나
차량별 지원 여부를 확인해야 한다. 현재 [최신 API 문서](https://developer.android.com/reference/android/car/VehiclePropertyIds)의
새 `READ_CAR_SEATS` 권한을 API 34에 소급 적용하지 않는다.

## Android Automotive UI 제약

- [차량용 앱 분류](https://developer.android.com/training/cars): 주행 앱은 미디어·내비·POI·IoT·날씨 등 지원 분류가 있고,
  주차 앱의 영상·게임·브라우저 등은 별도 조건을 따른다. 동승 케어 자체가 독립적인 Google Play 지원 분류라는 근거는 확인하지 못했다.
- [Car App Library](https://developer.android.com/training/cars/apps/library)는 host가 제공하는 주의분산 제한 템플릿을 쓴다.
  자유로운 Compose UI가 같은 주행 허용을 받는 것은 아니다.
- [AOSP 주의분산 지침](https://source.android.com/docs/automotive/driver_distraction/guidelines?hl=en)은
  주행 시 전경 Activity의 `distractionOptimized` 여부를 확인한다. 이번 앱은 이를 선언하지 않는다.
- [UX 제한 소비 지침](https://source.android.com/docs/automotive/driver_distraction/consume)은 UI가
  `CarUxRestrictionsManager`의 제한 상태를 따르도록 안내한다. 단순 속도·기어 판정만으로 UX 허용 상태를 대신하지 않는다.

결론: 이번은 **Compose 정차 시연 앱 + 명시적인 개발용 센서 패널**이다. 실차 주행용 UI,
음성 백그라운드 동작, 전원 OFF 뒤 프로세스 수명, App Market 카테고리는 10-28 설명회 질문과 다음 통합 검증으로 남긴다.
