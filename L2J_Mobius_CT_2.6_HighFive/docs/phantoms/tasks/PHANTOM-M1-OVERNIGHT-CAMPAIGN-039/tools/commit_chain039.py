"""Структура цепочки одного SQL-компонента. НЕ подтверждает native FULL_SAVE.

Нормализатор обязан отдельно проверить raw SHA, источник COMMITTED и реальные поля.
Этот пример ничего не читает из БД, не чинит payload и не придумывает промежуточные версии.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path
from typing import Any, Mapping, Sequence

_SHA = re.compile(r'[0-9a-f]{64}\Z')
_KEY = ('database', 'profileId', 'objectId', 'component')


def _integer(value: Any) -> bool:
    return type(value) is int and 0 <= value < (1 << 63)


def _key(row: Mapping[str, Any]) -> tuple:
    if not isinstance(row, Mapping):
        raise ValueError('Запись не является объектом')
    if not all(isinstance(row.get(k), str) and row[k] for k in ('database', 'component')):
        raise ValueError('Нет namespace записи')
    if not all(_integer(row.get(k)) and row[k] > 0 for k in ('profileId', 'objectId')):
        raise ValueError('Нет точной identity')
    return tuple(row[k] for k in _KEY)


def _hash(value: Any) -> bool:
    return isinstance(value, str) and _SHA.fullmatch(value) is not None


def check_chain(origin: Mapping[str, Any], latest: Mapping[str, Any],
                commits: Sequence[Mapping[str, Any]], *, maximum_links: int = 256) -> dict[str, Any]:
    """Проверяет только непрерывность версий/hash; не достаточность игровых доказательств."""
    def result(status: str, reason: str, count: int = 0) -> dict[str, Any]:
        return dict(scope='STRUCTURAL_CHAIN_ONLY', status=status, reason=reason,
                    links=count, fullSaveProven=False)
    try:
        if not _integer(maximum_links) or not 1 <= maximum_links <= 4096:
            return result('INVALID', 'Недопустимый бюджет цепочки')
        if not isinstance(commits, (list, tuple)) or len(commits) > maximum_links:
            return result('INVALID', 'Превышен бюджет входа')
        identity = _key(origin)
        if _key(latest) != identity:
            return result('INVALID', 'Другая identity/БД/component')
        for row in (origin, latest):
            if not _integer(row.get('version')) or not _hash(row.get('digest')):
                return result('INVALID', 'Некорректная версия или digest')
        first, end = origin['version'], latest['version']
        if end < first or end - first > maximum_links:
            return result('INVALID', 'Обратная версия или слишком длинная цепочка')
        by_version: dict[int, Mapping[str, Any]] = {}
        operations: set[str] = set()
        for row in commits:
            if _key(row) != identity:
                return result('INVALID', 'Witness принадлежит другой identity')
            if row.get('status') != 'COMMITTED':
                return result('INVALID', 'PREPARED/UNKNOWN не являются commit')
            if not all(isinstance(row.get(k), str) and row[k].strip()
                       for k in ('operationId', 'producer', 'evidenceId')):
                return result('INVALID', 'Witness без provenance')
            a, b = row.get('beforeVersion'), row.get('afterVersion')
            if not _integer(a) or not _integer(b) or b != a + 1:
                return result('INVALID', 'Непоследовательные версии witness')
            if not _hash(row.get('beforeDigest')) or not _hash(row.get('afterDigest')):
                return result('INVALID', 'Некорректный hash witness')
            if not first <= a < end:
                return result('INVALID', 'Witness вне заявленного интервала')
            if a in by_version or row['operationId'] in operations:
                return result('CONFLICT', 'Ветвление/повтор witness, требуется разбор raw')
            by_version[a] = row
            operations.add(row['operationId'])
        digest, count = origin['digest'], 0
        for version in range(first, end):
            row = by_version.get(version)
            if row is None:
                return result('GAP', f'Нет подтверждённого перехода от версии {version}', count)
            if row['beforeDigest'] != digest:
                return result('CONFLICT', f'Несовпадение before digest на версии {version}', count)
            digest = row['afterDigest']
            count += 1
        if digest != latest['digest']:
            return result('CONFLICT', 'Конечный payload не связан с исходным', count)
        return result('EXACT' if count == 0 else 'LINKED', 'Только структура цепочки совпала', count)
    except (KeyError, ValueError, TypeError) as exc:
        return result('INVALID', str(exc))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.input.stat().st_size > 4 * 1024 * 1024:
        parser.error('Вход больше 4 MiB; нормализуйте один identity/interval')
    data = json.loads(args.input.read_text(encoding='utf-8-sig'))
    verdict = check_chain(data['origin'], data['latest'], data['commits'])
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(verdict, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps(verdict, ensure_ascii=False))
    return 0 if verdict['status'] in ('EXACT', 'LINKED') else 2


if __name__ == '__main__':
    raise SystemExit(main())
