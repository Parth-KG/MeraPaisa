# Releasing Mera Paisa

This app is sideloaded to a few phones rather than shipped through the Play Store, which
makes the signing key more important than it would otherwise be — not less.

## Why the signing key matters

Android identifies an app by its package name **and** the key it was signed with. Two APKs
with the same package name but different keys are, as far as the system is concerned,
different apps that happen to collide.

That has one practical consequence, and it is the whole reason this file exists:

> If you rebuild with a different key, your friends cannot install the update over the old
> app. They have to uninstall first — and uninstalling deletes the app's data directory,
> which is where the ledger lives. Everything they are owed goes with it.

So: create a key once, keep it safe, and sign every build with it. Debug builds are signed
with the auto-generated debug key, which is per-machine and not stable — never hand those out
as a release.

## Creating a keystore

Once, and then never again:

```sh
keytool -genkeypair -v \
  -keystore mera-paisa.jks \
  -alias mera-paisa \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

Keep the file **outside** the repository. `.gitignore` already excludes `*.jks`, `*.keystore`
and `keystore.properties`, but the safest place is somewhere the repo cannot reach at all.

Back it up somewhere you will still have in five years — a password manager's file vault, an
encrypted drive, wherever your other irreplaceable files live. There is no recovery: lose the
keystore or forget its password and the only way to ship another build is to uninstall the app
from every phone it is on.

## Wiring it into the build

Put the credentials in a `keystore.properties` file next to the repo (not in it):

```properties
storeFile=/absolute/path/to/mera-paisa.jks
storePassword=…
keyAlias=mera-paisa
keyPassword=…
```

Then add a signing config to `app/build.gradle.kts`. It reads the file if present and falls
back to an unsigned build if not, so a fresh clone still builds:

```kotlin
val keystoreProperties = Properties().apply {
    val file = rootProject.file("../keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // …existing minify settings…
        }
    }
}
```

No signing config is committed here on purpose.

## Building

```sh
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

Release builds run R8 with `isMinifyEnabled` and `isShrinkResources` on — currently about
2.7 MB against 22.5 MB for the debug build. Shrinking can remove something that only reflection
was keeping alive, so **install and open a release build yourself before sending it to
anyone**: add a person, record an amount, settle them, check the widget and the CSV export.
If something is missing, add a `-keep` rule to `app/proguard-rules.pro`.

## Upgrading a friend's phone

With a stable key it is just `adb install -r app-release.apk`, or sending them the APK. The
ledger survives, because it is the same app.

If you ever *do* have to change keys, get everyone to export their ledger to CSV first
(the share button in the top bar). It will not restore automatically, but at least the
numbers exist somewhere.
