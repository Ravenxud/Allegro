#!/usr/bin/env python3
"""用本机 Chromium 通过 CDP 抓一份真实的 MHTML（Page.captureSnapshot）。

WebView 的 saveWebArchive 与桌面 Chrome 用的是同一套 Chromium MHTML 生成器，
所以这份产物能代表真机上的报文格式 —— 用它来验证 MhtmlArchive 的解析。

依赖：只有标准库（自己实现最小 WebSocket 客户端）。
"""
import base64
import json
import os
import socket
import struct
import subprocess
import sys
import time
import urllib.request

CHROME = "/root/.cache/ms-playwright/chromium-1134/chrome-linux/chrome"
PORT = 9333


class WS:
    def __init__(self, url: str):
        _, rest = url.split("://", 1)
        hostport, path = rest.split("/", 1)
        host, port = hostport.split(":")
        self.sock = socket.create_connection((host, int(port)), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        req = (
            f"GET /{path} HTTP/1.1\r\nHost: {hostport}\r\nUpgrade: websocket\r\n"
            f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        )
        self.sock.sendall(req.encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += self.sock.recv(4096)
        assert b"101" in buf.split(b"\r\n")[0], buf[:200]

    def send(self, obj):
        payload = json.dumps(obj).encode()
        header = bytearray([0x81])
        mask = os.urandom(4)
        length = len(payload)
        if length < 126:
            header.append(0x80 | length)
        elif length < 65536:
            header.append(0x80 | 126)
            header += struct.pack(">H", length)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", length)
        header += mask
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(bytes(header) + masked)

    def recv(self):
        data = self._read_exact(2)
        length = data[1] & 0x7F
        if length == 126:
            length = struct.unpack(">H", self._read_exact(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", self._read_exact(8))[0]
        payload = self._read_exact(length)
        return json.loads(payload)

    def _read_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("closed")
            buf += chunk
        return buf

    def call(self, msg_id, method, params=None):
        self.send({"id": msg_id, "method": method, "params": params or {}})
        while True:
            msg = self.recv()
            if msg.get("id") == msg_id:
                if "error" in msg:
                    raise RuntimeError(msg["error"])
                return msg.get("result", {})


def main():
    page_url = sys.argv[1]
    out = sys.argv[2]
    profile = "/tmp/chrome-dav-profile"
    chrome = subprocess.Popen(
        [
            CHROME,
            "--headless=new",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            f"--user-data-dir={profile}",
            f"--remote-debugging-port={PORT}",
            "about:blank",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        ws_url = None
        for _ in range(60):
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=2) as resp:
                    targets = json.load(resp)
                pages = [t for t in targets if t.get("type") == "page"]
                if pages:
                    ws_url = pages[0]["webSocketDebuggerUrl"]
                    break
            except Exception:
                pass
            time.sleep(0.5)
        if not ws_url:
            raise SystemExit("chrome 没起来")

        ws = WS(ws_url)
        ws.call(1, "Page.enable")
        ws.call(2, "Page.navigate", {"url": page_url})
        time.sleep(1.5)
        result = ws.call(3, "Page.captureSnapshot", {"format": "mhtml"})
        data = result["data"]
        with open(out, "w", newline="") as fh:
            fh.write(data)
        print(f"已写入 {out}（{len(data)} 字符）")
    finally:
        chrome.terminate()


if __name__ == "__main__":
    main()
