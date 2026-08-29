# Cutting a release

The shape is NeoForge's: a manifest describes the release, every file is verified by SHA-1, and
nothing is trusted by name. A truncated download or a stale mirror fails loudly instead of
producing a broken install that wastes an afternoon.

## Build

```sh
./gradlew build          # Infinite.jar
loader/build.sh          # InfiniteLoader.jar, InfiniteInstaller.jar
```

## Stage

```
Infinite-<version>/
  InfiniteInstaller.jar
  Infinite.jar
  InfiniteLoader.jar
  version.json
  README.md
  INSTALL.md
  NOTICE.md
  docs/
```

> [!NOTE]
> No `run.bat` or `run.sh` any more. The installer registers with your launcher and the
> launcher launches it. If you want to play without a launcher, `java -jar InfiniteLoader.jar`
> still does the job.

## Generate the manifest

```sh
tools/make_manifest.py Infinite-<version>/ <version> <base-url> <lwjgl-component.json> \
    --update-url https://<host>/infinite/latest/version.json
```

It hashes every file in the release folder and writes `version.json`. Library metadata comes
straight out of the Prism/MultiMC `org.lwjgl.json` component, so the URLs, sizes and hashes are
Mojang's own instead of something I typed in by hand.

<details>
<summary><b>What version.json looks like</b></summary>

```jsonc
{
  "version": "1.0-210826",
  "self":      "https://host/infinite/1.0-210826/version.json",
  "updateUrl": "https://host/infinite/latest/version.json",
  "requires": { "baseJar": { "id": "a1.0.4", "sha1": "…", "size": 749244 } },
  "files":     [ { "path": "Infinite.jar", "sha1": "…", "size": …, "url": "…" } ],
  "libraries": [ { "name": "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209", "artifact": { … } } ]
}
```

`self` is the immutable per-version URL. `updateUrl` is the moving "latest" pointer. The
loader compares its local `version` against whatever `updateUrl` returns.

</details>

## Publish

Upload the release folder to `<base-url>/infinite/<version>/`, then copy `version.json` to
`<base-url>/infinite/latest/version.json`.

> [!IMPORTANT]
> Copy the manifest to `latest/` **last**. It's the trigger. If it lands before the files it
> points at, every client that checks in that window fails its hash check.

Then zip the release folder for people who'd rather download it directly.

## How updating works

The loader compares its local `version.json` against `updateUrl` on startup, pulls anything
that changed, and verifies each file by SHA-1 before it lands. It writes the manifest **last**,
so an interrupted update retries next time instead of assuming it finished.

The loader can't replace its own jar while it's running on Windows, and there's no start script
left to swap it afterwards, so a loader update lands beside it as `.new` and gets picked up
when someone runs the installer from a fresh download. The mod jar updates in place, which is
the part that matters.

An update check that fails is never fatal. Being offline, or my host having a bad day, must not
stop anyone playing.

## Registered installs

If someone installed through `InfiniteInstaller.jar` into a launcher, the loader isn't in the
picture at all and nothing self updates. They download the new release and run the installer
again. It registers the new version next to the old one, so rolling back is picking the older
profile.

## Checklist

- [ ] `./gradlew roundTripCheck` passes
- [ ] `./gradlew build` and `loader/build.sh` both clean
- [ ] `java -jar InfiniteLoader.jar --check` passes against a real a1.0.4 jar
- [ ] `java -jar InfiniteInstaller.jar --headless` registers into a throwaway `.minecraft`
- [ ] `NOTICE.md` still matches what's actually bundled
- [ ] Manifest generated, files uploaded, `latest/` updated **last**
