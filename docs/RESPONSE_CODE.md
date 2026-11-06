# Offline legacy service response codes

For an owner whose console already shows the older numeric Privileged Mode challenge, the legacy Java calculation is:

```java
new java.util.Random(Long.parseLong(challenge)).nextInt(999999)
```

The challenge seeds the generator. No per-user account password or secret key is involved in that calculation. This does not guarantee the same check exists on every iFIT version, and it cannot bring back a service menu that firmware no longer exposes. Use this only on equipment you own or are authorized to service, with the belt stopped and safety key removed.

## Use without a website

### Python

```sh
python3 tools/response_code.py 123456
```

The synthetic example returns `382410`. The script needs Python3 only. It reads one command-line value, prints one response, and has no network/device/file access. It does not type a code into a console, unlock it remotely or control motion.

### Standalone browser file

Download [response-code.html](../tools/response-code.html), save it locally and open it in a modern browser. It runs locally without a server. The file contains its own UI and calculation, no external scripts, analytics, network connections or saved input. Its Content Security Policy disables network connections and form submission. It needs JavaScript BigInt support.

Enter only the challenge from your own current console, then enter the result locally on that console. The result is an integer and can have fewer than six displayed digits, matching the community helper's display. Leading zeros in the *challenge* are parsed as a decimal number. No actual owner's challenge/response is included in this repository.

## Exact arithmetic

This is the specified 48-bit `java.util.Random` linear congruential generator:

1. Initialize state with `(challenge XOR 0x5DEECE66D) AND (2^48-1)`.
2. Advance state with `(state * 0x5DEECE66D + 11) AND (2^48-1)`.
3. Take the upper 31 bits and calculate its remainder modulo 999999.
4. Match Java's bounded-integer rejection rule: when `bits - remainder + 999998` would overflow a signed 32-bit positive integer, advance again rather than returning the remainder.

The upper bound is **999999 exclusive**, not 1000000. Python's built-in `random` uses a different generator and is not a drop-in replacement.

Primary algorithm specification: [Java Random API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html). The legacy community helper's actual JavaScript can be inspected in the public source of [getresponsecode.com](https://getresponsecode.com/), which is linked by the [QZ Companion access guide](https://github.com/cagnulein/QZCompanionNordictrackTreadmill#readme). These local implementations were written for this project; no website HTML, analytics or OEM binary is bundled.

## Important compatibility detail: two modes

The inspected community JavaScript helper calculates with ordinary JavaScript Numbers for the rejection check. They do not overflow as signed 32-bit Java ints do, so that helper effectively returns the first remainder even in Java's rejection cases.

The local tool therefore has two explicit modes:

- `java` (default): exact `java.util.Random(...).nextInt(999999)` behavior.
- `helper`: reproduce the inspected community helper's first-result behavior.

```sh
python3 tools/response_code.py 005189 --mode java
python3 tools/response_code.py 005189 --mode helper
```

This synthetic edge case yields `204721` and `443980` respectively. Over the complete synthetic `000000–999999` challenge domain, the modes differ for 224 inputs (0.0224%). For the remaining inputs they agree. This is an arithmetic comparison, not a statement that one mode works on all console firmware. An assistant should not repeatedly try codes, enumerate a console's inputs or claim successful access without the owner's local result.

## Validation and limits

The Python implementation and the actual standalone HTML script were each compared offline against JDK17's own `Random.nextInt` for every synthetic seed `0..999999`. All Java-mode results matched. Helper mode matches the inspected site's one-step arithmetic. Input validation, leading-zero challenges, standalone form/error handling and no-network/no-storage constraints were checked.

Run `python3 tests/response_code_test.py` with JDK17 and Python3. An optional `node tests/response_code_js_test.mjs` verifies the HTML script without launching a browser. Neither test contacts a treadmill or the community website. No actual device was unlocked to validate this public utility; matching the legacy algorithm does not certify a particular installed firmware.
