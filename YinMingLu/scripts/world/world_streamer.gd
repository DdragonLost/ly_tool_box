extends Node3D
## 暗黑2 式大地图：按网格分块，只保留玩家周围若干格，走出边界再加载相邻块、卸载远处块。

@export var chunk_scene: PackedScene
@export var chunk_registry: ChunkRegistry
@export var default_npc_scene: PackedScene
@export var chunk_size: float = 24.0
@export var load_radius: int = 2
@export var unload_padding: int = 1

var _loaded: Dictionary = {}


func _physics_process(_delta: float) -> void:
	if chunk_scene == null:
		return
	var player := get_tree().get_first_node_in_group(Game.GROUP_PLAYER) as Node3D
	if player == null:
		return

	var p := player.global_position
	var cx := int(floor(p.x / chunk_size))
	var cz := int(floor(p.z / chunk_size))
	var r := load_radius
	var unload_dist := r + unload_padding

	for dz in range(-r, r + 1):
		for dx in range(-r, r + 1):
			var key := Vector2i(cx + dx, cz + dz)
			if not _loaded.has(key):
				_spawn_chunk(key)

	var to_drop: Array[Vector2i] = []
	for key in _loaded.keys():
		var d := maxi(abs(key.x - cx), abs(key.y - cz))
		if d > unload_dist:
			to_drop.append(key)

	for key in to_drop:
		_free_chunk(key)


func _spawn_chunk(key: Vector2i) -> void:
	var root := chunk_scene.instantiate() as Node3D
	var wx := float(key.x) * chunk_size + chunk_size * 0.5
	var wz := float(key.y) * chunk_size + chunk_size * 0.5
	root.position = Vector3(wx, 0.0, wz)
	if root.has_method("setup"):
		root.call("setup", chunk_size, key)
	add_child(root)
	_loaded[key] = root
	_apply_chunk_definition(key, root)


func _free_chunk(key: Vector2i) -> void:
	var node: Node = _loaded[key]
	_loaded.erase(key)
	node.queue_free()


func _apply_chunk_definition(key: Vector2i, chunk_root: Node3D) -> void:
	if chunk_registry == null:
		return
	var def := chunk_registry.get_definition(key)
	if def == null:
		return
	var has_npcs := not def.npc_spawns.is_empty()
	var has_enemies := not def.enemy_spawns.is_empty()
	if not has_npcs and not has_enemies:
		return

	var holder := Node3D.new()
	holder.name = "ChunkContent"
	chunk_root.add_child(holder)

	if has_npcs:
		for spawn in def.npc_spawns:
			_spawn_chunk_entity(holder, spawn, default_npc_scene, key, "NPC")
	if has_enemies:
		for spawn in def.enemy_spawns:
			_spawn_chunk_entity(holder, spawn, null, key, "Enemy")


func _spawn_chunk_entity(
	holder: Node3D,
	spawn: ChunkNpcSpawn,
	default_scene: PackedScene,
	key: Vector2i,
	kind: String
) -> void:
	var pack: PackedScene = spawn.npc_scene if spawn.npc_scene != null else default_scene
	if pack == null:
		push_warning("WorldStreamer: chunk %s %s spawn has no scene." % [key, kind])
		return
	var inst := pack.instantiate() as Node3D
	if inst == null:
		push_warning("WorldStreamer: chunk %s %s root must be Node3D." % [key, kind])
		return
	inst.position = spawn.local_position
	inst.rotation_degrees.y = spawn.rotation_y_degrees
	holder.add_child(inst)
