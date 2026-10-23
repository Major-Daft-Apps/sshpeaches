#!/bin/bash
# Copies the NETCTL lines LiveNetworkResilienceTest logs (tag NetCtl) into netproxy.py's control file.
# ADB (the adb command for the test device) and NETPROXY_DIR (default: this script's directory) come from the caller.
NETPROXY_DIR=${NETPROXY_DIR:-$(cd "$(dirname "$0")" && pwd)}
ADB=${ADB:-adb}
$ADB logcat -c
$ADB logcat -v raw -s NetCtl:I | while read -r line; do
  case "$line" in \{*) echo "$line" > "$NETPROXY_DIR/netctl.json.tmp" && mv "$NETPROXY_DIR/netctl.json.tmp" "$NETPROXY_DIR/netctl.json"; echo "$(date +%T) $line" >> "$NETPROXY_DIR/netctl-applied.log";; esac
done
