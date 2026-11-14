# Releasing Shiori (Android)

There are two builds from the same code. Both are R8-minified `release` builds, signed with the standard Android
debug key (`~/.android/debug.keystore`) like the debug builds shipped up to 44, so they update those in place:

| Build | Gradle | applicationId | Launcher name | Release asset |
| --- | --- | --- | --- | --- |
| Shiori Friends (social) | default | `app.mihon.friends` | Shiori Friends | `Shiori-Friends-arm64.apk` |
| Shiori (no social) | `-Pshiori.social=false` | `app.shiori` | Shiori | `Shiori-arm64.apk` |

The plain build gets no Supabase config, so the Arena, Cards, suggestions, recaps and their settings stay hidden,
and it has no "Shiori Cards" launcher shortcut.

## Update check

Both builds list `https://api.github.com/repos/<SHIORI_UPDATE_REPO>/releases?per_page=20` every time they come to
the foreground. `SHIORI_UPDATE_REPO` is a BuildConfig field in `app/build.gradle.kts` (now `Makogai/shiori-android`;
`OWNER/REPO` turns the check off).

A build is offered the newest release that:

- is not a draft and not a pre-release,
- has the tag `v<versionName>-<versionCode>`, e.g. `v0.21.3-40`, with a versionCode **higher** than the installed one,
- has that build's own asset (table above), signed with the same key as the installed app.

The release notes (body) are shown in the update dialog as Markdown.

## Scheme

Every release carries **both real APKs**, tag `v<versionName>-<versionCode>`, and both builds share the versionCode:

- `Shiori-Friends-arm64.apk`: Shiori Friends (`app.mihon.friends`),
- `Shiori-arm64.apk`: plain Shiori (`app.shiori`).

Never upload the Friends build under the plain name: `Shiori-arm64.apk` is always the plain build. If only the plain
build ever needs a release of its own, tag it `v<versionName>-<versionCode>-plain` with only `Shiori-arm64.apk`;
Friends skips it because its asset is missing.

## Steps

1. Bump `versionCode` (always) and `versionName` (if wanted) in `app/build.gradle.kts`, and commit.
2. Run `scripts/build-shiori.sh`; it writes `build-out/Shiori-Friends-arm64.apk` and `build-out/Shiori-arm64.apk`.
3. Publish:

```sh
code=$(sed -n 's/^ *versionCode = //p' app/build.gradle.kts)
name=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
gh release create "v$name-$code" build-out/Shiori-Friends-arm64.apk build-out/Shiori-arm64.apk \
  --repo Makogai/shiori-android --title "Shiori $name ($code)" --notes "What changed" --latest
```

Skipping a version in the dialog hides only that tag; the next tag is offered again.
