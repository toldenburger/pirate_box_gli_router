# Install guide

Tested on a **GL.iNet GL-AR300M (NAND model)** with **OpenWrt 25.12.6**. Flashing firmware can brick a router: read the whole
guide first, and know the two ways back in (U-Boot recovery and OpenWrt failsafe, both below).

Words used: **admin side** = the router's wired LAN port (you, by cable); **visitor side** = the open Wi-Fi network;
**the stick** = the USB stick that holds everything.

## 0. Which router do you have?

The GL-AR300M exists in variants. This guide is for the one whose label says just `GL-AR300M` and which has 128 MB NAND
flash next to its 16 MB NOR flash. The `-Lite` and `AR300M16` variants have NOR flash only and need different images.
After flashing, `cat /proc/mtd` shows a large `ubi` partition (about 124 MB) on the NAND model.
There is a small slide switch on the side that picks which flash it boots from: per the vendor's docs, toward the reset
button = NAND. On the tested unit that was right (it booted NAND). Leave the stock firmware on the NOR side as your fallback.

## 1. Flash OpenWrt (U-Boot web recovery, no serial cable)

1. From `https://downloads.openwrt.org/releases/<version>/targets/ath79/nand/` download
   `openwrt-<version>-ath79-nand-glinet_gl-ar300m-nand-squashfs-factory.img` and `sha256sums`.
   Check it: `grep gl-ar300m-nand-squashfs-factory sha256sums | sed 's/ \*/  /' | sha256sum -c -`
   (this proves the download is intact, it does not prove authenticity; OpenWrt also publishes signatures).
2. Computer to the router's **LAN** port by cable. Unplug the router, hold the **reset** button, plug power in,
   release after the LED has flashed 5 times. The router now runs a tiny recovery web server at `http://192.168.1.1`.
3. Your computer needs to reach `192.168.1.1`. If your home network also uses 192.168.1.x (so it clashes), use
   `tools/router_route.sh` (see its header) instead of switching Wi-Fi off.
4. Open the page. It has two forms: *NAND* (`*.img`) and *NOR* (`*.bin`). Upload the `.img` in the **NAND** form
   (from a script: `curl -F "nand_firmware=@file.img" http://192.168.1.1/`). It writes and reboots, several minutes.
   Do not cut the power meanwhile.
5. OpenWrt comes up at `192.168.1.1`. Set a **root password** straight away (LuCI at `http://192.168.1.1`), and add your
   SSH public key (System > Administration > SSH-Keys).

**If something goes wrong:** repeat step 2 and flash again; the stock firmware on the NOR flash is untouched.
**OpenWrt failsafe** (settings broken, locked out): unplug power, plug in, and when the LED blinks fast press reset once;
the router comes up at `192.168.1.1` with default settings and no firewall. Log in, run `mount_root`, fix, `reboot`.

## 2. Address plan and the one rule about Docker

| Network | Address | Who |
|---|---|---|
| Admin (wired LAN) | `192.168.8.1/24` | you, by cable only |
| Visitor Wi-Fi (`box`) | `192.168.60.1/24` | visitors and the web server |
| WAN | unplugged in the field | only plugged in to install packages |

Do **not** leave the admin side on OpenWrt's default `192.168.1.1`: it clashes with most home routers the moment you plug
the WAN port into your home network. And avoid address ranges that Docker or other software on *your computer* already
uses for its own networks (Docker commonly takes `172.17-31.x` and `192.168.16-255.x`): packets from a source address
inside such a range are rewritten and the router looks dead. `192.168.0-15.x` and `10.x` are safer.

## 3. Network configuration

`router/uci_reference.sh` has the settings as commands, with the reasons. In short:

- a new interface `box` (static, `192.168.60.1/24`) with its own DHCP;
- an **open** Wi-Fi network on it, **client isolation on**, a fixed 2.4 GHz channel and your **country code**
  (set the country: it controls which channels and power are legal where you live);
- a firewall zone `box`: input **reject**, forward **reject**, and only three rules in: DHCP (udp 67), DNS (53 tcp+udp),
  web (tcp 80). Nothing is forwarded to any other network. The admin pages (LuCI) and SSH listen **only** on the admin
  address, not on the visitor side;
- dnsmasq answers every name with the box's address (`address=/#/192.168.60.1`) and filters IPv6 answers
  (`filter_aaaa`), so phones see a "sign in to network" page and open the welcome page by themselves.
  This also breaks the router's own DNS: remove it while you install packages (see below).

## 4. Packages (OpenWrt 25.12 uses `apk`, not `opkg`)

The router needs internet once. Plug the **WAN** port into a network with DHCP and internet, remove the DNS wildcard
for now, then:

```
apk update
apk add kmod-usb-storage kmod-fs-ext4 block-mount e2fsprogs lighttpd lighttpd-mod-cgi lighttpd-mod-accesslog lighttpd-mod-access
/etc/init.d/lighttpd disable        # the packaged default instance would listen on port 80 on every address
```
Unplug WAN again afterwards and put the DNS wildcard back.

## 5. The USB stick

Use **ext4**, not exFAT or FAT: a power bank running flat or a pulled cable is normal here, and ext4's journal copes
with it far better. On a Linux computer (replace `sdX` by the stick: `lsblk` first, this erases it):

```
sudo parted -s /dev/sdX mklabel msdos mkpart primary ext4 1MiB 100%
sudo mkfs.ext4 -m 0 -L pbox_data -E root_owner=$(id -u):$(id -g) /dev/sdX1
```
`-m 0` skips the reserved 5% (pointless on a data stick); `-L` is the label the helper scripts look for;
`-E root_owner=...` makes your user own the top folder so you can copy files without sudo.

Put on the stick (mounted at, say, `/run/media/you/pbox_data`):

```
box/            <- copy this repo's box/ folder (then add your ebooks to box/files/, songs/pictures to box/media/)
box_lib/        <- copy box_lib/
box_conf/       <- copy router/lighttpd_box.conf here
box_data/       <- create: holds logs and keys (never inside box/, which is what the web server shows)
box_data/tmp/   <- create
```
Create the two owner keys (random, only on the stick):

```
cd /run/media/you/pbox_data/box_data
umask 077
head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-16 > clock_key
head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-16 > shutdown_key
```
Keep media modest: a few dozen MP3s and pictures resized to about 1000 px are plenty for a small router's Wi-Fi.
`chmod +x box/cgi-bin/*` if your copy lost the executable bits. Unmount safely (`udisksctl unmount -b ...`) before unplugging.

## 6. Install the pieces on the router

Plug the stick into the router, then over SSH on the admin side:

- `block info` shows the stick's UUID. Add a mount for it at `/mnt/usb` (see `uci_reference.sh`; `enabled_fsck '1'` repairs after a power cut).
- Copy `router/50_boxlog` to `/etc/hotplug.d/dhcp/50_boxlog` (executable). It logs visitor joins/leaves.
- Copy `router/box_web` to `/etc/init.d/box_web`, `chmod +x`, `/etc/init.d/box_web enable`. It waits for the stick, writes a
  "boot" line to the log, then starts lighttpd with `/mnt/usb/box_conf/lighttpd_box.conf` on the visitor address.
- Check the config: `lighttpd -tt -f /mnt/usb/box_conf/lighttpd_box.conf` (no output = fine).

## 7. Test, in this order

1. **On the router:** `wget -q -O - http://192.168.60.1/` shows the welcome page; `wget -q -O - "http://192.168.60.1/cgi-bin/list?dir=media"` lists your media.
2. **From a phone** on the open Wi-Fi: the page should open by itself ("sign in to network"). Try chat, a download, an upload, a song (and seeking in it).
3. **Hidden things stay hidden:** requests like `/../box_data/clock_key` must give an error or the welcome page, never file content.
4. **Power-cycle** the router with the stick in: Wi-Fi, stick and web server must come back by themselves and the chat history must survive.
5. **Clean shutdown** (phone link, see [OPERATING.md](OPERATING.md)); on the next boot `dmesg | grep EXT4-fs` must **not** show
   "recovery complete" for the stick.

## 8. Gotchas (all of these cost time)

OpenWrt/BusyBox:
- BusyBox `wc -c` **reads the whole file** to count bytes. Do not use it on big files (the media list took minutes). Use `ls -ln file | awk '{print $5}'`.
- No `od`, `pkill`, `timeout`, `hexdump`; `wget` has no `-S` (errors show the HTTP status). `ip -br` does not exist; `dumpe2fs` is not installed.
- `sync` alone does not mark an ext4 stick clean. A proper shutdown stops lighttpd (it holds files open), `sync`, `umount /mnt/usb`, then `poweroff`
  (that is what `box/cgi-bin/shutdown` does). The router's own flash always logs a recovery at boot; that is normal.
- lighttpd's `url.access-deny` needs the separate `lighttpd-mod-access` package (and `"mod_access"` in `server.modules`).
- Uploads are streamed (`server.stream-request-body = 2`) into a stick directory (`server.upload-dirs`), not RAM, or a 50 MB upload would exhaust the router's memory.
- The router has **no battery clock**: after each power-up its time is wrong until you set it (clock link) or give it internet (NTP).

Your computer and phone:
- Home router and box both on `192.168.1.1`? Always verify the hardware address of whatever answers.
- NetworkManager removes the temporary addresses from `tools/router_route.sh` whenever the cable link drops; run it again.
- Phones keep **mobile data as the default network** while joined to a Wi-Fi with no internet, so the owner links (clock/shutdown)
  only load with mobile data **off**. Never answer "use as is / stay connected" at the sign-in prompt: Android then makes the box
  Wi-Fi the default network and your other apps lose their internet.
- An always-on, non-bypassable VPN (Tailscale, for example) makes Android refuse apps the right to talk to a specific
  network (`EPERM`). The Android app needs an exception for itself; see [android/README.md](../android/README.md).

## 9. Updating packages later

Plug WAN in, remove the DNS wildcard (`uci del_list dhcp.@dnsmasq[0].address='/#/192.168.60.1'`, `uci set dhcp.@dnsmasq[0].filter_aaaa=0`,
`uci commit dhcp`, `/etc/init.d/dnsmasq restart`), `apk update && apk upgrade`, then put the wildcard back and unplug WAN.
