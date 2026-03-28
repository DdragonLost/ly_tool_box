extends Node3D
## 试玩场景：应用存档位置、按块记录探索并写检查点。

@onready var _player: CharacterBody3D = $Player
@onready var _streamer: Node3D = $WorldStreamer

var _last_chunk: Vector2i = Vector2i(999999, 999999)


func _ready() -> void:
	call_deferred("_bootstrap_world_state")


func _bootstrap_world_state() -> void:
	if not is_instance_valid(_player) or not is_instance_valid(_streamer):
		return
	var spawn := SaveManager.peek_playground_spawn()
	if spawn != null:
		_player.global_position = spawn

	var cs := _streamer.get("chunk_size")
	var chunk_size: float = float(cs) if cs != null else 24.0
	var cx := int(floor(_player.global_position.x / chunk_size))
	var cz := int(floor(_player.global_position.z / chunk_size))
	_last_chunk = Vector2i(cx, cz)
	SaveManager.mark_chunk_explored(_last_chunk)
	SaveManager.save_playground_checkpoint(_player.global_position)


func _physics_process(_delta: float) -> void:
	if not is_instance_valid(_player) or not is_instance_valid(_streamer):
		return
	if DialogueManager.is_dialogue_open():
		return

	var cs2 := _streamer.get("chunk_size")
	var chunk_size: float = float(cs2) if cs2 != null else 24.0
	var p := _player.global_position
	var cx := int(floor(p.x / chunk_size))
	var cz := int(floor(p.z / chunk_size))
	var key := Vector2i(cx, cz)
	if key != _last_chunk:
		_last_chunk = key
		SaveManager.mark_chunk_explored(key)
		SaveManager.save_playground_checkpoint(p)


func _exit_tree() -> void:
	Game.clear_mobile_state()
	Game.interact_target = null
	if is_instance_valid(_player):
		SaveManager.save_playground_checkpoint(_player.global_position)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		if DialogueManager.is_dialogue_open():
			DialogueManager.close_dialogue()
			get_viewport().set_input_as_handled()
			return
		get_viewport().set_input_as_handled()
		Game.go_to_main_menu()
