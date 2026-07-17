# Lurker for Android

A native Android client for [Lurker](https://github.com/amiantos/lurker), an IRC client with a
server that stays connected for you.

> **Status: early client.** Born as a spike to prove Lurker's WebSocket + REST contract can be driven
> from a native client — that part is done — and since grown into something usable day to day: it
> stays signed in, survives network drops, renders formatted chat, and handles slash-commands,
> settings, and DCC transfers. It is still built around a single source of truth rather than the
> layered architecture the real app ([lurker#492](https://github.com/amiantos/lurker/issues/492))
> calls for. See [Scope](#what-this-does-and-doesnt-do).

## What it proves

Lurker is a true bouncer: the client never touches IRC. The server does all the IRC work — parsing,
TLS, SASL, reconnect, history, highlight matching, ignore filtering — and speaks to clients in
high-level concepts (networks, buffers, messages, members) over one WebSocket plus a small REST
surface.

Until recently, though, the WebSocket authenticated by **cookie only**, so a native client couldn't
open one at all. [lurker#489](https://github.com/amiantos/lurker/issues/489) added bearer-token auth
to the upgrade, and this app is the end-to-end proof that it works:

1. `POST /api/auth/login/token` — password in, session token out, no browser in the loop.
2. `GET /api/networks` with `Authorization: Bearer <token>` — the same token authenticates REST.
3. `GET /ws` with the same bearer header on the **upgrade** — the thing browsers cannot do, and
   precisely why the web client is cookie-bound and native clients don't have to be.

A native session is an ordinary session: the bearer *is* the session token the web client's cookie
already carries, just handed to the app in a response body instead of a `Set-Cookie`.

## What this does (and doesn't) do

**Does:**

- **Stays signed in.** The bearer session is saved to app-private storage, so the app reconnects
  silently on launch. Sign out clears it (and best-effort revokes it server-side).
- **Survives drops.** The socket auto-reconnects with exponential backoff, and on return from the
  background it resumes with `?since=<lastId>` so you don't miss messages. A dead token (401) bounces
  you to the sign-in screen instead of spinning.
- **Reads naturally.** Renders mIRC colors and text formatting (bold/italic/underline/mono), makes
  URLs tappable, and shows joins / parts / quits / nick changes / kicks / modes / topics as inline
  system lines.
- **Buffer list that scales.** Grouped by network, DMs after channels, buffers with activity floated
  to the top, with unread and highlight badges. Opening a buffer clears its badge and marks it read.
- **Slash-commands.** `/me`, `/msg`, `/query`, `/join`, `/part`, `/nick`, `/topic`, `/kick`, `/mode`,
  `/op`, `/ban`, `/whois`, `/away`, `/ctcp`, `/slap`, `/cycle`, `/raw`, … (`/help` lists them). The
  handful with special wire semantics map to structured socket messages; the rest lower to a `raw`
  IRC line. `//` sends a literal leading slash.
- **Settings.** A registry-driven editor over `/api/settings` — every server-advertised setting is
  editable by type (toggle, number, choice, text) with no per-key hard-coding.
- **DCC receive.** Lists transfers, accept / reject / cancel, with live progress from `dcc-transfer`
  frames.

**Doesn't (yet):** member lists · uploads · message search · highlight-rule editing · push
notifications · passkey sign-in (the token mint is password-only) · **DCC send & chat** — the server
engine for those lives on a branch that isn't wired or deployed, so that surface is scaffolded and
disabled until the API ships.

The code is still one source of truth, not a layered architecture. Transport and observable state
live in [`LurkerClient.kt`](app/src/main/java/net/amiantos/lurker/LurkerClient.kt); the pure,
unit-tested logic is split out into [`Mirc.kt`](app/src/main/java/net/amiantos/lurker/Mirc.kt) and
[`Commands.kt`](app/src/main/java/net/amiantos/lurker/Commands.kt); the screens are in
[`MainActivity.kt`](app/src/main/java/net/amiantos/lurker/MainActivity.kt). The real app
([lurker#492](https://github.com/amiantos/lurker/issues/492)) is still meant to be rebuilt around one
internal model with a transport-adapter seam; this is the well-worn stepping stone, not that.

## A note on token storage

The saved bearer *is* your session — treat it like a password. It lives in app-private
SharedPreferences, unreadable by other apps on a non-rooted device, but it is **not** encrypted at
rest. Moving it into `EncryptedSharedPreferences` / the Android Keystore is the obvious next
hardening step and is the reason cleartext HTTP (below) must not survive into a shipping build.

## Running it

Requires a Lurker server you can reach, with a **password** set on your account — the token mint
endpoint is password-only, so a passkey-only account can't sign in yet.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Point it at your server on the sign-in screen. Two things catch people out:

- **Use the API server's port** (`8010` by default), not the Vite client dev port — that one only
  serves the web SPA and has no `/api` or `/ws`.
- **From the emulator, the host machine is `10.0.2.2`**, not `localhost` (which is the emulator
  itself). So a local dev server is `http://10.0.2.2:8010`. From a physical device on the same
  network, use the machine's LAN address instead.

Cleartext HTTP is enabled in the manifest so a plain-HTTP dev server works. That is a prototype
convenience and should not survive into a shipping build.

## A note on the buffer list

On connect, the server ships one `backlog` frame per buffer — but for channels and DMs those frames
are **shells** with no messages in them. Lurker auto-focuses nothing on load, so it doesn't read a
buffer's history until the client actually opens it. The client sends `open-buffer` and the server
replies with a real backlog frame.

Get this wrong and you build a correct-looking buffer list where every channel is empty, which reads
as a bug but is the lazy-hydration design working as intended. It's the one piece of the contract
that isn't obvious from the frame names.

## License

[MPL-2.0](LICENSE), same as Lurker.
