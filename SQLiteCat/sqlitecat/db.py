from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from typing import Any, Iterable, Optional


class DbError(RuntimeError):
    pass


@dataclass(frozen=True)
class QueryResult:
    columns: list[str]
    rows: list[tuple[Any, ...]]
    rowcount: Optional[int] = None


class Db:
    def __init__(self) -> None:
        self._conn: Optional[sqlite3.Connection] = None
        self.path: Optional[str] = None

    @property
    def connected(self) -> bool:
        return self._conn is not None

    def connect(self, path: str) -> None:
        self.close()
        try:
            conn = sqlite3.connect(path)
        except sqlite3.Error as e:
            raise DbError(str(e)) from e

        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON;")
        self._conn = conn
        self.path = path

    def close(self) -> None:
        if self._conn is not None:
            try:
                self._conn.close()
            finally:
                self._conn = None
                self.path = None

    def _require(self) -> sqlite3.Connection:
        if self._conn is None:
            raise DbError("未连接数据库")
        return self._conn

    def list_objects(self) -> list[tuple[str, str]]:
        """
        返回 (name, type) 列表，type in ('table','view')，排除 sqlite_ 内置对象。
        """
        conn = self._require()
        cur = conn.execute(
            """
            SELECT name, type
            FROM sqlite_master
            WHERE type IN ('table','view')
              AND name NOT LIKE 'sqlite_%'
            ORDER BY type, name
            """
        )
        return [(r["name"], r["type"]) for r in cur.fetchall()]

    def table_info(self, table: str) -> list[dict[str, Any]]:
        conn = self._require()
        cur = conn.execute(f"PRAGMA table_info({quote_ident(table)});")
        return [dict(r) for r in cur.fetchall()]

    def primary_keys(self, table: str) -> list[str]:
        info = self.table_info(table)
        pks = [c["name"] for c in info if int(c.get("pk") or 0) > 0]
        # PRAGMA table_info pk 顺序用 pk 值表示（1..n）
        pks.sort(key=lambda name: next(int(c["pk"]) for c in info if c["name"] == name))
        return pks

    def count_rows(self, table: str) -> int:
        conn = self._require()
        cur = conn.execute(f"SELECT COUNT(*) AS n FROM {quote_ident(table)};")
        return int(cur.fetchone()["n"])

    def browse_table(self, table: str, limit: int, offset: int) -> QueryResult:
        conn = self._require()
        sql = f"SELECT * FROM {quote_ident(table)} LIMIT ? OFFSET ?;"
        cur = conn.execute(sql, (int(limit), int(offset)))
        rows = cur.fetchall()
        cols = [d[0] for d in cur.description] if cur.description else []
        return QueryResult(columns=cols, rows=[tuple(r) for r in rows])

    def execute_script(self, sql: str) -> QueryResult:
        """
        执行一段 SQL（允许多语句）。若最后一条语句产生结果集，则返回该结果集。
        否则返回 rowcount（受 sqlite3 限制，可能为 -1）。
        """
        conn = self._require()
        statements = split_sql_statements(sql)
        if not statements:
            return QueryResult(columns=[], rows=[], rowcount=0)

        last_cols: list[str] = []
        last_rows: list[tuple[Any, ...]] = []
        last_rowcount: Optional[int] = None

        try:
            with conn:
                for stmt in statements:
                    cur = conn.execute(stmt)
                    if cur.description:
                        last_cols = [d[0] for d in cur.description]
                        last_rows = [tuple(r) for r in cur.fetchall()]
                        last_rowcount = None
                    else:
                        last_cols = []
                        last_rows = []
                        last_rowcount = cur.rowcount
        except sqlite3.Error as e:
            raise DbError(str(e)) from e

        return QueryResult(columns=last_cols, rows=last_rows, rowcount=last_rowcount)

    def execute_query(self, sql: str, params: Iterable[Any] = ()) -> QueryResult:
        conn = self._require()
        try:
            cur = conn.execute(sql, tuple(params))
            if cur.description:
                cols = [d[0] for d in cur.description]
                rows = [tuple(r) for r in cur.fetchall()]
                return QueryResult(columns=cols, rows=rows)
            return QueryResult(columns=[], rows=[], rowcount=cur.rowcount)
        except sqlite3.Error as e:
            raise DbError(str(e)) from e

    def update_by_pk(self, table: str, pk_cols: list[str], row: dict[str, Any], updates: dict[str, Any]) -> int:
        """
        根据主键更新一行。row 提供原始行（用于取主键值）。
        """
        if not pk_cols:
            raise DbError("该表没有主键，无法按行更新")
        if not updates:
            return 0

        conn = self._require()
        set_cols = list(updates.keys())
        set_expr = ", ".join([f"{quote_ident(c)} = ?" for c in set_cols])
        where_expr = " AND ".join([f"{quote_ident(c)} = ?" for c in pk_cols])
        sql = f"UPDATE {quote_ident(table)} SET {set_expr} WHERE {where_expr};"

        params = [updates[c] for c in set_cols] + [row[c] for c in pk_cols]
        try:
            with conn:
                cur = conn.execute(sql, params)
                return cur.rowcount
        except sqlite3.Error as e:
            raise DbError(str(e)) from e

    def delete_by_pk(self, table: str, pk_cols: list[str], row: dict[str, Any]) -> int:
        if not pk_cols:
            raise DbError("该表没有主键，无法按行删除")

        conn = self._require()
        where_expr = " AND ".join([f"{quote_ident(c)} = ?" for c in pk_cols])
        sql = f"DELETE FROM {quote_ident(table)} WHERE {where_expr};"
        params = [row[c] for c in pk_cols]
        try:
            with conn:
                cur = conn.execute(sql, params)
                return cur.rowcount
        except sqlite3.Error as e:
            raise DbError(str(e)) from e


def quote_ident(name: str) -> str:
    # SQLite 标识符双引号转义
    return '"' + name.replace('"', '""') + '"'


def split_sql_statements(sql: str) -> list[str]:
    """
    轻量切分 SQL 语句（按分号），忽略字符串字面量中的分号。
    不追求覆盖所有 SQL 方言边界，只用于 GUI 控制台的多语句执行。
    """
    s = sql.strip()
    if not s:
        return []

    out: list[str] = []
    buf: list[str] = []
    in_single = False
    in_double = False
    i = 0
    while i < len(s):
        ch = s[i]
        buf.append(ch)
        if ch == "'" and not in_double:
            # 处理 '' 转义
            if i + 1 < len(s) and s[i + 1] == "'":
                buf.append(s[i + 1])
                i += 1
            else:
                in_single = not in_single
        elif ch == '"' and not in_single:
            # 处理 "" 转义
            if i + 1 < len(s) and s[i + 1] == '"':
                buf.append(s[i + 1])
                i += 1
            else:
                in_double = not in_double
        elif ch == ";" and not in_single and not in_double:
            stmt = "".join(buf).strip()
            if stmt and stmt != ";":
                out.append(stmt)
            buf = []
        i += 1

    tail = "".join(buf).strip()
    if tail:
        out.append(tail)
    return out

