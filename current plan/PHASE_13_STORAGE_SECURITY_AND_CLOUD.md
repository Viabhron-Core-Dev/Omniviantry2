# PHASE 13: Storage Security, Hardware Keystore, Cloud Archiving & CI/CD Pipeline

## 1. Architectural Overview & Security Invariants
Phase 13 implements enterprise-grade storage security, hardware-backed encryption key management, hardened cloud archiving, and a bulletproof CI/CD pipeline for Omnivian.

### Security Invariants:
1. **Credential Immunity**: Zero hardcoded or committed credentials. Keystores, signing configs, and secrets are injected via CI/CD secrets or generated dynamically at runtime.
2. **Hardware-Backed KeyStore (TEE / StrongBox)**: Encryption keys are derived using Android's Hardware Security Module (`AndroidKeyStore`). Plaintext keys are never stored on disk.
3. **Hardened Export Filter**: Any cloud export (GitHub, Google Drive, ZIP) must pass through `HardenedExportManager`, stripping `.env*`, `*.keystore`, `*.jks`, `*.p12`, and authentication tokens.
4. **LogKeeper Compliance**: All security operations, key derivations, and cloud exports log execution telemetry via `com.example.utils.LogKeeper` with zero credentials, zero PII, and zero file content.

---

## 2. Systematic Mini-Phase Breakdown

### Mini-Phase 13.1: Hardware-Backed Master Key Manager (`DatabaseKeyStore.kt`)
- **Location**: `app/src/main/java/com/example/engine/db/DatabaseKeyStore.kt`
- **Responsibilities**:
  - Interacts with `AndroidKeyStore` using AES-256 GCM (`AES/GCM/NoPadding`).
  - Probes hardware capabilities: attempts StrongBox Keymaster (`setIsStrongBoxBacked(true)`), gracefully falling back to hardware TEE (`KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT`).
  - Generates and encrypts a 256-bit cryptographically secure random master database passphrase.
  - Exposes `getOrCreateDatabasePassphrase(context: Context): ByteArray` with secure in-memory wipe patterns (`clearPassphrase(passphrase: ByteArray)`).
  - Telemetry: Logs key generation, key retrieval, and hardware security level to `LogKeeper`.

### Mini-Phase 13.2: Storage Security & Integrity Manager (`DatabaseSecurityManager.kt`)
- **Location**: `app/src/main/java/com/example/engine/db/DatabaseSecurityManager.kt`
- **Responsibilities**:
  - Provides runtime security posture inspection: `getSecurityAudit(context: Context): DatabaseSecurityReport`.
  - Audits database file size, page count, journal mode (WAL), table count across the 19 entities.
  - Verifies hardware key backing status and active encryption mode.
  - Integrates with `AppDatabase` to monitor database access and integrity checks (`PRAGMA integrity_check`).
  - Telemetry: Logs audit transitions and integrity check outcomes.

### Mini-Phase 13.3: Hardened Cloud & Drive Workspace Archiver (`WorkspaceArchiveManager.kt`)
- **Location**: `app/src/main/java/com/example/engine/export/WorkspaceArchiveManager.kt`
- **Responsibilities**:
  - Creates sealed, portable `.omniroot.zip` archive packages for Google Drive or external cloud backups.
  - Runs active workspaces through `HardenedExportManager.isSensitiveFile` filter before archiving.
  - Computes SHA-256 checksums per file and bundles a root `manifest.json` with security metadata.
  - Guarantees 0 sensitive files included in the archive (`0-secret verified`).
  - Provides decompression and restoration with path-traversal protection (`Zip Slip` guard).

### Mini-Phase 13.4: CI/CD Pipeline Hardening (`.github/workflows/build.yml`)
- **Location**: `.github/workflows/build.yml`
- **Responsibilities**:
  - Pre-build security gate: runs automated secret scan checking for `.keystore`, `.jks`, `.p12`, `.env` before compiling.
  - Test validation gate: executes JVM unit tests and Robolectric test suite (`gradle :app:testDebugUnitTest`).
  - Build gate: executes `gradle assembleDebug --no-daemon --no-configuration-cache`.
  - Artifact packaging: uploads tested, clean debug APK artifact.

### Mini-Phase 13.5: UI Integration & Cloud Export Surface
- **Location**: `app/src/main/java/com/example/ui/export/GithubExportBottomSheet.kt` & `app/src/main/java/com/example/ui/settings/GlobalSecurityDialog.kt`
- **Responsibilities**:
  - Adds 1-tap "Export Hardened Cloud Archive (.zip)" action in the export bottom sheet.
  - Provides a Security & KeyStore Audit dialog displaying Hardware TEE status, AES-256 key presence, and database integrity state.
