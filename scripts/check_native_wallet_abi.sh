#!/usr/bin/env bash
# Check the kit's prebuilt wallet2 libraries before a build can ship them.
#
# Why this exists: on iOS, a wallet2 library rebuild dropped fork statics
# (Monero::Wallet::generateAddress/generateKey/bytesToWords). The undefined
# symbols were "fixed" with stubs that return "". Every BIP39 wallet then came
# out keyless, and Receive showed a burn address. The Android kit's JNI calls
# the same fork statics. A dropped or stubbed symbol must fail CI.
#
# For each ABI (arm64-v8a, armeabi-v7a, x86_64), in
# monero-kit-android/monerokit/external-libs/<abi>/monero/:
#   1. The JNI (monerujo.cpp) still calls generateKey and generateAddress, and
#      every Monero::Wallet:: and Monero::WalletManagerFactory:: static it
#      calls is defined in libwallet_api.a (nm type T or W).
#   2. Each function in CHECKS below is defined, and its machine code calls
#      the real derivation (the callees listed for it). A stub that returns ""
#      calls none of them. An nm check alone passes a stub.
#
# Usage:
#   scripts/check_native_wallet_abi.sh              check the kit's libraries
#   scripts/check_native_wallet_abi.sh --libs DIR   check DIR/<abi>/monero/*.a
#   scripts/check_native_wallet_abi.sh --self-test  make copies of the libraries
#       with generateKey/generateAddress stubbed (and, separately, dropped),
#       and show that the check rejects both
#
# NDK (llvm-nm, llvm-objdump; the self-test also uses llvm-ar, llvm-objcopy,
# clang++): $ANDROID_NDK_HOME, else $ANDROID_HOME/ndk/<ndkVersion from the
# kit's build.gradle>, else the same under $ANDROID_SDK_ROOT.
#
# If a legitimate library upgrade fails this check (for example, the
# derivation moved into a helper function), a person must read the
# disassembly and update CHECKS. Never make the check pass with a stub.

set -euo pipefail
set -f # no globbing: the CHECKS patterns contain [ ] and *

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIT="$ROOT/monero-kit-android/monerokit"
JNI_SRC="$KIT/src/main/cpp/monerujo.cpp"
ABIS="arm64-v8a armeabi-v7a x86_64"
REQUIRED_JNI_STATICS="Monero::Wallet::generateKey Monero::Wallet::generateAddress"

# One line per function: archive | name for messages | symbol (ERE on the
# mangled name) | callees its body must reference (anchored EREs, space-separated).
# [my] is uint64_t: unsigned long on 64-bit ABIs, unsigned long long on
# armeabi-v7a. [mj] is size_t.
CHECKS='
libwallet_api.a|Monero::Wallet::generateKey|^_ZN6Monero6Wallet11generateKeyE|^_ZN6crypto13ElectrumWords14words_to_bytesE ^_ZN6crypto10crypto_ops13generate_keysE ^keccak$
libwallet_api.a|Monero::Wallet::generateAddress|^_ZN6Monero6Wallet15generateAddressE|^_ZN6crypto13ElectrumWords14words_to_bytesE ^_ZN6crypto10crypto_ops13generate_keysE ^keccak$ ^_ZN6crypto14hash_to_scalarE ^_ZN10cryptonote26get_account_address_as_strE
libwallet_api.a|Monero::Wallet::keyValid|^_ZN6Monero6Wallet8keyValidE|^_ZN6crypto10crypto_ops24secret_key_to_public_keyE ^_ZN10cryptonote28get_account_address_from_strE
libwallet_api.a|Monero::Wallet::addressValid|^_ZN6Monero6Wallet12addressValidE|^_ZN10cryptonote28get_account_address_from_strE
libwallet_api.a|Monero::WalletManagerImpl::recoveryWallet (with seed offset)|^_ZN6Monero17WalletManagerImpl14recoveryWalletE.*S9_S9_NS_11NetworkTypeE[my][my]S9_$|^_ZN6Monero10WalletImpl7recoverE
libwallet_api.a|Monero::WalletImpl::recover (with seed offset)|^_ZN6Monero10WalletImpl7recoverE.*S9_S9_S9_$|^_ZN6crypto13ElectrumWords14words_to_bytesE ^_ZN5tools7wallet28generateE
libmnemonics.a|crypto::ElectrumWords::words_to_bytes (to a secret key)|^_ZN6crypto13ElectrumWords14words_to_bytesE.*9ec_scalar|^_ZN6crypto13ElectrumWords14words_to_bytesERKN4epee15wipeable_stringERS2_[mj]bR
libmnemonics.a|crypto::ElectrumWords::words_to_bytes (to bytes)|^_ZN6crypto13ElectrumWords14words_to_bytesERKN4epee15wipeable_stringERS2_[mj]bR|^_ZN8Language9SingletonINS_7EnglishEE8instanceEv
'

die() { echo "error: $*" >&2; exit 2; }

find_ndk_bin() {
    local version candidate host
    version=$(sed -nE "s/^[[:space:]]*ndkVersion[[:space:]]*['\"]([^'\"]+)['\"].*/\1/p" "$KIT/build.gradle" | head -1)
    for candidate in \
        "${ANDROID_NDK_HOME:-}" \
        "${ANDROID_HOME:+$ANDROID_HOME/ndk/$version}" \
        "${ANDROID_SDK_ROOT:+$ANDROID_SDK_ROOT/ndk/$version}" \
        "$HOME/Library/Android/sdk/ndk/$version" \
        "$HOME/Android/Sdk/ndk/$version"; do
        [[ -n "$candidate" && -d "$candidate/toolchains/llvm/prebuilt" ]] || continue
        for host in darwin-x86_64 darwin-arm64 linux-x86_64 linux-aarch64; do
            if [[ -x "$candidate/toolchains/llvm/prebuilt/$host/bin/llvm-nm" &&
                  -x "$candidate/toolchains/llvm/prebuilt/$host/bin/llvm-objdump" ]]; then
                echo "$candidate/toolchains/llvm/prebuilt/$host/bin"
                return 0
            fi
        done
    done
    echo "error: no NDK found: set ANDROID_NDK_HOME, or install NDK ${version:-?} under \$ANDROID_HOME/ndk" >&2
    return 1
}

# "member<TAB>symbol" for every function (T) or weak (W) symbol defined in the archive.
defined_symbols() {
    "$NM" --defined-only "$1" 2>/dev/null | awk '
        /^[^ \t].*:$/ { member = $0; sub(/:$/, "", member); next }
        NF >= 3 && $(NF - 1) ~ /^[TW]$/ { print member "\t" $NF }
    ' || true
}

# "member<TAB>target" for every call target in the body of one symbol: relocation
# targets (calls into other objects) and symbolized branch targets (calls that
# the assembler resolved inside the same object, which carry no relocation).
call_targets() {
    local archive="$1" symbol="$2"
    "$OBJDUMP" -d -r --no-show-raw-insn --disassemble-symbols="$symbol" "$archive" 2>/dev/null | awk -v self="$symbol" '
        /file format / {
            if (match($0, /\([^()]*\):[ \t]+file format /)) {
                member = substr($0, RSTART + 1)
                sub(/\):[ \t]+file format .*/, "", member)
            }
            next
        }
        /[ \t]R_[A-Z0-9]+_[A-Z0-9_]+[ \t]/ {
            target = $NF
            sub(/[-+]0x[0-9a-f]+$/, "", target)
            if (target != self) print member "\t" target
            next
        }
        /\t(bl|blx|b|call|callq|jmp|jmpq)\t/ {
            if (match($0, /<[^<>+]+>$/)) {
                target = substr($0, RSTART + 1, RLENGTH - 2)
                if (target != self) print member "\t" target
            }
        }
    ' || true
}

# Prints one line per problem in one ABI's libraries; prints nothing when all is well.
check_abi() {
    local abi="$1" dir="$2/$1/monero"
    local archive name symbol_re callees defs names defs_api defs_mnemonics symbols symbol members count
    local targets member member_targets callee missing statics static cls fn prefix

    for archive in libwallet_api.a libmnemonics.a; do
        [[ -f "$dir/$archive" ]] || echo "MISSING ARCHIVE $abi: $dir/$archive"
    done
    [[ -f "$dir/libwallet_api.a" && -f "$dir/libmnemonics.a" ]] || return 0
    defs_api="$(defined_symbols "$dir/libwallet_api.a")"
    defs_mnemonics="$(defined_symbols "$dir/libmnemonics.a")"

    # 1. The JNI still calls the derivation statics, and every static it calls is defined.
    statics="$(grep -ohE 'Monero::(Wallet|WalletManagerFactory)::[A-Za-z_][A-Za-z0-9_]*\(' "$JNI_SRC" | sed 's/($//' | sort -u)"
    for static in $REQUIRED_JNI_STATICS; do
        grep -qx "$static" <<< "$statics" || echo "JNI $abi: $JNI_SRC no longer calls $static"
    done
    names="$(cut -f2 <<< "$defs_api")"
    for static in $statics; do
        cls="${static#Monero::}"
        cls="${cls%%::*}"
        fn="${static##*::}"
        prefix="_ZN6Monero${#cls}${cls}${#fn}${fn}E"
        grep -q "^$prefix" <<< "$names" ||
            echo "MISSING $abi: $static (called by the JNI) is not defined in libwallet_api.a"
    done

    # 2. Derivation and wallet-creation functions are real code.
    while IFS='|' read -r archive name symbol_re callees; do
        [[ -n "$archive" ]] || continue
        case "$archive" in
            libwallet_api.a) defs="$defs_api" ;;
            libmnemonics.a) defs="$defs_mnemonics" ;;
            *) defs="$(defined_symbols "$dir/$archive")" ;;
        esac
        symbols="$(cut -f2 <<< "$defs" | grep -E "$symbol_re" | sort -u || true)"
        if [[ -z "$symbols" ]]; then
            echo "MISSING $abi: $name is not defined in $archive"
            continue
        fi
        for symbol in $symbols; do
            members="$(awk -F'\t' -v s="$symbol" '$2 == s { print $1 }' <<< "$defs")"
            count="$(grep -c . <<< "$members" || true)"
            if [[ "$count" -gt 1 ]]; then
                echo "DUPLICATE $abi: $name is defined in $count members of $archive:" $members
            fi
            targets="$(call_targets "$dir/$archive" "$symbol")"
            for member in $members; do
                member_targets="$(awk -F'\t' -v m="$member" '$1 == m { print $2 }' <<< "$targets")"
                missing=""
                for callee in $callees; do
                    grep -qE "$callee" <<< "$member_targets" || missing="$missing $callee"
                done
                if [[ -n "$missing" ]]; then
                    echo "STUB $abi: $name in $archive($member) never calls:$missing"
                fi
            done
        done
    done <<< "$CHECKS"
}

run_check() {
    local libs="$1" abi problems all="" failed=0
    echo "NDK tools: $NDK_BIN"
    echo "Libraries: $libs"
    for abi in $ABIS; do
        problems="$(check_abi "$abi" "$libs")"
        if [[ -n "$problems" ]]; then
            echo "$problems"
            all="$all$problems"$'\n'
            failed=1
        else
            echo "ok $abi: the JNI statics are defined; derivation and wallet creation call the real code"
        fi
    done
    if [[ "$failed" -ne 0 ]]; then
        echo "ABI check FAILED: $(grep -c . <<< "$all") problem(s)."
        echo "The wallet2 library no longer implements what wallet creation needs. Do not stub"
        echo "these symbols: restore the implementation or fix the library build."
        return 1
    fi
    echo "ABI check passed: $ABIS"
}

# ---------------------------------------------------------------- self-test

self_test() {
    local ar="$NDK_BIN/llvm-ar" objcopy="$NDK_BIN/llvm-objcopy" cxx="$NDK_BIN/clang++"
    local tool tmp abi triple real work sym member changed stub_defs output status stubs missing fails=0
    for tool in "$ar" "$objcopy" "$cxx"; do
        [[ -x "$tool" ]] || die "the self-test needs $tool"
    done
    tmp="$(mktemp -d "${TMPDIR:-/tmp}/wallet-abi-selftest.XXXXXX")"
    SELFTEST_TMP="$tmp"
    trap 'rm -rf "${SELFTEST_TMP:-}"' EXIT

    # What a "make it link" fix for dropped fork statics looks like.
    cat > "$tmp/stub.cpp" <<'EOF'
#include <string>
namespace Monero {
struct Wallet {
    static std::string generateKey(const std::string &seed, const std::string &seed_offset, bool privateKey, bool spendKey);
    static std::string generateAddress(const std::string &seed, const std::string &seed_offset,
                                       unsigned int account_index, unsigned int address_index, bool testnet);
};
std::string Wallet::generateKey(const std::string &, const std::string &, bool, bool) { return ""; }
std::string Wallet::generateAddress(const std::string &, const std::string &, unsigned int, unsigned int, bool) { return ""; }
}
EOF

    for abi in $ABIS; do
        case "$abi" in
            arm64-v8a) triple=aarch64-linux-android27 ;;
            armeabi-v7a) triple=armv7a-linux-androideabi27 ;;
            x86_64) triple=x86_64-linux-android27 ;;
        esac
        real="$KIT/external-libs/$abi/monero"
        work="$tmp/work/$abi"
        mkdir -p "$work" "$tmp/stubbed/$abi/monero" "$tmp/dropped/$abi/monero"
        cp "$real/libwallet_api.a" "$real/libmnemonics.a" "$tmp/stubbed/$abi/monero/"
        cp "$real/libwallet_api.a" "$real/libmnemonics.a" "$tmp/dropped/$abi/monero/"

        "$cxx" --target="$triple" -std=c++17 -O2 -fPIC -c "$tmp/stub.cpp" -o "$work/stub_wallet_statics.o"
        stub_defs="$("$NM" --defined-only "$work/stub_wallet_statics.o")"

        # Take the real generateKey/generateAddress out: rename them in their object file.
        changed=""
        for sym in $(defined_symbols "$real/libwallet_api.a" | cut -f2 | grep -E '^_ZN6Monero6Wallet(11generateKey|15generateAddress)E'); do
            grep -q " T $sym\$" <<< "$stub_defs" || die "the $abi stub does not define $sym (mangling differs)"
            member="$(defined_symbols "$real/libwallet_api.a" | awk -F'\t' -v s="$sym" '$2 == s { print $1 }')"
            [[ -f "$work/$member" ]] || (cd "$work" && "$ar" x "$real/libwallet_api.a" "$member")
            "$objcopy" --redefine-sym "$sym=selftest_removed$sym" "$work/$member"
            case " $changed " in *" $member "*) ;; *) changed="$changed $member" ;; esac
        done
        [[ -n "$changed" ]] || die "generateKey/generateAddress not found in $real/libwallet_api.a"

        (cd "$work" && "$ar" r "$tmp/stubbed/$abi/monero/libwallet_api.a" $changed stub_wallet_statics.o)
        (cd "$work" && "$ar" r "$tmp/dropped/$abi/monero/libwallet_api.a" $changed)
    done

    echo "== self-test 1: generateKey/generateAddress replaced by stubs that return \"\""
    set +e
    output="$("${BASH:-bash}" "$0" --libs "$tmp/stubbed" 2>&1)"
    status=$?
    set -e
    echo "$output"
    stubs="$(grep -cE '^STUB .*Monero::Wallet::generate(Key|Address) ' <<< "$output" || true)"
    missing="$(grep -cE '^MISSING ' <<< "$output" || true)"
    if [[ "$status" -eq 1 && "$stubs" -eq 6 && "$missing" -eq 0 ]]; then
        echo "self-test 1 ok: the check rejects the stubs (exit 1, 6 STUB findings)"
    else
        echo "self-test 1 FAILED: exit $status, $stubs STUB findings (want 6), $missing MISSING (want 0)"
        fails=1
    fi

    echo "== self-test 2: generateKey/generateAddress dropped from the library"
    set +e
    output="$("${BASH:-bash}" "$0" --libs "$tmp/dropped" 2>&1)"
    status=$?
    set -e
    echo "$output"
    missing="$(grep -cE '^MISSING .*Monero::Wallet::generate(Key|Address) ' <<< "$output" || true)"
    if [[ "$status" -eq 1 && "$missing" -eq 12 ]]; then
        echo "self-test 2 ok: the check rejects the dropped symbols (exit 1, 12 MISSING findings)"
    else
        echo "self-test 2 FAILED: exit $status, $missing MISSING findings (want 12: JNI list and CHECKS, 2 symbols, 3 ABIs)"
        fails=1
    fi

    if [[ "$fails" -ne 0 ]]; then
        echo "SELF-TEST FAILED"
        return 1
    fi
    echo "SELF-TEST PASSED"
}

case "${1:-}" in
    -h | --help)
        sed -n '2,/^$/p' "$0" | sed -E 's/^# ?//'
        exit 0
        ;;
esac

NDK_BIN="$(find_ndk_bin)" || exit 2
NM="$NDK_BIN/llvm-nm"
OBJDUMP="$NDK_BIN/llvm-objdump"

case "${1:-}" in
    "") run_check "$KIT/external-libs" ;;
    --libs)
        [[ -n "${2:-}" && -d "$2" ]] || die "--libs needs a directory"
        run_check "$(cd "$2" && pwd)"
        ;;
    --self-test) self_test ;;
    *) die "unknown argument: $1 (see --help)" ;;
esac
