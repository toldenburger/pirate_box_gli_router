#!/bin/sh
# REFERENCE, NOT A TESTED INSTALLER.
# These are the OpenWrt (UCI) settings the box uses, collected from a hand-built setup on a GL.iNet GL-AR300M with
# OpenWrt 25.12.6. They were applied in several steps with checks in between, never as one script. Read them, adapt the
# variables, and run the parts you understand over SSH on the router (from the admin side, by cable).
# The guard below stops you from running the file by accident; delete it only after you have read everything.
echo "reference only: read this file, do not run it as a whole" >&2
exit 1

# ---- your values -------------------------------------------------------------------------------------------
ADMIN_IP=192.168.8.1        # router admin address (cable side). Avoid ranges your own computer already uses.
BOX_IP=192.168.60.1         # visitor side: the web server and DNS answer here
SSID=piratebox_open
COUNTRY=XX                  # YOUR two-letter country code (sets legal channels/power). Do not leave XX.
CHANNEL=6
STICK_UUID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx     # from: block info (with the stick plugged in)

# ---- 1. visitor network ------------------------------------------------------------------------------------
uci set network.box=interface
uci set network.box.proto='static'
uci add_list network.box.ipaddr="$BOX_IP/24"

# ---- 2. open Wi-Fi on it, clients isolated -----------------------------------------------------------------
uci set wireless.radio0.country="$COUNTRY"
uci set wireless.radio0.channel="$CHANNEL"
uci -q delete wireless.radio0.disabled
uci set wireless.box_ap=wifi-iface
uci set wireless.box_ap.device='radio0'
uci set wireless.box_ap.mode='ap'
uci set wireless.box_ap.network='box'
uci set wireless.box_ap.ssid="$SSID"
uci set wireless.box_ap.encryption='none'
uci set wireless.box_ap.isolate='1'          # visitors cannot see each other
uci set wireless.box_ap.maxassoc='20'        # a small router: keep the number of clients sane
uci set wireless.box_ap.disabled='0'

# ---- 3. DHCP for visitors (1 h leases; no IPv6) ------------------------------------------------------------
uci set dhcp.box=dhcp
uci set dhcp.box.interface='box'
uci set dhcp.box.start='100'
uci set dhcp.box.limit='100'
uci set dhcp.box.leasetime='1h'
uci set dhcp.box.dhcpv4='server'
uci set dhcp.box.dhcpv6='disabled'
uci set dhcp.box.ra='disabled'
uci add_list dhcp.box.dhcp_option="6,$BOX_IP"

# ---- 4. firewall: visitors get DHCP, DNS and web, nothing else, and nothing is forwarded --------------------
uci set firewall.box_zone=zone
uci set firewall.box_zone.name='box'
uci add_list firewall.box_zone.network='box'
uci set firewall.box_zone.input='REJECT'
uci set firewall.box_zone.output='ACCEPT'
uci set firewall.box_zone.forward='REJECT'
uci set firewall.box_dhcp=rule
uci set firewall.box_dhcp.name='Allow-DHCP-box'
uci set firewall.box_dhcp.src='box'
uci set firewall.box_dhcp.proto='udp'
uci set firewall.box_dhcp.dest_port='67'
uci set firewall.box_dhcp.family='ipv4'
uci set firewall.box_dhcp.target='ACCEPT'
uci set firewall.box_dns=rule
uci set firewall.box_dns.name='Allow-DNS-box'
uci set firewall.box_dns.src='box'
uci set firewall.box_dns.proto='tcp udp'
uci set firewall.box_dns.dest_port='53'
uci set firewall.box_dns.target='ACCEPT'
uci set firewall.box_web=rule                # open this only AFTER the admin page no longer listens on 0.0.0.0 (step 5)
uci set firewall.box_web.name='Allow-Web-box'
uci set firewall.box_web.src='box'
uci set firewall.box_web.proto='tcp'
uci set firewall.box_web.dest_port='80'
uci set firewall.box_web.target='ACCEPT'

# ---- 5. admin side: own address, and the admin page + SSH only there ---------------------------------------
# LAST STEP before reboot: this changes the address you are connected to and drops your SSH session.
# Work from the cable, keep the failsafe procedure (docs/INSTALL.md) in mind, and verify before you disconnect.
# (A self-reverting "sleep 600; restore" background job did NOT survive the network restart in testing: do not rely on one.)
uci -q delete network.lan.ipaddr
uci add_list network.lan.ipaddr="$ADMIN_IP/24"
uci -q delete uhttpd.main.listen_http
uci -q delete uhttpd.main.listen_https
uci add_list uhttpd.main.listen_http="$ADMIN_IP:80"
uci add_list uhttpd.main.listen_https="$ADMIN_IP:443"
uci set dropbear.main.Interface='lan'

# ---- 6. mount the stick by UUID at /mnt/usb, repair it first if it was not unmounted cleanly ---------------
uci set fstab.box_usb=mount
uci set fstab.box_usb.uuid="$STICK_UUID"
uci set fstab.box_usb.target='/mnt/usb'
uci set fstab.box_usb.fstype='ext4'
uci set fstab.box_usb.options='noatime'
uci set fstab.box_usb.enabled='1'
uci set fstab.box_usb.enabled_fsck='1'

# ---- 7. every name resolves to the box (so phones show the sign-in page); no IPv6 answers ------------------
# This also breaks the router's own DNS: remove it while installing packages (docs/INSTALL.md).
uci add_list dhcp.@dnsmasq[0].address="/#/$BOX_IP"
uci set dhcp.@dnsmasq[0].filter_aaaa='1'

# ---- apply -------------------------------------------------------------------------------------------------
uci commit
/etc/init.d/box_web enable                   # after copying router/box_web to /etc/init.d/
# then reboot (or, from the cable side: network reload, dnsmasq/uhttpd/dropbear/firewall restart) and test, see docs/INSTALL.md
