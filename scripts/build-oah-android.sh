#!/bin/bash
set -euo pipefail
app_dir="$(cd "$(dirname "$0")/.." && pwd)"
oah_repo="${OAH_REPO:-$app_dir/../../oah}"
ndk_dir="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/27.0.12077973}"
case "$(uname -s)" in
  Darwin) host_tag=darwin-x86_64 ;;
  Linux) host_tag=linux-x86_64 ;;
  *) echo "build-oah-android.sh: unsupported host $(uname -s)" >&2; exit 1 ;;
esac
toolchain="$ndk_dir/toolchains/llvm/prebuilt/$host_tag/bin"
for entry in 'aarch64-linux-android arm64-v8a' 'x86_64-linux-android x86_64'; do
  read -r rust_target abi <<< "$entry"
  linker="$toolchain/${rust_target}27-clang"
  env_name="CARGO_TARGET_$(echo "$rust_target" | tr '[:lower:]-' '[:upper:]_')_LINKER"
  env "$env_name=$linker" RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384" cargo build --manifest-path "$oah_repo/Cargo.toml" -p oah-mobile --release --target "$rust_target" --locked
  output="$app_dir/app/src/main/jniLibs/$abi"
  mkdir -p "$output"
  cp "$oah_repo/target/$rust_target/release/liboah_mobile.so" "$output/"
  "$linker" -shared -fPIC -O2 "$app_dir/app/src/main/cpp/oah_jni.c" -L"$output" -loah_mobile -Wl,-z,max-page-size=16384 -o "$output/liboah_jni.so"
done
