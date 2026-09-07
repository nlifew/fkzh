#!/system/bin/sh
# late_start service 阶段：给 Chrome 装 iptables 重定向规则
#
#   com.android.chrome --tcp--> 127.0.0.1:443   ==>   127.0.0.1:8000
#
# 规则放在自建链 ZHIHU_REDIRECT 里，再从 nat OUTPUT 跳进来。这样反复执行不会
# 叠加规则，卸载时也只要删掉这一条链即可，不会误伤系统自己的规则。

MODDIR=${0%/*}

PKG=com.android.chrome
CHAIN=ZHIHU_REDIRECT
DST_IP=127.0.0.1
DST_PORT=443
TO_PORT=8000
LOG=/data/local/tmp/zhihu-redirect.log

log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }

echo "===== $(date) 模块启动 =====" > "$LOG"

# ---------- 1. 等开机完成 ----------
# 这一步不能省：包管理器没起来就查不到 Chrome 的 uid，网络栈没起来 iptables 也
# 可能被 netd 后续的初始化冲掉。
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 180 ]; do
    sleep 1
    i=$((i + 1))
done
log "sys.boot_completed=$(getprop sys.boot_completed)（等待 ${i}s）"
sleep 5

# ---------- 2. 定位 iptables ----------
IPT_BIN=$(command -v iptables 2>/dev/null)
[ -n "$IPT_BIN" ] || IPT_BIN=/system/bin/iptables
if [ ! -x "$IPT_BIN" ]; then
    log "错误: 找不到可执行的 iptables"
    exit 1
fi

# -w 等待 xtables 锁，避免和 netd 抢锁失败；老版本不支持则退回不带 -w
ipt() {
    "$IPT_BIN" -w 5 "$@" 2>>"$LOG" || "$IPT_BIN" "$@" 2>>"$LOG"
}

# ---------- 3. 查 Chrome 的 uid ----------
# 主用户下的 uid，优先读 packages.list（不依赖 pm 服务），失败再问包管理器
base_uid=$(awk -v p="$PKG" '$1 == p { print $2; exit }' /data/system/packages.list 2>/dev/null)
if [ -z "$base_uid" ]; then
    base_uid=$(cmd package list packages -U 2>/dev/null |
               awk -F'uid:' -v p="package:$PKG" '$1 ~ p"[ ]*$" { gsub(/[^0-9]/, "", $2); print $2; exit }')
fi
if [ -z "$base_uid" ]; then
    log "错误: 查不到 $PKG 的 uid，模块未生效（Chrome 装了吗？）"
    exit 1
fi

# 分身/工作资料下同一个包的 uid = 用户号 * 100000 + appid，一并覆盖
appid=$((base_uid % 100000))
UIDS=""
for d in /data/user/*; do
    u=${d##*/}
    case "$u" in
        '' | *[!0-9]*) continue ;;
    esac
    [ -d "$d/$PKG" ] || continue
    UIDS="$UIDS $((u * 100000 + appid))"
done
[ -n "$UIDS" ] || UIDS=$base_uid
log "$PKG 的 uid:$UIDS"

# ---------- 4. 装规则 ----------
ipt -t nat -N "$CHAIN" 2>/dev/null   # 已存在则忽略
ipt -t nat -F "$CHAIN"

for uid in $UIDS; do
    ipt -t nat -A "$CHAIN" -p tcp -d "$DST_IP" --dport "$DST_PORT" \
        -m owner --uid-owner "$uid" -j REDIRECT --to-ports "$TO_PORT" &&
        log "已添加: uid=$uid $DST_IP:$DST_PORT -> :$TO_PORT"
done

# 挂到 nat OUTPUT 链首，已挂过就不重复挂
if ! ipt -t nat -C OUTPUT -j "$CHAIN" 2>/dev/null; then
    ipt -t nat -I OUTPUT 1 -j "$CHAIN" && log "已把 $CHAIN 挂到 nat OUTPUT"
else
    log "$CHAIN 已在 nat OUTPUT 中"
fi

log "当前规则:"
"$IPT_BIN" -t nat -S "$CHAIN" >> "$LOG" 2>&1

# ---------- 5. 顺带确认 hosts 生效 ----------
if grep -q -i 'www\.zhihu\.com' /system/etc/hosts 2>/dev/null; then
    log "hosts 已生效: $(grep -i 'www\.zhihu\.com' /system/etc/hosts)"
else
    log "警告: /system/etc/hosts 里没看到 www.zhihu.com，检查模块挂载是否成功"
fi

log "完成"
