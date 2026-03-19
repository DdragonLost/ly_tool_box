from __future__ import annotations

import csv
from typing import Any, Iterable


def to_display(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (bytes, bytearray, memoryview)):
        b = bytes(value)
        if len(b) <= 64:
            return "0x" + b.hex()
        return "0x" + b[:64].hex() + f"...({len(b)} bytes)"
    return str(value)


def export_csv(path: str, columns: list[str], rows: Iterable[Iterable[Any]]) -> None:
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(columns)
        for r in rows:
            w.writerow(list(r))

