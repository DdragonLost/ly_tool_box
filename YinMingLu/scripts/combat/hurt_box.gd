extends Area3D
## 挂在可受伤单位下，layer=hurtbox；伤害由 MeleeHitbox 检测。


func _ready() -> void:
	collision_layer = 16
	collision_mask = 0
	monitorable = true
	monitoring = false
	add_to_group(Game.GROUP_HURTBOX)
