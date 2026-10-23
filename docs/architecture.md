# Architecture

## Overview

Earwig (a fork of Voice) is built on a **modular, layered architecture**. The design separates infrastructure, shared domain logic, and user-facing features.
This modularization improves build times, testability, and maintainability, while keeping feature ownership clear.

At a high level:

* **Infrastructure modules** provide the app entry point, build tooling, and navigation.
* **Core modules** encapsulate reusable domain and service logic (playback, scanning, data, logging, etc.).
* **Feature modules** implement user-facing screens, composed from core logic and UI components.
* **Feature flags** are defined per-feature and implemented via `:core:featureflag`, sourced through remote config.

## Layers

### Infrastructure

* `:app` – Main application entry point, dependency injection setup
* `:navigation` – Navigation framework abstractions and route definitions
* `:plugins` – Gradle build plugins for consistency and convention
* `:scripts` – Build and utility scripts

### Core (Shared Logic)

Core modules provide the underlying services and abstractions:

* **UI & Theming**

  * `:core:ui` – Shared UI components, typography, colors, Material 3 theming

* **Data & Storage**

  * `:core:data:api` – Interfaces for repositories and data sources
  * `:core:data:impl` – Implementations for Room, network, or file access
  * `:core:documentfile` – File system abstractions

* **Playback & Media**

  * `:core:playback` – Audio playback logic using ExoPlayer
  * `:core:sleeptimer:api` & `:core:sleeptimer:impl` – Sleep timer contracts and implementation
  * `:core:transcript` – Transcript JSON parsing, the per-book files folder, and time-window lookups
  * `:core:ai` – Clients for the listener's own API keys: Claude (answers, research, Extras), Deepgram
    (transcription) and Ideogram (pictures and covers). Keys are typed into Settings and kept on the device
  * `:core:companion` – A book's guided setup and everything it produces, stored as JSON per book: Deepgram
    transcription in resumable parts, online research, idea cards and the listener's own idea check, shelf
    Extras (sandboxed HTML pages and the bridge they call), scene chips, and the book's look (cover colors or a
    generated cover). Setup jobs run in WorkManager so they outlive the screen
  * `:core:copilot` – The pipeline behind every AI action (Ask, What's happening now, Recap, Snip), shared by
    the headset buttons (`:core:playback`) and the book chat (`:features:playbackScreen`). Answers come from
    Claude, grounded in the transcript up to the listener's position, and are read out with the phone's
    text-to-speech

* **Utility Modules**

  * `:core:scanner` – File scanning and metadata extraction
  * `:core:search` – Search logic
  * `:core:strings` – Localized string resources

* **Cross-Cutting Concerns**

  * Logging: `:core:logging:core`, `:core:logging:crashlytics`, `:core:logging:debug`
  * Feature Flags: `:core:featureflag` – Feature flag abstractions backed by remote config
  * Remote Config: `:core:remoteconfig:core`, `:core:remoteconfig:firebase`, `:core:remoteconfig:noop`

### Features

Feature modules are screen- or flow-based. Each module owns its UI (Compose) and presentation logic, while delegating to `:core` modules for
data and services:

* `:features:playbackScreen` – Main playback interface, scene chips and their cards, the book chat
* `:features:bookOverview` – Library / book list, and the add-a-book button that starts a book's setup
* `:features:sleepTimer` – Sleep timer control UI
* `:features:settings` – App settings
* `:features:folderPicker` – Folder selection flow
* `:features:cover` – Cover art management
* `:features:onboarding` – First-time user flow
* `:features:bookmark` – The Notebook: snips and the Extras shelf
* `:features:bookSetup` – A book's guided setup: transcription progress, research, idea cards, own ideas, look
* `:features:extras` – The viewer for one shelf Extra, a sandboxed WebView backed by `:core:companion`
* `:features:widget` – Homescreen widget support
* `:features:review:play` & `:features:review:noop` – App review prompts

## Dependency Flow

* Features depend **only on `:core` and infrastructure abstractions**.
* `:core` modules depend on each other as needed, but never on features.
* Infrastructure modules (`:app`, `:navigation`) wire everything together at runtime.

This ensures **unidirectional dependency flow**:

```
Infrastructure → Core → Features
```

## Diagram

````mermaid
flowchart LR
    subgraph Infrastructure
        app(":app")
        navigation(":navigation")
    end

    subgraph Core
        core(":core")
    end

    subgraph Features
        features(":features")
    end

    app --> navigation
    app --> features
    features --> core
    features --> navigation
````

## Tech Decisions

* **Compose (UI)** – Declarative UI with Material 3 for consistency and accessibility
* **Metro (DI)** – Lightweight dependency injection across modules
* **Navigation3** – Type-safe, modular navigation
* **ExoPlayer (Media3)** – Robust audio playback engine
* **Room** – Persistent storage
* **Kotlin Serialization** – JSON parsing and object serialization
* **Coil** – Efficient image loading

## Module Lifecycle

1. **Add new functionality as a feature module.**
2. **Extract reusable logic into `:core` modules** once multiple features need it.
3. **Keep infrastructure minimal** — mainly for wiring and build configuration.
