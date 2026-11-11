# APK distribution

> Versión en español: [DISTRIBUTION.es.md](DISTRIBUTION.es.md)

One path only, triggered by a `vX.Y.Z` tag (or by running the workflow by hand with a version): it
builds the signed release APK (`.github/actions/build-release-apk`) and uploads it to Firebase App
Distribution with a clean `versionName` (no suffix). **No GitHub Release or artifact is published**:
the repo is public and the APK has real keys compiled in (see "Secrets inside the APK"), so it is
only handed out through Firebase, to testers.

| | |
|---|---|
| Workflow | `.github/workflows/release-apk.yml` |
| Trigger | tag `vX.Y.Z` / manual (with a version) |
| Result | The APK in Firebase App Distribution (group `testers`, or the `FIREBASE_TESTER_GROUP` variable). No GitHub Release |
| `versionName` | the tag's version, no suffix |
| `versionCode` | `<run_number>` |
| Extra secrets | `FIREBASE_SERVICE_ACCOUNT_JSON` |

There used to be a second path (`distribute.yml`) that uploaded a build to Firebase on every push to
`main`, with `versionName` `<last tag>-dev.<run_number>`. It was removed on purpose: only what you
decide to mark with a tag reaches testers, never an unversioned in-between build.

## Secrets in Doppler

GitHub needs only one secret, `DOPPLER_TOKEN` (a read-only service token for `nanodemo/prd`). The
rest comes from Doppler:

| Secret | Use |
|---|---|
| `GEMINI_API_KEY`, `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `MEDIAPIPE_*` | Compiled into `BuildConfig` (tiers B, C, E) |
| `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | APK signing |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase App Distribution step |

Before building, the composite action checks that all of them exist and fails with the exact name
of the one that is missing.

### Fixed keystore (one time)

Without a fixed keystore every runner would sign with a different debug key and nobody could update
the app without uninstalling it.

```powershell
& "$env:USERPROFILE\.jdks\jdk-21.0.12.1+1\bin\keytool.exe" -genkeypair -v -keystore nanodemo-release.jks -alias nanodemo -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("nanodemo-release.jks")) | doppler secrets set SIGNING_KEYSTORE_BASE64 -p nanodemo -c prd
```

`*.jks` and `*.keystore` are in `.gitignore`. Keep a copy of the `.jks` **outside the repo**: if it
is lost, the same signature cannot be regenerated. Careful when piping passwords in PowerShell 5.1:
it appends `\r\n`; check the length of the stored secret.

## Firebase App Distribution

1. A **service account** in Google Cloud (the Firebase project) with the **Firebase App
   Distribution Admin** role; its JSON key goes in `FIREBASE_SERVICE_ACCOUNT_JSON`. Do not use the
   Admin SDK account (broader permissions than needed).
2. **App Distribution** enabled in the Firebase console and a **tester group** with the alias
   `testers` (or any other, through the `FIREBASE_TESTER_GROUP` repo variable).
3. `DOPPLER_TOKEN` in GitHub (Settings → Secrets and variables → Actions).

The upload step runs a pinned `firebase-tools` version through `npx` (see `release-apk.yml`), and the
actions in that workflow and in the composite action are pinned by commit SHA, because that job holds
the service-account key. Bump them deliberately.

## Publishing a version

```bash
git tag v1.2.0
git push origin v1.2.0
```

or Actions → **Release APK** → *Run workflow* with `1.2.0` (it does not create the tag: it only
builds and distributes; you add the tag yourself if you want to mark the commit). The workflow
validates the format (`X.Y.Z` or `X.Y.Z-suffix`), rejects a version whose tag already exists and
uploads the APK to Firebase App Distribution with `versionName` = the version, no `-dev` suffix.

It needs `DOPPLER_TOKEN` and `FIREBASE_SERVICE_ACCOUNT_JSON`; the workflow only asks for the
`contents: read` permission. The `models-v1` tag (MediaPipe models) does not trigger this workflow:
the pattern is `v*`. The GitHub Releases `v0.1.0`–`v0.9.0` were deleted when the repo was made
public (their APKs had real keys); the tags are kept.

## Secrets inside the APK

`GEMINI_API_KEY`, `BRAVE_SEARCH_API_KEY` and the Firebase keys are compiled into `BuildConfig`:
anyone with the APK can extract them. Doppler protects where the secrets live, not what ends up
inside the APK. That is why the (public) repo does **not** publish APKs in GitHub Releases or as an
Actions artifact: the APK only goes to Firebase App Distribution, to trusted people. Even so, a
tester can extract the keys: restrict them in their consoles (Gemini/Firebase per app and package,
spending caps) and rotate them if an APK leaks. Since the repo is now public, `MEDIAPIPE_MODEL_TOKEN`
is no longer needed to download `models-v1` (it was removed from Doppler and from the secrets the
action requires; the code treats it as optional).

## Taking it to the template

The two files are independent of the app's content (they only assume an `:app` module and Doppler):

```
.github/actions/build-release-apk/action.yml   # build + signing
.github/workflows/release-apk.yml              # build + distribution (Firebase)
```

When copying them, change: the app name (if used in notes), the list of secrets the action verifies
(`action.yml`) and the `VERSION_CODE` / `VERSION_NAME` that `app/build.gradle.kts` reads (see its
header). The template already has its own `release.yml` for template releases (`vYYYY.MM.PATCH` +
CHANGELOG): it is not overwritten, the APK uses `release-apk.yml`. That is why its tags also match
`v*`; if the template publishes template tags and APK tags in the same repo, the patterns have to be
separated (e.g. `app-v*` for the APK).
