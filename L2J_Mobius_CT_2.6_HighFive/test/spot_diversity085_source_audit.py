"""Read-only source inventory for 085; never attests native loader/Geo/combat eligibility.

Usage: python test/spot_diversity085_source_audit.py <module> <external-output>
Only exact polygon source geometry and NPC-bound point anchors are associated.
Routes are authored BACKGROUND graph hints; NORMAL GK is deliberately NOT_RUN.
"""
import collections
import csv
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def cycle(vertices):
    vertices = tuple(vertices)
    return min(vertices[i:] + vertices[:i] for i in range(len(vertices))) if vertices else ()


def geometry(element, vertex_tag):
    vertices = tuple((int(v.attrib['x']), int(v.attrib['y'])) for v in element.findall(vertex_tag))
    return (int(element.attrib['minZ']), int(element.attrib['maxZ']), min(cycle(vertices), cycle(tuple(reversed(vertices)))))


def run(module, output):
    module, output = module.resolve(), output.resolve()
    if output.is_relative_to(module.parent):
        raise ValueError('Output must be outside the checkout.')
    output.mkdir(parents=True, exist_ok=False)
    data = module / 'dist/game/data'
    hashes, trees = {}, {}
    for family in ('phantoms/topology', 'phantoms/knowledge', 'phantoms/travel', 'stats/npcs', 'spawns', 'mapregion'):
        lines = []
        for path in sorted((data / family).rglob('*.xml')):
            relative = path.relative_to(data).as_posix()
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            hashes[relative] = digest
            lines.append(f'{relative}\t{digest}\n')
        trees[family] = {'files': len(lines), 'sha256': hashlib.sha256(''.join(lines).encode()).hexdigest()}
    nodes, anchors, edges = {}, {}, []
    for path in sorted((data / 'phantoms/topology').glob('*.xml')):
        root = ET.parse(path).getroot()
        for node in root.findall('node'):
            nodes[node.attrib['id']] = node
        for anchor in root.findall('anchor'):
            anchors[anchor.attrib['id']] = anchor
        edges.extend(root.findall('edge'))
    farms = {key: a for key, a in anchors.items() if a.attrib['role'] == 'FARMING' and a.attrib.get('instanceId', '0') == '0'}
    polygons = collections.defaultdict(list)
    bound_points = collections.defaultdict(list)
    for key, anchor in farms.items():
        node = nodes[anchor.attrib['nodeId']]
        if 'npcId' in anchor.attrib:
            bound_points[int(anchor.attrib['npcId'])].append((key, anchor))
        elif node.attrib['form'] == 'POLYGON':
            if max((int(anchor.attrib['x']) - int(v.attrib['x'])) ** 2 + (int(anchor.attrib['y']) - int(v.attrib['y'])) ** 2 for v in node.findall('vertex')) <= 4_000_000:
                for source in node.findall('source'):
                    polygons[(source.attrib['path'], geometry(node, 'vertex'))].append(key)
    npcs = {}
    for path in sorted((data / 'stats/npcs').glob('*.xml')):
        for npc in ET.parse(path).getroot().findall('npc'):
            status, rewards = npc.find('status'), npc.find('rewards')
            status = {} if status is None else status.attrib
            rewards = {} if rewards is None else rewards.attrib
            if npc.attrib.get('type') in ('Monster', 'RiftInvader') and status.get('attackable', 'true') == 'true' and status.get('targetable', 'true') == 'true':
                npcs[int(npc.attrib['id'])] = (int(npc.attrib.get('level', 85)), float(rewards.get('exp', 0)), float(rewards.get('sp', 0)))
    pairs = collections.Counter()
    rejects = collections.Counter()
    for path in sorted((data / 'spawns').rglob('*.xml')):
        root = ET.parse(path).getroot()
        source = 'data/' + path.relative_to(data).as_posix()
        if root.attrib.get('enabled', 'true') != 'true':
            rejects['disabled_files'] += 1
            continue
        for spawn in root.findall('spawn'):
            if spawn.find('cond') is not None or spawn.find('condition') is not None or spawn.attrib.get('instanceId', '0') != '0':
                rejects['conditional_or_instance_groups'] += 1
                continue
            territory = spawn.find('territory')
            matched = [] if territory is None else polygons.get((source, geometry(territory, 'node')), [])
            for npc in spawn.findall('npc'):
                npc_id, amount = int(npc.attrib['id']), int(npc.attrib.get('count', 1))
                if npc_id not in npcs or amount <= 0:
                    continue
                for key in matched:
                    pairs[npc_id, key] += amount
                if all(axis in npc.attrib for axis in ('x', 'y', 'z')):
                    for key, anchor in bound_points.get(npc_id, []):
                        squared = sum((int(npc.attrib[axis]) - int(anchor.attrib[axis])) ** 2 for axis in ('x', 'y', 'z'))
                        tolerance = int(anchor.attrib.get('tolerance', 0))
                        # This is a source association only; normalized-point evidence remains native NOT_RUN.
                        if squared <= max(tolerance, 4) ** 2:
                            pairs[npc_id, key] += amount
    graph = collections.defaultdict(list)
    for edge in edges:
        a = edge.attrib
        if a['mode'] == 'BACKGROUND' and a.get('backgroundEligible') == 'true' and a.get('fromAnchorId') and a.get('toAnchorId'):
            graph[a['fromAnchorId']].append(a['toAnchorId'])
            if a.get('bidirectional') == 'true':
                graph[a['toAnchorId']].append(a['fromAnchorId'])
    ingresses = ['giran.city.center']
    for family in ('human-fighter', 'elf', 'orc'):
        values = sorted(key for key in anchors if key.startswith('population.ingress.' + family + '.'))
        if values:
            ingresses.append(values[0])
    rows = []
    for ingress in ingresses:
        if ingress not in anchors:
            rejects['missing_ingress:' + ingress] += 1
            continue
        reachable, queue = {ingress}, collections.deque([ingress])
        while queue:
            for target in graph[queue.popleft()]:
                if target not in reachable:
                    reachable.add(target)
                    queue.append(target)
        for level in (1, 10, 20, 40, 52, 61, 76, 80, 85):
            for radius in (2, 5, 10):
                candidates = [(npc, key) for npc, key in pairs if max(1, level - radius) <= npcs[npc][0] <= level + 2]
                routed = [(npc, key) for npc, key in candidates if key in reachable]
                selected = {key for _, key in routed}
                rows.append((level, ingress, radius, len(candidates), len({key for _, key in candidates}), len(routed), len({npc for npc, _ in routed}), len(selected), len({anchors[key].attrib['nodeId'] for key in selected}), len({anchors[key].attrib.get('mapRegionLocId', 'UNKNOWN') for key in selected}), 'NATIVE_ELIGIBILITY_NOT_RUN' if routed else 'NO_SOURCE_ROUTE_CANDIDATES'))
    with (output / 'inventory.tsv').open('w', encoding='utf-8', newline='') as stream:
        writer = csv.writer(stream, delimiter='\t')
        writer.writerow(('level', 'ingress', 'lowerRadius', 'sourcePairs', 'sourceAnchors', 'backgroundGraphPairs', 'npcIds', 'anchors', 'nodes', 'regionsIncludingUnknown', 'eligibility'))
        writer.writerows(rows)
    (output / 'source-hashes.json').write_text(json.dumps(hashes, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    summary = {'trees': trees, 'nodes': len(nodes), 'anchors': len(anchors), 'edges': len(edges), 'farmAnchors': len(farms), 'sourceAssociatedPairs': len(pairs), 'sourceAssociatedAnchors': len({key for _, key in pairs}), 'ingresses': ingresses, 'rejected': dict(rejects), 'productionCorpus': 'PRODUCTION_CORPUS_NOT_RUN: existing native loader route requires DB bootstrap; no substitute validation backend', 'limits': 'Source XML associations and authored BACKGROUND graph only; no runtime spawn conditions, mostSpecific point projection, NORMAL GK, Geo, equipment, reward policy, class or combat attestation.'}
    (output / 'summary.json').write_text(json.dumps(summary, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2, sort_keys=True))


if __name__ == '__main__':
    run(Path(sys.argv[1]), Path(sys.argv[2]))
