"""TASK025 adapter of the existing exact SEALED/native SQL verifier."""
from pathlib import Path

source = Path(__file__).resolve().parent.parent / "PHANTOM-M1-RUNTIME-CONTRACTS-024" / "Verify-Restart024.py"
body = source.read_text(encoding="utf-8-sig")
body = body.replace(".startswith('l2jmobiush5_localplay_contract024')", ".startswith('l2jmobiush5_localplay_contract025')")
body = body.replace("r['owner']=='TASK024_CONTRACT'", "r['owner'] in ('TASK024_CONTRACT','TASK025_CONTRACT')")
exec(compile(body, str(source), "exec"))
