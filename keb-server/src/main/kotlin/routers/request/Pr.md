# Introduce awaitReceive and awaitReceiveOrNull APIs

## Summary

`ServerRequest.awaitBody*` APIs have some problems:

1. `ServerRequest.awaitBody` forces you to think about the coroutines-bridge exception handling, since it uses
   `kotlinx.coroutines.reactive.awaitSingle`. Also, it is not documented that it might throw `NoSuchElementException`
   and `IllegalArgumentException` for **coroutines-related** errors.

2. `ServerRequest.awaitBody` might throw `IllegalArgumentException` for two different reasons, for serialization-related
   errors and for coroutines-related errors.

3. `ServerRequest.awaitBodyOrNull` should never throw as the signature indicates. The convention established by the
   stdlib mandate that an operation with the name `xxxOrNull` returns `null` instead of throwing in case there is an
   error.

## Proposal

Introduce APIs that address all the above.

Add `ServerRequest.awaitReceiveNullable`.

- This API "abstracts" away the coroutine-bridging exception handling from the user
- Throws only in case of serialization error
- Returns `null` in case user is expecting the body to be "missing"

Add `ServerRequest.awaitReceive`.

- This API "abstracts" away the coroutine-bridging exception handling from the user
- Throws only in case of serialization error

Both APIs introduce a "mindset" that only serialization-wise can something go wrong, in other words, user input.

**Extensions:**

Based on these functions, the user can create their own function for a general catch-all and return `null` pattern

```
runCatching { awaitReceiveOrNull }.getOrNull()
```

## Deprecations/Migrations

Deprecate `ServerRequest.awaitBodyOrNull` which can be replaced with `ServerRequest.awaitReceiveNullable`, as
**behavior** wise they are pretty close.

Deprecate `ServerRequest.awaitBody` with not direct replacement, but promote the usage of `ServerRequest.awaitReceive`.