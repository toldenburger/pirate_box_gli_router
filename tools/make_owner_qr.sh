#!/bin/sh
# Show a QR code for one of the owner links (clock or shutdown), then delete it again.
#   tools/make_owner_qr.sh clock       QR for  http://<box ip>/admin/clock.html#<key>
#   tools/make_owner_qr.sh shutdown    QR for  http://<box ip>/admin/shutdown.html#<key>
# The key is read from the box (stick plugged into this computer, or the router over the cable:
# run tools/router_route.sh up first). The PNG lives in a private temp folder and is removed
# after 5 minutes. Scan it with the phone while it is joined to piratebox_open, mobile data OFF.

what=$1
BOX_IP=${BOX_IP:-192.168.60.1}               # the box address visitors and your phone use
BOX_ADMIN_IP=${BOX_ADMIN_IP:-192.168.8.1}      # the router admin address (cable side)
BOX_LABEL=${BOX_LABEL:-pbox_data}              # label of the USB stick
case "$what" in clock|shutdown) ;; *) echo "usage: $0 clock|shutdown" >&2; exit 1 ;; esac

key=
DEV=/dev/disk/by-label/$BOX_LABEL
if [ -e "$DEV" ]; then
	MNT=$(findmnt -no TARGET "$DEV" 2>/dev/null)
	if [ -z "$MNT" ]; then udisksctl mount -b "$(readlink -f "$DEV")" >/dev/null 2>&1; MNT=$(findmnt -no TARGET "$DEV"); fi
	[ -n "$MNT" ] && key=$(cat "$MNT/box_data/${what}_key" 2>/dev/null)
fi
if [ -z "$key" ]; then
	key=$(ssh -o BatchMode=yes -o ConnectTimeout=6 root@$BOX_ADMIN_IP "cat /mnt/usb/box_data/${what}_key" 2>/dev/null)
fi
key=$(printf '%s' "$key" | tr -d '\r\n')
if [ -z "$key" ]; then
	echo "Could not read the $what key. Plug the stick into this computer, or run tools/router_route.sh up first." >&2
	exit 1
fi

dir=$(mktemp -d) || exit 1
chmod 700 "$dir"
png=$dir/${what}_link_qr.png
( umask 077; qrencode -o "$png" -s 10 -m 2 "http://$BOX_IP/admin/${what}.html#$key" ) || exit 1
unset key
( setsid xdg-open "$png" >/dev/null 2>&1 & )
echo "QR opened: $png  (deleted automatically in 5 minutes)"
( setsid sh -c "sleep 300; rm -rf '$dir'" >/dev/null 2>&1 & )
