# Godot 4.6 STOMP NUL Parser Issue and Fix

## Problem Summary

When opening the Godot project, the editor reported repeated startup errors:

- `Unicode parsing error, some characters were replaced with � (U+FFFD): Unexpected NUL character`

These errors appeared immediately on editor launch (before running the harness scene), which indicated the failure was in scripts parsed at startup rather than runtime matchmaking logic.

## Environment

- Godot: `4.6.2.stable`
- Project: `forge-test-harness`
- Affected file: `addons/forge_sdk/internal/forge_stomp_client.gd`

## Root Cause

The SDK STOMP client previously represented the STOMP frame terminator as a string-level NUL pattern (`"\u0000"` first, then `char(0)`).

On this Godot 4.6 setup, that caused parser/language-server startup errors around NUL handling even when source files contained no literal NUL bytes on disk.

Changing the terminator constant to a visible placeholder (`"~"`) removed parser errors, confirming the trigger was null-character handling in GDScript source/string parsing.

## Why Placeholder `~` Is Not a Real Fix

STOMP frames must terminate with byte `0` (NUL). Replacing terminators with `~` avoids parser errors but breaks protocol correctness for realtime messaging.

## Correct Fix Implemented

`forge_stomp_client.gd` was rewritten to use byte-level framing instead of string-level NUL characters:

1. Read buffer changed from `String` to `PackedByteArray`.
2. Incoming packets are appended as bytes (`append_array(pkt)`).
3. Frame boundaries are detected with `find(0)` (byte value).
4. Frame bytes are converted to UTF-8 text only after splitting.
5. Outgoing STOMP frames are serialized to UTF-8 and terminated with `bytes.append(0)`.

This preserves protocol correctness while avoiding parser-sensitive NUL constructs in source.

## Key Behavioral Outcome

- Startup parser spam is avoided.
- STOMP framing remains spec-correct.
- Realtime connect/subscribe/message flow can proceed without relying on string literals containing null characters.

## Notes

- This is a compatibility hardening change for Godot 4.6 behavior.
- The SDK design doc was authored around Godot 4.3; this fix reduces version-friction for newer editor/parser behavior.
