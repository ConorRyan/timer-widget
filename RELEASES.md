# Releasing Timer Widget

This is the runbook for cutting a release whose signed APK is published on
GitHub, with your own signature (the same system as Whistle Tabber and
Weeklies). One-time setup is already done for 1.0.0: the release keystore
exists.

## Prerequisites (once per machine)

- The release keystore: `~/.local/share/keystores/timer-widget-release.jks`
  (alias `timerwidget`) with its password in
  `~/.local/share/keystores/timer-widget-release.password`. Back this up
  offline; losing it breaks updates for every installed user. Never commit
  it to any repo.
- Android SDK + JDK 21, with `apksigner` from the build-tools
  (`~/.local/share/android-sdk/build-tools/<version>/apksigner`).

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

   Keep the `v` prefix and the release asset naming below: they match the
   `Binaries:` URL pattern in the fdroiddata recipe,
   `releases/download/v%v/timer-widget.%v.apk`.

4. **Check the build is reproducible.** Two clean builds from the same tree
   must be byte-identical, or F-Droid's signature-copy verification (and any
   future reproducible-build claim) fails:

   ```bash
   ./gradlew clean assembleRelease
   cp app/build/outputs/apk/release/app-release-unsigned.apk /tmp/apk1
   ./gradlew clean assembleRelease
   cmp /tmp/apk1 app/build/outputs/apk/release/app-release-unsigned.apk \
     && echo reproducible
   ```

   If the two differ, fix determinism before releasing (minification
   configurations and embedded timestamps are the usual culprits).

5. **Sign the APK** with the release key:

   ```bash
   ~/.local/share/android-sdk/build-tools/<version>/apksigner sign \
     --ks ~/.local/share/keystores/timer-widget-release.jks \
     --ks-pass "file:$HOME/.local/share/keystores/timer-widget-release.password" \
     --ks-key-alias timerwidget \
     --v1-signing-enabled false \
     --alignment-preserved true \
     --out timer-widget.<versionName>.apk \
     app/build/outputs/apk/release/app-release-unsigned.apk
   ```

   The two flags are required for the reproducible-build flow: apksigner
   otherwise adds v1 JAR-signing files and re-aligns every entry, both of
   which change the APK's byte layout so F-Droid's signature-copy
   verification fails. (v1 is unnecessary on minSdk 26; alignment is already
   correct from the build.)

   Verify the signature with `apksigner verify` on the output file.

6. **Create the GitHub release** on the tag and upload
   `timer-widget.<versionName>.apk` as a release asset. The filename must
   follow the pattern exactly: `timer-widget.<versionName>.apk`. Use the
   en-US changelog file as the release notes.

## F-Droid (pending)

The fdroiddata recipe is drafted but not published yet. When it lands, the
recipe gets `Binaries:` + `AllowedAPKSigningKeys` and the reference APK must
be built by fdroidserver (`fdroid build io.github.conorryan.timerwidget`),
not plain Gradle, from the exact final release commit; steps 4 and 5 above
then sign the fdroid-built unsigned APK instead. The signing certificate's
SHA-256 fingerprint (public, it is embedded in every signed APK), lowercase
without colons, is
`a58ad60df4aa4a2ad44c8ae16f434ea44acd444a9be77fee4d184285a63f9afb`.

Do NOT add `signatures/<versionCode>/` files to fdroiddata together with
`Binaries:`; shipping both makes the fdroiddata CI run a publish step that
fails. `Binaries:` + `AllowedAPKSigningKeys` alone is the working setup.

## Notes

- Users can switch freely between the GitHub release and F-Droid because
  both carry the same signature. Keep signing every GitHub release with
  this key or you fork the install base.
- Rebuilding the release APK later (e.g. for a hot re-release) must use the
  same key; a different key cannot update existing installs.
