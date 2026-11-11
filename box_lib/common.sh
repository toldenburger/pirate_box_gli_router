# Shared helpers for the box scripts and the DHCP hook. Sourced, not run.
# The caller sets BASE first (the folder that holds box/, box_lib/, box_data/).
#
# Event log (tab separated), one line per event:
#   time <TAB> uptime_s <TAB> clock(set|unsynced) <TAB> event <TAB> device <TAB> detail
# "device" is the first 8 hex chars of sha256(secret salt + MAC), never the MAC itself.

DATA=${PB_DATA:-$BASE/box_data}
EVENTS=$DATA/events.log
MAX_EVENTS=${PB_MAX_EVENTS:-5000}
mkdir -p "$DATA"

# The router has no battery clock: "unsynced" until setclock (or NTP) has run.
clock_state() {
	if [ -e /tmp/box_clock_ok ]; then echo set; else echo unsynced; fi
}

salt() {
	f=$DATA/.salt
	if [ ! -s "$f" ]; then
		# no od/hexdump on OpenWrt busybox: hash random bytes to get a hex string
		( umask 077; head -c 32 /dev/urandom | sha256sum | cut -c1-32 > "$f" )
	fi
	cat "$f"
}

mac_of_ip() {
	ip neigh show "$1" 2>/dev/null | awk '{ for (i = 1; i < NF; i++) if ($i == "lladdr") { print $(i + 1); exit } }'
}

device_id() {
	[ -n "$1" ] || { echo unknown; return; }
	printf '%s%s' "$(salt)" "$1" | sha256sum | cut -c1-8
}

# Device id of the browser making the current CGI request.
client_dev() {
	device_id "${PB_FAKE_MAC:-$(mac_of_ip "$REMOTE_ADDR")}"
}

# log_event <event> <device> <detail>
log_event() {
	detail=$(printf '%s' "$3" | tr -d '\t\r\n')
	printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
		"$(date '+%Y-%m-%dT%H:%M:%S%z')" "$(cut -d' ' -f1 /proc/uptime)" "$(clock_state)" \
		"$1" "$2" "$detail" >> "$EVENTS"
	# keep only the newest MAX_EVENTS lines (size-based, so it works without a real clock)
	if [ "$(wc -l < "$EVENTS")" -gt "$MAX_EVENTS" ]; then
		tail -n "$MAX_EVENTS" "$EVENTS" > "$EVENTS.tmp" && mv "$EVENTS.tmp" "$EVENTS"
	fi
}
