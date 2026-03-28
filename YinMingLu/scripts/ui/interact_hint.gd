extends CanvasLayer
## 桌面端提示：范围内可交谈时显示（移动端依赖「谈」按钮）。


@onready var _root: Control = $Root
@onready var _label: Label = %LblHint


func _ready() -> void:
	layer = 90
	process_mode = Node.PROCESS_MODE_ALWAYS


func _process(_delta: float) -> void:
	var dlg := DialogueManager.is_dialogue_open()
	var show := Game.interact_target != null and not dlg
	_root.visible = show
	if show:
		_label.text = "按 E 交谈"
