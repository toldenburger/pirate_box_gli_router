#!/usr/bin/env python3
"""Summarise the box logs at home.

Usage: show_log.py <box_data folder>      (e.g. /path/to/stick/box_data)
Reads events.log (joins, chat, uploads, clock) and web.log (lighttpd: page views, downloads).
Device ids are salted hashes, so you can see "the same device came back" but not whose it is.
"""
import collections
import datetime as dt
import os
import re
import sys
import urllib.parse

WEB =re.compile(r'^(\S+) \[([^\]]+)\] "(\S+) (\S+)[^"]*" (\d{3}) (\S+)')


def read_events(path):
    rows = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8", errors="replace"):
            p = line.rstrip("\n").split("\t")
            if len(p) >= 6:
                rows.append(dict(ts=p[0], up=p[1], clock=p[2], event=p[3], dev=p[4], detail=p[5]))
    return rows


def parse_ts(s):
    try:
        return dt.datetime.strptime(s, "%Y-%m-%dT%H:%M:%S%z")
    except ValueError:
        return None


def read_web(path):
    rows = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8", errors="replace"):
            m = WEB.match(line)
            if m:
                ip, when, method, url, status, size = m.groups()
                try:
                    t = dt.datetime.strptime(when, "%d/%b/%Y:%H:%M:%S %z")
                except ValueError:
                    t = None
                rows.append(dict(ip=ip, t=t, method=method, url=url, status=int(status),
                                 size=0 if size == "-" else int(size)))
    return rows


def main():
    if len(sys.argv) != 2 or not os.path.isdir(sys.argv[1]):
        sys.exit(__doc__)
    d = sys.argv[1]
    events = read_events(os.path.join(d, "events.log"))
    web = read_web(os.path.join(d, "web.log"))

    # ip -> [(time, device)] from join events, to guess who made each web request
    joins = collections.defaultdict(list)
    for e in events:
        if e["event"] == "join":
            m = re.search(r"ip=(\S+)", e["detail"])
            if m:
                joins[m.group(1)].append((parse_ts(e["ts"]), e["dev"]))

    def who(ip, t):
        best = None
        for jt, dev in joins.get(ip, []):
            if t is None or jt is None or jt <= t:
                best = dev
        return best or "?"

    polls = sum(1 for r in web if r["url"].startswith("/cgi-bin/chat?since="))
    print(f"events: {len(events)}   web requests: {len(web) - polls}   chat polls (page/app refreshes): {polls}")
    unsynced = sum(1 for e in events if e["clock"] != "set")
    if unsynced:
        print(f"WARNING: {unsynced} events were logged before the clock was set; "
              "their dates are wrong (compare 'uptime' to order them).")

    print("\n== per day ==")
    days = collections.defaultdict(lambda: collections.Counter())
    devs = collections.defaultdict(set)
    for e in events:
        day = e["ts"][:10] + ("" if e["clock"] == "set" else " (clock unsynced)")
        days[day][e["event"]] += 1
        if e["event"] == "join":
            devs[day].add(e["dev"])
    for day in sorted(days):
        c = days[day]
        print(f"{day}: {c['boot']} boots, {len(devs[day])} devices joined, {c['chat_post']} chat posts, "
              f"{c['upload']} uploads, {c['clock_set']} clock sets")

    print("\n== downloads (files/ and media/, status 200/206) ==")
    n = 0
    for r in web:
        if r["method"] == "GET" and r["status"] in (200, 206) and re.match(r"^/(files|media)/", r["url"]):
            when = r["t"].strftime("%Y-%m-%d %H:%M") if r["t"] else "?"
            print(f"{when}  dev {who(r['ip'], r['t'])}  {r['size']:>10} B  {urllib.parse.unquote(r['url'])}")
            n += 1
    if not n:
        print("(none)")

    print("\n== uploads, denied clock attempts ==")
    shown = 0
    for e in events:
        if e["event"] in ("upload", "clock_denied"):
            print(f"{e['ts']}  dev {e['dev']}  {e['event']}  {e['detail']}")
            shown += 1
    if not shown:
        print("(none)")


if __name__ == "__main__":
    main()
