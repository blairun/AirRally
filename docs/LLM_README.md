# LLM README: Ping Project Context

> **CRITICAL FOR AI AGENTS:** Read this file FIRST before making any architectural changes or implementing new features.

> **NOTE:** Respect the .llmignore file!

> **NOTE:** After making updates, if appropriate, you can run these commands to test changes.

```powershell
# Sync Project with Gradle Files (only if they have changed)
.\gradlew --refresh-dependencies

# Test build
.\gradlew assembleDebug

# Run Test Suite
.\gradlew :app:testDebugUnitTest --tests "*GameEngineTest"

# Build, Install
.\gradlew installDebug
# Lanuch on all devices
adb devices | Select-String "\tdevice" | ForEach-Object {
    $deviceId = $_.ToString().Split("`t")[0]
    Write-Host "Starting app on: $deviceId"
    adb -s $deviceId shell am start -n com.air.pong/.ui.MainActivity
}
```

## 1. Project Map
This project is a two-player Bluetooth table tennis game.
-   **`app/src/main/java/.../core`**: The "Brain". Pure Kotlin.
    -   `network/`: Bluetooth implementation (Nearby Connections).
    -   `sensors/`: Raw sensor data processing.
    -   `game/`: The State Machine. **Source of Truth**.
        -   `GameEngine.kt`: Main state machine.
        -   `SwingExtensions.kt`: Swing physics and classification logic.
-   **`app/src/main/java/.../ui`**: The "Face". Android Views/Composables.
    -   `screens/`: Top-level screens.
        -   `settings/`: Settings sub-screens (Appearance, Debug, etc.).
    -   Observes `GameState` from `core`.
    -   Sends `UserIntent` (e.g., "Serve Button Pressed") to `core`.

## 2. Active Constraints (DO NOT BREAK)
1.  **No External Physics Engines**: Use simple time-based math (`FlightTime`). Do not import Box2D or similar.
2.  **State Machine Supremacy**: The UI *never* decides game state. It only renders the current state from the `GameEngine`.
3.  **Byte-Based Protocol**: Network messages must be compact (Bytes/Ints), not JSON strings, to minimize latency.
4.  **Context-First Docs**: If you change the architecture, you **MUST** update this file.

## 3. Key Concepts
-   **"The Ball"**: The ball is not a physics object. It is a *timer*.
    -   When Player A hits, a timer starts.
    -   When the timer expires (+/- window), Player B must have swung.
-   **"The Swing"**: A swing is a `SensorEvent` where acceleration > Threshold.

## 4. Current Architecture Decision Records (ADR)
-   `docs/adr/001-project-structure.md`: Why we split `core` and `ui`.

## 5. Task Management Strategy
We use inline code comments to track granular tasks, bugs, and ideas. This allows us to keep the context right next to the code.

### Tag Types
-   `TODO`: General task or feature to be implemented.
-   `FIXME`: Code that works but needs improvement or refactoring.
-   `BUG`: Known defect that needs fixing.
# LLM README: Ping Project Context

> **CRITICAL FOR AI AGENTS:** Read this file FIRST before making any architectural changes or implementing new features.
>
> **NOTE:** The user will handle project sync and building in Android Studio. Do NOT attempt to run `./gradlew` commands or build the project yourself! Assume the environment is set up correctly.

> **NOTE:** Respect the .llmignore file!

## 1. Project Map
This project is a two-player Bluetooth table tennis game.
-   **`app/src/main/java/.../core`**: The "Brain". Pure Kotlin.
    -   `network/`: Bluetooth implementation (Nearby Connections).
    -   `sensors/`: Raw sensor data processing.
    -   `game/`: The State Machine. **Source of Truth**.
-   **`app/src/main/java/.../ui`**: The "Face". Android Views/Composables.
    -   Observes `GameState` from `core`.
    -   Sends `UserIntent` (e.g., "Serve Button Pressed") to `core`.

## 2. Active Constraints (DO NOT BREAK)
1.  **No External Physics Engines**: Use simple time-based math (`FlightTime`). Do not import Box2D or similar.
2.  **State Machine Supremacy**: The UI *never* decides game state. It only renders the current state from the `GameEngine`.
3.  **Byte-Based Protocol**: Network messages must be compact (Bytes/Ints), not JSON strings, to minimize latency.
4.  **Context-First Docs**: If you change the architecture, you **MUST** update this file.

## 3. Key Concepts
-   **"The Ball"**: The ball is not a physics object. It is a *timer*.
    -   When Player A hits, a timer starts.
    -   When the timer expires (+/- window), Player B must have swung.
-   **"The Swing"**: A swing is a `SensorEvent` where acceleration > Threshold.

## 4. Current Architecture Decision Records (ADR)
-   `docs/adr/001-project-structure.md`: Why we split `core` and `ui`.

## 5. Task Management Strategy
We use inline code comments to track granular tasks, bugs, and ideas. This allows us to keep the context right next to the code.

### Tag Types
-   `TODO`: General task or feature to be implemented.
-   `FIXME`: Code that works but needs improvement or refactoring.
-   `BUG`: Known defect that needs fixing.
-   `HACK`: Temporary workaround that should be addressed later.
-   `IDEA`: A suggestion or thought for future improvement.

### LLM Workflow for Tags
1.  **Discovery**: Look for these tags in the codebase to identify "micro-tasks".
2.  **Resolution**: When you address a tagged issue, **YOU MUST REMOVE THE TAG**. Do not leave stale TODOs.
3.  **Documentation**: If the resolution is complex, replace the tag with a permanent comment explaining the solution, or update the `docs/` if it's an architectural change.

## 6. Release Process
To create a new release, use the `release.ps1` script in the root directory.

### Prerequisites
-   `keystore.properties` should exist for signed release builds (see `build.gradle.kts` for format).
-   If `keystore.properties` is missing, the script will build a Debug APK.

### Usage
```powershell
.\release.ps1
```

### What it does
1.  Bumps the `versionName` (patch) and `versionCode` in `app/build.gradle.kts`.
2.  Commits and pushes the version bump to Git.
3.  Builds the APK (`assembleRelease` or `assembleDebug`).
4.  Prints instructions for creating the GitHub release.

### Dry Run
To see what will happen without making changes:
```powershell
.\release.ps1 -DryRun
```

## 7. Debugging
-   **Debug Mode**: Accessible via Settings -> Debug Settings.
    -   Allows entering a single-player "Debug Game" to test UI and logic.
    -   Includes "Simulate Hit" and "Auto-Play" controls to simulate opponent behavior.
