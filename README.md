# Kdx

A web text editor written in kotlin, based on [xi-editor](https://github.com/xi-editor/xi-editor/tree/master).

## Project status & goals

Always wandering how a text-editor works and inspired by the blog
[fleet-below-deck](https://blog.jetbrains.com/fleet/2022/01/fleet-below-deck-part-i-architecture-overview/),
I thought of deep diving in implementation specifics by building one that operates in web-space.
However, I am keeping things simple because the main motive is to understand the nuances.
Expect many shortcuts and poorly designed APIs, as my focus is on completing a minimal editor (for now).

**Goals:**

- Port core functionality from `xi-editor-ropes`
- Port core functionality from `xi-editor-core`
- Create a minimal server
- Create the front-end
- Complete a minimal text editor

**Status:**

- [x] Port core functionality from `xi-editor-ropes`

### Side notes

[kdx-core](kdx-core/src/commonMain/kotlin/Rope.kt) includes an implementation of a persistent
[Rope](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index) backed by a BTree.