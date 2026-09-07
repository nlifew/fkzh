#!/bin/bash
# 打包成可刷入的 zip。模块文件必须位于 zip 根目录，不能多一层父目录。
set -euo pipefail

cd "$(dirname "$0")"

NAME=$(awk -F= '$1 == "id" { print $2 }' module.prop)
VER=$(awk -F= '$1 == "version" { print $2 }' module.prop)
OUT="../${NAME}-${VER}.zip"

rm -f "$OUT"

# 排除 macOS 的元数据文件，否则刷入时会出现 __MACOSX 之类的垃圾
find . -name '.DS_Store' -delete
zip -r9 "$OUT" . \
    -x '.*' -x '*/.*' -x 'build.sh' -x '__MACOSX/*' >/dev/null

echo "已生成: $(cd .. && pwd)/$(basename "$OUT")"
unzip -l "$OUT"
