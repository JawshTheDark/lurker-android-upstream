# Lurker for Android

A native Android client for [Lurker](https://github.com/amiantos/lurker) — Kotlin + Jetpack Compose,
built to feel like a modern messaging app rather than a terminal. It talks to a Lurker bouncer server
over its WebSocket + REST contract, and it also ships a **standalone direct-IRC mode** so it works
even without a Lurker server at all.

> **Status: release-candidate.** What began as a spike to prove Lurker's bearer-token contract could
> drive a native client has grown into a daily driver: signed-in persistence, resilient reconnect,
> formatted chat, member lists, uploads, search, DCC, notifications, and a full second backend that
> speaks raw IRC and bouncers. Actively used against production servers and the hosted app.

## Two ways to run it

**Lurker mode** — connect to a Lurker server, which stays connected to IRC for you (a true bouncer:
the client never touches IRC; the server does parsing, TLS, SASL, reconnect, history, highlight
matching, and ignore filtering). Works with:

- **Hosted [lurker.chat](https://lurker.chat)** — sign in with your email; the token is minted at the
  control plane and all traffic is proxied to your cell transparently.
- **Self-hosted Lurker** — sign in with your username and password against your own server.

**Direct mode** — a standalone IRC client with no Lurker server required. Powered by
[KICL](https://github.com/KittehOrg/KittehIRCClientLib) (Netty, IRCv3), it connects **straight to IRC
networks** or to a **bouncer** (soju / ZNC). SASL, multi-network, cap negotiation, and auto-reconnect
are all handled on-device; secrets are encrypted at rest. Connecting to a bouncer gets much of the
always-on benefit back, since the bouncer stays connected upstream. First-class support for soju's
`bouncer-networks` means one login can surface all your upstream networks as separate entries.

Pick the mode on first launch; it can be switched later from Settings.

## Features

- **Stays signed in.** The session is saved to app-private storage and reconnects silently on launch.
  Optional biometric / device-credential lock on open.
- **Resilient.** Auto-reconnect with backoff; on return from background it resumes with `?since=` so
  nothing is missed. An optional foreground service holds the connection open for reliable
  notifications.
- **Reads like a chat app.** iMessage-style bubbles with sender grouping, mIRC color + formatting
  (bold / italic / underline / mono), tappable links, and inline system lines for joins, parts,
  quits, nick changes, kicks, modes, and topics. Light / Dark / OLED themes and a 24-hour clock
  option; font size scales cleanly.
- **Inline media.** Images, GIFs, and video embed in the timeline with a built-in viewer.
- **Buffer list that scales.** Grouped by network, DMs after channels, active buffers floated up,
  unread + highlight badges, pins, and mark-all-read. Secure (E2E) buffers show a lock.
- **Mentions.** Highlighted messages get a gold bubble (colour configurable), and tapping a
  notification opens the channel and jumps to the exact message.
- **Members.** Full member list with op/voice prefixes and per-nick actions (query, whois, op, kick,
  ban, notes…).
- **Composer.** Formatting toolbar + colour picker, tab-completion for nicks and commands, typing
  indicators, swipe-to-reply, and image/file uploads (plus Android share-sheet targets).
- **Slash-commands.** `/me`, `/msg`, `/query`, `/join`, `/part`, `/nick`, `/topic`, `/kick`, `/mode`,
  `/op`, `/ban`, `/whois`, `/away`, `/ctcp`, `/list`, `/raw`, … (`/help` lists them). `//` sends a
  literal leading slash.
- **Channel browser.** `/list` opens a searchable, sortable channel directory that streams results
  live as the server's `LIST` completes.
- **Search & contacts.** Server-side message search with highlights; friends / notes / ignores.
- **DCC.** Receive **and** send file transfers, plus DCC chat, with live progress.
- **Settings.** A registry-driven editor over the server's settings surface — every advertised
  setting is editable by type — alongside device-local app preferences.

## Flavors

One codebase, two identities (Gradle product flavors):

- **`full`** — `net.amiantos.lurker`, "Lurker" — the private daily-driver / sideload build.
- **`spooky`** — `chat.irc.lurker`, "Spooky for Lurker" — the public Play Store build.

Fork-only surfaces (custom aliases, DCC send/chat, fserve) are gated at **runtime** on the connected
server's capabilities, so both flavors carry all the code and simply light those up on a capable
server — there's no stripped branch to maintain.

## Building

```bash
# Debug (full flavor)
./gradlew assembleFullDebug
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk

# Release APK (sideload) / Play bundle
./gradlew assembleFullRelease
./gradlew bundleSpookyRelease
```

Release signing for the Play build reads a gitignored `keystore.properties`; absent it (fresh clone),
release falls back to debug signing so the build still succeeds.

To sign in, point it at your server: the hosted app is `lurker.chat` (email login), a self-hosted
server is its address (username login — use the **API** port, `8010` by default, not the web dev
port). From the emulator the host machine is `10.0.2.2`, not `localhost`. Cleartext HTTP is permitted
for local dev servers.

## Security notes

- **Sessions** live in app-private storage, unreadable by other apps on a non-rooted device.
- **Direct-mode IRC/SASL secrets** are encrypted at rest via `EncryptedSharedPreferences` — direct
  mode stores real IRC account passwords, so these are kept out of cleartext.

## Architecture

The UI talks to one observable backend. The base
[`LurkerClient`](app/src/main/java/net/amiantos/lurker/LurkerClient.kt) drives the WebSocket + REST
transport to a Lurker server; `DirectIrcBackend` subclasses it and overrides the transport with a
KICL-backed raw-IRC / bouncer engine, reusing the same Compose state and rendering. Pure,
unit-tested logic — mIRC parsing, command parsing, completion — is split into
[`Mirc.kt`](app/src/main/java/net/amiantos/lurker/Mirc.kt),
[`Commands.kt`](app/src/main/java/net/amiantos/lurker/Commands.kt), and friends; the screens live in
[`MainActivity.kt`](app/src/main/java/net/amiantos/lurker/MainActivity.kt).

## License

[MPL-2.0](LICENSE), same as Lurker.
