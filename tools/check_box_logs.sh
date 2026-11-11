#!/bin/sh
# One command to read the box logs at home.
#   tools/check_box_logs.sh            find the stick (label pbox_data), mount it if needed, summarise
#   tools/check_box_logs.sh <folder>   summarise a copy of box_data you already have
# Also keeps a dated copy of the raw logs in $BOX_LOG_ARCHIVE (default ~/box_log_archive, OUTSIDE this repo: logs contain visitor data).
# No sudo needed (uses udisksctl, like the desktop automount).

HERE=$(cd "$(dirname "$0")/.." && pwd)

if [ -n "$1" ]; then
	DATA=$1
else
	DEV=/dev/disk/by-label/${BOX_LABEL:-pbox_data}
	if [ ! -e "$DEV" ]; then
		echo "Stick not found (label ${BOX_LABEL:-pbox_data}). Plug it into this computer first." >&2
		exit 1
	fi
	MNT=$(findmnt -no TARGET "$DEV" 2>/dev/null)
	if [ -z "$MNT" ]; then
		udisksctl mount -b "$(readlink -f "$DEV")" >/dev/null || exit 1
		MNT=$(findmnt -no TARGET "$DEV")
	fi
	DATA=$MNT/box_data
fi

[ -d "$DATA" ] || { echo "No box_data folder at: $DATA" >&2; exit 1; }

# dated copy of the raw logs (never the secret keys or the salt)
ARCH=${BOX_LOG_ARCHIVE:-$HOME/box_log_archive}/$(date +%Y-%m-%d_%H%M)
mkdir -p "$ARCH"
for f in events.log web.log chat.log; do [ -f "$DATA/$f" ] && cp "$DATA/$f" "$ARCH/"; done
echo "raw logs copied to: $ARCH"
echo

python3 -I "$HERE/tools/show_log.py" "$DATA"
