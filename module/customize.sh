SKIPUNZIP=0

ui_print " "
ui_print "- Zhihu Redirect"
ui_print "  1) www.zhihu.com -> 127.0.0.1"
ui_print "  2) Chrome 的 127.0.0.1:443 -> 127.0.0.1:8000"
ui_print " "

# 先按当前系统的 hosts 生成一份，保证首次挂载时文件和目录就位；
# 之后每次开机由 post-fs-data.sh 重新生成
mkdir -p "$MODPATH/system/etc"
if [ -r /system/etc/hosts ]; then
    cp -f /system/etc/hosts "$MODPATH/system/etc/hosts"
else
    printf '127.0.0.1\tlocalhost\n::1\t\tip6-localhost\n' > "$MODPATH/system/etc/hosts"
fi
grep -q -i 'www\.zhihu\.com' "$MODPATH/system/etc/hosts" ||
    printf '127.0.0.1\twww.zhihu.com\n' >> "$MODPATH/system/etc/hosts"

# 检查 Chrome 是否安装，只提示不拦截安装
if [ -z "$(awk '$1 == "com.android.chrome" { print $2 }' /data/system/packages.list 2>/dev/null)" ]; then
    ui_print "! 没检测到 com.android.chrome，iptables 规则开机时会跳过"
    ui_print " "
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/system/etc/hosts" 0 0 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

ui_print "- 安装完成，重启后生效"
ui_print "- 运行日志: /data/local/tmp/zhihu-redirect.log"
ui_print " "
