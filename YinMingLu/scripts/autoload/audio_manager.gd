extends Node
## 统一管理 BGM / SFX 总线；音量与 SaveManager 配合。

const BUS_MASTER := "Master"
const BUS_BGM := "BGM"
const BUS_SFX := "SFX"

var _bgm_player: AudioStreamPlayer

func _ready() -> void:
	_ensure_aux_buses()
	_bgm_player = AudioStreamPlayer.new()
	_bgm_player.name = "BgmPlayer"
	_bgm_player.bus = BUS_BGM
	add_child(_bgm_player)


func _ensure_aux_buses() -> void:
	if AudioServer.get_bus_index(BUS_BGM) < 0:
		var idx := AudioServer.bus_count
		AudioServer.add_bus(idx)
		AudioServer.set_bus_name(idx, BUS_BGM)
	if AudioServer.get_bus_index(BUS_SFX) < 0:
		var idx := AudioServer.bus_count
		AudioServer.add_bus(idx)
		AudioServer.set_bus_name(idx, BUS_SFX)


func play_bgm(stream: AudioStream, from_position: float = 0.0) -> void:
	if stream == null:
		return
	_bgm_player.stream = stream
	_bgm_player.play(from_position)


func stop_bgm() -> void:
	_bgm_player.stop()


func play_sfx(stream: AudioStream, pitch_scale: float = 1.0) -> void:
	if stream == null:
		return
	var p := AudioStreamPlayer.new()
	p.stream = stream
	p.bus = BUS_SFX
	p.pitch_scale = pitch_scale
	add_child(p)
	p.finished.connect(p.queue_free)
	p.play()


func set_bus_volume_linear(bus_name: String, linear: float) -> void:
	var idx := AudioServer.get_bus_index(bus_name)
	if idx < 0:
		return
	AudioServer.set_bus_volume_db(idx, linear_to_db(clampf(linear, 0.0, 1.0)))
