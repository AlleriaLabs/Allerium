# Allerium Android release signing

All official APK releases must use one permanent Android signing key. Keep the keystore and its passwords private and backed up. If the key is lost, future APKs cannot update installations signed with it.

## 1. Create the keystore (Windows)

Run this in PowerShell on a trusted computer with Java's `keytool` available:

```powershell
keytool -genkeypair -v -keystore allerium-release.jks -alias allerium -keyalg RSA -keysize 4096 -validity 10000
```

Choose a strong keystore password and key password. Remember the alias (`allerium`) and store the keystore plus passwords in a secure password manager and an encrypted backup. Do not put the keystore in this repository or send it in chat.

## 2. Convert the keystore to Base64

In the same PowerShell directory:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("allerium-release.jks")) | Set-Content -NoNewline "allerium-release-base64.txt"
```

Open `allerium-release-base64.txt` and copy its entire contents. Treat this text as a private signing credential.

## 3. Add GitHub Actions secrets

Open the repository's **Settings → Secrets and variables → Actions → New repository secret** and create these four secrets:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Entire Base64 text from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `allerium` |
| `ANDROID_KEY_PASSWORD` | Key password |

Never add these values to source files, workflow YAML, commits, issues, or chat messages.

## 4. Publish releases

After all four secrets are configured, publish the next version tag from the updated `main` branch (currently `v1.0.1`) or run **Actions → Build and publish Allerium release → Run workflow** with that tag. The original `v1.0.0` release was already built before persistent signing was configured; use `v1.0.1` for the first permanently signed release. The workflow builds a signed release APK and attaches it to the GitHub release.

Keep the same keystore for every future version. Increase `versionCode` for every release; Android uses it to determine whether an APK is a valid upgrade.

## Important: existing debug installations

The already-published `v1.0.0` APK and earlier CI debug APKs were built using a runner-generated debug key. That temporary key is not the permanent release key. Android only permits an in-place update when the signing certificates match. Therefore, an installation made from `v1.0.0` or one of those earlier CI debug APKs will need a one-time uninstall before installing the first permanently signed release APK (`v1.0.1`). Back up server settings first if needed. Back up server settings first if needed. After moving to the permanent release-signed APK, subsequent releases using this same key can install over it without uninstalling.
