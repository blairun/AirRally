# ADR 001: Context-First Project Structure

## Status
Accepted

## Context
Previous iterations of this project failed because LLMs (and developers) lost track of the complex interactions between Bluetooth, Sensors, and Game Logic. The code became "spaghetti" with UI logic mixed into network callbacks.

## Decision
We will enforce a strict separation of concerns using a "Core vs. UI" split.

1.  **`core/`**: Contains all business logic.
    -   Must be testable *without* an Android Emulator (mostly).
    -   Defines the `GameState` and `GameEngine`.
2.  **`ui/`**: Contains all Android Views/Activities.
    -   Passive. Only renders state.
3.  **`LLM_README.md`**: A dedicated context file for AI agents.

## Consequences
-   **Pros**: Easier to test, easier for LLMs to reason about "Game Logic" vs "Android Boilerplate".
-   **Cons**: Requires more boilerplate interfaces (e.g., `INetworkAdapter`) to decouple `core` from Android APIs.
