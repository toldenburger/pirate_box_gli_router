#!/bin/sh
# Temporary routes from this computer to the router at the other end of an Ethernet cable.
#
#   sudo ROUTER_DEV=<your wired port> tools/router_route.sh up|down|status
#
# Why: the router's admin address (default 192.168.8.1) is on its own small network. This gives
# your computer a matching one-address (/32) address and a one-address route, in memory only, so
# nothing else on this computer's network changes and your internet keeps using Wi-Fi.
# It also prepares the OpenWrt failsafe pair (router 192.168.1.1, you 192.168.1.2).
#
# Settings (environment variables):
#   ROUTER_DEV       REQUIRED. The wired port facing the router, e.g. enp3s0 (see: ip -br link)
#   ROUTER_ADMIN_IP  admin address of the router        (default 192.168.8.1)
#   ROUTER_ADMIN_ME  address this computer uses for it  (default 192.168.8.2)
#
# Gotchas found while building this:
# * NetworkManager removes these addresses whenever the cable link drops (router reboot): run "up" again.
# * Never pick an address that falls inside a Docker network on this computer (docker bridges
#   often cover 192.168.16.0/20 ... 192.168.240.0/20 and 172.17-31.x): packets from such a source
#   address get rewritten and the router looks dead. 192.168.0-15.x and 10.x are safer.
# * Check you are really talking to the router (compare the hardware address in `ip neigh`
#   with the label on the router), especially if your home router also uses 192.168.1.1.

DEV=${ROUTER_DEV:?set ROUTER_DEV to the wired port facing the router, e.g. ROUTER_DEV=enp3s0}
ADMIN=${ROUTER_ADMIN_IP:-192.168.8.1}
ME=${ROUTER_ADMIN_ME:-192.168.8.2}
PAIRS="192.168.1.2:192.168.1.1 $ME:$ADMIN"

case "$1" in
up)
	for pair in $PAIRS; do
		me=${pair%%:*}; router=${pair##*:}
		ip addr show dev "$DEV" | grep -q " $me/32" || ip addr add "$me/32" dev "$DEV"
		ip route replace "$router/32" dev "$DEV" src "$me"
	done
	;;
down)
	for pair in $PAIRS; do
		me=${pair%%:*}; router=${pair##*:}
		ip route del "$router/32" dev "$DEV" 2>/dev/null
		ip addr del "$me/32" dev "$DEV" 2>/dev/null
	done
	;;
status) ;;
*)
	echo "usage: ROUTER_DEV=<port> $0 up|down|status" >&2
	exit 1
	;;
esac

ip -br addr show dev "$DEV"
ip route get "$ADMIN" | head -1
ip route get 192.168.1.1 | head -1
