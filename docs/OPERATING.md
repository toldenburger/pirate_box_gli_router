# Operating the box

For you, the owner. Visitors only see the Wi-Fi and the welcome page.

## In the field (checklist)

1. Plug the router into a USB power bank (or any 5 V supply). Wait about 90 seconds until the LEDs are steady.
2. Join the open Wi-Fi with your phone. The welcome page should open by itself.
3. Set the clock (the router has no battery clock): open your **clock** shortcut. It says "Clock set to ...".
   Phones keep mobile data as the default network on a Wi-Fi without internet, so these owner links only load with
   **mobile data off** (unless you use the Android app, which does not need this).
4. When done: open the **shutdown** shortcut, tap the button, confirm. Wait until **all LEDs are completely off**
   (on the tested router they go fully dark), then unplug.
   Don't just pull the power: the stick would have to repair itself at the next start.

Make the shortcuts: `tools/make_owner_qr.sh clock` and `tools/make_owner_qr.sh shutdown` show a QR code for five
minutes, then delete it. Scan it with the phone camera while on the box Wi-Fi, then "Add to Home screen".
(Settings `BOX_IP`, `BOX_ADMIN_IP`, `BOX_LABEL` can be changed through environment variables.) The key sits in the link
after the `#`, so treat the QR like a password and don't leave the image around.

## Reading the logs at home

Take the stick out of the (shut down) router, plug it into your computer, run:

```
tools/check_box_logs.sh
```
It finds the stick by its label (`BOX_LABEL`, default `pbox_data`), mounts it without sudo, saves a dated copy of the raw
logs in `~/box_log_archive/` (change with `BOX_LOG_ARCHIVE`; keep it outside this repo, it is visitor data) and prints a summary:

- **per day**: power-ups (boots), devices that joined, chat posts, uploads, clock sets. Lines marked `(clock unsynced)`
  happened before you set the clock: their dates are wrong but their order is right.
- **downloads**: each file/song/picture fetched, with the device that fetched it.
- **uploads / denied**: what visitors uploaded, and wrong-key attempts on the owner links.

Device ids (8 hex characters) are a salted hash of the phone's hardware address, not names. Modern phones use a **new random
address for every connection**, so one person can appear under several ids: read it as sessions, not people.
Join/leave times come from the DHCP lease (one hour), so a "leave" appears up to an hour after the device really left.

Chat text is not in the summary. Read it from the stick: `cut -f2- "<stick>/box_data/chat.log"` (fields: number, nickname, text).
Uploaded files are in `<stick>/box/files/uploads/`: they come from strangers, look but don't trust or run them.

**Start fresh** (on the mounted stick, no sudo): `cd <stick>/box_data; : > events.log; : > web.log; : > web_error.log; rm -f chat.log`
(`: > file` empties a file), and `rm -f ../box/files/uploads/*` for uploads. **Don't** delete `clock_key`, `shutdown_key` or `.salt`.
Unmount before unplugging: `udisksctl unmount -b /dev/disk/by-label/pbox_data`.

## Reading the logs without taking the stick out

Cable from your computer to the router's LAN port, then `sudo ROUTER_DEV=<your port> tools/router_route.sh up` (see its header).
Then copy the logs over SSH and run `python3 tools/show_log.py <folder>`:

```
mkdir -p ~/box_logs && for f in events.log web.log chat.log; do ssh root@192.168.8.1 "cat /mnt/usb/box_data/$f" > ~/box_logs/$f; done
```
When done: `sudo ROUTER_DEV=<your port> tools/router_route.sh down`.

## Troubleshooting

| What you see | What to do |
|---|---|
| `check_box_logs.sh` cannot find the stick | Plug it in; its label must match `BOX_LABEL` (`lsblk -o NAME,LABEL`). |
| Owner link says "connection refused" on the phone | Turn mobile data off, or the browser sends it over the mobile network. |
| Router does not answer at its admin address | `router_route.sh up` again; compare the answering hardware address with the router's label. |
| "recovery complete" for the stick in the router's boot log | The power was cut instead of a clean shutdown. Data is fine; use the shutdown link next time. |
| Media list never loads | Something re-reads big files with BusyBox `wc -c`; see the gotchas in [INSTALL.md](INSTALL.md). |
| Locked out of the router | OpenWrt failsafe (INSTALL.md section 1). |

## Power bank notes

Tested with a 20,000 mAh (about 74 Wh) bank: the router ran 1 h 07 min idle on it without the bank ever switching off, and the
indicator dropped about 2%. That is roughly 1 W at the router, so on the order of 30-60 hours idle; more with people
streaming music. Treat it as a rough figure (the indicator is coarse). Some banks switch themselves off for very small loads:
test yours for an hour or two before relying on it.
