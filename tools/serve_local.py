#!/usr/bin/env python3
"""Local test server for the box: static files plus CGI scripts, like uhttpd.

Usage: serve_local.py [port]    (default 8080, http://localhost:8080)
Scripts run with `sh` and a tiny CGI environment, the same inputs uhttpd gives.
"""
import os
import re
import subprocess
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
BOX = os.path.realpath(os.environ.get("PB_BOX") or os.path.join(HERE, "..", "box"))
CGI = os.path.join(BOX, "cgi-bin")
BLANK = re.compile(rb"\r?\n\r?\n")


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=BOX, **kwargs)

    def do_GET(self):
        if self.path.startswith("/cgi-bin/"):
            return self.cgi()
        super().do_GET()

    def do_POST(self):
        if self.path.startswith("/cgi-bin/"):
            return self.cgi()
        self.send_error(405)

    def cgi(self):
        parts = urlsplit(self.path)
        script = os.path.realpath(os.path.join(BOX, parts.path.lstrip("/")))
        if os.path.dirname(script) != CGI or not os.path.isfile(script):
            return self.send_error(404)

        length = int(self.headers.get("Content-Length") or 0)
        env = {
            "PATH": os.environ["PATH"],
            "REQUEST_METHOD": self.command,
            "QUERY_STRING": parts.query,
            "REMOTE_ADDR": self.client_address[0],
        }
        env.update({k: v for k, v in os.environ.items() if k.startswith("PB_")})
        if length:
            env["CONTENT_LENGTH"] = str(length)

        proc = subprocess.Popen(
            ["sh", script], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            env=env, cwd=CGI,
        )
        left = length
        try:
            while left > 0:
                chunk = self.rfile.read(min(65536, left))
                if not chunk:
                    break
                proc.stdin.write(chunk)
                left -= len(chunk)
            proc.stdin.close()
        except BrokenPipeError:
            pass
        raw = proc.stdout.read()
        proc.wait()

        match = BLANK.search(raw)
        head, body = (raw[:match.start()], raw[match.end():]) if match else (b"", raw)
        status = 200
        headers = []
        for line in head.decode("latin-1").splitlines():
            key, _, value = line.partition(":")
            if key.lower() == "status":
                status = int(value.split()[0])
            else:
                headers.append((key, value.strip()))
        self.send_response(status)
        for key, value in headers:
            self.send_header(key, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print(f"serving {BOX} on http://localhost:{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
