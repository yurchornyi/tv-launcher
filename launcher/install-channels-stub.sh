#!/bin/sh
# Installs the channels stub (channels-stub/) and removes the stock Google TV launcher for the user, data kept.
# Why: SmartTube publishes its home-screen channels (what the top shelf shows for it) only while a launcher it
# knows is installed; the stub carries one of those names, so Google's launcher no longer has to stay, disabled,
# where something re-enabled it once (docs/TROUBLESHOOTING.md). Undo: tools/tv.py 'pm install-existing --user 0
# com.google.android.apps.tv.launcherx' and 'pm uninstall com.tv.leanback.launcher'.
# Usage: TV_HOST=<ip> launcher/install-channels-stub.sh
set -e
cd "$(dirname "$0")"
PY=../tools/venv/bin/python
[ -x "$PY" ] || PY=python3
./gradlew :channels-stub:assembleRelease -q --console=plain
APK=channels-stub/build/outputs/apk/release/channels-stub-release.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/channels-stub.apk
# tv.py exits 0 whatever the TV answers, so the install's own "Success" is what is checked.
OUT=$("$PY" -I ../tools/tv.py 'pm install -r /data/local/tmp/channels-stub.apk; rm -f /data/local/tmp/channels-stub.apk')
echo "$OUT"
case "$OUT" in
  *Success*) ;;
  *) echo "ERROR: the stub did not install; the stock launcher was left alone." >&2; exit 1 ;;
esac
# Removing the stock launcher while it still owns Home leaves the TV with none (Google TV's setup then loops).
HOME_PKG=$("$PY" -I ../tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
  | tail -n 1 | tr -d '\r' | cut -d/ -f1)
if [ "$HOME_PKG" != com.gathin.tvhome ]; then
  echo "ERROR: Home is $HOME_PKG, not this launcher; the stock launcher was left alone (launcher/build-and-install.sh first)." >&2
  exit 1
fi
"$PY" -I ../tools/tv.py 'pm uninstall -k --user 0 com.google.android.apps.tv.launcherx'
echo "Stub installed, stock launcher removed for the user. SmartTube refreshes its channels within 15 minutes."
