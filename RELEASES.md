# Releasing Timer Widget

This is the runbook for cutting a release that lands on F-Droid with your own
signature (reproducible builds). One-time setup is already done for 1.0.0:
the release keystore exists, and the fdroiddata recipe has
`AllowedAPKSigningKeys` + `Binaries` + auto-update configured.

## Prerequisites (once per machine)

- The release keystore: `~/keystores/timer-widget-release.jks` with its
  password in `~/keystores/timer-widget-release.password`. Back this up
  offline; losing it breaks updates for every installed user. Never commit
  it to any repo.
- An fdroiddata clone with fdroidserver installed (e.g. the venv with
  `fdroid` on PATH) and `apksigcopier` available.
- Android SDK + JDK 21 (same major versions as CI uses).

## Per release

1. **Bump the version** in `app/build.gradle.kts`: `versionName` (e.g.
   `1.0.1`) and `versionCode` (strictly increasing, e.g. `2`).

2. **Add release notes** at
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and
   `fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt`
   (max 500 characters each). F-Droid reads the changelog from the source at
   the tag, keyed by `versionCode`.

3. **Commit, tag, push:**

   ```bash
   git add -A && git commit -m "Release <versionName>"
   git tag -a v<versionName> -m "Timer Widget <versionName>"
   git push origin main v<versionName>
   ```

   The tag name is free-form for F-Droid's update bot, but keep the `v`
   prefix: the `Binaries:` URL pattern is
   `releases/download/v%v/timer-widget.%v.apk`, so the tag and the release
   asset name must both follow it.

4. **Build the reference APK with fdroidserver, not plain Gradle.** In the
   fdroiddata clone:

   ```bash
   fdroid build io.github.conorryan.timerwidget
   ```

   This guarantees the reference binary is produced exactly the way
   F-Droid's build server produces it. The unsigned APK lands at
   `unsigned/io.github.conorryan.timerwidget_<versionCode>.apk`.

5. **Sign it** with the release key:

   ```bash
   apksigner sign \
     --ks ~/keystores/timer-widget-release.jks \
     --ks-pass file:~/keystores/timer-widget-release.password \
     --ks-key-alias timerwidget \
     --v1-signing-enabled false \
     --alignment-preserved true \
     --out timer-widget.<versionName>.apk \
     unsigned/io.github.conorryan.timerwidget_<versionCode>.apk
   ```

   The two flags are required for the reproducible-build flow: apksigner
   otherwise adds v1 JAR-signing files and re-aligns every entry, both of
   which change the APK's byte layout so F-Droid's signature-copy
   verification fails. (v1 is unnecessary on minSdk 26; alignment is
   already correct from the build.)

6. **Create the GitHub release** on the tag and upload
   `timer-widget.<versionName>.apk` as a release asset. The filename must
   match the `Binaries:` pattern exactly: `timer-widget.<versionName>.apk`.

7. **fdroiddata update MR.** The checkupdates bot notices the new tag within
   a day and opens an update MR (that is `AutoUpdateMode: Version` +
   `UpdateCheckMode: Tags` working). No extra files are needed from you:
   `fdroid build` downloads the release asset itself and transplants the
   signature from it (`Binaries:` + `AllowedAPKSigningKeys`). If the bot is
   slow, open the MR yourself: new `Builds:` entry and bumped
   `CurrentVersion`/`CurrentVersionCode`.

   Do NOT add `signatures/<versionCode>/` files to fdroiddata: shipping
   signature files together with `Binaries:` makes the fdroiddata CI run a
   publish step that fails (`reference binary missing` - the build leaves
   the reference in `tmp/binaries/`, publish looks in
   `unsigned/binaries/`). `Binaries:` + `AllowedAPKSigningKeys` alone is
   the working setup (that is how Podcini.A does it).

8. **Optional but recommended: verify locally** before the MR runs. In the
   fdroiddata clone (with the release asset already uploaded):

   ```bash
   fdroid build io.github.conorryan.timerwidget
   ```

   This rebuilds from source, downloads your reference APK, and checks the
   two are byte-identical before applying the signature - the same check
   the MR pipeline runs.

## Notes

- The build must stay reproducible: any nondeterminism (some minification
  configurations, embedded timestamps) breaks step 9 and the MR pipeline.
  If the pipeline's `fdroid publish` step ever reports a mismatch, remove
  the `Binaries:`/signatures for that version and fall back to F-Droid's
  key, then investigate.
- Users can switch freely between the GitHub release and F-Droid because
  both carry the same signature. Keep signing every GitHub release with
  this key or you fork the install base.
- Rebuilding the release APK later (e.g. for a hot re-release) must use
  the same key; a different key cannot update existing installs.
