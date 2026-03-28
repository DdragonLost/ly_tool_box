extends CanvasLayer
## 移动端 HUD：摇杆 + 普攻 + 交谈。桌面可通过 export 强制显示以便调试。

@export var force_show_for_desktop_debug: bool = false

@onready var _attack: Button = %BtnAttack
@onready var _interact: Button = %BtnInteract


func _ready() -> void:
	layer = 100
	var show_ui := (
		DisplayServer.is_touchscreen_available()
		or OS.has_feature("mobile")
		or force_show_for_desktop_debug
	)
	visible = show_ui
	_attack.pressed.connect(_on_attack_pressed)
	_interact.pressed.connect(_on_interact_pressed)


func _process(_delta: float) -> void:
	if not is_visible_in_tree():
		return
	var dlg := DialogueManager.is_dialogue_open()
	_attack.disabled = dlg
	if dlg:
		_interact.disabled = false
		_interact.text = "续"
	else:
		_interact.disabled = Game.interact_target == null
		_interact.text = "谈"


func _on_attack_pressed() -> void:
	Game.request_touch_attack()


func _on_interact_pressed() -> void:
	Game.try_interact()
