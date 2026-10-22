# MoneroOne for Android

A native, open-source, non-custodial Monero wallet for Android.

[Website](https://monero.one) · [Google Play](https://play.google.com/store/apps/details?id=one.monero.moneroone)

## Features

- Create wallets with a 24-word BIP39 seed; restore 24-word BIP39 or 25-word legacy Monero seeds
- Send and receive Monero
- Sync directly with Monero nodes over daemon RPC; choose a built-in node or add your own
- PIN and biometric unlock
- Price charts and portfolio tracking
- Home screen widgets

Wallet scanning and transaction signing run on the device through [monero-kit-android](https://github.com/bwgh0/monero-kit-android), which wraps Monero's wallet2 library. Seeds are stored locally in encrypted preferences, with the encryption key protected by Android Keystore.

## Building

```bash
./gradlew assembleDebug
```

## License

[MIT](LICENSE)
