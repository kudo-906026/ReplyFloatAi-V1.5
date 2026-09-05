# ReplyFloat AI (v1.5)

**ReplyFloat AI** is an intelligent, floating contextual assistant for Android. It monitors incoming questions across messaging apps and games in real time using a dual-engine detection pipeline, queries AI models through an automated multi-provider failover chain, and surfaces suggested replies via an unobtrusive floating overlay.

---

## 🌟 Key Capabilities

- **⚡ Dual-Engine Question Detection**:
  - **Fast Primary Path (Accessibility Node Scan)**: Scans view hierarchy text nodes in ~3–5ms for standard apps (WhatsApp, Telegram, Discord, Slack, SMS, Browsers).
  - **On-Device OCR Fallback (ML Kit)**: Automatically triggers continuous screen analysis on a background thread (~35–80ms) when apps draw directly to custom canvases, OpenGL, or Unity surfaces with zero accessibility nodes (e.g., *Super Sus* chat, games).
- **🛡️ Capture Telemetry & Driver Compatibility**:
  - Automatically captures hardware buffers via Android 11+ Accessibility Screenshot API with automatic ARGB_8888 software conversion and Canvas fallbacks.
  - Transparently logs raw capture results, frame buffer dimensions, error codes, and extracted text blocks in real-time Diagnostics without false assumptions.
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
- **📊 Real-Time Diagnostics & Telemetry**:
  - Live inspection of detection events, latency metrics, and API health status.
  - Filter logs by: *All, Failovers, Matched, OCR Fallback, Accessibility, Rejected*.
  - Deep telemetry for screenshot capture, pixel buffer verification (FLAG_SECURE detection), and raw ML Kit OCR output.
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
4. **Diagnostic Logging**: The real-time Diagnostics tab displays exact capture telemetry for every attempt—including hardware buffer dimensions, conversion status, system error codes, and raw ML Kit extracted text blocks.

---

## 🔓 Bypassing FLAG_SECURE (Root & Virtual Machine Setup)

If target games or apps enforce `FLAG_SECURE` (causing black/blank screenshot buffers in Diagnostics), you can remove this restriction and use ReplyFloat AI freely using either a rooted phone or a rooted virtual machine container:

### Method 1: Rooted Physical Phone
1. **Root your device**: Using Magisk, KernelSU, or APatch.
2. **Install LSPosed**: Flash the Zygisk-LSPosed framework module.
3. **Install "Disable FLAG_SECURE"**: Search and download the *Disable-FLAG_SECURE* Xposed module from the LSPosed repository.
4. **Activate & Reboot**: Enable the module in LSPosed (scope set to System Framework or target app) and restart your phone.
5. **Use Freely**: Android OS will no longer blank out the screenshot buffer, allowing continuous OCR to capture game chat text and surface floating smart replies without restriction.

### Method 2: Virtual Machine (No Physical Root Required)
If you prefer not to modify or root your primary physical device:
1. **Install an Android Virtual Machine App**: Download a VM environment such as **VMOS Pro**, **VPhoneGaGa**, **F1 VM**, or **TwoYi**.
2. **Enable Root in VM**: Turn on root permissions inside the virtual machine's settings menu.
3. **Install LSPosed inside VM**: Set up the LSPosed framework within the virtual Android instance.
4. **Install & Enable "Disable FLAG_SECURE"**: Add the *Disable FLAG_SECURE* module inside LSPosed in the VM.
5. **Install Target Game & ReplyFloat AI**: Run both the game (e.g. *Super Sus*) and ReplyFloat AI inside the VM container to use OCR and floating smart replies freely.

---

## 🖥️ Desktop Usage & Source Code Download

For desktop users, developers, and testing on emulators:
- **Download the Source Code**: You can download the complete project as a ZIP archive or clone the repository directly to your computer using the AI Studio project settings menu.
- **Android Studio & Emulators**:
  - Open the project in Android Studio (Giraffe, Hedgehog, Koala, Ladybug, or newer with JDK 17).
  - Run the application on standard Android Studio Virtual Devices (AVD), Genymotion, or Android desktop emulators (e.g., BlueStacks, LDPlayer, Nox).
  - Desktop emulators often support root privileges and disabling `FLAG_SECURE` directly in their system settings, making development, game chat OCR testing, and AI fallback experimentation fast and flexible.

---

## 🛠️ Diagnostics

The app includes built-in inspection and telemetry tools:
- **Diagnostics Tab**:
  - Review active provider health, recent latency benchmarks, error statuses (e.g., HTTP 401, 429), and failover logs.
  - Inspect on-device screenshot capture status, pixel buffer verification, system error codes, and raw ML Kit OCR text output.
  - Filter, inspect, and export or clear logs at any time.

---

## 💻 Tech Stack & Requirements

- **Platform**: Android 11+ (API Level 30+ for OCR screen capture, minSdk 26)
- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose with Material Design 3
- **Vision Engine**: Google ML Kit Text Recognition (Latin script, bundled on-device)
- **Networking**: Ktor / Kotlinx Coroutines & Serialization
- **Architecture**: MVVM with centralized `AppStateManager` & unidirectional state flows
