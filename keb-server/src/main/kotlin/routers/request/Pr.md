# ServerRequest.awaitBody()

## Commit message

Introduce awaitReceive API

..

## Summary

`ServerRequest.awaitBody*` APIs have some problems:

1. `ServerRequest.awaitBody` forces you to think about the coroutines-bridge exception handling, since it uses
   `kotlinx.coroutines.reactive.awaitSingle`. Also, it is not documented that it might throw `NoSuchElementException`
   and
   `IllegalArgumentException` for **coroutines-related** errors.

2. `ServerRequest.awaitBody` might throw `IllegalArgumentException` for two different reasons, for serialization-related
   errors and for coroutines-related errors.

3. `ServerRequest.awaitBodyOrNull` should never throw as the signature indicates. The convention established by the
   stdlib mandate that an operation with the name `xxxOrNull` returns `null` instead of throwing in case there is an
   error.

## Proposal

Introduce `ServerRequest.awaitReceive` and friends which addresses all the above.

Add `ServerRequest.awaitReceiveNullable` which acts as a replacement for `ServerRequest.awaitBodyOrNull` behavior wise,
but **semantically** are not the same. The core differences are:

- This API "abstracts" away the coroutine-bridging exception handling from the user

## Migration

Deprecate `ServerRequest.awaitBodyOrNull` and promote the patter