"""扫描 versions 目录生成构建矩阵 JSON，输出到 GitHub Actions matrix。"""
__author__ = 'Fallen_Breath'

import json
import os
import sys


def main():
    target_subproject_env = os.environ.get('TARGET_SUBPROJECT', '')
    target_subprojects = set(filter(None, target_subproject_env.split(',')))
    print(f'目标子项目: {target_subprojects}')

    with open('settings.json') as f:
        settings: dict = json.load(f)

    all_versions = set(settings['versions'])

    if not target_subprojects:
        subprojects = list(settings['versions'])
    else:
        # 用集合差集一次性检测非法输入
        unknown = target_subprojects - all_versions
        if unknown:
            print(f'未知子项目: {unknown}', file=sys.stderr)
            sys.exit(1)
        subprojects = [v for v in settings['versions'] if v in target_subprojects]

    matrix = {
        'include': [{'subproject': s} for s in subprojects],
    }

    matrix_json = json.dumps(matrix)
    with open(os.environ['GITHUB_OUTPUT'], 'w') as f:
        f.write(f'matrix={matrix_json}\n')

    print('matrix:')
    print(json.dumps(matrix, indent=2))


if __name__ == '__main__':
    main()
