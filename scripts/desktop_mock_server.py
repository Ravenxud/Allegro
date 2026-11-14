#!/usr/bin/env python3
"""桌面版代理的 mock 站点（仅本机验证用）。

模拟真实站点的两种行为：
1. 按 Client Hints 决定走桌面页还是移动页（这正是 WebView 做不到的地方）
2. m./www 互踢的 302 循环（B 站现象），用来验证重定向策略能停下

用法：python3 scripts/desktop_mock_server.py 8099
"""
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099

DESKTOP_HTML = b"<html><head><title>t</title></head><body>DESKTOP PAGE</body></html>"
MOBILE_HTML = b"<html><body>MOBILE PAGE</body></html>"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _hint_mobile(self):
        return self.headers.get("Sec-CH-UA-Mobile", "").strip() == "?1"

    def _send(self, code, body, ctype="text/html; charset=utf-8", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _redirect(self, location):
        self._send(302, b"", extra={"Location": location})

    def do_GET(self):
        path = self.path.split("?")[0]

        # 1) 按 hints 决定内容：桌面 hints -> 桌面页；否则跳移动页
        if path == "/hint":
            if self._hint_mobile():
                return self._redirect("/mobile")
            return self._send(200, DESKTOP_HTML)

        # 2) m./www 互踢：m 看到桌面 UA 就跳 www，www 看到移动 hint 就跳 m
        if path == "/m":
            if "Macintosh" in self.headers.get("User-Agent", "") or "Windows" in self.headers.get("User-Agent", ""):
                return self._redirect("/www")
            return self._send(200, MOBILE_HTML)
        if path == "/www":
            if self._hint_mobile():
                return self._redirect("/m")
            return self._send(200, DESKTOP_HTML)

        # 3) 永远互跳，用来验证策略会主动停止
        if path == "/loop-a":
            return self._redirect("/loop-b")
        if path == "/loop-b":
            return self._redirect("/loop-a")

        # 4) 非 HTML，应该被跳过
        if path == "/image":
            return self._send(200, b"\x89PNG\r\n", ctype="image/png")

        # 5) Cookie：设置一个，再回读
        if path == "/set":
            return self._send(200, DESKTOP_HTML, extra={"Set-Cookie": "sid=abc123; Path=/; HttpOnly"})
        if path == "/whoami":
            cookie = self.headers.get("Cookie", "")
            body = ("COOKIE:" + cookie).encode()
            return self._send(200, body)

        return self._send(404, b"not found")


if __name__ == "__main__":
    print(f"desktop mock on {PORT}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
