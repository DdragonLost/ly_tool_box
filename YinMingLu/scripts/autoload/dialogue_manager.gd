extends Node
## 极简对话：多行文本 +「下一句」；不暂停场景树，仅由玩家与 UI 自行忽略输入。

var is_open: bool = false

var _panel: CanvasLayer


func _ready() -> void:
	_panel = load("res://scenes/ui/dialogue_panel.tscn").instantiate() as CanvasLayer
	add_child(_panel)
	_panel.dialogue_closed.connect(_on_dialogue_closed)


func show_lines(lines: PackedStringArray) -> void:
	if lines.is_empty():
		return
	is_open = true
	_panel.present(lines)


func advance_line() -> void:
	if not is_open:
		return
	_panel.advance()


func close_dialogue() -> void:
	if not is_open:
		return
	_panel.force_close()


func is_dialogue_open() -> bool:
	return is_open


func _on_dialogue_closed() -> void:
	is_open = false
