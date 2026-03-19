from __future__ import annotations

import tkinter as tk
import tkinter.font as tkfont
from tkinter import filedialog, messagebox, ttk
from typing import Any, Optional

from .db import Db, DbError, QueryResult
from .utils import export_csv, to_display


class App(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("SQLiteCat")
        self.geometry("1100x700")
        self.minsize(900, 600)

        self.db = Db()

        self._selected_object: Optional[str] = None
        self._selected_type: Optional[str] = None

        self._build_ui()

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(1, weight=1)

        top = ttk.Frame(self, padding=8)
        top.grid(row=0, column=0, sticky="nsew")
        top.columnconfigure(2, weight=1)

        ttk.Button(top, text="打开数据库", command=self.open_db).grid(row=0, column=0, sticky="w")
        ttk.Button(top, text="关闭", command=self.close_db).grid(row=0, column=1, sticky="w", padx=(8, 0))

        self.path_var = tk.StringVar(value="未连接")
        ttk.Label(top, textvariable=self.path_var).grid(row=0, column=2, sticky="we", padx=(12, 0))

        main = ttk.Panedwindow(self, orient="horizontal")
        main.grid(row=1, column=0, sticky="nsew")

        left = ttk.Frame(main, padding=(8, 8, 4, 8))
        right = ttk.Frame(main, padding=(4, 8, 8, 8))
        main.add(left, weight=1)
        main.add(right, weight=4)

        # 左侧：对象列表（表/视图）
        left.rowconfigure(1, weight=1)
        left.columnconfigure(0, weight=1)
        ttk.Label(left, text="对象").grid(row=0, column=0, sticky="w")

        self.tree = ttk.Treeview(left, columns=("type",), show="tree headings", selectmode="browse", height=18)
        self.tree.heading("#0", text="名称")
        self.tree.heading("type", text="类型")
        self.tree.column("#0", width=260, minwidth=180, stretch=False)
        self.tree.column("type", width=90, minwidth=70, stretch=False, anchor="center")
        self.tree.grid(row=1, column=0, sticky="nsew")
        self.tree.bind("<<TreeviewSelect>>", self._on_select_object)

        ysb = ttk.Scrollbar(left, orient="vertical", command=self.tree.yview)
        xsb = ttk.Scrollbar(left, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=ysb.set, xscrollcommand=xsb.set)
        ysb.grid(row=1, column=1, sticky="ns")
        xsb.grid(row=2, column=0, sticky="we", pady=(6, 0))

        # 右侧：tabs
        right.rowconfigure(0, weight=1)
        right.columnconfigure(0, weight=1)

        self.tabs = ttk.Notebook(right)
        self.tabs.grid(row=0, column=0, sticky="nsew")

        self.data_tab = DataBrowser(self.tabs, self.db, self.get_selected_table)
        self.sql_tab = SqlConsole(self.tabs, self.db)
        self.tabs.add(self.data_tab, text="数据浏览")
        self.tabs.add(self.sql_tab, text="SQL 控制台")

        # 底部状态
        self.status_var = tk.StringVar(value="就绪")
        status = ttk.Label(self, textvariable=self.status_var, anchor="w", padding=(8, 2))
        status.grid(row=2, column=0, sticky="we")

    def set_status(self, msg: str) -> None:
        self.status_var.set(msg)

    def open_db(self) -> None:
        path = filedialog.askopenfilename(
            title="选择 SQLite 数据库",
            filetypes=[
                ("SQLite", "*.db *.sqlite *.sqlite3"),
                ("All files", "*.*"),
            ],
        )
        if not path:
            return
        try:
            self.db.connect(path)
        except DbError as e:
            messagebox.showerror("连接失败", str(e))
            return

        self.path_var.set(path)
        self.set_status("已连接数据库")
        self.refresh_objects()
        self.data_tab.reset()
        self.sql_tab.reset()

    def close_db(self) -> None:
        self.db.close()
        self.path_var.set("未连接")
        self._selected_object = None
        self._selected_type = None
        for i in self.tree.get_children():
            self.tree.delete(i)
        self.data_tab.reset()
        self.sql_tab.reset()
        self.set_status("已关闭数据库")

    def refresh_objects(self) -> None:
        for i in self.tree.get_children():
            self.tree.delete(i)
        if not self.db.connected:
            return
        try:
            objs = self.db.list_objects()
        except DbError as e:
            messagebox.showerror("读取失败", str(e))
            return
        for name, typ in objs:
            self.tree.insert("", "end", iid=f"{typ}:{name}", text=name, values=(typ,))

    def _on_select_object(self, _evt: Any) -> None:
        sel = self.tree.selection()
        if not sel:
            return
        iid = sel[0]
        try:
            typ, name = iid.split(":", 1)
        except ValueError:
            return
        self._selected_object = name
        self._selected_type = typ
        if typ == "table":
            self.data_tab.on_table_selected()
        else:
            self.data_tab.reset()
        self.set_status(f"已选择 {typ}: {name}")

    def get_selected_table(self) -> Optional[str]:
        if self._selected_type == "table":
            return self._selected_object
        return None


class ResultTable(ttk.Frame):
    def __init__(self, master: tk.Misc) -> None:
        super().__init__(master)
        self.rowconfigure(0, weight=1)
        self.columnconfigure(0, weight=1)

        self.tree = ttk.Treeview(self, show="headings", selectmode="browse")
        self.tree.grid(row=0, column=0, sticky="nsew")

        ysb = ttk.Scrollbar(self, orient="vertical", command=self.tree.yview)
        xsb = ttk.Scrollbar(self, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=ysb.set, xscrollcommand=xsb.set)
        ysb.grid(row=0, column=1, sticky="ns")
        xsb.grid(row=1, column=0, sticky="we")

        self._columns: list[str] = []
        self._rows: list[tuple[Any, ...]] = []
        self._measure_font = tkfont.nametofont("TkDefaultFont")

    @property
    def columns(self) -> list[str]:
        return self._columns

    @property
    def rows(self) -> list[tuple[Any, ...]]:
        return self._rows

    def clear(self) -> None:
        for c in self.tree["columns"]:
            self.tree.heading(c, text="")
        self.tree["columns"] = ()
        for i in self.tree.get_children():
            self.tree.delete(i)
        self._columns = []
        self._rows = []

    def set_result(self, res: QueryResult, max_rows: int | None = None) -> None:
        self.clear()
        cols = res.columns
        self._columns = cols
        self._rows = res.rows if max_rows is None else res.rows[:max_rows]

        self.tree["columns"] = cols
        for c in cols:
            self.tree.heading(c, text=c)
            # 让横向滚动生效：不做自动拉伸；列宽按标题/内容估算
            self.tree.column(c, width=self._suggest_col_width(c), minwidth=80, stretch=False, anchor="w")

        for idx, r in enumerate(self._rows):
            values = [to_display(v) for v in r]
            self.tree.insert("", "end", iid=str(idx), values=values)

        # 插入数据后再根据内容微调一次（取前若干行，避免 O(行数*列数)）
        self._autosize_columns(sample_rows=min(200, len(self._rows)))

    def get_selected_row_index(self) -> Optional[int]:
        sel = self.tree.selection()
        if not sel:
            return None
        try:
            return int(sel[0])
        except ValueError:
            return None

    def _suggest_col_width(self, col_name: str) -> int:
        # 标题长度为基准给个初值
        base = self._measure_font.measure(col_name) + 28
        return max(120, min(420, base))

    def _autosize_columns(self, sample_rows: int) -> None:
        if not self._columns or not self._rows:
            return
        pad = 28
        min_w = 120
        max_w = 520
        rows = self._rows[:sample_rows]
        for ci, c in enumerate(self._columns):
            w = self._measure_font.measure(c) + pad
            for r in rows:
                if ci >= len(r):
                    continue
                w = max(w, self._measure_font.measure(to_display(r[ci])) + pad)
                if w >= max_w:
                    w = max_w
                    break
            w = max(min_w, min(max_w, w))
            self.tree.column(c, width=w, minwidth=80, stretch=False)


class DataBrowser(ttk.Frame):
    def __init__(self, master: tk.Misc, db: Db, get_selected_table) -> None:
        super().__init__(master, padding=8)
        self.db = db
        self.get_selected_table = get_selected_table

        self.limit_var = tk.IntVar(value=200)
        self.page_var = tk.IntVar(value=1)
        self.total_var = tk.StringVar(value="")
        self.schema_var = tk.StringVar(value="")

        self._current_total: int = 0
        self._pk_cols: list[str] = []

        self.columnconfigure(0, weight=1)
        self.rowconfigure(2, weight=1)

        top = ttk.Frame(self)
        top.grid(row=0, column=0, sticky="we")
        top.columnconfigure(6, weight=1)

        ttk.Label(top, text="每页").grid(row=0, column=0, sticky="w")
        ttk.Entry(top, textvariable=self.limit_var, width=8).grid(row=0, column=1, sticky="w", padx=(6, 10))
        ttk.Label(top, text="页码").grid(row=0, column=2, sticky="w")
        ttk.Entry(top, textvariable=self.page_var, width=8).grid(row=0, column=3, sticky="w", padx=(6, 10))

        ttk.Button(top, text="刷新", command=self.refresh).grid(row=0, column=4, sticky="w", padx=(0, 6))
        ttk.Button(top, text="导出 CSV", command=self.export_current).grid(row=0, column=5, sticky="w")

        ttk.Label(top, textvariable=self.total_var, anchor="e").grid(row=0, column=6, sticky="we", padx=(12, 0))

        schema = ttk.LabelFrame(self, text="表结构", padding=8)
        schema.grid(row=1, column=0, sticky="nsew", pady=(10, 8))
        schema.columnconfigure(0, weight=1)
        schema.rowconfigure(0, weight=1)

        schema_box = ttk.Frame(schema)
        schema_box.grid(row=0, column=0, sticky="nsew")
        schema_box.columnconfigure(0, weight=1)
        schema_box.rowconfigure(0, weight=1)

        self.schema_text = tk.Text(schema_box, height=5, wrap="none", undo=False)
        self.schema_text.grid(row=0, column=0, sticky="nsew")
        schema_ysb = ttk.Scrollbar(schema_box, orient="vertical", command=self.schema_text.yview)
        schema_xsb = ttk.Scrollbar(schema_box, orient="horizontal", command=self.schema_text.xview)
        self.schema_text.configure(yscrollcommand=schema_ysb.set, xscrollcommand=schema_xsb.set)
        schema_ysb.grid(row=0, column=1, sticky="ns")
        schema_xsb.grid(row=1, column=0, sticky="we", pady=(6, 0))

        self.schema_text.configure(state="disabled")

        self.table = ResultTable(self)
        self.table.grid(row=2, column=0, sticky="nsew")

        ops = ttk.Frame(self)
        ops.grid(row=3, column=0, sticky="we", pady=(8, 0))
        ttk.Button(ops, text="更新选中行…", command=self.update_selected).grid(row=0, column=0, sticky="w")
        ttk.Button(ops, text="删除选中行", command=self.delete_selected).grid(row=0, column=1, sticky="w", padx=(8, 0))

    def reset(self) -> None:
        self.total_var.set("")
        self.schema_var.set("")
        self._set_schema_text("")
        self.table.clear()
        self._current_total = 0
        self._pk_cols = []

    def on_table_selected(self) -> None:
        self.page_var.set(1)
        self.refresh()

    def _load_schema(self, table: str) -> None:
        info = self.db.table_info(table)
        pks = [c["name"] for c in info if int(c.get("pk") or 0) > 0]
        self._pk_cols = self.db.primary_keys(table)

        lines: list[str] = []
        for c in info:
            pk = " PK" if c["name"] in pks else ""
            nn = " NOT NULL" if int(c.get("notnull") or 0) else ""
            dflt = f" DEFAULT {c.get('dflt_value')}" if c.get("dflt_value") is not None else ""
            lines.append(f"- {c['name']}  ({c.get('type') or ''}){pk}{nn}{dflt}")
        pk_line = f"主键: {', '.join(self._pk_cols)}" if self._pk_cols else "主键: (无)"
        text = pk_line + "\n" + "\n".join(lines)
        self.schema_var.set(text)
        self._set_schema_text(text)

    def _set_schema_text(self, text: str) -> None:
        self.schema_text.configure(state="normal")
        self.schema_text.delete("1.0", "end")
        self.schema_text.insert("1.0", text)
        self.schema_text.configure(state="disabled")

    def refresh(self) -> None:
        table = self.get_selected_table()
        if not table:
            self.reset()
            return
        try:
            limit = max(1, int(self.limit_var.get()))
        except Exception:
            limit = 200
            self.limit_var.set(limit)
        try:
            page = max(1, int(self.page_var.get()))
        except Exception:
            page = 1
            self.page_var.set(page)
        offset = (page - 1) * limit

        try:
            self._load_schema(table)
            total = self.db.count_rows(table)
            res = self.db.browse_table(table, limit=limit, offset=offset)
        except DbError as e:
            messagebox.showerror("查询失败", str(e))
            return

        self._current_total = total
        start = offset + 1 if total > 0 else 0
        end = min(offset + limit, total)
        self.total_var.set(f"总行数: {total}    显示: {start}-{end}")
        self.table.set_result(res)

    def export_current(self) -> None:
        if not self.table.columns:
            messagebox.showinfo("提示", "没有可导出的结果")
            return
        path = filedialog.asksaveasfilename(
            title="导出 CSV",
            defaultextension=".csv",
            filetypes=[("CSV", "*.csv"), ("All files", "*.*")],
        )
        if not path:
            return
        try:
            export_csv(path, self.table.columns, self.table.rows)
        except Exception as e:
            messagebox.showerror("导出失败", str(e))
            return
        messagebox.showinfo("导出完成", f"已导出到:\n{path}")

    def _get_selected_row_dict(self) -> Optional[dict[str, Any]]:
        idx = self.table.get_selected_row_index()
        if idx is None:
            return None
        if idx < 0 or idx >= len(self.table.rows):
            return None
        row = self.table.rows[idx]
        return {c: row[i] for i, c in enumerate(self.table.columns)}

    def update_selected(self) -> None:
        table = self.get_selected_table()
        if not table:
            return
        row = self._get_selected_row_dict()
        if row is None:
            messagebox.showinfo("提示", "请先选择一行")
            return
        if not self._pk_cols:
            messagebox.showwarning("无法更新", "该表没有主键，无法按行更新")
            return

        dlg = UpdateDialog(self, table=table, row=row, pk_cols=self._pk_cols)
        self.wait_window(dlg)
        if not dlg.ok:
            return
        updates = dlg.updates
        if not updates:
            return

        try:
            n = self.db.update_by_pk(table, self._pk_cols, row=row, updates=updates)
        except DbError as e:
            messagebox.showerror("更新失败", str(e))
            return
        self.refresh()
        messagebox.showinfo("更新完成", f"受影响行数: {n}")

    def delete_selected(self) -> None:
        table = self.get_selected_table()
        if not table:
            return
        row = self._get_selected_row_dict()
        if row is None:
            messagebox.showinfo("提示", "请先选择一行")
            return
        if not self._pk_cols:
            messagebox.showwarning("无法删除", "该表没有主键，无法按行删除")
            return

        pk_desc = ", ".join([f"{c}={to_display(row.get(c))}" for c in self._pk_cols])
        if not messagebox.askyesno("确认删除", f"确定删除选中行？\n({pk_desc})"):
            return
        try:
            n = self.db.delete_by_pk(table, self._pk_cols, row=row)
        except DbError as e:
            messagebox.showerror("删除失败", str(e))
            return
        self.refresh()
        messagebox.showinfo("删除完成", f"受影响行数: {n}")


class UpdateDialog(tk.Toplevel):
    def __init__(self, master: tk.Misc, table: str, row: dict[str, Any], pk_cols: list[str]) -> None:
        super().__init__(master)
        self.title(f"更新行 - {table}")
        self.resizable(True, True)
        self.geometry("650x420")
        self.ok = False
        self.updates: dict[str, Any] = {}

        self._row = row
        self._pk_cols = set(pk_cols)

        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=1)

        frm = ttk.Frame(self, padding=10)
        frm.grid(row=0, column=0, sticky="nsew")
        frm.columnconfigure(0, weight=1)
        frm.rowconfigure(1, weight=1)

        ttk.Label(frm, text="输入要更新的列值（留空表示不更新该列）。主键列不可编辑。").grid(
            row=0, column=0, sticky="w", pady=(0, 10)
        )

        canvas = tk.Canvas(frm, highlightthickness=0)
        canvas.grid(row=1, column=0, sticky="nsew")
        yscroll = ttk.Scrollbar(frm, orient="vertical", command=canvas.yview)
        xscroll = ttk.Scrollbar(frm, orient="horizontal", command=canvas.xview)
        yscroll.grid(row=1, column=1, sticky="ns")
        xscroll.grid(row=2, column=0, sticky="we", pady=(6, 0))
        canvas.configure(yscrollcommand=yscroll.set, xscrollcommand=xscroll.set)

        inner = ttk.Frame(canvas)
        canvas.create_window((0, 0), window=inner, anchor="nw")
        inner.bind(
            "<Configure>", lambda _e: canvas.configure(scrollregion=canvas.bbox("all"))
        )

        self._vars: dict[str, tk.StringVar] = {}
        r = 0
        for col, val in row.items():
            ttk.Label(inner, text=col).grid(row=r, column=0, sticky="w", padx=(0, 10), pady=4)
            if col in self._pk_cols:
                ttk.Label(inner, text=to_display(val), foreground="#666").grid(row=r, column=1, sticky="we", pady=4)
            else:
                v = tk.StringVar(value="")
                self._vars[col] = v
                e = ttk.Entry(inner, textvariable=v)
                e.grid(row=r, column=1, sticky="we", pady=4)
                hint = ttk.Label(inner, text=f"原值: {to_display(val)}", foreground="#666")
                hint.grid(row=r, column=2, sticky="w", padx=(10, 0), pady=4)
            r += 1
        inner.columnconfigure(1, weight=1)

        btns = ttk.Frame(frm)
        btns.grid(row=3, column=0, sticky="e", pady=(10, 0))
        ttk.Button(btns, text="取消", command=self._cancel).grid(row=0, column=0, padx=(0, 8))
        ttk.Button(btns, text="确定", command=self._ok).grid(row=0, column=1)

        self.transient(master.winfo_toplevel())
        self.grab_set()
        self.protocol("WM_DELETE_WINDOW", self._cancel)

    def _cancel(self) -> None:
        self.ok = False
        self.destroy()

    def _ok(self) -> None:
        updates: dict[str, Any] = {}
        for col, v in self._vars.items():
            txt = v.get()
            if txt == "":
                continue
            # 简单规则：输入 NULL(不区分大小写) -> None；否则按字符串写入
            if txt.strip().lower() == "null":
                updates[col] = None
            else:
                updates[col] = txt
        self.updates = updates
        self.ok = True
        self.destroy()


class SqlConsole(ttk.Frame):
    def __init__(self, master: tk.Misc, db: Db) -> None:
        super().__init__(master, padding=8)
        self.db = db

        self.columnconfigure(0, weight=1)
        self.rowconfigure(2, weight=1)

        bar = ttk.Frame(self)
        bar.grid(row=0, column=0, sticky="we")
        bar.columnconfigure(2, weight=1)

        ttk.Button(bar, text="执行 (Ctrl+Enter)", command=self.run).grid(row=0, column=0, sticky="w")
        ttk.Button(bar, text="导出结果 CSV", command=self.export_current).grid(row=0, column=1, sticky="w", padx=(8, 0))
        self.msg_var = tk.StringVar(value="")
        ttk.Label(bar, textvariable=self.msg_var, anchor="e").grid(row=0, column=2, sticky="we", padx=(12, 0))

        ttk.Label(self, text="SQL").grid(row=1, column=0, sticky="w", pady=(10, 2))

        text_box = ttk.Frame(self)
        text_box.grid(row=2, column=0, sticky="nsew")
        text_box.columnconfigure(0, weight=1)
        text_box.rowconfigure(0, weight=1)

        self.text = tk.Text(text_box, height=8, wrap="none", undo=True)
        self.text.grid(row=0, column=0, sticky="nsew")
        text_ysb = ttk.Scrollbar(text_box, orient="vertical", command=self.text.yview)
        text_xsb = ttk.Scrollbar(text_box, orient="horizontal", command=self.text.xview)
        self.text.configure(yscrollcommand=text_ysb.set, xscrollcommand=text_xsb.set)
        text_ysb.grid(row=0, column=1, sticky="ns")
        text_xsb.grid(row=1, column=0, sticky="we", pady=(6, 0))
        self.text.bind("<Control-Return>", lambda _e: (self.run(), "break"))

        self.result = ResultTable(self)
        self.result.grid(row=3, column=0, sticky="nsew", pady=(10, 0))
        self.rowconfigure(3, weight=2)

        self.reset()

    def reset(self) -> None:
        self.msg_var.set("")
        self.result.clear()
        if self.text.get("1.0", "end").strip() == "":
            self.text.insert("1.0", "SELECT name, type FROM sqlite_master ORDER BY type, name;")

    def run(self) -> None:
        sql = self.text.get("1.0", "end").strip()
        if not sql:
            return
        try:
            res = self.db.execute_script(sql)
        except DbError as e:
            messagebox.showerror("执行失败", str(e))
            return

        if res.columns:
            self.msg_var.set(f"返回 {len(res.rows)} 行")
            self.result.set_result(res, max_rows=5000)
            if len(res.rows) > 5000:
                messagebox.showinfo("提示", "结果行数较多，仅展示前 5000 行（导出不受此限制）")
        else:
            rc = res.rowcount
            self.msg_var.set(f"完成，受影响行数: {rc if rc is not None else '未知'}")
            self.result.clear()

    def export_current(self) -> None:
        if not self.result.columns:
            messagebox.showinfo("提示", "当前没有可导出的结果集")
            return
        path = filedialog.asksaveasfilename(
            title="导出 CSV",
            defaultextension=".csv",
            filetypes=[("CSV", "*.csv"), ("All files", "*.*")],
        )
        if not path:
            return
        try:
            export_csv(path, self.result.columns, self.result.rows)
        except Exception as e:
            messagebox.showerror("导出失败", str(e))
            return
        messagebox.showinfo("导出完成", f"已导出到:\n{path}")


def main() -> None:
    try:
        app = App()
        app.mainloop()
    except Exception as e:
        messagebox.showerror("程序错误", str(e))

