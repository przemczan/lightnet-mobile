---
icon: material/cellphone
---

# App

The Lightnet mobile app is a Kotlin Multiplatform project — one Compose UI shared between Android and iOS — that talks to a Lightnet controller over the binary WebSocket and JSON HTTP APIs.

!!! tip "First time?"
    If you just want to use it, the hub's **[Get Started → Use the app](../getting-started/using-the-app.md)** page is the place to start. The pages below are the developer-facing reference.

<div class="grid cards" markdown>

-   :material-information-outline: **Overview**

    ---

    Purpose, architecture, platform support, and the dependency stack.

    [:material-arrow-right: Overview](overview.md)

-   :material-rocket-launch-outline: **Getting Started**

    ---

    Clone, build, install, and run on Android or iOS. Includes the demo-device shortcut for development without hardware.

    [:material-arrow-right: Getting Started](getting-started.md)

-   :material-code-tags: **Development**

    ---

    Package layout, the device domain layer, the Compose visualiser, and key conventions.

    [:material-arrow-right: Development](development.md)

-   :material-wifi: **Connectivity**

    ---

    mDNS discovery, the binary WebSocket framing, and the `MockConnector` test harness.

    [:material-arrow-right: Connectivity](connectivity.md)

</div>

---

[:fontawesome-brands-github: przemczan/lightnet-mobile](https://github.com/przemczan/lightnet-mobile){ .md-button }
