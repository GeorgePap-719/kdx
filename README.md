# Kdx

Implementation of a text engine using a [CRDT](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type) model.
Using a CRDT framework allows the engine to be used for concurrent editing of text on multiple devices.

The implementation is a port of [xi-editor-engine](https://github.com/xi-editor/xi-editor/tree/master) in Kotlin.
Most things are kept simple because the main motive is to understand the nuances. Expect many shortcuts and poorly
designed APIs, as the main focus was to complete the engine.

**Side notes:**

The [implementation](kdx-core/src/commonMain/kotlin/Rope.kt) contains an implementation of a persistent
[Rope](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index) backed by a BTree.