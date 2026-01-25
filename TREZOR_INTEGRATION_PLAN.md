# Trezor Safe 7 Integration - MoneroOne (Android + iOS)

## Context

Add Trezor Safe 7 hardware wallet support to MoneroOne on both platforms. Safe 7 is the only Trezor with Bluetooth (BLE), making it the only model that works on both Android and iOS. The approach: embed a lightweight HTTP server inside each app that emulates `trezord` (Trezor Bridge) on `localhost:21325`. wallet2's `libdevice_trezor` (already linked in both apps) connects there by default - no C++ changes needed.

**Estimated effort:** 10-14 weeks for both platforms in parallel.

**Prerequisite:** Buy a Trezor Safe 7 for testing. Verify Monero works on it (it runs the same firmware as Safe 3/5 which support Monero, but it's not explicitly listed on their marketing page yet).

---

## Architecture

```
MoneroOne App (Android / iOS)
│
├── Trezor UI (new)
│   ├── BLE scan/pair screen
│   ├── "Confirm on device" overlay
│   └── Device status indicator
│
├── MoneroKit / wallet2 C++ (existing, unchanged)
│   └── libdevice_trezor → HTTP to 127.0.0.1:21325
│                              │
├── Embedded Trezor Bridge (new) ◄──┘
│   ├── HTTP server on :21325
│   ├── /enumerate, /acquire, /release, /call
│   └── Hex ↔ BLE chunk translation
│                              │
└── BLE Transport (new) ◄─────┘
    ├── Android: BluetoothGatt API
    ├── iOS: CoreBluetooth
    └── THP wire protocol (244-byte chunks)
              │
              ▼ Bluetooth Low Energy
       ┌──────────────┐
       │ Trezor Safe 7 │
       └──────────────┘
```

---

## Trezor BLE Protocol Details

| Item | Value |
|------|-------|
| Service UUID | `8c000001-a59b-4d58-a9ad-073df69fa1b1` |
| RX Characteristic (write) | `8c000002-a59b-4d58-a9ad-073df69fa1b1` |
| TX Characteristic (notify) | `8c000003-a59b-4d58-a9ad-073df69fa1b1` |
| Chunk size | 244 bytes |
| Pairing | Secure Simple Pairing with Numeric Comparison |

### trezord HTTP API (what libdevice_trezor calls)

| Endpoint | Purpose |
|----------|---------|
| `POST /` | Version check → `{"version":"3.0.0"}` |
| `POST /enumerate` | List devices → `[{path, session, ...}]` |
| `POST /acquire/{path}/{prev}` | Claim device → `{"session":"uuid"}` |
| `POST /release/{session}` | Release device |
| `POST /call/{session}` | Send/receive hex-encoded protobuf |

`/call` body format: `[4 hex = msg_type][8 hex = length][rest = protobuf payload]`

---

## What Already Exists

### Android (monero-kit-android)
- `libdevice_trezor.a` linked (`CMakeLists.txt:143-145, 216`)
- `Wallet.Device.Trezor` enum (`Wallet.java:127`)
- `createWalletFromDeviceJ` JNI in C++ (`monerujo.cpp:378-406`) — **no Java wrapper yet**
- `getDeviceType()` / `queryWalletDevice()` in both layers
- Ledger transport as reference pattern (`ledger/`, `btchip/`)
- Sidekick Bluetooth service as BLE reference (`BluetoothService.java`)

### iOS (MoneroKit.Swift)
- `WalletDevice_Trezor = 2` (`wallet2_api_c.h:460-467`)
- `MONERO_WalletManager_createWalletFromDevice()` (`wallet2_api_c.h:931`)
- `MONERO_Wallet_getDeviceType()`, `coldKeyImageSync()`, `reconnectDevice()`
- `MONERO_Wallet_setDevicePin()`, `setDevicePassphrase()` (`wallet2_api_c.h:510-512`)
- Device enum in `device.hpp` with `TREZOR=2`

---

## Implementation Phases

### Phase 1: BLE Transport (2-3 weeks)

**Android — new files in `monerokit/.../trezor/`:**
- `TrezorBleTransport.java` — BluetoothGatt connection, chunk write/read via BlockingQueue
- `TrezorBleScanner.java` — BluetoothLeScanner with service UUID filter
- `TrezorDevice.java` — model (name, MAC, BLE device ref)
- `TrezorWireProtocol.java` — 244-byte chunk framing, CRC-32

**iOS — new files in `MoneroOne/Core/Trezor/`:**
- `TrezorBleTransport.swift` — CBCentralManager/CBPeripheral, async chunk I/O
- `TrezorBleScanner.swift` — scan with service UUID, `@Published` device list
- `TrezorDevice.swift` — model (name, UUID, peripheral)
- `TrezorWireProtocol.swift` — 244-byte chunk framing, CRC-32

**Manifest/plist changes:**
- Android: Add `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` permissions
- iOS: Add `NSBluetoothAlwaysUsageDescription`, `bluetooth-central` background mode

### Phase 2: Embedded Bridge Server (2-3 weeks)

**Android:**
- Add `org.nanohttpd:nanohttpd:2.3.1` dependency
- `TrezorBridgeServer.java` — NanoHTTPD on `:21325`, implements 5 endpoints
- `TrezorBridgeManager.java` — lifecycle (start/stop server + BLE)

**iOS:**
- `TrezorBridgeServer.swift` — `NWListener` (Network.framework, zero deps) on `:21325`
- `TrezorBridgeManager.swift` — `@MainActor ObservableObject`, lifecycle

**The critical `/call` flow:**
1. Read hex body from HTTP request
2. Decode to bytes: msg_type + length + protobuf
3. Frame into 244-byte BLE chunks
4. Write to Trezor RX characteristic
5. Read response chunks from TX notifications
6. Reassemble, encode as hex, return as HTTP response

### Phase 3: Wallet Creation from Device (1-2 weeks)

**Android:**
- Add `createWalletFromDevice()` Java wrapper in `WalletManager.java` (JNI exists)
- Add `Seed.Trezor` variant to `MoneroKit.kt`'s sealed `Seed` class
- Handle in `createWalletIfNotExists()` → calls `createWalletFromDevice("Trezor", ...)`
- Add Trezor init path in app's `WalletManager.kt` and `WalletViewModel.kt`

**iOS:**
- Add `.trezor` case to `MoneroWallet` enum
- Wire `MONERO_WalletManager_createWalletFromDevice()` in `MoneroCore.swift`
- Add `unlockWithTrezor()` in app's `WalletManager.swift`

**Key:** `deviceName` parameter must be `"Trezor"` — this triggers `libdevice_trezor` in wallet2.

### Phase 4: Transaction Signing (2-3 weeks)

Signing is handled entirely by wallet2/libdevice_trezor via the bridge. The existing `send()` flow works unchanged. Main work:

- **Cold key image sync:** Add `coldKeyImageSync()` native wrapper on Android (iOS C API already exists)
- **Device callbacks:** Forward `onDeviceButtonRequest`, `onDeviceButtonPressed`, `onDevicePassphraseRequest` from wallet2 listener to the UI layer (both platforms)
- **Reconnect:** Wire up `reconnectDevice()` for BLE drops during signing
- **Timeouts:** Set generous timeouts (60s+) on bridge HTTP and BLE — signing involves many round-trips

### Phase 5: UI Integration (2-3 weeks)

**Android (Jetpack Compose) — new screens:**
- `TrezorScanScreen.kt` — BLE discovery list, tap to connect, pairing dialog
- `TrezorConfirmOverlay.kt` — "Confirm on Trezor" full-screen overlay during signing
- Modify `WelcomeScreen.kt` — add "Connect Trezor" button
- Modify `SettingsScreen.kt` — add "Hardware Wallet" section
- Modify `SendScreen.kt` — show Trezor overlay during signing
- Add routes in `NavGraph.kt`

**iOS (SwiftUI) — new views:**
- `TrezorScanView.swift` — BLE discovery, tap to connect
- `TrezorConfirmOverlay.swift` — signing confirmation overlay
- `TrezorViewModel.swift` — `@MainActor ObservableObject`
- Modify `WelcomeView.swift` — add "Connect Trezor" button
- Modify `SettingsView.swift` — add hardware wallet section
- Modify `SendConfirmationView.swift` — Trezor overlay

---

## New Dependencies

| Platform | Library | Purpose |
|----------|---------|---------|
| Android | `org.nanohttpd:nanohttpd:2.3.1` | Embedded HTTP server |
| iOS | None (Network.framework) | Embedded HTTP server |

---

## Risks

| Risk | Mitigation |
|------|-----------|
| Monero not confirmed on Safe 7 | Verify with actual device before building. Same firmware as Safe 3/5. |
| THP protocol complexity | Start with basic channel allocation; BLE pairing provides link-layer encryption |
| BLE latency during signing | Generous timeouts (60s+), clear "confirm on device" UI |
| iOS kills background BLE | `bluetooth-central` background mode, keep app foreground during signing |

---

## Minimum Viable Test

1. Scan → find Safe 7 via BLE
2. Connect and pair
3. Start bridge on `:21325`
4. Call `createWalletFromDevice(path, pw, nettype, "Trezor", 0, "5:20")`
5. libdevice_trezor calls `/enumerate` → bridge returns device
6. libdevice_trezor calls `/acquire` → bridge returns session
7. libdevice_trezor calls `/call` with Initialize → bridge proxies via BLE → Trezor responds with Features
8. Wallet created with address from Trezor
9. Verify address matches Trezor Suite

---

## Key Files Reference

**Android (modify):**
- `monerokit/.../model/WalletManager.java` — add `createWalletFromDevice` wrapper
- `monerokit/.../MoneroKit.kt` — add `Seed.Trezor`, handle in wallet creation
- `monerokit/build.gradle` — add NanoHTTPD dependency
- `app/.../WalletManager.kt` — Trezor init path
- `app/.../WalletViewModel.kt` — Trezor wallet creation method
- `app/AndroidManifest.xml` — BLE permissions

**Android (create):**
- `monerokit/.../trezor/TrezorBleTransport.java`
- `monerokit/.../trezor/TrezorBleScanner.java`
- `monerokit/.../trezor/TrezorDevice.java`
- `monerokit/.../trezor/TrezorWireProtocol.java`
- `monerokit/.../trezor/TrezorBridgeServer.java`
- `monerokit/.../trezor/TrezorBridgeManager.java`
- `app/.../ui/screens/trezor/TrezorScanScreen.kt`
- `app/.../ui/screens/trezor/TrezorConfirmOverlay.kt`

**iOS (modify):**
- `MoneroKit.Swift/.../MoneroCore.swift` — add `.trezor` case
- `MoneroKit.Swift/.../MoneroWallet.swift` — add trezor variant
- `MoneroOne/.../WalletManager.swift` — Trezor init path
- `MoneroOne/Info.plist` — BLE usage descriptions

**iOS (create):**
- `MoneroOne/.../Core/Trezor/TrezorBleTransport.swift`
- `MoneroOne/.../Core/Trezor/TrezorBleScanner.swift`
- `MoneroOne/.../Core/Trezor/TrezorDevice.swift`
- `MoneroOne/.../Core/Trezor/TrezorWireProtocol.swift`
- `MoneroOne/.../Core/Trezor/TrezorBridgeServer.swift`
- `MoneroOne/.../Core/Trezor/TrezorBridgeManager.swift`
- `MoneroOne/.../Features/Trezor/TrezorScanView.swift`
- `MoneroOne/.../Features/Trezor/TrezorConfirmOverlay.swift`
- `MoneroOne/.../Features/Trezor/TrezorViewModel.swift`

---

## Reference Resources

- [trezord-go source](https://github.com/trezor/trezord-go) — bridge protocol reference
- [trezor-android library](https://github.com/trezor/trezor-android) — Android USB communication
- [trezor-firmware monero app](https://github.com/trezor/trezor-firmware/tree/main/core/src/apps/monero) — firmware Monero implementation
- [monero-agent](https://github.com/ph4r05/monero-agent) — Python reference for Trezor-Monero protocol
- [messages-monero.proto](https://github.com/trezor/trezor-firmware/blob/master/common/protob/messages-monero.proto) — Monero protobuf definitions
- [trezor-suite mobile](https://github.com/trezor/trezor-suite/tree/develop/suite-native/app) — React Native BLE reference
