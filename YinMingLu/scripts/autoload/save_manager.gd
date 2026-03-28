extends Node
## 用户目录：音量设置 + 世界进度（已探索块、试玩场景最后位置）。

const SETTINGS_PATH := "user://settings.cfg"
const PROGRESS_PATH := "user://progress_v1.cfg"

const SEC_AUDIO := "audio"
const KEY_MASTER_LINEAR := "master_linear"
const KEY_BGM_LINEAR := "bgm_linear"
const KEY_SFX_LINEAR := "sfx_linear"

const SEC_EXP := "exploration"
const SEC_CKPT := "checkpoint"
const KEY_CHUNKS := "chunks"
const KEY_SCENE := "scene"
const KEY_PX := "pos_x"
const KEY_PY := "pos_y"
const KEY_PZ := "pos_z"

var _explored: Dictionary = {}


func _ready() -> void:
	apply_audio_from_settings()
	load_progress()


func save_settings() -> void:
	var cfg := ConfigFile.new()
	cfg.load(SETTINGS_PATH)
	cfg.set_value(SEC_AUDIO, KEY_MASTER_LINEAR, db_to_linear(AudioServer.get_bus_volume_db(AudioServer.get_bus_index(AudioManager.BUS_MASTER))))
	var bgm_i := AudioServer.get_bus_index(AudioManager.BUS_BGM)
	var sfx_i := AudioServer.get_bus_index(AudioManager.BUS_SFX)
	if bgm_i >= 0:
		cfg.set_value(SEC_AUDIO, KEY_BGM_LINEAR, db_to_linear(AudioServer.get_bus_volume_db(bgm_i)))
	if sfx_i >= 0:
		cfg.set_value(SEC_AUDIO, KEY_SFX_LINEAR, db_to_linear(AudioServer.get_bus_volume_db(sfx_i)))
	cfg.save(SETTINGS_PATH)


func apply_audio_from_settings() -> void:
	var cfg := ConfigFile.new()
	if cfg.load(SETTINGS_PATH) != OK:
		return
	if cfg.has_section_key(SEC_AUDIO, KEY_MASTER_LINEAR):
		AudioManager.set_bus_volume_linear(AudioManager.BUS_MASTER, cfg.get_value(SEC_AUDIO, KEY_MASTER_LINEAR))
	if cfg.has_section_key(SEC_AUDIO, KEY_BGM_LINEAR):
		AudioManager.set_bus_volume_linear(AudioManager.BUS_BGM, cfg.get_value(SEC_AUDIO, KEY_BGM_LINEAR))
	if cfg.has_section_key(SEC_AUDIO, KEY_SFX_LINEAR):
		AudioManager.set_bus_volume_linear(AudioManager.BUS_SFX, cfg.get_value(SEC_AUDIO, KEY_SFX_LINEAR))


## ---------- 世界进度 ----------


func load_progress() -> void:
	_explored.clear()
	var cfg := ConfigFile.new()
	if cfg.load(PROGRESS_PATH) != OK:
		return
	if not cfg.has_section_key(SEC_EXP, KEY_CHUNKS):
		return
	var raw: String = str(cfg.get_value(SEC_EXP, KEY_CHUNKS))
	if raw.is_empty():
		return
	for part in raw.split("|"):
		if part.is_empty():
			continue
		var bits := part.split(",")
		if bits.size() == 2:
			_explored[part] = true


func mark_chunk_explored(coord: Vector2i) -> void:
	var key := "%d,%d" % [coord.x, coord.y]
	if _explored.has(key):
		return
	_explored[key] = true
	_write_progress_preserve_checkpoint()


func is_chunk_explored(coord: Vector2i) -> bool:
	return _explored.has("%d,%d" % [coord.x, coord.y])


func get_explored_chunks() -> Array[Vector2i]:
	var out: Array[Vector2i] = []
	for k in _explored.keys():
		var bits := str(k).split(",")
		if bits.size() == 2:
			out.append(Vector2i(int(bits[0]), int(bits[1])))
	return out


func save_playground_checkpoint(player_position: Vector3) -> void:
	var cfg := ConfigFile.new()
	cfg.load(PROGRESS_PATH)
	cfg.set_value(SEC_EXP, KEY_CHUNKS, _explored_to_csv())
	cfg.set_value(SEC_CKPT, KEY_SCENE, Game.SCENE_PLAYGROUND)
	cfg.set_value(SEC_CKPT, KEY_PX, player_position.x)
	cfg.set_value(SEC_CKPT, KEY_PY, player_position.y)
	cfg.set_value(SEC_CKPT, KEY_PZ, player_position.z)
	cfg.save(PROGRESS_PATH)


## 若有与本场景路径一致的检查点则返回 Vector3，否则返回 null。
func peek_playground_spawn() -> Variant:
	var cfg := ConfigFile.new()
	if cfg.load(PROGRESS_PATH) != OK:
		return null
	if not cfg.has_section_key(SEC_CKPT, KEY_SCENE):
		return null
	var sc := str(cfg.get_value(SEC_CKPT, KEY_SCENE))
	if sc != Game.SCENE_PLAYGROUND:
		return null
	if not cfg.has_section_key(SEC_CKPT, KEY_PX):
		return null
	return Vector3(
		float(cfg.get_value(SEC_CKPT, KEY_PX)),
		float(cfg.get_value(SEC_CKPT, KEY_PY)),
		float(cfg.get_value(SEC_CKPT, KEY_PZ))
	)


func reset_world_progress() -> void:
	_explored.clear()
	if FileAccess.file_exists(PROGRESS_PATH):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(PROGRESS_PATH))


func _explored_to_csv() -> String:
	var parts: PackedStringArray = PackedStringArray()
	var keys := _explored.keys()
	keys.sort()
	for k in keys:
		parts.append(str(k))
	return "|".join(parts)


func _write_progress_preserve_checkpoint() -> void:
	var cfg := ConfigFile.new()
	cfg.load(PROGRESS_PATH)
	cfg.set_value(SEC_EXP, KEY_CHUNKS, _explored_to_csv())
	cfg.save(PROGRESS_PATH)
