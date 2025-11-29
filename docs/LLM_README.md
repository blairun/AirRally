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

