class_name MeleeHitbox
extends Area3D
## 普攻打击盒：短时开启，仅与 hurtbox 层重叠结算一次伤害。

@export var damage: int = 12

var _hit_this_swing: Dictionary = {}
var _swing_id: int = 0


func _ready() -> void:
	collision_layer = 8
	collision_mask = 16
	monitoring = false
	monitorable = false
	area_entered.connect(_on_area_entered)
	_collision_shape().disabled = true


func _collision_shape() -> CollisionShape3D:
	return $CollisionShape3D as CollisionShape3D


func begin_swing(duration: float) -> void:
	_swing_id += 1
	var sid := _swing_id
	_hit_this_swing.clear()
	monitoring = true
	_collision_shape().disabled = false
	call_deferred("_scan_existing_overlaps")
	get_tree().create_timer(duration).timeout.connect(func() -> void:
		if sid != _swing_id:
			return
		_end_swing()
	)


func _end_swing() -> void:
	monitoring = false
	_collision_shape().disabled = true


func _scan_existing_overlaps() -> void:
	for a in get_overlapping_areas():
		_on_area_entered(a)


func _on_area_entered(area: Area3D) -> void:
	if not area.is_in_group(Game.GROUP_HURTBOX):
		return
	var victim: Node = area.get_parent()
	if victim == null:
		return
	var id := victim.get_instance_id()
	if _hit_this_swing.has(id):
		return
	_hit_this_swing[id] = true
	if victim.has_method("take_damage"):
		victim.call("take_damage", damage)
