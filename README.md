# pirate_box_gli_router

An **offline "box"** on a cheap travel router (GL.iNet GL-AR300M running OpenWrt). It broadcasts an open Wi-Fi network
with no internet. Anyone who joins lands on a small welcome page with three buttons:

- **Chat**: a tiny shared chat room.
- **Files**: ebooks and other files to download, plus an upload button.
- **Media**: songs and pictures, played/shown right in the browser.

Everything lives on a USB stick plugged into the router. Nothing leaves the box. It runs from a USB power bank.

It is inspired by the old [PirateBox](https://piratebox.cc) idea (last release 2018, needs an old OpenWrt), but shares
no code with it: this is plain static pages, a few tiny POSIX `sh` CGI scripts and `lighttpd`, tested on OpenWrt 25.12.

For the owner there is also:

- a **log** of who joined, chat posts, uploads and downloads (visitor devices are only stored as salted hashes), readable at home with one command;
- **phone shortcuts** to set the router's clock (it has no battery clock) and to **shut down cleanly**;
- an optional **Android app** (`android/`) that raises a notification when someone posts in the chat while your phone is on the box's Wi-Fi.

## Status and honesty

- Built and tested on: **GL.iNet GL-AR300M (the NAND model)**, **OpenWrt 25.12.6**, `lighttpd` 1.4.85, a 16 GB ext4 USB stick,
  Android 17 (GrapheneOS) for the app. Other routers/versions are untested; expect to adapt.
- The router settings were first done by hand. `router/uci_reference.sh` documents them, but it is **a reference, not a
  tested one-shot installer**. Read it, understand it, run the parts you need.
- It is an open Wi-Fi with a public chat and public uploads. Read [docs/SAFETY_AND_PRIVACY.md](docs/SAFETY_AND_PRIVACY.md)
  before you switch it on in public. You are responsible for what runs on your network and for following your local laws
  (including privacy law, because the log stores device identifiers).

## Try the web part on your computer (no router needed)

```
python3 tools/serve_local.py 8080      # then open http://localhost:8080
```
It serves `box/` and runs the CGI scripts the way the router does. Put test files in `box/files/` and `box/media/`.
(It creates `box_data/` next to `box/`; that folder is git-ignored.)

## Documentation

| | |
|---|---|
| [docs/INSTALL.md](docs/INSTALL.md) | flash the router, network layout, packages, the USB stick, services, tests, gotchas |
| [docs/OPERATING.md](docs/OPERATING.md) | using it in the field, clean shutdown, reading the logs at home, troubleshooting |
| [docs/SAFETY_AND_PRIVACY.md](docs/SAFETY_AND_PRIVACY.md) | what visitors can reach, what is logged, known weaknesses, legal notes |
| [android/README.md](android/README.md) | the chat-alert app: build, install, the two phone settings that matter |

## Layout

```
box/          the web root that goes on the stick (pages, cgi-bin/, admin/, files/, media/)
box_lib/      shared shell helpers (event log, hashed device ids)
router/       files that go on the router (DHCP hook, init script, lighttpd config, uci reference)
tools/        run on your computer: local test server, log checker, QR helper, router route helper
android/      the optional Box Chat app (plain Java, builds without Gradle)
docs/         install, operating, safety
```

## License

MIT, see [LICENSE](LICENSE). Pictures, music and ebooks are not included: bring your own and mind their copyright.
