extends CanvasLayer
## 由 DialogueManager 持有；不抢全局输入，翻页由 Game.try_interact / 按钮触发。

signal dialogue_closed

var _lines: PackedStringArray = []
var _idx: int = 0

@onready var _body: Label = %LblBody
@onready var _btn: Button = %BtnNext


func _ready() -> void:
	visible = false
	layer = 200
	process_mode = Node.PROCESS_MODE_ALWAYS
	_btn.pressed.connect(_on_next_pressed)


func present(lines: PackedStringArray) -> void:
	_lines = lines.duplicate()
	_idx = 0
	visible = true
	_refresh_label()
	_update_button_caption()


func advance() -> void:
	if not visible:
		return
	_on_next_pressed()


func force_close() -> void:
	if visible:
		_close()


func _on_next_pressed() -> void:
	_idx += 1
	if _idx >= _lines.size():
		_close()
	else:
		_refresh_label()
		_update_button_caption()


func _refresh_label() -> void:
	if _lines.is_empty():
		return
	_body.text = _lines[_idx]


func _update_button_caption() -> void:
	var last := _idx >= _lines.size() - 1
	_btn.text = "关闭" if last else "下一句"


func _close() -> void:
	visible = false
	_lines.clear()
	_idx = 0
	dialogue_closed.emit()
