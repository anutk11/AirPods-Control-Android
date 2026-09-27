# AirPods Control for Android

Experimental Android app for AirPods control over Bluetooth Classic L2CAP/AACP.

## Goal

- Establish Classic Bluetooth L2CAP connection to AACP PSM `0x1001`
- Perform the LibrePods-compatible AACP handshake
- Verify handshake acknowledgement before sending commands
- Implement AirPods controls incrementally, including listening modes and rename
- Build installable APKs with GitHub Actions

This project uses LibrePods as a protocol reference. It does not claim every AirPods generation or Android Bluetooth stack supports every feature.

## Current status

Initial repository created. The next implementation milestone is the Classic L2CAP transport and handshake.
