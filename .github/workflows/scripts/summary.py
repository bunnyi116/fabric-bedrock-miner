"""扫描 build-artifacts 中的 mod jars，生成 GitHub Actions 步骤摘要表格。"""
__author__ = 'Fallen_Breath'

import glob
import hashlib
import json
import os


def read_prop(file_path: str, key: str) -> str:
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            stripped = line.strip()
            if stripped and not stripped.startswith('#') and '=' in stripped:
                k, v = stripped.split('=', 1)
                if k.strip() == key:
                    return v.strip()
    raise KeyError(f'在 {file_path} 中找不到键: {key}')


def get_sha256_hash(file_path: str) -> str:
    with open(file_path, 'rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def main():
    target_subproject_env = os.environ.get('TARGET_SUBPROJECT', '')
    target_subprojects = set(filter(None, target_subproject_env.split(',')))
    print(f'目标子项目: {target_subprojects}')

    with open('settings.json') as f:
        settings: dict = json.load(f)

    excluded_suffixes = ('-sources.jar', '-dev.jar', '-shadow.jar')
    warnings = []
    rows = []

    for subproject in settings['versions']:
        if target_subprojects and subproject not in target_subprojects:
            print(f'跳过 {subproject}')
            continue

        game_versions = read_prop(
            f'versions/{subproject}/gradle.properties', 'game_versions'
        )
        game_versions = game_versions.replace('\r', '').replace('\n', ', ').strip()

        file_paths = [
            fp for fp in glob.glob(f'build-artifacts/{subproject}/build/libs/*.jar')
            if not fp.endswith(excluded_suffixes)
        ]

        if not file_paths:
            file_name = '*not found*'
            file_size = '*N/A*'
            sha256 = '*N/A*'
        else:
            file_name = f'`{os.path.basename(file_paths[0])}`'
            file_size = f'{os.path.getsize(file_paths[0]):,} B'
            sha256 = f'`{get_sha256_hash(file_paths[0])}`'
            if len(file_paths) > 1:
                warnings.append(
                    f'子项目 {subproject} 中发现过多构建文件: {", ".join(file_paths)}'
                )

        rows.append(f'| {subproject} | {game_versions} | {file_name} | {file_size} | {sha256} |')

    with open(os.environ['GITHUB_STEP_SUMMARY'], 'w') as f:
        f.write('## 构建产物摘要\n\n')
        f.write('| 项目 | 版本 | 文件 | 大小 | SHA-256 |\n')
        f.write('| --- | --- | --- | --- | --- |\n')
        for row in rows:
            f.write(row + '\n')

        if warnings:
            f.write('\n### 警告\n\n')
            for warning in warnings:
                f.write(f'- {warning}\n')


if __name__ == '__main__':
    main()
