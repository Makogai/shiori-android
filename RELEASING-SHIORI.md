# Releasing Shiori (Android)

There are two builds from the same code:

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

Publish **one release per version with both APKs** attached, tag `v<versionName>-<versionCode>`. Both builds share
the versionCode. If only the plain build ever needs a release of its own, tag it `v<versionName>-<versionCode>-plain`
with only `Shiori-arm64.apk`; Friends skips it because the asset is missing.

### Shiori Friends v39

The v39 Friends build looks only at `releases/latest` for `Shiori-arm64.apk`. From v40 on that asset is the plain
build (`app.shiori`), so v39 would offer plain Shiori, which installs as a separate app instead of updating. v39 was
never published, so install the v40 `Shiori-Friends-arm64.apk` by hand once; it updates itself from then on.

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
