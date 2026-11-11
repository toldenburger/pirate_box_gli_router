# Safety and privacy

This is a hobby project, provided **as is** (see the license). You run an open Wi-Fi network with a public chat and public
uploads. That is your responsibility: what visitors post, what they upload, what your network is used for, and compliance
with the law where you are. Nothing here is legal advice.

## What visitors can reach

Verified on the tested setup (firewall rules, listening sockets, and attack requests against the web server):

- The visitor Wi-Fi accepts only **DHCP, DNS and web (port 80)** on the router. Everything else is rejected. **Nothing is forwarded** to any
  other network, and the box has no internet.
- The router's admin page and SSH listen **only** on the cable-side admin address, never on the visitor side.
- **Client isolation** is on: visitors cannot see each other's devices.
- The web server only serves the `box/` folder. Logs, keys and the salt live in `box_data/`, **outside** it. Path-traversal
  attempts (plain and encoded), hidden files, directory listings, the router's own admin paths and bad query values were all refused
  or answered with the welcome page.
- CGI scripts only run from `box/cgi-bin/`. Uploaded files are never executed. Uploaded `.html/.htm/.xhtml/.svg/.js/.xml/.php`
  files get `.txt` appended so a visitor cannot plant a page that runs in other people's browsers. Chat text is displayed as plain text.
- Uploads are limited to 50 MB each and refused when the stick is nearly full.

## Known weaknesses and trade-offs (by design)

- **Open Wi-Fi over plain HTTP.** Anyone in radio range can read what visitors send and receive. Don't put anything on the box
  you wouldn't put on a public notice board.
- **Owner links** (clock, shutdown) carry their secret key in clear text over that open Wi-Fi. Someone sniffing it could set a wrong
  clock or power the box off. Nothing worse, and every attempt is logged. The keys are random, per box, stored only on the stick.
- **Public chat and uploads, no moderation.** A visitor can fill the stick over time. You remove things by deleting files on the stick.
- **Physical access** to the router means the admin password/key is the only barrier. Use a strong root password.
- **The stick is not encrypted.** If lost, chat, uploads and logs are readable (they contain no secrets of yours; the owner keys let someone set the clock/shut down that box).
- Uploaded files are not scanned for malware.

## What is logged, and how to limit it

The log (`events.log`, `web.log`, `chat.log` on the stick) records: power-ups, clock sets, wrong-key attempts, devices joining and
leaving, chat posts (the text is in `chat.log`), uploads (name and size), and every web request (address, time, URL, bytes).

- Device hardware addresses are **never stored**: only the first 8 hex characters of `sha256(secret salt + address)`. The salt is
  random, lives in `box_data/.salt` and never leaves the stick. Same device, same id, but it cannot be reversed to an address
  without the salt and a guess.
- The visitor-network address of a device (192.168.60.x) appears in the web log; it is only meaningful together with the
  join log. Phones that use a fresh random hardware address per connection show up under a new id each time.
- The event log keeps the newest 5000 events (set with `PB_MAX_EVENTS`); the web log is not rotated.
- In many places (for example under the EU GDPR) device identifiers and IP addresses count as personal data, and running a
  public hotspot that logs them can carry obligations (a notice, a retention limit, a purpose). Decide that for your situation.
  A one-line notice on the welcome page (`box/index.html`) and clearing the logs regularly are easy first steps.
  To log less, remove the `log_event` calls in `box/cgi-bin/*` and the hook in `/etc/hotplug.d/dhcp/`, and/or drop `mod_accesslog`
  from `router/lighttpd_box.conf`.

## Content

Don't share what you have no right to share. Ebooks, music and pictures on the box are yours to check for copyright. The repository
ships none. Public-domain archives exist for books and artwork; the licence of anything you add is your responsibility.

## Reporting problems

If you find a security problem in this code, open an issue (or, if you would rather not make it public, contact the repository owner
through GitHub) with the steps to reproduce.
