# ReplyFloat AI (v1.5)

**ReplyFloat AI** is an intelligent, floating contextual assistant for Android. It monitors incoming questions across messaging apps and games in real time using a dual-engine detection pipeline, queries AI models through an automated multi-provider failover chain, and surfaces suggested replies via an unobtrusive floating overlay.

---

## 🌟 Key Capabilities

- **⚡ Dual-Engine Question Detection**:
  - **Fast Primary Path (Accessibility Node Scan)**: Scans view hierarchy text nodes in ~3–5ms for standard apps (WhatsApp, Telegram, Discord, Slack, SMS, Browsers).
  - **On-Device OCR Fallback (ML Kit)**: Automatically triggers continuous screen analysis on a background thread (~35–80ms) when apps draw directly to custom canvases, OpenGL, or Unity surfaces with zero accessibility nodes (e.g., *Super Sus* chat, games).
- **🛡️ FLAG_SECURE & Window Protection Awareness**:
  - Automatically identifies when a target window (such as a Virtual Machine container or anti-cheat protected game) enforces Android's `FLAG_SECURE`.
  - Distinguishes OS-level blank/black frame restrictions from app bugs and logs clear diagnostics: `Screen capture blocked by target app (FLAG_SECURE) — OCR unavailable for this app`.
- **🔄 Resilient Multi-Provider AI Fallback Chain**:
  - Automatically fails over if an API quota is exhausted (HTTP 429), authentication fails (HTTP 401), or a server errors.
  - Supports:
    - **Google Gemini** (Gemini 2.5 Flash, Gemini 2.5 Pro, Flash-Lite) with dynamic model selection.
    - **OpenAI** (GPT-4o, GPT-4o-mini).
    - **Anthropic Claude** (Claude 3.5 Sonnet, Claude 3.5 Haiku).
    - **Ollama** (Local self-hosted LLM endpoints).
    - **Built-in Offline Heuristics** (Always available fallback).
- **🎯 Floating Overlay System**:
  - Draggable floating head that stays accessible across any running application.
  - Interactive suggested reply chips with one-tap copy and quick dismiss.
- **📊 Real-Time Diagnostics & Simulator Bench**:
  - Live inspection of detection events, latency metrics, and API health status.
  - Filter logs by: *All, Failovers, Matched, OCR Fallback, Accessibility, Rejected*.
  - Built-in simulator to test normal messaging, game chat OCR, and `FLAG_SECURE` protected window scenarios without needing external apps running.
- **🔒 App Whitelist & Privacy**:
  - Define precisely which apps ReplyFloat AI monitors.
  - All text parsing and OCR occur 100% locally on-device. No screen content is ever uploaded or stored externally.

---

## 🏗️ Detection Architecture

```
                       Incoming On-Screen Content
                                   │
                                   ▼
                    Accessibility Node Hierarchy Scan
                                   │
                    ┌──────────────┴──────────────┐
                    │                             │
            Nodes Found (>0)               Zero Nodes Found
                    │                     (Canvas / Game UI)
                    ▼                             │
         Fast Text Extraction (~3ms)              ▼
                    │                   Continuous Screen Capture
                    │                             │
                    │               ┌─────────────┴─────────────┐
                    │               │                           │
                    │         Normal Frame             Blank / Black Frame
                    │               │                    (FLAG_SECURE)
                    │               ▼                           │
                    │      On-Device ML Kit OCR                 ▼
                    │       Text Recognition (~50ms)     Diagnostic Event:
                    │               │                   "FLAG_SECURE Blocked"
                    └───────────────┬───────────────────────────┘
                                    │
                                    ▼
                         Question Analysis Engine
                         (Interrogative Pattern Match)
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
                Question                        Non-Question
                    │                               │
                    ▼                               ▼
          AI Provider Chain                  Ignored (No Overlay)
         (Gemini/OpenAI/Claude)
                    │
                    ▼
          Floating Reply Chips
```

---

## 📱 Getting Started & Permissions Setup

ReplyFloat AI requires two core Android system permissions to operate across applications:

### 1. Overlay Permission (`SYSTEM_ALERT_WINDOW`)
- Required to display the floating assistant bubble and suggested reply chips over other apps.
- Go to **Settings > Apps > Special App Access > Display over other apps**, or tap **Grant Permission** directly inside the ReplyFloat AI status card.

### 2. Accessibility Service
- Required to monitor on-screen text nodes and invoke the screenshot API for on-device OCR fallback.
- Go to **Settings > Accessibility > Downloaded Apps > ReplyFloat AI** and toggle the service **ON**.

### 3. API Key Configuration
- Navigate to the **Providers** tab in the bottom navigation bar.
- Enter your API key for your preferred provider (Gemini, OpenAI, or Anthropic Claude).
- Select your preferred model (e.g., Gemini 2.5 Flash for speed or Gemini 2.5 Pro for complex reasoning).
- If no external API key is entered, ReplyFloat AI seamlessly utilizes built-in offline heuristics.

---

## 🎮 Game & Custom Canvas Compatibility

Standard accessibility services cannot read text rendered in Unity, Unreal, or OpenGL game surfaces (such as in *Super Sus*). ReplyFloat AI addresses this via its **Continuous Screen Analyze** engine:
1. When in a whitelisted game, the background monitor continuously captures frames.
2. Google ML Kit performs on-device Latin text recognition entirely on `Dispatchers.Default` (background thread), keeping gameplay at full frame rate.
3. If an emergency meeting or chat question is detected (e.g., *"Where were you when the body was found?"*), floating reply chips appear immediately.
4. **Note on FLAG_SECURE**: Certain banking apps, Virtual Machine containers, and games with anti-tamper modules apply `WindowManager.LayoutParams.FLAG_SECURE`. In these apps, the Android OS intentionally blacks out screen captures at the driver level. ReplyFloat AI automatically detects this condition and logs an informative diagnostic notice.

---

## 🛠️ Diagnostics & Simulator

The app includes two built-in developer tools:
- **Simulator Tab**:
  - Test question detection using the pre-configured WhatsApp scenario.
  - Test game chat parsing using the Super Sus emergency meeting scenario.
  - Test `FLAG_SECURE` window protection behavior with a dedicated simulation trigger.
- **Diagnostics Tab**:
  - Review active provider health, recent latency benchmarks, error statuses (e.g., HTTP 401, 429), and failover logs.
  - Export or clear logs at any time.

---

## 💻 Tech Stack & Requirements

- **Platform**: Android 11+ (API Level 30+ for OCR screen capture, minSdk 26)
- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose with Material Design 3
- **Vision Engine**: Google ML Kit Text Recognition (Latin script, bundled on-device)
- **Networking**: Ktor / Kotlinx Coroutines & Serialization
- **Architecture**: MVVM with centralized `AppStateManager` & unidirectional state flows
