# Web AI Hub & Multi-Tab Browser Chat Architecture Plan

## Executive Summary
This document defines the architecture and implementation roadmap for the **Web AI Hub** (Browser-Style Chat).
Unlike the primary Engine Mode (which combines native API routing and on-device local LLM execution), the Web AI Hub enables users to run genuine web AI platforms (ChatGPT, Claude, Perplexity, Google Gemini, DeepSeek, Grok, etc.) inside **Chrome Custom Tabs (CCT)**.

### Key Architectural Pillars
1. **100% Authentication Compatibility**: By using Chrome Custom Tabs on the device's system browser engine, Google OAuth, Apple ID, Microsoft, Passkeys, and CAPTCHAs work seamlessly without the `403 disallowed_useragent` blocks found in WebViews.
2. **Dedicated Mode in Omnivian**: The Web AI Hub replaces the central chat and input area while keeping the **Global Sidebar fully accessible**.
3. **Sidebar Pinned Placement**: Positioned permanently at the very top of the chat thread list in `GlobalSidebar.kt`.
4. **App Freeze / Low-Power Sleep**: Automatically unloads on-device GGUF/llama weights from RAM and pauses background sync/embeddings when Web AI mode is active to guarantee maximum device memory for the browser engine.
5. **Clean Dashboard Portal with Multi-Select**: A dashboard showing configured services where users can check multiple AIs and launch them simultaneously.
6. **Chrome-Style Bottom Strip in Custom Tabs**:
   - Displays **only service icons** and **close `(x)`** touch targets.
   - Smooth horizontal scrolling for opened tabs.
   - Highlights the active tab.
   - Features a `[+]` button at the end to add another service or account on the fly.
7. **Multi-Account Support (No AI Models Needed)**: Accounts are managed in Global Settings under "Web AI Providers" with support for isolated/ephemeral sessions or target browser package routing.

---

## Mini-Phase Breakdown

```
Mini-Phase 1: Data Model & Global Settings (Web AI Providers & Account Management)
     │
     ▼
Mini-Phase 2: Global Sidebar Pinned Entry & Background Sleep/Throttling Engine
     │
     ▼
Mini-Phase 3: Web AI Portal Screen (Clean Dashboard & Multi-Select Launch)
     │
     ▼
Mini-Phase 4: Custom Tabs Engine with RemoteViews Bottom Tab Strip (Icon + Close + Scroll + [+])
     │
     ▼
Mini-Phase 5: Dynamic In-Tab Switching, Multi-Account Isolation & [+] Quick Adder
```

---

### Mini-Phase 1: Data Model & Global Settings (Web AI Providers & Accounts)

* **Goal**: Establish the storage and settings configuration for Web AI providers and account profiles. No model definitions required.
* **Target Files**:
  * `app/src/main/java/com/example/engine/webaiprovider/WebAiProviderModel.kt`
  * `app/src/main/java/com/example/engine/webaiprovider/WebAiProviderManager.kt`
  * `app/src/main/java/com/example/ui/settings/WebAiProviderSettingsScreen.kt`
  * `app/src/main/java/com/example/ui/settings/GlobalSettingsDialog.kt`
* **Tasks**:
  1. Define data models:
     - `WebAiService(id, name, baseUrl, iconRes, brandColor, enabled, profiles)`
     - `WebAiAccountProfile(id, serviceId, label, launchUrl, sessionMode, targetPackage)`
     - `WebAiSessionMode`: `DEFAULT` (shared browser cookies), `EPHEMERAL` (isolated/incognito), `CUSTOM_BROWSER`.
  2. Implement `WebAiProviderManager` with pre-seeded providers:
     - ChatGPT (`https://chatgpt.com`)
     - Claude (`https://claude.ai`)
     - Perplexity (`https://perplexity.ai`)
     - Google Gemini (`https://gemini.google.com`)
     - DeepSeek (`https://chat.deepseek.com`)
     - Grok (`https://grok.com`)
  3. Create `WebAiProviderSettingsScreen.kt` in Global Settings to enable/disable services, add custom URLs, and configure account profiles with custom badges.
  4. Link to `GlobalSettingsDialog.kt` under "Web AI Providers".

---

### Mini-Phase 2: Global Sidebar Pinned Entry & Background Sleep/Throttling Engine

* **Goal**: Pin the Web AI Hub at the very top of the chat list and build the memory-freeze engine that suspends heavy background tasks.
* **Target Files**:
  * `app/src/main/java/com/example/engine/lifecycle/HeavyTaskThrottler.kt`
  * `app/src/main/java/com/example/ui/chat/GlobalSidebar.kt`
  * `app/src/main/java/com/example/ui/chat/ChatViewModel.kt`
* **Tasks**:
  1. Build `HeavyTaskThrottler.kt`:
     - `suspendHeavyTasks()`: Releases on-device GGUF/llama.cpp model weights from RAM, stops active media playback, pauses background vector indexing/database sync, and calls `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL`.
     - `resumeHeavyTasks()`: Restores light services when returning to engine mode.
  2. Update `GlobalSidebar.kt`:
     - Place a pinned item at the top of the chat thread list: **Web AI Hub** (with browser icon and active open-tabs count badge).
     - Selecting it triggers `suspendHeavyTasks()` and navigates the main screen to the Web AI Portal.

---

### Mini-Phase 3: Web AI Portal Screen (Clean Dashboard & Multi-Select Launch)

* **Goal**: Build the main portal view that replaces the chat UI when Web AI Mode is selected.
* **Target Files**:
  * `app/src/main/java/com/example/ui/webaiprovider/WebAiPortalScreen.kt`
  * `app/src/main/java/com/example/MainActivity.kt`
* **Tasks**:
  1. Implement `WebAiPortalScreen.kt`:
     - Top Bar: Global Sidebar hamburger icon, Title "Web AI Hub", and settings shortcut.
     - Main Body: Clean, responsive grid of configured Web AI cards showing service icons, account names, and status chips.
     - Multi-Selection: Tapping cards toggles checkmarks/selection for batch opening.
     - Bottom Docked Bar: "Launch Selected (N)" primary button and "Resume Active Session" button if tabs are already running.
  2. Connect navigation route in `MainActivity.kt` to swap between Native Chat and `WebAiPortalScreen`.

---

### Mini-Phase 4: Custom Tabs Engine with RemoteViews Bottom Tab Strip

* **Goal**: Launch Chrome Custom Tabs with a custom RemoteViews bottom toolbar displaying icon-only bubbles, close buttons, horizontal scroll, and `[+]`.
* **Target Files**:
  * `app/src/main/res/layout/custom_tab_bottom_strip.xml`
  * `app/src/main/res/layout/custom_tab_bubble_item.xml`
  * `app/src/main/java/com/example/engine/webaiprovider/WebAiCustomTabManager.kt`
* **Tasks**:
  1. Build RemoteViews XML layout for Custom Tab bottom bar:
     - HorizontalScrollView containing a row of icon bubbles.
     - Each bubble shows: Service Brand Icon, Close `(x)` target, and active highlight ring.
     - Pinned `[+]` button at the end of the scroll list.
  2. Implement `WebAiCustomTabManager.kt`:
     - Binds `CustomTabsServiceConnection`, initializes session warmup (`warmup()`), and pre-renders selected URLs (`mayLaunchUrl()`).
     - Builds `CustomTabsIntent` with `setSecondaryToolbarViews(...)` / `setRemoteViews(...)`.
     - Associates `PendingIntent`s with each tab icon, close button, and the `[+]` add button.

---

### Mini-Phase 5: Dynamic In-Tab Switching, Multi-Account Isolation & `[+]` Quick Adder

* **Goal**: Handle interactions from the bottom strip (switching, closing, adding) and enforce account profile isolation.
* **Target Files**:
  * `app/src/main/java/com/example/engine/webaiprovider/WebAiTabBroadcastReceiver.kt`
  * `app/src/main/java/com/example/engine/webaiprovider/WebAiCustomTabManager.kt`
  * `app/src/main/java/com/example/ui/webaiprovider/WebAiQuickAddDialog.kt`
* **Tasks**:
  1. Implement `WebAiTabBroadcastReceiver.kt`:
     - **On Tab Click**: Updates active tab ID, redirects the current Custom Tab session URL, and refreshes the RemoteViews highlight.
     - **On Close `(x)` Click**: Removes the tab from the active session; dismisses Custom Tab if all tabs are closed.
     - **On Add `(+)` Click**: Opens a lightweight dialog/overlay allowing the user to select another service or account to append to the strip.
  2. Implement Account Isolation:
     - For `EPHEMERAL` accounts, apply `CustomTabsIntent.EXTRA_ENABLE_EPHEMERAL_BROWSING`.
     - For `CUSTOM_BROWSER` profiles, target specific installed browser packages (e.g., Brave or Chrome Work).

---

## Verification & Guardrails

* **Google Login Verification**: Test authentication against Google OAuth across all supported web providers inside the Custom Tab session.
* **Memory Footprint Check**: Verify with Android Profiler that entering Web AI Mode suspends GGUF/llama background memory allocations.
* **Interaction Verification**: Test horizontal scrolling of the tab strip, switching between active tabs without losing page state, closing individual tabs, and adding new tabs via the `[+]` button.
