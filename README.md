# NeoSmartpen R1 Android 14 Integration Guide (NASDK 2.27)

This project contains the definitive solution for stabilizing the Bluetooth connection between a NeoSmartpen R1 and an Android 14+ application using the legacy proprietary `NASDK-release_2.27.aar` SDK. 

For weeks, developers attempting to connect the R1 on modern Android devices faced an instant, unexplained `PEN_CONNECTION_FAILURE (Code 3) / FAILURE (2)`. This document serves as a post-mortem and architectural guide explaining exactly *why* it failed, and *how* we reverse-engineered the SDK to achieve a pure, 100% stable BLE (GATT) connection.

## The 3 Historic Roadblocks & Their Solutions

### 1. The Hidden "Double-MAC" Requirement
**The Problem:** The R1 is a dual-mode pen. Over BLE, it broadcasts a randomized LE MAC address (e.g., `FA:33:45...`). However, the legacy NASDK SDK natively expects the immutable Classic Bluetooth SPP MAC (e.g., `9C:7B:D2...`). Passing just the BLE MAC caused the SDK's internal validation to fail.
**The Solution:** We extracted a hidden utility from the SDK: `kr.neolab.sdk.util.UuidUtil.changeAddressFromLeToSpp()`. This allows us to intercept the raw BLE scan packet, mathematically derive the true SPP MAC, and inject **BOTH** addresses into the SDK simultaneously.

### 2. The Async State Machine Trap (`setLeMode` Failure)
**The Problem:** To force the SDK into Modern BLE mode, developers call `PenCtrl.getInstance().setLeMode(true)`. However, if `ctrl.disconnect()` is called right before it (to "reset" the state), the disconnect executes an *asynchronous* unbind. Because the state machine is temporarily occupied (`penStatus != PEN_INIT`), `setLeMode` silently returns `false` and ignores the command.
**The Disaster:** When `setLeMode` fails, the SDK falls back to `BTAdt` (Classic Bluetooth Mode). It then forcefully attempts to establish a Classic Rfcomm Socket to the BLE MAC, instantly crashing and throwing `FAILURE (2)`.
**The Solution:** We introduced state-awareness in `PenManager.kt`. If the pen is not in `PEN_INIT` (1), we call `disconnect()`, completely clear the state, and `delay(1000)` to wait for the async unbind to complete *before* successfully locking in `setLeMode(true)`.

### 3. The UUID_VER_2 Hardcoding Bug (The Final Boss)
**The Problem:** Even after perfecting the Double-MAC and locking in `BTLEAdt` (BLE Mode), Android 14 successfully negotiated the native GATT connection... but the SDK immediately self-destructed the connection and fired `FAILURE (2)`.
**The Discovery:** By extracting the raw BLE advertisement packet bytes (`11 07 68 FE 1A 49 47 B1 50...`) and reversing the little-endian sequence, we discovered the R1 pen broadcasts `ServiceUuidV5` (`4f99f138-9d53-5bfa-9e50-b147491afe68`). 
**The Bug:** The official `ctrl.connect(sppAddress, leAddress)` method provided by the SDK explicitly hardcodes the service search to `UUID_VER.VER_2`. When the SDK queried the R1 for `VER_2` characteristics, it found nothing, panicked, and instantly disconnected the successful GATT link.
**The Solution:** We utilized Java Reflection to dig deep into the proprietary `.aar` binary. We dynamically extracted the `kr.neolab.sdk.pen.bluetooth.BTLEAdt$UUID_VER.VER_5` enum and invoked the hidden 5-argument initialization method:
`connect(sppAddress, leAddress, VER_5, appType, protocolVer)`
This bypassed the hardcoded `VER_2` trap, matching the SDK's internal service discovery loop perfectly to the physical R1 hardware.

---

## The "Invisible Stroke" Mystery: Why Dots were Dropping
Even after achieving a stable connection, real-time stroke data (`onReceiveDot`) initially failed to trigger. Our investigation revealed two critical internal SDK behaviors:

*   **Android 14 Connectivity:** Resolved via `ReceiverContextWrapper` (flag injection) and `Double-MAC` connection strategy.
*   **Data Stream Stabilization:** Fixed via Reflection-based heartbeat force to keep `isReceivedPageIdChange` active.
*   **PUI (Pen User Interface):** Added manual coordinate-based command detection to trigger "Submit" when tapping specific areas on the paper.
*   **Audio UX:** Integrated `apple_pay.wav` for real-time success feedback.

### 1. The `isReceivedPageIdChange` Blocker (Code 118)
**The Problem:** The NASDK implements a safety gate to prevent orphaned dots (dots without a known page). Within `CommProcessor20.java`, every `PEN_ACTION_DOWN` (starting a stroke) forcefully resets an internal flag `isReceivedPageIdChange` to `false`. 
**The SDK Logic:** The SDK then *expects* a `TYPE_PAGE_ID_CHANGE (0x6B)` packet from the pen to set that flag back to `true` before it will process any coordinate packets (`0x6E`). 
**The Failure:** If the pen is used on the same page, or if the notification packet is delayed/dropped, the SDK enters a state where it receives coordinates but instantly discards them, firing a hidden `ERROR_TYPE_MISSING_PAGE_CHANGE (118)` message.
**The Fix:** We implemented a "Heartbeat Force" mechanism using Java Reflection. Every time a message is received, our `PenManager` checks this internal flag and forcefully sets it to `true` if the SDK is stuck, unblocking the data stream instantly.

### 2. The Note ID Filter (`reqAddUsingNoteAll`)
**The Problem:** The SDK defaults to a "Strict Filter" mode where it ignores pen data unless the specific `Note ID` has been registered in its internal allow-list. 
**The Fix:** To ensure compatibility with any NeoSmartpen notebook, we injected a proactive `reqAddUsingNoteAll()` command during the authorization handshake. This tells the SDK to ignore the allow-list and process data from all notebooks.

### 3. Android 14 `registerReceiver` Compliance
**The Problem:** NASDK 2.27 was built for older Android versions and uses `Context.registerReceiver()` without the `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED` flags required by Android 14. This caused the SDK to crash internally when trying to manage Bluetooth state.
**The Fix:** We implemented a `ReceiverContextWrapper` in `PenManager.kt` that intercepts the SDK's registration calls and dynamically injects the appropriate `RECEIVER_EXPORTED` flags, ensuring 100% stability on modern Android OS.

---

## PUI (Paper User Interface) Detection & "Submit" Triggers

Unlike the React `toeic-app` which dynamically parses `.nproj` XML files, the Android native implementation in `PracticeViewModel.kt` uses high-performance coordinate-based collision detection for PUI symbols. This ensures real-time response on mobile hardware without XML parsing overhead.

### Verified Coordinate Mappings (Ncode Units)

We have calibrated the following notebooks to trigger a "Submit" (OCR initiation) when the pen taps the top-right command area:

| Notebook | Owner ID | Book ID | X Range (NU) | Y Range (NU) | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Book 462** | 27 | 462 | `47.0 .. 53.0` | `0.0 .. 12.0` | Calibrated to user sample (48.7, 7.4) |
| **Book 368** | 27 | 368 | `78.0 .. 89.0` | `0.0 .. 22.0` | Deduced from `.nproj` assets |
| **Book 100** | 50 | 100 | `84.0 .. 93.0` | `101.0.. 112.0` | Expanded to top-left per user req |

### Implementation Logic
The detection occurs in `isPuiSubmitArea(dot: Dot)`. If a `PEN_ACTION_DOWN` (type 17) is detected within these ranges, the app triggers `handleSubmitDrawing()` and discards the dot, preventing it from being rendered as a stroke.

---

## Result
With these deep-level fixes (Reflection State Sync, Note Unblocking, Context Wrapping, and PUI Calibration), the NeoSmartpen R1 now achieves **perfect, real-time stroke visualization and command detection** on Android 14. 🎉
