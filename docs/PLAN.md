# Implementation Plan: AirRally Bluetooth Table Tennis Game

## Overview
This plan addresses the gaps identified in the current implementation and provides a roadmap to build a fully functional Bluetooth table tennis game according to the PRD specifications.

> **Task Management Note**: We are transitioning to using inline code tags (`TODO`, `FIXME`, `BUG`, `HACK`, `IDEA`) for granular task tracking. This document tracks high-level phases and architectural goals, while specific implementation details and micro-tasks should be managed via these tags in the codebase. See `docs/LLM_README.md` for details.

## Current State Analysis

### ✅ What's Already Done
- Project structure and build configuration
- Core game state model (`GameState`, `GamePhase`, `Player` enums)
- Basic game engine with scoring and serve rotation logic
- Interface definitions (`NetworkAdapter`, `SensorProvider`)
- AndroidManifest permissions declarations
- Compose UI setup
- Network protocol implementation and codec
- Concrete Bluetooth adapter using Nearby Connections
- Sensor implementation for swing detection
- Integration layer connecting all components
- User interface screens (menu, lobby, game, game over)
- Runtime permission handling
- Audio and haptic feedback (Basic implementation)
- Timing window logic for hit detection
- **Game Over Screen**: Show score, winner, "Play Again" button. [done]
- **Safety Dialog**: Warning on launch. [done]
- **Gameplay Expansion**:
    - Granular State Machine (`BallState`, `SwingType`). [done]
    - Swing Classification (Flat, Lob, Spike) based on accelerometer. [done]
    - Event Log & Hit Feedback UI. [done]
    - Hit Testing & Timing Window Settings. [done]

### ❌ What's Missing / To Do
- **Sound Effects Refinement**: Distinct tones for serve, bounce, hit, miss, score.
- **UI Polish**: Animations, graphics, themes.
- **Settings Screen**: Flight time, difficulty, debug mode.
- **Settings Screen**: Flight time, difficulty, debug mode.
- **Testing**: Comprehensive unit and integration tests.

### ✅ Recently Completed
- **Permissions Fix**: Added `ACCESS_COARSE_LOCATION` for older Android versions.
- **Gameplay Cooldown**: Added `POINT_SCORED` phase with 2s delay between points.
- **Debug Tones**: Verified 1:1 mapping and simultaneous playback with game sounds.

## Implementation Phases

---

## Phase 1: Foundation & Protocol Layer [COMPLETED]

**Goal**: Establish the communication protocol and network infrastructure.

### Component 1.1: Message Protocol

#### [NEW] [Protocol.kt](../app/src/main/java/com/air/pong/core/network/Protocol.kt)

Define all message types as sealed class hierarchy:
```kotlin
sealed class GameMessage {
    data class Handshake(val versionCode: Int) : GameMessage()
    data class Settings(val flightTime: Long, val difficulty: Int) : GameMessage()
    object StartGame : GameMessage()
    data class ActionSwing(val timestamp: Long, val force: Float) : GameMessage()
    data class Result(val hitOrMiss: Boolean, val scoringPlayer: Player?) : GameMessage()
    data class GameStateSync(val p1Score: Int, val p2Score: Int, val phase: GamePhase) : GameMessage()
    object Pause : GameMessage()
    object PeerLeft : GameMessage()
    object Rematch : GameMessage()
}
```

#### [NEW] [MessageCodec.kt](../app/src/main/java/com/air/pong/core/network/MessageCodec.kt)

Implement byte-based encoding/decoding:
- Each message starts with single byte type identifier (0x01-0x09)
- Followed by payload bytes specific to message type
- Use `ByteBuffer` for efficient serialization
- Add checksum validation for critical messages

**Message Format Examples**:
```
MSG_HANDSHAKE: [0x01][version:4 bytes]
MSG_ACTION_SWING: [0x04][timestamp:8 bytes][force:4 bytes]
MSG_RESULT: [0x05][hit:1 byte][scoringPlayer:1 byte]
```

### Component 1.2: Network Implementation

#### [NEW] [NearbyConnectionsAdapter.kt](../app/src/main/java/com/air/pong/network/NearbyConnectionsAdapter.kt)

Concrete implementation of `NetworkAdapter` using Google Nearby Connections API:

**Key Features**:
- Advertise as host with device name
- Discover and connect to host
- Send/receive byte array messages
- Emit connection state changes via Flow
- Handle connection lifecycle (connect, disconnect, error)
- Implement message queuing for reliability

**Dependencies**: Already included (`play-services-nearby:19.0.0`)

**Connection Flow**:
1. Host calls `startAdvertising(deviceName)`
2. Client calls `startDiscovery()` → discovers endpoints → connects to first/selected
3. Both sides exchange `MSG_HANDSHAKE` to verify version
4. Transition to CONNECTED state

#### [NEW] [NetworkMessageHandler.kt](../app/src/main/java/com/air/pong/core/network/NetworkMessageHandler.kt)

Helper to process incoming messages and convert them to game actions:
- Decode raw bytes using `MessageCodec`
- Validate message ordering/sequencing
- Emit typed message events via Flow

---

## Phase 2: Permissions & Sensors [COMPLETED]

**Goal**: Handle runtime permissions and implement sensor-based swing detection.

### Component 2.1: Permissions

#### [NEW] [PermissionsManager.kt](../app/src/main/java/com/air/pong/utils/PermissionsManager.kt)

Centralized permission handling:
- Check if required permissions are granted
- Request permissions using Activity Result API
- Handle permission denial with explanation dialog
- Expose permission state as StateFlow

**Required Permissions**:
- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
- Android 11-: `ACCESS_FINE_LOCATION`, `BLUETOOTH`, `BLUETOOTH_ADMIN`

### Component 2.2: Sensor Implementation

#### [NEW] [AccelerometerSensorProvider.kt](../app/src/main/java/com/air/pong/sensors/AccelerometerSensorProvider.kt)

Concrete implementation of `SensorProvider`:

**Algorithm**:
1. Register for `TYPE_LINEAR_ACCELERATION` sensor updates
2. Calculate magnitude: `sqrt(x² + y² + z²)`
3. Trigger swing when magnitude > 2.0g (configurable threshold)
4. Debounce: Ignore swings within 500ms of last swing
5. Emit `SwingEvent(timestamp, force)` via Flow

**Tuning Parameters**:
- `SWING_THRESHOLD`: 2.0g (can adjust based on testing)
- `DEBOUNCE_MS`: 500ms
- Sensor sampling rate: `SENSOR_DELAY_GAME` (20ms)

---

## Phase 3: Core Integration & Game Logic [COMPLETED]

**Goal**: Wire all components together and implement missing game logic.

### Component 3.1: GameEngine Fixes

#### [MODIFY] [GameEngine.kt](../app/src/main/java/com/air/pong/core/game/GameEngine.kt)

**Changes**:
1. **Fix isHost tracking**: Call `setLocalPlayer()` from `startGame()`
2. **Add timing window logic**:
   ```kotlin
   ```kotlin
   fun checkHitTiming(swingTimestamp: Long): HitResult {
       val arrival = gameState.value.ballArrivalTimestamp
       val delta = swingTimestamp - arrival
       // Window is shifted to start at the bounce (BOUNCE_OFFSET_MS before arrival)
       // to prevent hitting before the bounce sound.
       val startWindow = -BOUNCE_OFFSET_MS
       val endWindow = startWindow + (2 * getHitWindow())
       
       return when {
           delta < startWindow -> HitResult.MISS_EARLY
           delta <= endWindow -> HitResult.HIT
           else -> HitResult.MISS_LATE
       }
   }
   ```
   ```
3. **Add network message sender callback**: Pass messages to be sent
4. **Implement receiver-authoritative logic**: The receiving device determines hit/miss

### Component 3.2: Game Coordinator

#### [NEW] [GameViewModel.kt](../app/src/main/java/com/air/pong/ui/GameViewModel.kt)

Central orchestrator that connects all layers:

**Responsibilities**:
- Manages `GameEngine`, `NetworkAdapter`, `SensorProvider` instances
- Observes sensor swings → validates timing → calls GameEngine
- Observes GameEngine state changes → sends network messages
- Observes network messages → updates GameEngine
- Exposes UI state via StateFlow

**Flow Diagram**:
```
SensorProvider → GameViewModel → GameEngine
                      ↕
               NetworkAdapter
```

**Example Flows**:
- **Local Swing**: Sensor detects swing → ViewModel validates if it's my turn → calls `GameEngine.processSwing()` → sends `MSG_ACTION_SWING` to opponent
- **Remote Swing**: Receive `MSG_ACTION_SWING` → update GameEngine → check timing → send `MSG_RESULT` back
- **Miss**: Hit window expires → ViewModel detects → calls `GameEngine.handleMiss()` → sends `MSG_RESULT`

---

## Phase 4: User Interface [COMPLETED]

**Goal**: Build all required screens with proper state rendering.

### Screen 4.1: Main Menu

#### [NEW] [MainMenuScreen.kt](../app/src/main/java/com/air/pong/ui/screens/MainMenuScreen.kt)

**Components**:
- App title/logo
- "Host Game" button → starts advertising
- "Join Game" button → starts discovery
- Connection status indicator
- Settings button

**State Handling**:
- Disable buttons if permissions not granted
- Show "Connecting..." when advertising/discovering
- Navigate to Lobby when connected

### Screen 4.2: Lobby Screen

#### [NEW] [LobbyScreen.kt](../app/src/main/java/com/air/pong/ui/screens/LobbyScreen.kt)

**Components**:
- "Connected to: [opponent name]" display
- Settings display (host can adjust, client sees read-only)
- "Start Game" button (host only)
- "Leave" button

**Flow**:
1. Host adjusts settings → sends `MSG_SETTINGS`
2. Both players ready → Host presses Start → sends `MSG_START_GAME`
3. Navigate to Game Screen

### Screen 4.3: Game Screen

#### [NEW] [GameScreen.kt](../app/src/main/java/com/air/pong/ui/screens/GameScreen.kt)

**Layout** (per PRD Section 5):
- **Background color**: Green when my turn, Red when opponent's turn
- **Score display**: Large text showing "P1: X | P2: Y"
- **State message**: "Your Serve", "Opponent Serving", "Rally!", etc.
- **Debug overlay** (toggleable): Current state, RTT, sensor peak, opponent swing data

**Dynamic Updates**:
- Observe `GameViewModel.gameState`
- Animate background color transitions
- Update scores immediately on point
- Show serve prompt when `gamePhase == WAITING_FOR_SERVE && isMyTurn`

### Screen 4.4: Game Over Screen

#### [NEW] [GameOverScreen.kt](../app/src/main/java/com/air/pong/ui/screens/GameOverScreen.kt)

**Components**:
- Winner announcement
- Final score display
- "Rematch" button (sends `MSG_REMATCH`)
- "Main Menu" button

### Navigation

#### [MODIFY] [MainActivity.kt](../app/src/main/java/com/air/pong/ui/MainActivity.kt)

Replace placeholder with Compose Navigation:
- Use `NavHost` with routes: `main_menu`, `lobby`, `game`, `game_over`
- Initialize `GameViewModel` as Activity-scoped
- Handle permission requests via `rememberLauncherForActivityResult`

---

## Phase 5: Feedback & Polish [IN PROGRESS]

**Goal**: Add audio/haptic feedback and lifecycle management.

### Component 5.1: Audio

#### [NEW] [AudioManager.kt](../app/src/main/java/com/air/pong/audio/AudioManager.kt)

**Sound Effects** (generate or use free assets):
- `sfx_hit.mp3`: Played on the device that swung
- `sfx_bounce.mp3`: Played on receiving device at `T_arrival - (FlightTime/2)`
- `sfx_miss.mp3`: Played when swing is outside window
- `winner_*.mp3`: Randomly played on winner's device at Game Over [DONE]

**Implementation**:
- Use `MediaPlayer` or `SoundPool` for low-latency playback
- Preload all sounds on initialization
- Expose `playHit()`, `playBounce()`, `playMiss()`, `playRandomWinSound()` methods

**Integration**:
- GameViewModel observes game events → calls AudioManager

### Component 5.2: Haptics

#### [NEW] [HapticManager.kt](../app/src/main/java/com/air/pong/haptics/HapticManager.kt)

**Vibration Patterns**:
- Short vibration (20ms) on successful hit
- Long vibration (200ms) on miss/loss

**Implementation**:
- Use `Vibrator.vibrate(VibrationEffect)`
- Check API level for compatibility

### Component 5.3: Lifecycle Management

#### [MODIFY] [GameViewModel.kt](../app/src/main/java/com/air/pong/ui/GameViewModel.kt)

**Add lifecycle awareness**:
- Observe Activity lifecycle via `LifecycleObserver`
- On `onPause()`: Send `MSG_PAUSE` if in game
- On `onResume()`: Resume if paused
- On `onDestroy()`: Send `MSG_PEER_LEFT` and clean up

**Disconnection Handling**:
- Observe `NetworkAdapter.connectionState`
- On `ERROR` or `DISCONNECTED` during game → show "Connection Lost" dialog → return to main menu

---

## Phase 6: Testing & Edge Cases

**Goal**: Ensure robustness and correctness.

### Component 6.1: Unit Tests

#### [NEW] [GameEngineTest.kt](../app/src/test/java/com/air/pong/core/game/GameEngineTest.kt)

**Test Cases**:
- Serve rotation: Verify every 2 points, alternating at deuce
- Scoring: Verify correct player receives point on miss
- Win conditions: Win at 11 by 2, deuce extends game
- Turn tracking: Verify `isMyTurn` is correct for host/client

### Component 6.2: Integration Tests

**Manual Testing Scenarios**:
1. Connect two devices via Bluetooth
2. Play full game to completion
3. Test rapid swings (debouncing)
4. Test edge of timing window (early/late hits)
5. Test disconnection and reconnection

**Potential Issues to Watch**:
- Clock drift between devices (use relative timestamps)
- Message ordering (especially rapid swings)
- Network latency affecting timing

---

## Phase 7: Final Polish

**Goal**: Complete remaining features and refine UX.

### Component 7.1: Settings Screen

#### [NEW] [SettingsScreen.kt](../app/src/main/java/com/air/pong/ui/screens/SettingsScreen.kt)

**Settings**:
- Flight time: Slider from 500ms to 2000ms
- Difficulty: Easy/Medium/Hard (affects hit window ± 300/200/100ms)
- Debug mode: Toggle debug overlay
- Sensor sensitivity: Adjust swing threshold
- About Section: Version, Credits, Links

### Component 7.2: Safety Warning

#### [NEW] [SafetyDialog.kt](../app/src/main/java/com/air/pong/ui/dialogs/SafetyDialog.kt)

Show on first app launch:
- Warning about swinging phone
- Recommendation to use wrist strap
- Clear play area
- Checkbox "Don't show again"

### Component 7.3: UI Polish

- Add animations for score changes
- Add particle effects or visual feedback on hits
- Smooth background color transitions
- Loading indicators during connection

- Add animations for score changes
- Add particle effects or visual feedback on hits
- Smooth background color transitions
- Loading indicators during connection

### Component 7.4: Gameplay Expansion [COMPLETED]

**Goal**: Enhance depth with granular states and swing types.

**Features**:
- **Swing Classification**: Detect Flat, Lob, Spike based on X/Y/Z acceleration.
- **Ball State**: Track if ball is In Air, Bounced on My Side, or Bounced on Opponent Side.
- **Variable Flight Time**: Lobs are slower (1.5x), Spikes are faster (0.5x).
- **Event Log**: Real-time readout of game events (e.g., "Opponent Hit: HARD SPIKE").
- **Hit Testing**: Settings panel to visualize sensor data and swing type.

### Component 7.5: Swing Types & Risk

**Swing Classification Table**:

| Swing Type | Description | Flight Time Modifier | Net Risk | Out Risk | Window Shrink |
|---|---|---|---|---|---|---|
| **SOFT_FLAT** | Gentle push. | 1.2x (Slower) | 0% | 0% | 0% |
| **MEDIUM_FLAT** | Standard hit. | 1.0x (Normal) | 0% | 5% | 20% |
| **HARD_FLAT** | Aggressive drive. | 0.8x (Fast) | 5% | 15% | 35% |
| **SOFT_LOB** | Low lob. | 1.4x (Slow) | 0% | 0% | 0% |
| **MEDIUM_LOB** | Standard lob. | 1.5x (Slower) | 0% | 5% | 0% |
| **HARD_LOB** | High lob. | 1.6x (Slowest) | 0% | 20% | 0% |
| **SOFT_SPIKE** | Weak spike. | 0.8x (Fast) | 50% | 0% | 0% |
| **MEDIUM_SPIKE** | Controlled smash. | 0.6x (Faster) | 15% | 5% | 40% |
| **HARD_SPIKE** | Full power smash. | 0.4x (Fastest) | 30% | 10% | 60% |

### Risk & Difficulty Logic

#### 1. Risk (Hitter's Side)
When a swing is detected, we roll a die (0-100):
-   **Net Check**: If random < Net Risk, the shot hits the net. Immediate point for opponent.
-   **Out Check**: If random < (Net Risk + Out Risk), the shot goes out. Immediate point for opponent.
-   **Success**: If random >= (Net Risk + Out Risk), the shot is valid and travels to the opponent.

#### 2. Window Shrink (Receiver's Side)
If the shot is valid, the **Receiver's Hit Window** is tightened based on the incoming shot type.
-   *Formula*: `EffectiveWindow = BaseWindow * (100% - WindowShrink)`
-   *Example*: If Base Window is ±200ms (Total 400ms):
    -   Incoming `MEDIUM_FLAT` (10% shrink) → Window becomes ±180ms.
    -   Incoming `HARD_SPIKE` (50% shrink) → Window becomes ±100ms.
This makes aggressive shots significantly harder to return, rewarding the risk taken by the hitter.

## Verification Plan

### Automated Tests
- Run `./gradlew test` for unit tests
- Verify GameEngine logic with 100% coverage on core paths

### Manual Verification
1. **Connection Flow**: Host → Client connection successful
2. **Full Game**: Play to 11 points, verify scoring
3. **Serve Rotation**: Verify serves rotate correctly including deuce
4. **Timing**: Test early/late swings, verify miss detection
5. **Audio/Haptic**: Verify feedback plays correctly
6. **Lifecycle**: Background app during game, verify pause/resume
7. **Disconnection**: Disconnect during game, verify graceful handling
8. **Permissions**: Deny permissions, verify buttons disabled and explanation shown

### Performance Testing
- Measure round-trip time (RTT) for messages
- Verify no dropped frames during gameplay
- Check battery consumption during extended play

---

## Implementation Order

The phases should be tackled in order, but within each phase, components can be parallelized:

**Week 1**: Phase 1 (Protocol & Network)
**Week 2**: Phase 2 (Permissions & Sensors) + Phase 3 (Integration)
**Week 3**: Phase 4 (UI)
**Week 4**: Phase 5 (Feedback) + Phase 6 (Testing)
**Week 5**: Phase 7 (Polish) + Final QA

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| **Bluetooth latency** | Use relative timestamps; tune hit window based on testing |
| **Clock synchronization** | Ignore remote timestamps; estimate swing time based on message arrival minus fixed latency (handled in GameViewModel) |
| **Message loss** | Add sequence numbers and acknowledgments for critical messages |
| **Device compatibility** | Test on multiple devices; Require Gyroscope & Gravity sensors in Manifest & Runtime |
| **Sensor variance** | Make swing threshold configurable; provide calibration flow |

---

## Dependencies Summary

All required dependencies are already in `build.gradle.kts`:
- ✅ Nearby Connections: `play-services-nearby:19.0.0`
- ✅ Compose: Already configured
- ✅ Coroutines: Kotlin stdlib includes basic support
- ✅ Lifecycle: `lifecycle-runtime-ktx:2.7.0`

**No additional dependencies needed.**

---

## Success Criteria

The implementation is complete when:
1. ✅ Two devices can connect via Bluetooth
2. ✅ Players can play a full game with swing detection
3. ✅ Scoring and serve rotation work correctly
4. ✅ Audio and haptic feedback provide clear event signals
5. ✅ UI clearly indicates game state and whose turn it is
6. ✅ All edge cases (disconnect, pause, etc.) are handled gracefully
7. ✅ Code follows the architecture defined in PRD and ADR-001


