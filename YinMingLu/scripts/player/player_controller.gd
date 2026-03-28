extends CharacterBody3D

signal primary_attack_triggered()

@export var move_speed: float = 6.0
@export var gravity_multiplier: float = 1.0
@export var primary_attack_cooldown: float = 0.12
@export var melee_damage: int = 14
@export var melee_hitbox_duration: float = 0.14

@onready var _camera: Camera3D = $CameraPivot/Camera3D
@onready var _attack_pivot: Node3D = $AttackPivot
@onready var _melee_hitbox: Area3D = $AttackPivot/MeleeHitbox

var _primary_attack_cd: float = 0.0


func _ready() -> void:
	add_to_group(Game.GROUP_PLAYER)
	_camera.make_current()


func _unhandled_input(event: InputEvent) -> void:
	if DialogueManager.is_dialogue_open():
		if event.is_action_pressed("ui_accept") or event.is_action_pressed("interact"):
			Game.try_interact()
			get_viewport().set_input_as_handled()
		return
	if event.is_action_pressed("interact"):
		Game.try_interact()
		get_viewport().set_input_as_handled()


func _physics_process(delta: float) -> void:
	var g := ProjectSettings.get_setting("physics/3d/default_gravity") as float
	if DialogueManager.is_dialogue_open():
		Game.interact_target = null
		if not is_on_floor():
			velocity.y -= g * gravity_multiplier * delta
		velocity.x = 0.0
		velocity.z = 0.0
		move_and_slide()
		return

	_update_interact_target()

	if not is_on_floor():
		velocity.y -= g * gravity_multiplier * delta

	var input_v: Vector2
	if Game.mobile_move_active:
		input_v = Game.mobile_move_vector
	else:
		input_v = Input.get_vector("move_left", "move_right", "move_forward", "move_back")

	var dir := Vector3(input_v.x, 0.0, input_v.y)
	if dir.length_squared() > 0.0001:
		dir = dir.normalized()
		velocity.x = dir.x * move_speed
		velocity.z = dir.z * move_speed
	else:
		velocity.x = move_toward(velocity.x, 0.0, move_speed)
		velocity.z = move_toward(velocity.z, 0.0, move_speed)

	move_and_slide()

	if _primary_attack_cd > 0.0:
		_primary_attack_cd -= delta

	var from_mouse := Input.is_action_just_pressed("attack")
	var from_touch := Game.consume_touch_attack()
	if _primary_attack_cd <= 0.0 and (from_mouse or from_touch):
		_primary_attack_cd = primary_attack_cooldown
		_trigger_primary_attack()


func _trigger_primary_attack() -> void:
	_aim_attack_pivot()
	primary_attack_triggered.emit()
	var hb := _melee_hitbox as MeleeHitbox
	if hb:
		hb.damage = melee_damage
		hb.begin_swing(melee_hitbox_duration)


func _aim_attack_pivot() -> void:
	var dir := Vector3(0.0, 0.0, -1.0)
	var hvel := Vector3(velocity.x, 0.0, velocity.z)
	if hvel.length_squared() > 0.04:
		dir = hvel.normalized()
	else:
		dir = (-global_transform.basis.z).normalized()
		dir.y = 0.0
		if dir.length_squared() < 0.001:
			dir = Vector3(0.0, 0.0, -1.0)
		else:
			dir = dir.normalized()
	var chest := global_position + Vector3(0.0, 0.9, 0.0)
	_attack_pivot.global_position = chest
	_attack_pivot.look_at(chest + dir, Vector3.UP)


func _update_interact_target() -> void:
	var best: Node = null
	var best_d2 := INF
	for n in get_tree().get_nodes_in_group(Game.GROUP_NPC):
		if n.has_method("offers_interact") and bool(n.call("offers_interact")):
			var d2 := global_position.distance_squared_to((n as Node3D).global_position)
			if d2 < best_d2:
				best_d2 = d2
				best = n
	Game.interact_target = best
