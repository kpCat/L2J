"""Check every original owning-commit edge in bounded4096-version pages.

Pages are a read budget, not new witnesses. The unchanged039 checker sees the
actual raw commit records and their exact boundary hashes. No edge is invented.
"""
from commit_chain039 import check_chain


def check_complete_chain(origin, latest, commits, *, maximum_links=4096):
    if len(commits) <= maximum_links and latest['version'] - origin['version'] <= maximum_links:
        return check_chain(origin, latest, commits, maximum_links=maximum_links)
    def invalid(reason):
        return dict(scope='STRUCTURAL_CHAIN_ONLY', status='INVALID', reason=reason,
                    links=0, fullSaveProven=False)
    if maximum_links != 4096 or latest['version'] < origin['version']:
        return invalid('Existing4096 page budget or version direction differs')
    ordered = sorted(commits, key=lambda row: row['beforeVersion'])
    operations = set()
    for row in ordered:
        operation = row.get('operationId')
        if operation in operations:
            return invalid('Repeated actual operation across complete input')
        operations.add(operation)
        if not origin['version'] <= row['beforeVersion'] < latest['version']:
            return invalid('Original edge lies outside exact component endpoints')
    position, current, count = 0, dict(origin), 0
    while current['version'] < latest['version']:
        end = min(current['version'] + maximum_links, latest['version'])
        page = []
        while position < len(ordered) and ordered[position]['beforeVersion'] < end:
            page.append(ordered[position]); position += 1
        if not page or len(page) > maximum_links:
            return invalid('Missing or oversized original page')
        boundary = dict(latest) if end == latest['version'] else {
            **{key: origin[key] for key in ('database', 'profileId', 'objectId', 'component')},
            'version': page[-1]['afterVersion'], 'digest': page[-1]['afterDigest']}
        if boundary['version'] != end:
            return invalid('Original page does not reach its exact version boundary')
        checked = check_chain(current, boundary, page, maximum_links=maximum_links)
        if checked['status'] not in ('EXACT', 'LINKED'):
            return checked
        count += checked['links']; current = boundary
    if position != len(ordered) or current != dict(latest):
        return invalid('Unconsumed actual edge or final endpoint mismatch')
    return dict(scope='STRUCTURAL_CHAIN_ONLY', status='LINKED',
                reason='All original owning-commit edges checked; page budget4096 unchanged',
                links=count, fullSaveProven=False)
