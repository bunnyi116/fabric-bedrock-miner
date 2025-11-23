"""
为 GitHub Action 步骤摘要生成构件摘要表
只显示 fabricWrapper 版本包信息
"""
__author__ = 'bunnyi116, Fallen_Breath'

import functools
import glob
import hashlib
import json
import os

import jproperties


def read_prop(file_name: str, key: str) -> str:
    """从属性文件中读取指定键的值"""
    configs = jproperties.Properties()
    with open(file_name, 'rb') as f:
        configs.load(f)
    return configs[key].data


def get_sha256_hash(file_path: str) -> str:
    """计算文件的 SHA-256 哈希值"""
    sha256_hash = hashlib.sha256()

    with open(file_path, 'rb') as f:
        for buf in iter(functools.partial(f.read, 4096), b''):
            sha256_hash.update(buf)

    return sha256_hash.hexdigest()


def main():
    # 读取设置文件
    with open('settings.json') as f:
        settings: dict = json.load(f)

    # 写入 GitHub Action 步骤摘要
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'w') as f:
        f.write('## 构建构件摘要\n\n')
        f.write('| 构件类型 | 支持的 Minecraft 版本 | 文件 | 大小 | SHA-256 |\n')
        f.write('| --- | --- | --- | --- | --- |\n')

        warnings = []

        # 处理 fabricWrapper（这是我们要发布的版本包）
        fabric_wrapper_paths = glob.glob('build-artifacts/fabricWrapper/build/libs/*.jar')
        fabric_wrapper_paths = list(filter(lambda fp: not fp.endswith('-sources.jar') and not fp.endswith('-dev.jar') and not fp.endswith('-shadow.jar'), fabric_wrapper_paths))

        if len(fabric_wrapper_paths) == 0:
            file_name = '*未找到*'
            file_size = '*N/A*'
            sha256 = '*N/A*'
        else:
            file_name = '`{}`'.format(os.path.basename(fabric_wrapper_paths[0]))
            file_size = '{} B'.format(os.path.getsize(fabric_wrapper_paths[0]))
            sha256 = '`{}`'.format(get_sha256_hash(fabric_wrapper_paths[0]))
            if len(fabric_wrapper_paths) > 1:
                warnings.append('在 fabricWrapper 中找到过多构建文件: {}'.format(', '.join(fabric_wrapper_paths)))

        # 获取 fabricWrapper 支持的所有 Minecraft 版本
        all_game_versions = []
        for subproject in settings['versions']:
            try:
                game_versions = read_prop('versions/{}/gradle.properties'.format(subproject), 'game_versions')
                versions_list = [v.strip() for v in game_versions.split(',')]
                all_game_versions.extend(versions_list)
            except:
                pass

        # 去重并排序
        unique_versions = sorted(set(all_game_versions))
        fabric_wrapper_versions = ', '.join(unique_versions) if unique_versions else '*N/A*'

        # 突出显示 fabricWrapper 版本包
        f.write('| **fabricWrapper 版本包** | **{}** | **{}** | **{}** | **{}** |\n'.format(
            fabric_wrapper_versions, file_name, file_size, sha256))

        # 添加发布说明
        f.write('\n### 发布说明\n\n')
        f.write('**fabricWrapper 版本包会被发布到 Modrinth、CurseForge 等平台。**\n\n')
        f.write('fabricWrapper 是一个包含所有 Minecraft 版本支持的统一版本包，用户只需下载这一个文件即可在所有支持的 Minecraft 版本上使用。\n\n')

        if len(warnings) > 0:
            f.write('\n### 警告\n\n')
            for warning in warnings:
                f.write('- {}\n'.format(warning))


if __name__ == '__main__':
    main()