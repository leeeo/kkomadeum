# Increment 1 검증 기록

기준일: 2026-10-23 (KST). Windows 11 x64 로컬 개발 환경.

## 도구와 설치 내역

기존 도구를 재사용했다.

- JDK: Temurin OpenJDK `17.0.19+10` (scoop `temurin17-jdk`).
- Gradle: 사용자 캐시의 `8.14-all`. 이 버전으로 표준 Gradle Wrapper를 생성했다.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`.
- 기존 플랫폼: API 34 / 35 / 36. 빌드는 compileSdk 35, targetSdk 34, minSdk 28.
- 기존 Build Tools 35.0.0 / 36.0.0, Emulator 37.1.11.
- 시스템 PATH·레지스트리를 변경하지 않았다. SDK 경로는 Git 제외 `local.properties`에만 둔다.

설치 전 여유 공간: C 드라이브 **173,515,210,752바이트**, D 드라이브 **805,130,997,760바이트**.
20GB 조건을 만족한 뒤 다음 사용자 로컬 패키지만 추가 설치했다.

| 추가 항목 | 위치 | 확인된 크기 |
|---|---|---|
| Google AAOS API 34 ext9, x86_64, revision 5 | `%LOCALAPPDATA%\Android\Sdk\system-images\android-34-ext9\android-automotive\x86_64` | 파일 논리 크기 합 **6,051,473,925바이트** |
| `gachita34` AVD | `%USERPROFILE%\.android\avd\gachita34.avd` 및 `gachita34.ini` | 최초 기기 테스트 시 파일 논리 크기 합 **2,782,361,338바이트** (동적 증가) |
| Pleos Vehicle 2.3.2 검사 다운로드 | 저장소 `.local/vehicle/` (Git 제외) | 원본 AAR **983,759바이트**, 압축 해제 포함 합 **2,150,816바이트** |

Gradle의 Maven 의존성은 기존 `%USERPROFILE%\.gradle\caches`에 추가 캐시된다.
JDK·Gradle 배포본·Android command-line tools를 새로 설치하지 않았다.
공개 Pleos 전용 에뮬레이터 ZIP은 존재를 확인했지만 다운로드하지 않았다.

설치 명령:

```powershell
$sdk = "$env:LOCALAPPDATA/Android/Sdk"
& "$sdk/cmdline-tools/latest/bin/sdkmanager.bat" "system-images;android-34-ext9;android-automotive;x86_64"
"no" | & "$sdk/cmdline-tools/latest/bin/avdmanager.bat" create avd -n gachita34 `
  -k "system-images;android-34-ext9;android-automotive;x86_64" -d automotive_1080p_landscape
& "$sdk/emulator/emulator.exe" -avd gachita34 -port 5580 -no-snapshot -no-boot-anim `
  -gpu swiftshader_indirect -no-audio
```

삭제가 필요할 때 에뮬레이터를 종료한 뒤 사용자가 실행할 명령이다. 이번 작업에서는 삭제하지 않았다.

```powershell
$sdk = "$env:LOCALAPPDATA/Android/Sdk"
& "$sdk/cmdline-tools/latest/bin/avdmanager.bat" delete avd -n gachita34
& "$sdk/cmdline-tools/latest/bin/sdkmanager.bat" --uninstall "system-images;android-34-ext9;android-automotive;x86_64"
```

검사용 `.local/vehicle/`는 탐색기에서 해당 폴더만 삭제하면 된다.
기존 SDK·Gradle 캐시 전체를 삭제하면 다른 개인 프로젝트에 영향을 주므로 전체 삭제를 제거 방법으로 안내하지 않는다.

## 명령과 증거

```powershell
.\gradlew.bat --no-daemon --console=plain --warning-mode all `
  :domain:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
$env:ANDROID_SERIAL = "emulator-5580"
.\gradlew.bat --no-daemon --console=plain :app:connectedDebugAndroidTest
```

도메인 XML: `domain/build/test-results/test/TEST-app.leeeo.gachita.domain.RideSessionTest.xml`.
17개, 실패 0, 오류 0, 건너뜀 0을 확인했다. 시간 임계값은 입력 시계를 사용하므로 테스트에 sleep·폴링이 없다.
첫 빌드는 `BUILD SUCCESSFUL in 4m 54s`를 출력했지만 도구 감시 세션은 `timed_out / exit 1`로 종료됐다.
따라서 그 출력만으로 최종 빌드 성공 판정하지 않고, 수정된 SDK 포함 빌드를 별도 실행했다.
SDK 포함 빌드는 `BUILD SUCCESSFUL in 2m 5s`, 종료코드 **0**이었다.
AAOS 화면 테스트는 `Starting 3 tests` → `Finished 3 tests`, `BUILD SUCCESSFUL in 1m 36s`, 종료코드 **0**이었다.
전체 모의 세션·미확인 좌석 확인 차단·Activity 재생성 시 강화 상태 복원 3개를 모두 통과했다.
실행 기기는 API 34, `android.hardware.type.automotive`를 제공하며 실제 기본 화면은 **1080×600 px / 120 dpi**였다.

최종 manifest를 새로 처리한 검증:

```powershell
.\gradlew.bat --no-daemon --console=plain --rerun-tasks :domain:test :app:assembleDebug :app:lintDebug
```

`BUILD SUCCESSFUL in 1m 9s`, 종료코드 **0**, 도메인 17개 모두 통과.
최종 lint는 **오류 0, 경고 6**이다. API 34 타깃 1개와 최신 라이브러리 버전 안내 5개를 남겼다.
공모전 API 34 시연 기준과 검증된 의존성 조합을 고정한 것이며 경고를 숨기거나 비활성화하지 않았다.
아이콘 누락과 Android 12/이전 백업 설정 경고는 실제 설정을 추가해 해결했다.
Gradle의 AGP 8.9.2 내부 Boolean 프로퍼티·속성 API deprecation도 출력되었다. Gradle 9/10 업그레이드는 이번 범위 밖이다.

Gradle 기기 테스트는 종료 후 앱을 제거한다. 캡처 보존을 위해 앱과 테스트 APK를 `adb install -r`로 직접 다시 설치하고,
`adb shell am instrument -w -r app.leeeo.gachita.test/androidx.test.runner.AndroidJUnitRunner`로 실행했다.
각 설치는 `Success`, 직접 테스트는 `OK (3 tests)`, 종료코드 **0**이었다.
AAOS는 전경 사용자 **10**을 쓰므로 `/sdcard`의 사용자 0 경로에서 캡처를 찾을 수 없었다.
실제 앱 경로는 `/storage/emulated/10/Android/data/app.leeeo.gachita/files/screenshots/`다.
일반 shell 및 root의 FUSE 경로 접근도 거절되어, **이 테스트 에뮬레이터만** `adb root` 후
실제 데이터 경로 `/data/media/10/Android/data/app.leeeo.gachita/files/screenshots/`에서 회수했다.
호스트 OS 권한·설정이나 실제 차량은 변경하지 않았다.
낮은 기본 해상도에서는 긴 내용이 스크롤되므로, 제출 검토용 가로 캡처는 `wm size 1920x1080`, `wm density 160`으로 생성했다.

표시 이름을 임시 가칭으로 바꾼 뒤(10-23) 다음 빌드도 `BUILD SUCCESSFUL in 57s`, 종료코드 **0**으로 통과했다.

```powershell
.\gradlew.bat --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

최종 APK는 **30,124,829바이트**이며 SHA-256은 다음과 같다.

```text
E4EF8E3ECA4AF5ECCE8AF1D22A38301A66BB98D2C00CFA2F8DDC0843BDF915A4
```

이름 변경 후 1920×1080 / 160 dpi에서 앱·테스트 APK 직접 설치는 각각 `Success`,
instrumentation은 `OK (3 tests)`, 종료코드 **0**이었다.
`docs/screenshots/wide/00-start.png`~`05-complete.png` 6장을 직접 열어 한글 표시,
탑승 점검, 3분 안내, 하차 경고, 30초 강화와 비활성 확인 버튼, 완료 상태를 확인했다.
가로 화면에서 내용 겹침·빈 화면은 없었다. 지원 테마는 짙은 색 하나이며 별도 밝은 테마는 없다.
800×1280 / 240 dpi의 좁은 화면에서도 직접 instrumentation은 `OK (3 tests)`, 종료코드 **0**이었다.
`docs/screenshots/narrow/01-checking.png`~`05-complete.png`를 저장했다.
이동 안내와 도착 화면을 직접 확인했으며 한글 줄바꿈이 정상이고 아래 조작은 스크롤로 접근한다.
두 폭 모두 전체 세션과 상태 복원 테스트가 통과했다.
캡처 11장은 개인 작업 기록에도 같은 `wide/`, `narrow/` 구조로 보관했다.
검증 후 에뮬레이터 프로세스는 세션 실행 시간 제한으로 종료되었다. AVD는 보존되어 같은 명령으로 다시 실행할 수 있다.

Kotlin LSP는 미설치(`kotlin-lsp` command not found)라 실행할 수 없었다.
Kotlin 컴파일러·Android lint·기기 테스트로 검증한다.

## 2026-11-11 꼬마듬 이름 변경·release 검증

제출 이름을 **꼬마듬 — 아이와 타는 날의 돌봄 알림**으로 바꾼 뒤 같은 AVD(`gachita34`, `-no-window`)에서 다시 검증했다.
앱 라벨은 `꼬마듬 · 시뮬레이션`, 패키지 ID는 `app.leeeo.gachita` 그대로다.
Pleos 진단 버튼은 개발용 시뮬레이터 열로 옮겼고 센서 요약 카드는 조작 버튼 아래로 내렸다.

| 명령 | 결과 |
|---|---|
| `:domain:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | `BUILD SUCCESSFUL in 1m 46s`, 종료코드 0 |
| `:app:connectedDebugAndroidTest` (`emulator-5580`, 1920×1080 / 160 dpi) | `Starting 3 tests` → `Finished 3 tests`, XML tests=3 failures=0 errors=0, `BUILD SUCCESSFUL in 44s`, 종료코드 0 |
| `--rerun-tasks :domain:test :app:lintDebug :app:assembleRelease` | `BUILD SUCCESSFUL in 1m 29s`, 76개 태스크 전부 재실행, 종료코드 0 |
| 도메인 XML | tests=17 skipped=0 failures=0 errors=0 |
| lint | 오류 0, 경고 6 (`GradleDependency` 5, `OldTargetApi` 1, 이전과 동일) |
| 직접 instrumentation (가로·좌은 화면) | 각각 `INSTRUMENTATION_CODE: -1`(OK), 캐처 5장씩 갱신 |

`docs/screenshots/wide/00-start.png`는 release APK 시연 녹화의 시작 프레임에서 앱 영역(1920×908)만 잘랐다.

### release APK

- `:app:assembleRelease`의 `app-release-unsigned.apk`를 `tools/sign-release.mjs`로 zipalign(16KB 페이지 정렬 포함) 후 서명했다.
- `apksigner verify --print-certs`: `Verifies`, v3 서명, 서명자 1, RSA 3072, DN `CN=leeeo, O=leeeo`.
- 인증서 SHA-256 `7af162946c429e96e8599f6d0d765ae79e2536ac6de7b82eae86b0a0d577b17f`.
- `apk/leeeo_꼬마듬_release.apk` 22,688,310바이트, SHA-256 `c86fc42e3bd5082d633ad3d8fd3e824072f413196ad66ab066eb9cf6dbf84b08`.
- `zipalign -c -P 16 4` 통과. 에뮬레이터 설치 `Success`, `dumpsys package` 플래그 `HAS_CODE ALLOW_CLEAR_USER_DATA`(DEBUGGABLE·ALLOW_BACKUP 없음).

### 모바일 보안 점검 (OWASP MASVS v2 기준, 시제품 규모)

| MASVS 영역 | 확인 | 결과 |
|---|---|---|
| PLATFORM-1 노출 컴포넌트 | `aapt2 dump xmltree` | 런처 `MainActivity`만 exported. androidx `ProfileInstallReceiver`는 exported이나 `android.permission.DUMP`(signature)로 보호. `InitializationProvider` exported=false |
| PLATFORM-1 권한 | badging | `CAR_SEATS`(Pleos normal), SDK 병합 `VEHICLE_BINDING`, 앱 전용 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. 인터넷·위치·마이크 권한 없음 |
| RESILIENCE/CODE debuggable | 매니페스트·`dumpsys` | debuggable 없음(false), testOnly 없음 |
| STORAGE-2 백업 | 매니페스트 | `allowBackup=false`, `dataExtractionRules` 지정 |
| NETWORK-1 평문 통신 | 매니페스트·문자열 | INTERNET 권한 자체가 없어 네트워크 통신 없음. `usesCleartextTraffic` 미선언(targetSdk 34 기본 false). APK 안 `http://`는 XML 네임스페이스·라이선스 문자열뿐 |
| STORAGE/CRYPTO 비밀값 | APK 171개 파일 문자열 검사 | 개인 경로·메일·개인키·Google/GitHub/Slack/OpenAI 형식 키·서명 환경 변수 이름 0건. 서명 키는 저장소·APK에 없음 |
| PRIVACY | 코드 검토 | 개인정보 수집·전송 없음. 세션 상태는 Activity 저장 상태에만 보관 |

범위 밖: 난독화(R8 미적용), 우회·변조 탐지, 동적 분석 도구. 네트워크·민감정보가 없는 정차 시연앱이라 시제품 단계에서는 적용하지 않았다.
점검 범위 내 Critical/High 발견은 없다. 취약점 없음을 보장하는 것은 아니다.

## 제한

한국어 TTS는 기기의 엔진과 언어 데이터에 의존한다. 에뮬레이터는 `-no-audio`로 실행하므로 실제 청취 검증은 하지 않는다.
Google AAOS에는 Pleos Vehicle Service가 없으므로 공개 SDK의 실차값 성공 수신을 입증하지 못한다.
SDK 진단 버튼은 컴파일만 검증했으며 실차 성공 콜백과 실패 UI의 기기 재현은 다음 Pleos 통합 검증에 남긴다.
10-23 시점 APK는 debug 서명이었다. 11-11부터 심사용은 위 release APK다. 스토어 등록은 하지 않는다.
공식 Pleos Connect Emulator 2.1.0에서의 실행은 아직 검증하지 않았다.
