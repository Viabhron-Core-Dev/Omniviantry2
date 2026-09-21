# 2-Part Decoupled Artifact Pipeline: Secrets & Integrations Architecture

> **Document Status**: **ACTIVE ARCHITECTURAL SPECIFICATION**  
> **Classification**: Security Architecture, UI Implementation, & Export Pipelines  
> **Core Mandate**: Strict Decoupling of Code Repo (Part 1) and Encrypted Secrets (Part 2). Complete AI Blindness to Credentials. LogKeeper Event Audit.

---

## 1. Executive Summary & Problem Statement

In an agentic developer environment with filesystem tools (`view_file`, `grep_search`, `list_dir`), standard secret management approaches (such as storing API keys or tokens in `.env` or `secrets.json` within the workspace directory) create critical security vulnerabilities:
1. **Model Data Leakage**: An LLM agent requested to debug or inspect a project will inevitably read the workspace file and transmit raw API keys/tokens to external model inference servers.
2. **Repository Exposure**: Pushing code to GitHub, Google Drive, or exporting a project ZIP leaks live credentials into version control or third-party storage.

### The Decoupled Architecture
This specification establishes the **2-Part Decoupled Artifact Pipeline**:
- **Part 1 (The Repo)**: Publicly readable and editable web/app source code (`index.html`, `style.css`, `app.js`, `manifest.json`, assets). Accessible to the AI for coding, debugging, formatting, and file operations. Exportable to GitHub, Google Drive, and local storage.
- **Part 2 (The Secrets Bundle)**: AES-256-GCM encrypted vault containing sensitive keys (e.g. `GITHUB_TOKEN`, `CLOUDFLARE_API_KEY`, Webhook URLs). **Stored strictly outside the workspace directory**. Never placed on disk inside the chat workspace. Created only when saving to the Artifacts Library (Global Sidebar). Injected directly into memory (`window.__SECRETS__`) during runtime execution.

---

## 2. Core Architectural Invariants

1. **AI Blindness**: The AI agent is physically incapable of reading Part 2 because no secret file exists within `/workspaces/<workspace_id>/`.
2. **Runtime Blind Injection**: When running in the in-app preview or standalone PWA loader, the Android WebView intercepts page execution and binds secrets to `window.__SECRETS__` in memory before DOM scripts execute.
3. **Safe Forking / Editing**: When a user taps `[✏️ Edit]` on a saved artifact, **only Part 1 (The Repo)** is extracted into the new workspace. Part 2 remains locked in the Artifact Vault.
4. **Hardened Export**: Any export to GitHub, Google Drive, or External Storage packages **Part 1 ONLY**. It generates an empty `secrets.example.json` template without values. Real credentials are never exported.
5. **Universal LogKeeper Auditing**: Every stage of this pipeline reports lifecycle status and error metrics to `LogKeeper` while strictly stripping token/credential strings.

---

## 3. Mini-Phase Implementation Roadmap

The implementation is broken down into **5 decoupled, bite-sized mini-phases**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ MINI-PHASE 1: UI ONLY - THREAD SETTINGS (Integrations & Secrets Tabs)       │
│ File: ThreadSettingsScreen.kt                                               │
│ Action: Pure Compose UI + EncryptedPrefs vault for thread secrets.          │
│ LogKeeper: Logs "ThreadSettingsUpdated" (keys count only, zero values).     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ MINI-PHASE 2: RUNTIME BLIND INJECTION IN PREVIEW                            │
│ File: PWAPreviewBottomSheet.kt                                              │
│ Action: Injects window.__SECRETS__ = Object.freeze(...) via evaluateJs.     │
│ LogKeeper: Logs "PreviewSecretsInjected" (count of keys only).              │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ MINI-PHASE 3: THE 2-PART ARTIFACT SAVER (Global Sidebar Mini-Apps)          │
│ File: ArtifactWorkspaceManager.kt                                           │
│ Action: On save, write Part 1 (Repo) + Part 2 (Encrypted Column/Bundle).    │
│ LogKeeper: Logs "Artifact2PartPackaged" (artifactId, Part 1 size, Part 2).  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ MINI-PHASE 4: BLIND FORK & EDIT (Withholding Part 2 from Workspace)         │
│ Files: ArtifactsScreen.kt / ChatScreen.kt                                   │
│ Action: Fork/Edit copies ONLY Part 1 to workspace. Part 2 stays in vault.   │
│ LogKeeper: Logs "ArtifactForkedToWorkspace" (confirms Part 2 omitted).      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ MINI-PHASE 5: HARDENED REPO EXPORT (GitHub, GDrive, External Storage)       │
│ Files: GithubExportBottomSheet.kt / BackupManager.kt                        │
│ Action: Export sanitization filter ensuring ONLY Part 1 files are zipped.   │
│ LogKeeper: Logs "ExportInitiated" (target: GITHUB/GDRIVE/STORAGE, 0 secrets)│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Technical Specifications by Mini-Phase

### 🟢 Mini-Phase 1: Thread Settings Tabs (`Integrations 🔌` & `Secrets 🔑`)
- **Target File**: `app/src/main/java/com/example/ui/settings/ThreadSettingsScreen.kt`
- **Enum Extension**:
  ```kotlin
  enum class ThreadSettingTab(val title: String) {
      UNIVERSAL("Universal"),
      AGENTS("Agents"),
      VERSIONS("Versions"),
      GITHUB("GitHub"),
      INTEGRATIONS("Integrations 🔌"),
      SECRETS("Secrets 🔑")
  }
  ```
- **Integrations Tab Component**:
  - Preset cards with status toggles:
    - GitHub API (Personal Access Token / Octokit)
    - Cloudflare Workers / KV
    - Supabase (URL & Anon Key)
    - Custom REST Webhooks (Endpoint & Header keys)
- **Secrets Tab Component**:
  - Dynamic key-value table:
    - Key Name TextField (e.g. `GITHUB_TOKEN`)
    - Masked Secret TextField (`PasswordVisualTransformation()`, togglable visibility icon)
    - Add New Secret row / Delete button.
- **Storage Strategy**:
  - Stored in Android private storage: `context.getSharedPreferences("prefs_thread_secrets_$workspaceId", Context.MODE_PRIVATE)`.
  - Stored completely outside the `/workspaces/<workspace_id>/` filesystem.
- **LogKeeper Audit**:
  ```kotlin
  LogKeeper.log("ThreadSettings", "SecretsUpdated", "Workspace $workspaceId updated ${keys.size} secrets")
  ```

---

### 🟢 Mini-Phase 2: In-App Preview Blind Injection
- **Target File**: `app/src/main/java/com/example/ui/chat/PWAPreviewBottomSheet.kt`
- **Mechanism**:
  - On `WebViewClient.onPageStarted` or via `evaluateJavascript` before user scripts execute:
  ```kotlin
  val secretsMap = loadDecryptedSecrets(workspaceId)
  val secretsJson = JSONObject(secretsMap).toString()
  webView.evaluateJavascript("""
      window.__SECRETS__ = Object.freeze($secretsJson);
  """.trimIndent(), null)
  ```
- **Security Guardrail**:
  - `Object.freeze` prevents web scripts from mutating the secret values during the session.
  - The AI writes standard JavaScript relying on the global:
    ```javascript
    const token = window.__SECRETS__?.GITHUB_TOKEN;
    fetch("https://api.github.com/user/repos", {
        headers: { "Authorization": `Bearer ${token}` }
    });
    ```
- **LogKeeper Audit**:
  ```kotlin
  LogKeeper.log("PWAPreview", "BlindInjectionSuccess", "Injected ${secretsMap.size} runtime secrets into WebView")
  ```

---

### 🟢 Mini-Phase 3: The 2-Part Artifact Saver (Global Sidebar)
- **Target File**: `app/src/main/java/com/example/engine/fs/ArtifactWorkspaceManager.kt`
- **Execution Flow on `saveCurrentChatAsArtifact()`**:
  1. **Part 1 (The Repo)**:
     - Copies `index.html`, `style.css`, `app.js`, `manifest.json`, and all assets from `/workspaces/<wsId>/` into `/files/artifacts/<artifactId>/repo/`.
  2. **Part 2 (The Secrets Bundle)**:
     - Reads decrypted secrets from `prefs_thread_secrets_<wsId>`.
     - Encrypts via `ArtifactKeyStore.encryptData(secretsJson.toByteArray())` using hardware-backed AES-256-GCM.
     - Saves either to an encrypted file `/files/artifacts/<artifactId>/secrets_bundle.enc` or stores directly in Room DB `ArtifactEntity.encryptedSecretsBundle`.
  3. **Note**: The secrets bundle is **only created at the moment of saving to Artifacts**. It never touches the chat workspace.
- **LogKeeper Audit**:
  ```kotlin
  LogKeeper.log("ArtifactManager", "ArtifactCreated", "Artifact $artifactId created: Part 1 (${fileCount} files), Part 2 encrypted")
  ```

---

### 🟢 Mini-Phase 4: Blind Fork & Edit (Withholding Part 2 from AI)
- **Target Files**: `app/src/main/java/com/example/ui/artifacts/ArtifactsScreen.kt`, `app/src/main/java/com/example/engine/fs/ArtifactWorkspaceManager.kt`
- **Execution Flow on `[✏️ Edit / Fork]`**:
  1. Generates a new chat workspace ID (`fork_ws_<timestamp>`).
  2. Copies **Part 1 (The Repo)** from `/files/artifacts/<artifactId>/repo/` into `/workspaces/<fork_ws_id>/`.
  3. **Part 2 is omitted from the copy**: No secret files are written to the new workspace directory.
  4. Decrypts Part 2 in-memory and populates the private `prefs_thread_secrets_<fork_ws_id>` preferences so the user can immediately preview and test the forked app.
  5. The AI agent inspecting the workspace with `list_dir` or `view_file` only sees pure code. It remains physically incapable of leaking credentials.
- **LogKeeper Audit**:
  ```kotlin
  LogKeeper.log("ArtifactManager", "ForkArtifact", "Artifact $artifactId forked to workspace $forkWsId (Part 1 only; Part 2 withheld from workspace)")
  ```

---

### 🟢 Mini-Phase 5: Hardened Repo Export (GitHub, GDrive, External Storage)
- **Target Files**: `app/src/main/java/com/example/ui/export/GithubExportBottomSheet.kt`, `app/src/main/java/com/example/engine/backup/BackupManager.kt`
- **Sanitization Engine**:
  - When committing to GitHub or exporting a ZIP:
    - **Included**: All files from **Part 1 (The Repo)**.
    - **Excluded**: Any file matching `*.enc`, `.env`, `*secret*`, or outside private app directories.
    - **Template Injection**: Generates an empty `secrets.example.json` containing key names without values:
      ```json
      {
        "GITHUB_TOKEN": "",
        "CLOUDFLARE_API_KEY": ""
      }
      ```
- **LogKeeper Audit**:
  ```kotlin
  LogKeeper.log("ExportManager", "ExportSuccess", "Target: $destination, Exported: Part 1 only (${exportedFiles.size} files, 0 secrets)")
  ```

---

## 5. Security Scan & Credential Immunity Compliance

- **No Hardcoded Keys**: Zero keys or secrets are stored in code, build configurations, or git-tracked files.
- **Physical Segregation**: Workspace directories accessed by AI tools are strictly separated from encrypted app credential storage.
- **LogKeeper Sanitization**: LogKeeper only receives counts, component names, and lifecycle signals. It never receives credential strings.
- **Tamper Resistance**: Part 2 is encrypted using Android KeyStore hardware backing (`MasterKeyProvider` / AES-256-GCM).
