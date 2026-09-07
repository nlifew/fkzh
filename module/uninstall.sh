#!/system/bin/sh
# 卸载模块时清掉 iptables 规则。
# hosts 是 systemless 挂载的，模块目录一删、重启后自动还原，无需处理。

CHAIN=ZHIHU_REDIRECT
IPT_BIN=$(command -v iptables 2>/dev/null)
[ -n "$IPT_BIN" ] || IPT_BIN=/system/bin/iptables
[ -x "$IPT_BIN" ] || exit 0

ipt() { "$IPT_BIN" -w 5 "$@" 2>/dev/null || "$IPT_BIN" "$@" 2>/dev/null; }

# 先摘掉 OUTPUT 里的跳转，再清空并删除自建链
while ipt -t nat -C OUTPUT -j "$CHAIN" 2>/dev/null; do
    ipt -t nat -D OUTPUT -j "$CHAIN" || break
done
ipt -t nat -F "$CHAIN"
ipt -t nat -X "$CHAIN"

rm -f /data/local/tmp/zhihu-redirect.log
