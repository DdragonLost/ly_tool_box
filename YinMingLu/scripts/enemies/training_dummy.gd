extends CharacterBody3D
## 木桩敌人：仅承伤与显示血量，便于验证打击盒。


@export var max_hp: int = 100

var _hp: int = 100

@onready var _label: Label3D = $Label3D


func _ready() -> void:
	add_to_group(Game.GROUP_ENEMY)
	collision_layer = 4
	collision_mask = 1
	_hp = max_hp
	_refresh_label()


func take_damage(amount: int) -> void:
	_hp = maxi(_hp - amount, 0)
	_refresh_label()
	if _hp <= 0:
		_on_broken()


func _refresh_label() -> void:
	_label.text = "木桩 %d / %d" % [_hp, max_hp]


func _on_broken() -> void:
	_label.text = "已击破"
