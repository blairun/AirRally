---
trigger: always_on
---

# Development Guidelines

## 1. Useful commands for testing changes

```powershell
# Sync Project with Gradle Files (only if they have changed)
.\gradlew --refresh-dependencies

# Test build
.\gradlew assembleDebug

# Run Test Suite
.\gradlew :app:testDebugUnitTest --tests "*GameEngineTest"

# Build, Install
.\gradlew installDebug
```

## 2. Task Management Strategy
We use inline code comments to track granular tasks, bugs, and ideas.

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

## 3. Debugging
-   **Debug Mode**: Accessible via Settings -> Debug.
    -   Allows entering a single-player "Debug Game" to test UI and logic.
    -   Includes "Simulate Hit" and "Auto-Play" controls to simulate opponent behavior.