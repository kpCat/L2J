"""Propose evenly spaced points; only the stock native VERIFY can authorize MOVE."""
import argparse
import hashlib
import json
import math
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("--input", type=Path, required=True)
p.add_argument("--output", type=Path, required=True)
p.add_argument("--pins", type=int, nargs="*", default=[])
a = p.parse_args()
allowed = (Path(__file__).resolve().parents[1] / "evidence").resolve()
for path in (a.input, a.output):
    if not path.resolve().is_relative_to(allowed):
        raise ValueError("Own032 evidence paths required")
points = json.loads(a.input.read_text(encoding="utf-8-sig"))["points"]
proposed = [points[0]]
boundaries = sorted(set([0, *a.pins, len(points) - 1]))
if boundaries[0] != 0 or boundaries[-1] != len(points) - 1:
    raise ValueError("Pin outside actual input")
for left, right in zip(boundaries, boundaries[1:]):
    remaining = 280.0
    for start, end in zip(points[left:right], points[left + 1:right + 1]):
        cursor = dict(start)
        length = math.hypot(end["x"] - cursor["x"], end["y"] - cursor["y"])
        while length >= remaining:
            ratio = remaining / length
            cursor = {axis: cursor[axis] + ratio * (end[axis] - cursor[axis]) for axis in ("x", "y", "z")}
            proposed.append({axis: round(cursor[axis]) for axis in cursor})
            length -= remaining
            remaining = 280.0
        remaining -= length
    if proposed[-1] != points[right]:
        proposed.append(points[right])
if 2 * (len(proposed) - 1) > 40:
    raise ValueError("Proposal exceeds40 total native steps")
with a.output.open("x", encoding="utf-8") as stream:
    for point in proposed:
        stream.write(f'{point["x"]} {point["y"]} {point["z"]}\n')
print(json.dumps(dict(inputSha256=hashlib.sha256(a.input.read_bytes()).hexdigest(),
                     proposedSteps=len(proposed) - 1, nativeValidated=False)))
