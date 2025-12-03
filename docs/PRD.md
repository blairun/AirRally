# Project Requirements Document: Ping (Bluetooth Table Tennis)

## 1. Overview
"Ping" is a two-player mobile game that simulates table tennis using two Android devices connected via Bluetooth. The devices act as paddles, utilizing onboard sensors (accelerometer/gyroscope) to detect swings. The game relies on audio and haptic feedback to simulate the ball being hit and bouncing.

## 2. Core Gameplay Loop (The "Happy Path")
1.  **Connection**: Player 1 (Host) starts advertising. Player 2 (Client) scans and connects.
2.  **Lobby**: Both players confirm readiness.
3.  **Serve**: Player 1 is prompted to serve. They swing the phone.
4.  **Ball Travel**: A timer starts. Audio cues play on both devices.
5.  **Return**: Player 2 swings within the "Hit Window".
    -   **Hit**: The ball returns to Player 1.
    -   **Miss**: Player 1 scores a point.
6.  **Scoring**: First to 11 points wins.

## 3. Functional Requirements

### 3.1 Connectivity & State Management
The game is driven by a strict State Machine to ensure synchronization.

**States:**
-   `STATE_DISCONNECTED`: App start / Idle.
-   `STATE_ADVERTISING`: Host waiting for connection.
-   `STATE_DISCOVERING`: Client looking for host.
-   `STATE_CONNECTED_LOBBY`: Connected, waiting for "Start Game".
-   `STATE_GAME_SERVING`: Waiting for the server to swing.
-   `STATE_GAME_RALLY`: Ball is "in the air".
-   `STATE_GAME_ENDED`: Score limit reached.

**Protocol:**
-   **Transport**: Bluetooth (via Android Nearby Connections API or standard Bluetooth).
-   **Message Format**: Byte-based for low latency.
    -   `MSG_HANDSHAKE(version_code)`: Verify app version compatibility.
    -   `MSG_SETTINGS(flight_time, difficulty)`: Host sends authoritative settings.
    -   `MSG_START_GAME`: Host initiates.
    -   `MSG_ACTION_SWING(timestamp, force, swingType)`: Sent by the player who swung.
    -   `MSG_RESULT(hit_or_miss)`: Sent by the *receiving* device (Receiver Authoritative).
    -   `MSG_GAME_STATE(score_p1, score_p2, current_state)`: Periodic sync.
    -   `MSG_PAUSE`: Sent if app is backgrounded.
    -   `MSG_PEER_LEFT`: Sent on graceful exit.
    -   `MSG_REMATCH`: Sent by Host to restart game from Score Screen.

### 3.2 Physics & Sensor Logic (Expanded v2)
To avoid complexity, we use a deterministic "Time-of-Flight" model.

-   **Swing Detection**:
    -   Monitor Linear Accelerometer.
    -   Trigger: Magnitude > `THRESHOLD_SWING_FORCE` (e.g., 2.0g).
    -   **Classification**:
        -   **Type**: `FLAT` (Push), `LOB` (Pull/Up), `SMASH` (Down) based on Z-axis.
        -   **Force**: `SOFT`, `MEDIUM`, `HARD` based on Magnitude.
    -   Debounce: Ignore subsequent swings for 500ms.

-   **Ball Travel Logic**:
    -   `BaseFlightTime`: 1000ms (Adjustable in settings).
    -   **Modifiers**:
        -   `SOFT`: 1.2x FlightTime
        -   `HARD`: 0.8x FlightTime
        -   `LOB`: 1.5x FlightTime
        -   `SMASH`: 0.5x FlightTime (High Risk)
    -   When Player A hits at `T_hit`:
        -   Player B expects arrival at `T_arrival = T_hit + (BaseFlightTime * Modifier)`.
    -   **Hit Window**: Player B must swing between `T_arrival - Window` and `T_arrival + Window`.
    -   **Receiver Authoritative**: The receiving device determines if the ball was hit or missed to avoid desync.
        -   If Player B swings in window -> Send `MSG_RESULT(HIT)`.
        -   If Window expires without swing -> Send `MSG_RESULT(MISS)`.

### 3.3 Lifecycle & Edge Cases
-   **Permissions**:
    -   Must request `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (Android 12+).
    -   Must request `ACCESS_FINE_LOCATION` (Android 11 and below).
    -   If denied: Show explanation dialog. Disable "Host/Join" buttons.
-   **Backgrounding**:
    -   If app enters `onPause()` (User gets a call/home button):
        -   Send `MSG_PAUSE`.
        -   Both devices enter `STATE_PAUSED`.
        -   Show "Opponent Paused" overlay.
-   **Disconnection**:
    -   If socket closes unexpectedly:
        -   Show "Connection Lost".
        -   Return to Main Menu.
-   **Settings Sync**:
    -   Host is authoritative.
    -   Settings are locked once `STATE_GAME_SERVING` begins.

### 3.3 Feedback
-   **Audio**:
    -   `sfx_hit`: Played immediately on the device that swung.
    -   `sfx_bounce`: Played on the *receiving* device at `T_arrival - (FlightTime/2)`.
    -   `sfx_miss`: Played when a swing is detected outside the window.
-   **Haptics**:
    -   Short vibration on valid hit.
    -   Long vibration on miss/loss.

## 4. Technical Requirements
-   **Platform**: Android (Min SDK 24).
-   **Language**: Kotlin.
-   **Localization**: English, German, Spanish, French, Italian, Japanese, Korean, Portuguese (Brazil), Russian, Chinese (Simplified).
-   **Architecture**: MVVM with a "Context-First" modular structure.
    -   `core/`: Pure Kotlin logic (State Machine, Physics calc).
    -   `app/`: Android specific implementation.
-   **Permissions**:
    -   `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`.
    -   `ACCESS_FINE_LOCATION` (for legacy Bluetooth if needed).

## 5. UI/UX Design
-   **Main Menu**: Host/Join buttons.
-   **Game Screen**:
    -   **Visuals**: Minimalist. Large Score. Background color changes based on state (Green=My Turn, Red=Opponent's Turn).
    -   **Debug Overlay**: (Toggleable) Shows current State, Last RTT, Sensor Peak.

## 6. Risks & Mitigations
-   **Latency**: Use relative timestamps (e.g., "Swing happened 50ms ago") rather than absolute clocks if synchronization is hard.
-   **Safety**: Mandatory "Safety Warning" dialog on app launch.

## 7. Future Scope (Post-v1)
-   Spin mechanics (using Gyroscope).
-   Variable flight time based on swing force.
