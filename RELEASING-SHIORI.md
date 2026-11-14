# Releasing Shiori (Android)

The app checks `https://api.github.com/repos/<SHIORI_UPDATE_REPO>/releases/latest` every time it comes to
the foreground. `SHIORI_UPDATE_REPO` is a BuildConfig field in `app/build.gradle.kts` (now
`Makogai/shiori-android`; `OWNER/REPO` turns the check off).

A release is offered when:

- it is the repo's **latest** release (not a draft, not a pre-release),
- its tag is `v<versionName>-<versionCode>`, e.g. `v0.21.3-40`, and that versionCode is **higher** than the
  installed one,
- it has an asset named exactly **`Shiori-arm64.apk`**, signed with the same key as the installed app.

The release notes (body) are shown in the update dialog as Markdown.

## Steps

1. Bump `versionCode` (always) and `versionName` (if wanted) in `app/build.gradle.kts`, and commit.
2. Build the signed arm64 release APK and copy it to `Shiori-arm64.apk`.
3. Publish:

```sh
code=$(sed -n 's/^ *versionCode = //p' app/build.gradle.kts)
name=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
gh release create "v$name-$code" Shiori-arm64.apk \
  --repo Makogai/shiori-android --title "Shiori $name ($code)" --notes "What changed" --latest
```

Skipping a version in the dialog hides only that tag; the next tag is offered again.
