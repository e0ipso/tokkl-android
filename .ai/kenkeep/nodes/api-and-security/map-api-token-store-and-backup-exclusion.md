---
type: map
title: API token store and the rules that keep app data out of backups
description: >-
  ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off
  and the data extraction rules exclude every domain in every mode.
tags:
  - security
  - credentials
  - backup
kk_schema_version: 3
kk_id: map-api-token-store-and-backup-exclusion
kk_derived_from: []
kk_relates_to:
  - practice-pin-self-signed-certs-warn-on-http
  - practice-talk-only-to-configured-server
kk_depends_on: []
kk_confidence: high
---
`credentials/ApiTokenStore` keeps the API token encrypted with AES-256-GCM under a key in the Android Keystore, which never exports key material. It writes the ciphertext through `AtomicFile` in the app's files directory, and `clear()` deletes both the file and the key.

The manifest sets `android:allowBackup="false"` and points `android:dataExtractionRules` at `res/xml/data_extraction_rules.xml`, which Android 12 and later read. The app runs on nothing older since `minSdk` went to 31, so `backup_rules.xml` and `fullBackupContent`, which only Android 11 and earlier read, are gone. The file excludes all nine domains. A missing section leaves that transfer mode on for everything, so `cloud-backup`, `device-transfer` and `cross-platform-transfer platform="ios"` each list every domain. A new store therefore needs no backup rules of its own. `BackupRulesTest` (JVM) checks the merged manifest and that file. Sign-out deletes the token and its Keystore key.

<!-- kk:related:start -->
# Related

- Related: [practice-pin-self-signed-certs-warn-on-http](practice-pin-self-signed-certs-warn-on-http.md)
- Related: [practice-talk-only-to-configured-server](practice-talk-only-to-configured-server.md)
<!-- kk:related:end -->
