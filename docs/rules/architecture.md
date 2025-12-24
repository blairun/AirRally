---
trigger: always_on
---

# Project Architecture

## 1. Project Map
This project is a two-player Bluetooth table tennis game.
-   **`app/src/main/java/.../core`**: The "Brain". Pure Kotlin.
    -   `network/`: Bluetooth implementation (Nearby Connections).
    -   `sensors/`: Raw sensor data processing.
    -   `game/`: The State Machine. **Source of Truth**.
        -   `GameEngine.kt`: Main state machine, delegates mode-specific logic to strategies.
        -   `GameModeStrategy.kt`: Interface for game mode behaviors.
        -   `ClassicModeStrategy.kt`: 1v1 competitive mode (win by 2, serve rotation).
        -   `RallyModeStrategy.kt`: Co-op mode (shared lives, 3×3 grid, line bonuses).
        -   `ModeState.kt`: Sealed class for mode-specific state containers.
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
4.  **Strategy Pattern for Modes**: New game modes MUST be added via `GameModeStrategy` implementations, NOT by adding conditionals to GameEngine.
5.  **Context-First Docs**: If you change the architecture, you **MUST** update this file.

## 3. Key Concepts
-   **"The Ball"**: The ball is not a physics object. It is a *timer*.
    -   When Player A hits, a timer starts.
    -   When the timer expires (+/- window), Player B must have swung.
-   **"The Swing"**: A swing is a `SensorEvent` where acceleration > Threshold.
-   **"Game Mode Strategy"**: Mode-specific behavior (scoring, win conditions, risk) is encapsulated in strategy classes. GameEngine delegates to these strategies.

## 4. Adding a New Game Mode
1. Create a new class implementing `GameModeStrategy` (e.g., `SoloRallyModeStrategy.kt`)
2. Add the mode to the `GameMode` enum in `GameState.kt`
3. Update `GameEngine.getStrategy()` to return your strategy for the new mode
4. Add a new `ModeState` subclass if the mode needs custom state
5. Update UI as needed to handle new mode's display requirements

## 5. ADR Summary
-   **Core vs UI Split**: We enforce a strict separation of concerns. `core` contains all business logic and must be testable without an emulator (mostly). `ui` is passive and only renders state.
-   **Strategy Pattern (Dec 2025)**: Game modes use Strategy pattern to avoid conditional soup. Each mode defines its own scoring, risk, and win conditions. 