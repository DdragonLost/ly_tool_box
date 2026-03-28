extends CharacterBody3D
## 占位 NPC：范围检测 + 打开对话；后续可换为 Resource 驱动对话树。


@export var dialogue_lines: PackedStringArray = PackedStringArray(
	["村民：外乡人？", "这村里……不太平。", "你还是快些离开吧。"]
)

var _players_in_range: int = 0

@onready var _area: Area3D = $InteractArea


func _ready() -> void:
	add_to_group(Game.GROUP_NPC)
	collision_layer = 2
	collision_mask = 1
	_area.body_entered.connect(_on_body_entered)
	_area.body_exited.connect(_on_body_exited)


func offers_interact() -> bool:
	return _players_in_range > 0


func on_interact() -> void:
	if not offers_interact():
		return
	DialogueManager.show_lines(dialogue_lines)


func _on_body_entered(body: Node3D) -> void:
	if body.is_in_group(Game.GROUP_PLAYER):
		_players_in_range += 1


func _on_body_exited(body: Node3D) -> void:
	if body.is_in_group(Game.GROUP_PLAYER):
		_players_in_range = maxi(_players_in_range - 1, 0)
