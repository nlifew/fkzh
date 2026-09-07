#!/system/bin/sh
# post-fs-data 阶段：重建 systemless 的 /system/etc/hosts
#
# 这里不直接改真正的 /system/etc/hosts（现代 Android 上 /system 只读且受
# dm-verity 保护，改了会破坏校验）。做法是在模块目录下放一份 system/etc/hosts，
# KernelSU 会在挂载阶段把它覆盖到 /system/etc/hosts 上，重启即失效、卸载即还原。
#
# 本阶段早于模块挂载执行，所以此时读到的 /system/etc/hosts 是系统原始文件。

MODDIR=${0%/*}

SRC_HOSTS=/system/etc/hosts
OUT_HOSTS=$MODDIR/system/etc/hosts
DOMAIN=www.zhihu.com
TARGET_IP=127.0.0.1

mkdir -p "${OUT_HOSTS%/*}"

# 以系统原始 hosts 为基底重建，保证系统更新后本模块不会覆盖掉新增条目
if [ -r "$SRC_HOSTS" ]; then
    cp -f "$SRC_HOSTS" "$OUT_HOSTS"
else
    # 极少数设备没有该文件，给一份最小可用的
    printf '127.0.0.1\tlocalhost\n::1\t\tip6-localhost\n' > "$OUT_HOSTS"
fi

# 万一基底里已经带了该域名（比如挂载顺序变化导致读到的是上一轮的产物），
# 先剔除旧条目再追加，避免重复堆积
TMP_HOSTS=$OUT_HOSTS.tmp
grep -v -i -E "^[[:space:]]*[0-9a-fA-F.:]+[[:space:]]+${DOMAIN}[[:space:]]*$" "$OUT_HOSTS" > "$TMP_HOSTS" 2>/dev/null
mv -f "$TMP_HOSTS" "$OUT_HOSTS"

# 末尾追加目标条目
printf '%s\t%s\n' "$TARGET_IP" "$DOMAIN" >> "$OUT_HOSTS"

# 权限与 SELinux 上下文必须和原文件一致，否则挂载后应用读不到
chown 0:0 "$OUT_HOSTS" 2>/dev/null
chmod 0644 "$OUT_HOSTS" 2>/dev/null
chcon --reference="$SRC_HOSTS" "$OUT_HOSTS" 2>/dev/null ||
    chcon u:object_r:system_file:s0 "$OUT_HOSTS" 2>/dev/null
