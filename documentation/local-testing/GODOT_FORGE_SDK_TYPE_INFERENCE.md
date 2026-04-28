# Godot 4: “Cannot infer the type of … variable” in Forge SDK (`forge_auth.gd`)

This note documents a class of **static analysis / parse errors** you can see after dropping the Forge Godot addon into a project, and how to fix them with **minimal, local changes**.

## Symptoms

Godot’s script editor or output may report errors like:

- `Parse Error: Cannot infer the type of "result" variable because the value doesn't have a set type.`
- Same message for a variable named `ticket` (or similar).

In the Forge SDK, these often appear in `res://addons/forge_sdk/services/forge_auth.gd` on lines similar to:

- `var result := await _http.post_json(...)`
- `var ticket := _jwt_store.last_steam_ticket()`
- Another `var result := await _http.post_json(...)` in `_silent_reauth`

## Root cause

In **Godot 4.x**, the `:=` operator asks the compiler to **infer** a variable’s type from the right-hand side.

Inference **fails** when the expression’s type is **not known** to the analyzer. Common cases:

1. **Untyped member variables**  
   If `_http` is declared as `var _http` (no type), then calls like `_http.post_json(...)` have **no static return type**. The compiler cannot infer `result` from `await _http.post_json(...)`.

2. **Untyped collaborators**  
   If `_jwt_store` is declared as `var _jwt_store`, then `_jwt_store.last_steam_ticket()` may also be treated as **untyped** for inference purposes, so `var ticket := ...` fails.

This is **not** a logic bug in `login_steam` / `me` / `_silent_reauth`; it is the **type system** refusing to guess a type when dependencies are declared without types.

## Fix (minimal, recommended for a harness project)

Replace `:=` with **explicit types** on the three failing bindings.

### `login_steam`

**Before:**

```gdscript
var result := await _http.post_json(_STEAM_PATH, {"steam_ticket": steam_ticket})
```

**After:**

```gdscript
var result: ForgeResult = await _http.post_json(_STEAM_PATH, {"steam_ticket": steam_ticket})
```

### `_silent_reauth` — `ticket`

**Before:**

```gdscript
var ticket := _jwt_store.last_steam_ticket()
```

**After:**

```gdscript
var ticket: String = _jwt_store.last_steam_ticket()
```

### `_silent_reauth` — `result`

**Before:**

```gdscript
var result := await _http.post_json(_STEAM_PATH, {"steam_ticket": ticket})
```

**After:**

```gdscript
var result: ForgeResult = await _http.post_json(_STEAM_PATH, {"steam_ticket": ticket})
```

Save the file, then **reload the project** (or restart the editor) if errors linger in the cache.

## Optional upstream fix (SDK maintainers)

Typing the dependencies removes the need for these local annotations. For example:

- Give `configure(http, jwt_store, logger)` parameters concrete types (`ForgeHttpClient`, `ForgeJwtStore`, `ForgeLogger`, etc.).
- Declare `_http`, `_jwt_store`, and `_logger` with those types instead of bare `var`.

Then `post_json` / `last_steam_ticket` return types can be known and `:=` may work again without per-line annotations.

## Do not confuse with: NUL / Unicode errors

If the editor also reports:

- `Unicode parsing error … Unexpected NUL character`

that indicates **file corruption** (binary NUL bytes inside `.gd` text). Fix that by **re-copying** the addon from a clean source; explicit types will **not** fix corrupted files.

**Order of operations:**

1. Eliminate NUL / encoding corruption (clean copy of `addons/forge_sdk`).
2. Apply explicit types (or upgrade SDK) for inference errors.

## Related: avoid shadowing built-ins

When writing harness scripts, avoid naming a helper `log` — in Godot 4, `log` is the **natural logarithm** built-in and expects a `float`. Use names like `append_log` or `ui_log` instead.

---

*Document version: 1.0 — written for Forge harness / Godot 4 integration.*
