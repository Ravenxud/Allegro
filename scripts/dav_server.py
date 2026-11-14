#!/usr/bin/env python3
"""最小 WebDAV 服务器（仅用于本机验证 WebDavClient）：
支持 PROPFIND / MKCOL / PUT / GET / DELETE，Basic 认证。"""
import base64
import http.server
import os
import sys
import urllib.parse

ROOT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/davroot"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8081
USER, PASSWORD = "tester", "s3cret"
AUTH = "Basic " + base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _authorized(self):
        return self.headers.get("Authorization") == AUTH

    def _path(self):
        raw = urllib.parse.urlparse(self.path).path
        return os.path.join(ROOT, urllib.parse.unquote(raw).lstrip("/"))

    def _deny(self):
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="dav"')
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_PROPFIND(self):
        if not self._authorized():
            return self._deny()
        path = self._path()
        if not os.path.isdir(path):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        body = b'<?xml version="1.0"?><D:multistatus xmlns:D="DAV:"></D:multistatus>'
        self.send_response(207)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_MKCOL(self):
        if not self._authorized():
            return self._deny()
        path = self._path()
        if os.path.isdir(path):
            self.send_response(405)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        os.makedirs(path, exist_ok=True)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_PUT(self):
        if not self._authorized():
            return self._deny()
        path = self._path()
        length = int(self.headers.get("Content-Length", "0"))
        data = self.rfile.read(length)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        if not self._authorized():
            return self._deny()
        path = self._path()
        if not os.path.isfile(path):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        with open(path, "rb") as fh:
            data = fh.read()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_DELETE(self):
        if not self._authorized():
            return self._deny()
        path = self._path()
        if os.path.isfile(path):
            os.remove(path)
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    httpd = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"dav server on {PORT} root={ROOT}", flush=True)
    httpd.serve_forever()
