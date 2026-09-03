# Third party notices

The Minecraft Infinite server bundles the components below. Their licences require these
notices.

Shorter list than the client's. The server has no sound stack and no LWJGL, JOrbis,
PaulsCode.

## fastutil

- Copyright © Sebastiano Vigna
- <https://fastutil.di.unimi.it/>
- **Apache-2.0.** Full text in `licenses/Apache-2.0.txt`.

3,396 of the ~4,700 classes in this jar are fastutil. It should be a dependency, not vendored.
See [DISTRIBUTION.md](docs/DISTRIBUTION.md).

## Brigadier

- Copyright © Microsoft Corporation
- <https://github.com/Mojang/brigadier>
- **MIT.** Full text in `licenses/MIT-brigadier.txt`.

## Starlight

- Copyright © Spottedleaf and contributors
- <https://github.com/PaperMC/Starlight>
- **LGPL-3.0.** Full text in `licenses/LGPL-3.0.txt`, and the GPL-3.0 it extends in
  `licenses/GPL-3.0.txt`.

> [!CAUTION]
> Same problem as the client. LGPL-3.0 says you have to be able to swap this component out for
> your own build of it, and it's compiled into the same jar as everything else, so the jar
> alone doesn't satisfy that. Fix is shipping Starlight as a separate jar on the classpath, or
> providing the object files needed to relink. If it's been changed from upstream, that has to
> be stated and the changed source made available.

## JSON-java (org.json)

- Copyright © JSON.org / stleary and contributors
- <https://github.com/stleary/JSON-java>

> [!WARNING]
> **This one needs checking before anything goes public.** JSON-java's licence changed. Modern
> releases (roughly 20211205 onward) are public domain. Older ones carry the JSON License with
> the *"The Software shall be used for Good, not Evil"* clause, which Debian, Fedora and Apache
> all treat as non-free and refuse to ship.
>
> The copy in this jar has `JSONParserConfiguration` and `XMLXsiTypeConverter`, which are recent
> additions, so it's probably a public domain release. Probably isn't good enough. Pin the
> version as a real dependency and the question answers itself.

## Minecraft

Minecraft is a trademark of Mojang AB / Microsoft. This project is not affiliated with,
endorsed by, or associated with either of them.

**This jar contains no Mojang code or Mojang art.** Verified two ways:

- Every entry hashed against the official Alpha 1.0.4 client jar. **Zero byte identical files.**
- Every `CONSTANT_Class` entry in all 4,743 classes resolved against the 375 classes in Alpha
  1.0.4. **Zero references.**

That's why the server needs no base jar, no loader and no installer. It is not, however, a
statement that the code is independent of Mojang's, see
[DISTRIBUTION.md](docs/DISTRIBUTION.md).
