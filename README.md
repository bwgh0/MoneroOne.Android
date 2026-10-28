# MoneroOne for Android

A simple, privacy-focused Monero wallet for Android.

## Features

- Create and restore wallets (BIP39 24-word seed)
- Send and receive Monero
- Light wallet sync via LWS server
- Biometric authentication
- Price charts and portfolio tracking
- Home screen widget

## Building

```bash
./gradlew assembleDebug
```

## License

MIT


## Username development branch (2026-10-21)

This branch continues the local username claim milestone on top of `feature/motion-moments` (`ba215b0`), with the existing signing kit pinned to `e79d582`. It uses the same `oah-mobile` Rust boundary as iOS; record construction, rules and signature verification are not reimplemented in Kotlin.

```sh
rustup target add aarch64-linux-android x86_64-linux-android
OAH_REPO=/path/to/oah bash scripts/build-oah-android.sh
./gradlew :app:assembleDebug
```

The script defaults to NDK 27.0.12077973; `ANDROID_NDK_HOME` may override it. Generated JNI/shared libraries are ignored. Both ABIs use 16 KB page alignment. The local OAH repository needs `crates/oah-mobile` on `feature/mobile-claim`.

Release builds carry the production registry values (`registry.monero.one`, `mirror.monero.one`) and reach only those hosts. Debug builds carry the staging values (`staging-registry.monero.one`, `staging-mirror.monero.one`) and never reach production. For a registry on this machine, build Debug with the Gradle properties `usernameRegistryUrl=http://localhost:8787`, `usernameMirrorUrl`, `usernameGenesisRoot`, `usernameRegistrarKey` and `usernameRulesHash` (lowercase hex); each one overrides its staging value. Initialize the local registry and epoch 0 as its README describes. On an emulator use `adb reverse tcp:8787 tcp:8787`. Release builds reject development claims. CI builds the oah libraries with `.github/actions/build-oah` at a pinned oah commit; it needs the secret `OAH_DEPLOY_KEY` (a read-only deploy key of the oah repository). No network preflight runs on Home or Welcome.

The approved Home is 27R: same surface as the balance card, orange outer top edge, flat separator, only `@username`. The glass switcher and the balance card's inline History stay. The full Inset + Core card shows `@Joe` and `joe@monero.one`; both themes use fixed type for all 3–15-character usernames. Its light follows the game rotation vector, pauses offscreen/background and respects Remove animations. Copy exposes both formats and the link; Share exports the rendered card and identifiers.

Development and production use separate storage entries. The encrypted local binding is scoped by network and the wallet's primary address, which survives cache replacement on rescan. Only the designated subaddress goes to the registry. Address creation and signing use the wallet mutation lock and captured session. Persisting the exact signed request before POST allows recovery after a lost reply; the shared verifier checks the genesis against that request and the pinned registrar/rules before accepting it. Other wallets show neither the claimed username nor another free-claim CTA while the local slot is used. The registry is authoritative for eligibility.

`UsernameStoreTest` covers persistence, wallet/network scoping, replacement refusal and storage failure. `OahNativeTest` is a headless instrumentation test for JNI loading and real wallet2 signature verification, including substitution rejection; it is not UI automation.

This remains a local development milestone. Public registration needs the existing real-device/production deployment gates, production pins/epoch 0 and finalized terms. First-contact Send verification, new-phone ownership restoration, rotation and notices are separate phases. Development handles have no public DNS records.


For an offline visual check, the Debug-only `UsernamePreviewActivity` renders the real card and balance-strip components with synthetic data. Launch `one.monero.moneroone.debug/one.monero.moneroone.UsernamePreviewActivity` with `--es handle xmr_garden_club --ez dark true`; use `am start -S -W` to apply a new configuration. It has no wallet engine or registration action and is absent from Release. The Inter card font is fixed at its own size for every username, including 15 wide capitals.


### Username payments (2026-10-21)

Send classifies `@Joe` and `joe@monero.one` into the shared signature
verifier. A username is one label: `@getmonero.org` is invalid, and a dotted
name under monero.one stays reserved. The operator's `donate@monero.one` continues through
the existing DNSSEC/OpenAlias path. Home still shows only the short username.
The client fetches the chain from the operator and the signed snapshot from a
separate mirror, checks the pinned namespace genesis/rules/key, and persists
root identity pins and signed receipts before exposing a payment address.
Receipt write failures stop resolution.

The UI distinguishes “Signature checked,” “Not yet in the public log,” and stale
records. A first payment asks the user to confirm the username. The provisional
stale-record cap is 0.1 XMR; Send rechecks the username before broadcasting and
stops if the destination or trust status changed during review. The cap awaits
founder preference. API and mirror requests respect Tor with no direct fallback.
No public resolution configuration is enabled yet.

Local testing additionally requires a separate mirror serving the registry's
public objects plus a pinned genesis epoch root. iOS reads `USERNAME_MIRROR_URL`
and `USERNAME_GENESIS_ROOT`; Android reads the Gradle properties
`usernameMirrorUrl` and `usernameGenesisRoot`. Both are development-only and
restricted to localhost, alongside the existing registrar/rules pins. Run a
separate local HTTP server for `var/objects`; do not use the API as its own mirror.
