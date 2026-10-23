#!/usr/bin/env python3
"""CI 构建前补丁：注入 release 签名配置、改写 versionName、修正 chaquo 的 Python 路径。

用法: python3 .github/ci/patch_build.py <versionTag>
仅作用于 CI 工作区，不改动仓库内容。
"""
import os
import re
import sys

SIGNING_BLOCK = """
    signingConfigs {
        release {
            storeFile file(RELEASE_STORE_FILE)
            storePassword RELEASE_STORE_PASSWORD
            keyAlias RELEASE_KEY_ALIAS
            keyPassword RELEASE_KEY_PASSWORD
            v1SigningEnabled true
            v2SigningEnabled true
        }
    }
"""


def patch_app(tag):
    path = os.path.join("app", "build.gradle")
    s = open(path, encoding="utf-8").read().replace("\r\n", "\n")

    if "signingConfigs {" not in s:
        if "android {" not in s:
            raise SystemExit("app/build.gradle: 找不到 'android {' 块")
        s = s.replace("android {", "android {" + SIGNING_BLOCK, 1)

    # 在 debug / release 两个 buildType 里挂上签名配置
    if "signingConfig signingConfigs.release" not in s:
        s, n = re.subn(
            r"(?m)^([ \t]*)(minifyEnabled[ \t]+(?:true|false))[ \t]*$",
            r"\1\2\n\1signingConfig signingConfigs.release",
            s,
            count=2,
        )
        if n == 0:
            raise SystemExit("app/build.gradle: 找不到 minifyEnabled 行，无法注入 signingConfig")

    # versionName -> 构建标签
    if tag:
        s, n = re.subn(r"(?m)^(\s*versionName\s+)'[^']*'", r"\1'" + tag + "'", s, count=1)
        if n == 0:
            print("warn: 未找到 versionName，跳过")

    open(path, "w", encoding="utf-8", newline="\n").write(s)
    print("patched app/build.gradle")


def patch_pyramid():
    path = os.path.join("pyramid", "build.gradle")
    if not os.path.exists(path):
        return
    s = open(path, encoding="utf-8").read().replace("\r\n", "\n")
    new, n = re.subn(
        r'buildPython\(\s*"[^"]*"\s*\)',
        'buildPython("/usr/bin/python3")',
        s,
        count=1,
    )
    if n:
        open(path, "w", encoding="utf-8", newline="\n").write(new)
        print("patched pyramid/build.gradle (buildPython -> /usr/bin/python3)")
    else:
        print("pyramid/build.gradle: 未找到 buildPython，跳过")


if __name__ == "__main__":
    patch_app(sys.argv[1] if len(sys.argv) > 1 else "")
    patch_pyramid()