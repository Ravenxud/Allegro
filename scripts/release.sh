#!/usr/bin/env bash
# 用法：GITHUB_TOKEN=xxx ./scripts/release.sh 0.27.1-beta /path/to/Allegro-0.27.1-beta-debug.apk
#
# 干三件事：打 tag → 建 GitHub Release（正文取 RELEASE_NOTES.md 对应小节）→ 上传 APK 资产。
# 注意：**必须 prerelease=false**，否则 App 端的 `releases/latest` 看不到（见 HANDOFF 坑 13）。
set -euo pipefail

VERSION="${1:?用法: release.sh <version> <apk>}"
APK="${2:?缺少 apk 路径}"
REPO="${REPO:-Ravenxud/Allegro}"
TOKEN="${GITHUB_TOKEN:?需要 GITHUB_TOKEN 环境变量}"
TAG="v${VERSION}"

[ -f "$APK" ] || { echo "找不到 $APK"; exit 1; }

echo "==> tag $TAG"
git tag -f "$TAG"
git push -f origin "$TAG"

echo "==> 创建 release"
BODY_FILE=$(mktemp)
python3 - "$VERSION" "$BODY_FILE" <<'PY'
import re, sys
version, out = sys.argv[1], sys.argv[2]
text = open("RELEASE_NOTES.md", encoding="utf-8").read()
# 取 "# <version>" 到下一个 "# <版本号>" 之间
pattern = re.compile(r"^# " + re.escape(version) + r"\b.*?(?=^# \d|\Z)", re.S | re.M)
match = pattern.search(text)
open(out, "w", encoding="utf-8").write(match.group(0).strip() if match else text[:4000])
PY

python3 - "$REPO" "$TOKEN" "$TAG" "Allegro ${VERSION}" "${APK}" "$BODY_FILE" <<'PY'
import json, sys, urllib.request
repo, token, tag, name, apk, body_file = sys.argv[1:7]
body = open(body_file, encoding="utf-8").read()
payload = {
    "tag_name": tag,
    "target_commitish": "main",
    "name": name,
    "body": body,
    "draft": False,
    "prerelease": False,
}
req = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/releases",
    data=json.dumps(payload).encode(),
    headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req) as resp:
    data = json.load(resp)
print("release:", data["html_url"])
open("/tmp/.release_upload_url", "w").write(data["upload_url"].split("{")[0])
PY

UPLOAD=$(cat /tmp/.release_upload_url)
NAME=$(basename "$APK")
echo "==> 上传 $NAME（${UPLOAD}）"
# 说明：这里必须用 --http1.1；HTTP/2 下大文件偶发连接中断，资产会停在 starter 状态
curl -sS --http1.1 --retry 3 --retry-delay 3 \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$APK" \
  "$UPLOAD?name=$NAME" -o /tmp/.release_asset.json

python3 - <<'PY'
import json, hashlib
a = json.load(open("/tmp/.release_asset.json"))
print("asset:", a["name"], a["size"], a["state"], a.get("digest"))
assert a["state"] == "uploaded", "资产未完成上传（state=%s），重跑本脚本" % a["state"]
print("sha256:", hashlib.sha256(open(a["name"], "rb").read()).hexdigest() if False else a.get("digest"))
PY

echo "==> 完成。核对 /releases/latest："
curl -sS -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/$REPO/releases/latest" |
  python3 -c "import sys,json; r=json.load(sys.stdin); print(r['tag_name'], [a['name'] for a in r['assets']])"
