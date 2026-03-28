extends Control
## 左下角虚拟摇杆：触屏与鼠标（便于桌面调试）共用。

@export var max_radius: float = 72.0
@export var deadzone: float = 0.14

@onready var _knob: Control = $Knob

var _grabbed: bool = false
## 触屏 finger index；鼠标左键用 -2。
var _grab_index: int = -1


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	resized.connect(_on_resized)
	call_deferred("_sync_center")
	call_deferred("_reset_visual")


func _on_resized() -> void:
	_sync_center()
	if not _grabbed:
		_reset_visual()


func _sync_center() -> void:
	_center = size * 0.5


var _center: Vector2 = Vector2.ZERO


func _gui_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		var st := event as InputEventScreenTouch
		if st.pressed:
			if _grabbed:
				return
			_grabbed = true
			_grab_index = st.index
			_apply_position(st.position)
		elif st.index == _grab_index:
			_release()
	elif event is InputEventScreenDrag:
		var sd := event as InputEventScreenDrag
		if _grabbed and sd.index == _grab_index:
			_apply_position(sd.position)
	elif event is InputEventMouseButton:
		var mb := event as InputEventMouseButton
		if mb.button_index != MOUSE_BUTTON_LEFT:
			return
		if mb.pressed:
			if _grabbed:
				return
			_grabbed = true
			_grab_index = -2
			_apply_position(mb.position)
		elif _grab_index == -2:
			_release()
	elif event is InputEventMouseMotion:
		if _grabbed and _grab_index == -2:
			var mm := event as InputEventMouseMotion
			_apply_position(mm.position)


func _apply_position(local_pos: Vector2) -> void:
	var offset := local_pos - _center
	var len := offset.length()
	if len > max_radius and len > 0.0001:
		offset = offset * (max_radius / len)
		len = max_radius
	_knob.position = _center + offset - _knob.size * 0.5
	var analog := offset / max_radius if max_radius > 0.0001 else Vector2.ZERO
	if analog.length() < deadzone:
		analog = Vector2.ZERO
	Game.set_mobile_move(analog)


func _release() -> void:
	_grabbed = false
	_grab_index = -1
	_reset_visual()
	Game.clear_mobile_move()


func _reset_visual() -> void:
	_knob.position = _center - _knob.size * 0.5
