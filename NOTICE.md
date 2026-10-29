# Third-party notices

## Bark server

- Upstream: https://github.com/Finb/bark-server/tree/v2.3.9
- License: MIT, copyright 2018 Feng. Full text: `upstream/bark-server-2.3.9/LICENSE`.
- This repository includes the relevant Go source and dependency lockfile from v2.3.9.
- Local changes: force IPv4 listening for Android deployment; remove URL and body from access logs; save the latest registered device key into the app-private state directory when `BARK_ANDROID_STATE` is set.
- Go dependencies retain their own licenses and are resolved through `go.mod` / `go.sum`.
- Upstream publicly distributes APNs provider material for the official Bark app. This repository does not redistribute `apns/apns_certs.go`: `prepare-dependencies.mjs` obtains it directly from the official v2.3.9 source and verifies SHA-256 `a6f21221c70cd7a3cddfd30aaa59a5f8ab300465f8065fb273cb1b12bce30c82`. It is ignored by Git and is not a personal device token or this maintainer's signing credential.

## SmsForwarder

- Upstream: https://github.com/pppscn/SmsForwarder/releases/tag/v3.5.0
- License: BSD-2-Clause, copyright 2021 pppscn. Full text: `third_party/LICENSE.SmsForwarder`.
- The assistant bundles the unmodified official ARM64 APK obtained by `prepare-dependencies.mjs`, pinned to SHA-256 `726918cc6132728ffcadf36e09cc39213875c63dfabdb50ade3f47854364f319`.
- Source: https://github.com/pppscn/SmsForwarder/tree/v3.5.0
- The bundled APK remains a separate application; this repository does not relicense its code or dependencies.

Both upstream licenses are packaged as Android assets. The assistant name and icon do not imply endorsement by the upstream projects.
