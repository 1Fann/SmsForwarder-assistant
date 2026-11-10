# SmsForwarder Assistant

[中文](README.md) | English

Host Bark on Android, optionally using SmsForwarder to forward incoming SMS to iPhone.

[Source releases](https://github.com/1Fann/SmsForwarder-assistant/releases/latest) · [Release notes](https://github.com/1Fann/SmsForwarder-assistant/releases)

## Features

- Run Bark independently without requiring the SMS forwarder.
- Generate iPhone pairing details and send test notifications.
- Optionally install SmsForwarder and configure forwarding rules.
- Notification retries, SMS reconciliation and optional background recovery.

## Getting started

Requires Android 8+, ARM64 and Bark on the receiving iPhone. Automatic SMS setup, permission repair and independent monitoring require root.

Releases provide source ZIP/tar.gz archives, not installable APKs. Build the app using the project instructions, then grant permissions and configure your account locally. Retain the same signing identity for in-place upgrades.

## Documentation

- [Reliability and validation](docs/RELIABILITY.md)
- [Third-party notices](NOTICE.md)

## License

The assistant is [MIT-licensed](LICENSE); Bark, SmsForwarder and other components retain their licenses.
