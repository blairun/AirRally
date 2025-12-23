# AirRally Rebuild Options

Comparison of alternatives for rebuilding "AirRally" for Android & iOS with "Vibe Coding" (AI-assisted rapid dev) in mind.

| Feature | **Flutter** | **React Native (Expo)** | **Kotlin Multiplatform (KMP)** | **Unity** |
| :--- | :--- | :--- | :--- | :--- |
| **Language** | Dart | TypeScript / JavaScript | Kotlin | C# |
| **"Vibe Coding"** | ⭐⭐⭐⭐⭐<br>AI models define UI/logic very well. Hot reload is instant. | ⭐⭐⭐⭐⭐<br>Excellent AI support. Web-like dev cycle. | ⭐⭐⭐<br>Great for logic, but setting up multiplatform UI/Gradle is complex for AI to one-shot. | ⭐⭐<br>AI writes scripts well, but cannot "see" or edit the visual Editor/Scene easily. |
| **Cross-Platform P2P** | ✅ **Good**<br>Plugins like `nearby_connections` wrap Native APIs. Handling iOS/Android cross-play is solved by some packages. | ⚠️ **Complex**<br>Most libs are platform-specific (Android Nearby vs iOS Multipeer). Cross-play often requires custom BLE/WiFi implementation. | ⚠️ **Manual**<br>You keep your current logic! But you must manually write the iOS P2P layer (Swift/ObjC) and wrap it. | ✅ **Great**<br>Many paid assets/libraries handle this. Overkill for a simple UI game? |
| **Sensors** | ✅ `sensors_plus` is standard and rock solid. | ✅ Available, but bridge performance *can* be an issue for high-frequency updates (rare for ping pong). | ✅ Native performance. | ✅ Built-in Input system is robust. |
| **Re-use Existing Code** | ❌ Rewrites logic from Kotlin to Dart. | ❌ Rewrites logic from Kotlin to TS. | ✅ **100% Logic Reuse**<br>Your `GameEngine.kt` stays! You just rewrite UI and Network. | ❌ Complete rewrite. |
| **Look & Feel** | Custom rendering (Skia). "Pixel perfect" everywhere. | Native widgets. Looks like a standard iOS/Android app. | Compose Multiplatform (like Android Jetpack). Consistent look. | Game engine look. Non-standard UI components. |

## Recommendation

### 1. The "Vibe Coded" Choice: **Flutter**
If your goal is to let AI write the code and iterate fast, **Flutter** is currently the king of cross-platform "app-style" games.
*   **Why:** AI understands Flutter's widget tree perfectly. The `nearby_connections` plugin architecture is easier to drop in than KMP's `expect/actual` manual wiring.
*   **Trade-off:** You have to port your Kotlin logic to Dart (AI can do this easily).

### 2. The "Engineer's" Choice: **Kotlin Multiplatform (KMP)**
If you love your current `GameEngine` and Architecture.
*   **Why:** You keep your core game logic. You only rewrite the UI (in Compose Multiplatform, which is similar to what you have) and the Network layer.
*   **Trade-off:** "Vibe coding" the iOS specific networking layer (Bluetooth/Multipeer) will be harder because there isn't a single "magic library" yet. You'll likely need to paste in some Swift code.

### 3. The "Web Dev" Choice: **React Native**
If you prefer TypeScript.
*   **Why:** Huge ecosystem.
*   **Trade-off:** Peer-to-Peer cross-play is notoriously fragmented in RN. You might spend more time fighting libraries than coding the game.

## Key Risk: "Nearby" Cross-Play
Regardless of the tool, connecting Android (Google Nearby) to iOS (Multipeer) is **not natively compatible**.
*   **True Cross-Play** requires using a common protocol:
    *   **Bluetooth LE (BLE):** Doable in all 3, but lower bandwidth.
    *   **WiFi P2P:** Complex to handshake.
    *   **Framework Bundles:** Some Flutter/Unity packages bundle a C++ library to force "Google Nearby" onto iOS, enabling true cross-play. This is the "Magic Bullet" you are looking for.
