# AntiROTAid — Release Signing

The `ara` flavor (`app.antirotaid`) is signed with a dedicated **upload key**.
Signing secrets are **never** stored in `build.gradle.kts`. They are read at
build time from a gitignored `keystore.properties` (preferred) or from
environment variables of the same names.

## 1. Create a release keystore (one time)

Use the JDK shipped with Android Studio (`keytool`). Pick a strong, unique
password and **store it in a password manager** — if you lose it you can never
update the app on Google Play.

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" `
  -genkeypair -v `
  -keystore antirotaid-release.jks `
  -alias antirotaid `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=AntiROTAid"
```

> A working `antirotaid-release.jks` (alias `antirotaid`) already exists in
> `product/android/`. It is gitignored and must never be committed.

## 2. Store the keystore safely

* Keep the `.jks` outside source control (it is in `.gitignore`).
* Keep an offline backup (encrypted USB / password manager attachment).
* Recommended: also enroll in **Google Play App Signing** so Google holds the
  *app signing key* and your `.jks` is only the *upload key* (replaceable if
  lost). Closed testing works either way.

## 3. Configure secrets locally

Create `product/android/keystore.properties` (gitignored). Paths are resolved
relative to the `app/` module directory:

```properties
ANTIROTAID_KEYSTORE_PATH=../antirotaid-release.jks
ANTIROTAID_KEYSTORE_PASSWORD=********
ANTIROTAID_KEY_ALIAS=antirotaid
ANTIROTAID_KEY_PASSWORD=********
```

**Or** export environment variables of the same names (CI-friendly):

```powershell
$env:ANTIROTAID_KEYSTORE_PATH = "../antirotaid-release.jks"
$env:ANTIROTAID_KEYSTORE_PASSWORD = "********"
$env:ANTIROTAID_KEY_ALIAS = "antirotaid"
$env:ANTIROTAID_KEY_PASSWORD = "********"
```

If neither is present, the build script still loads but the release
signingConfig is left empty and a release build fails at signing time — by
design, so no secret ever has to live in the repo.

## 4. Build the signed bundle

```powershell
cd product\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat clean bundleAraRelease
```

Output:

```
product/android/app/build/outputs/bundle/araRelease/app-ara-release.aab
```

## 5. Warnings

* **Never** commit `*.jks`, `*.keystore`, or `keystore.properties`.
* **Never** paste keystore passwords into chat, code, screenshots, or commits.
* **Never** share the upload keystore. Losing it (without Play App Signing)
  means you cannot publish updates under `app.antirotaid` again.
