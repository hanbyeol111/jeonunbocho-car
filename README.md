# 전운보초

**옆자리에 아무도 없어도 혼자 연습할 수 있게.** 초보 운전자를 위한 Pleos Connect 연습 앱입니다. 기본 내비게이션 옆 창에서 연습 코스를 만들고, 연습 상황(유턴·회전교차로 등)이 가까워지면 할 일을 알려 주며, 주행이 끝나면 기록과 포인트를 보여 줍니다.

길 안내(지도·음성·TBT)는 Pleos 기본 내비게이션이 맡고, 전운보초는 소리를 내지 않고 화면으로만 돕습니다.

## 주요 기능

| 기능 | 설명 |
|---|---|
| 순환 코스 | 거리(1~50km)와 방향(시계·반시계)만 고르면 출발지로 돌아오는 경로를 기본 내비에 겁니다. |
| 상황별 연습 | 터널, 고가도로·지하차도, 급회전, 갈림길 차로 유지, 회전교차로, 유턴, 합류·분기, 고속도로 진입·진출 중 하나를 고르면 그 상황이 들어 있는 경로를 찾습니다. |
| 난이도별 코스 | Lv.1~10. 레벨이 오를수록 거리가 길어지고 어려운 상황이 들어갑니다. 지난 레벨은 다시 연습할 수 있습니다. |
| 목적지 안내 | 기본 내비에서 목적지를 정하고, 전운보초는 주행 중 안내만 더합니다. |
| 출발 전 코스 확인 | 실제 거리·예상 시간, 우회전·좌회전·유턴 횟수, 연습 상황, 받을 수 있는 포인트를 보여 줍니다. |
| 주행 화면 | 지금 할 일(다음 회전과 차선 준비) → 다음 연습 → 코스 진행. 연습 상황이 가까워지면(일반 도로 300m, 고속도로 1km) 도로교통법에 근거한 할 일을 최대 3줄로 보여 줍니다. |
| 결과 화면 | 통과한 연습 상황, 방향지시등 사용 여부, 거리·예상 시간 포인트와 레벨 변화를 보여 줍니다. 도중에 끝내면 포인트 없이 기록만 보여 줍니다. |
| 주행 규제 | DO(Distraction Optimization)를 선언하고, CarUxRestrictions의 설정 금지(`UX_RESTRICTIONS_NO_SETUP`)가 켜지면 코스 고르기 화면을 막습니다. 주행 화면과 결과 화면은 막지 않습니다. |

## 사용한 Pleos SDK·API

| SDK·API | 용도 |
|---|---|
| NaviHelper 3.0.5 | 경유지 2개로 순환 경로 요청(`requestRouteWithWaypoints`), 회전 목록(`onTBTInfo`), 경로 시작 정보(`onRouteStarted`), 도착(`onDestinationArrived`), 재탐색(`onReRouteEnded`), 현재 위치(`getCurrentLocationInfo`) |
| Vehicle SDK 2.3.2 | 방향지시등 상태(`TurnSignal.registerTurnSignalState`) |
| AAOS CarUxRestrictionsManager | 주행 규제 상태 수신(`registerListener`, `onUxRestrictionsChanged`) |

## 실행 방법

### APK로 설치

APK는 저장소의 [`apk/`](apk/) 폴더에 있습니다(`*_전운보초_release.apk`).

1. Pleos Connect Emulator를 실행합니다(아래 테스트 환경 참고).
2. APK를 설치합니다.
   ```bash
   adb install -r apk/<파일 이름>.apk
   ```
3. 앱 목록에서 **전운보초**를 엽니다. 기본 내비게이션과 나란히 띄우면(화면 분할) 함께 볼 수 있습니다.

### 소스에서 빌드

1. 이 저장소를 받아 Android Studio로 엽니다.
2. Gradle 동기화가 끝나면 실행 대상으로 Pleos Connect Emulator를 고르고 **Run 'app'** 을 누릅니다.
3. 단위 테스트는 에뮬레이터 없이 실행할 수 있습니다.
   ```bash
   ./gradlew testDebugUnitTest
   ```

Pleos SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/` 에서 받습니다(`settings.gradle.kts`에 등록돼 있습니다).

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0 (Android API 34), AVD 부팅 옵션 Cold boot |
| 그래픽 카드 | 외장 GPU 필요(Pleos 최소 사양 RTX 3060 6GB급). 개발에는 RTX 4070 Ti SUPER 사용 |
| 개발 도구 | Android Studio(내장 JDK로 Gradle 실행), compileSdk 37, minSdk 28 |
| 언어 | Kotlin |

### 테스트할 때 쓰는 설정

- **경로 안내**: 앱에서 코스를 만들면 기본 내비에 경로가 걸립니다. SDK에 경로 미리보기가 없어서, 맞는 경로를 찾는 동안 기본 내비 경로가 여러 번 바뀔 수 있습니다.
- **방향지시등**: Pleos Connect Virtual Console의 **Turn Signal State**에서 Left/Right를 켭니다.
- **주행 규제 켜기·끄기** (Pleos ADB 명령어 가이드):
  ```bash
  adb shell cmd hmg_car_service --service drivingstate driving_regulation on
  adb shell cmd hmg_car_service --service drivingstate driving_regulation off
  ```
- **화면 크기**: 시안 캔버스(600×800dp)보다 창이 크면 화면 전체를 같은 비율로 키웁니다. 라이트·다크 모드는 시스템 설정을 따릅니다.
- **로그**: Logcat에서 `JbRoute`(길 찾기), `JbDrive`(주행 기록), `JbUx`(주행 규제), `JbScreen`(창 크기)로 걸러 볼 수 있습니다.

## 현재 한계

- 과속·급감속 같은 안전 운전 항목은 아직 속도를 측정하지 않아 포인트에 넣지 않았습니다.
- 차선 번호 안내는 SDK에 차선 데이터가 없어, 회전 방향에 따른 참고 안내(예: "왼쪽 차선으로 미리 가 두면 편해요")만 제공합니다.
- 연습 상황 통과는 기본 내비 회전 목록의 변화로 판단합니다.

## 출처

- 아이콘: [Material Icons](https://fonts.google.com/icons) (Apache License 2.0)
- 주행 코칭 문구의 근거: 도로교통법 제14·18·19·22·25조의2·31·37·38·62·65조
