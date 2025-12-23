# Testing Strategy

This document outlines the testing strategy for the AirRally Bluetooth Table Tennis Game.

## Unit Tests

Unit tests are located in `app/src/test/java/`. They run on the local machine's JVM and do not require an Android device.

### Running Unit Tests
To run all unit tests, execute the following command in the terminal:
```bash
./gradlew test
```
(On Windows, use `.\gradlew.bat test`)

### Coverage
The `GameEngineTest` class covers the core game logic, including:
- **Game State**: Initial state, scoring, win conditions.
- **Serve Rotation**: Standard rotation and deuce logic.
- **Timing Windows**: Hit/Miss detection based on arrival time.
- **Swing Classification**: Logic to categorize swings as FLAT, LOB, or SMASH based on sensor data.
- **Flight Time Modifiers**: Adjustments to ball speed based on swing type.
- **Window Shrink**: Difficulty adjustments based on incoming shot aggression.
- **Risk Logic**: Probabilistic fault detection (Net/Out) for risky shots in Classic mode. Rally mode has zero risk since players are cooperating.

## Real Device Tests (Instrumented / Manual)

Certain features rely on hardware sensors and Bluetooth hardware, making them impossible to test accurately on standard emulators or via unit tests. These must be tested on real Android devices.

### Critical Real-Device Scenarios

#### 1. Bluetooth Connectivity
- **Discovery**: Verify devices can find each other.
- **Connection**: Verify stable connection establishment.
- **Latency**: Measure round-trip time (RTT) in a real environment.
- **Reconnection**: Verify behavior when a device moves out of range and returns.

#### 2. Sensor Accuracy
- **Swing Detection**: Verify that real physical swings trigger the accelerometer threshold.
- **Classification**:
    - **LOB**: Tilt screen UP and swing -> Should detect LOB.
    - **SMASH**: Tilt screen DOWN and swing -> Should detect SMASH.
    - **FLAT**: Hold screen VERTICAL and swing -> Should detect FLAT.
- **False Positives**: Verify that walking or shaking the phone gently does *not* trigger a swing.

#### 3. Audio & Haptics
- **Audio Latency**: Verify that the "bounce" sound plays at the correct time relative to the physical swing.
- **Haptic Feedback**: Verify vibration intensity and duration for hits vs. misses.

#### 4. Performance
- **Battery Usage**: Monitor battery drain during a 10-minute session.
- **Thermal**: Ensure devices do not overheat during extended play.

### Manual Test Checklist

| Category | Test Case | Expected Result |
|---|---|---|
| **Connection** | Host creates game, Client joins | Both see "Connected" in Lobby |
| **Gameplay** | Host serves | Client hears "Bounce" sound approx 1s later |
| **Gameplay** | Client returns serve | Host hears "Bounce" sound |
| **Gameplay** | Rally for 10+ hits | No sync errors or "ghost" misses |
| **Sensors** | Player performs "Smash" motion | Game logs "SMASH" swing type |
| **Sensors** | Player performs "Lob" motion | Game logs "LOB" swing type |
| **Lifecycle** | Press Home button during game | Game pauses, opponent notified |
| **Lifecycle** | Resume app | Game resumes or offers rematch |
| **Lifecycle** | Background/Resume in Settings | Sensor testing continues to work after resume |
