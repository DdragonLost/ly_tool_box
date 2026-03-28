extends Node
## 全局游戏入口：场景路径、分组名常量、场景切换（单机流程）。
## 联机阶段可在此扩展会话状态，无需改动 UI 按钮的调用方式。

func _enter_tree() -> void:
	_ensure_default_input_actions()


func _ensure_default_input_actions() -> void:
	_bind_key_action("move_forward", KEY_W)
	_bind_key_action("move_back", KEY_S)
	_bind_key_action("move_left", KEY_A)
	_bind_key_action("move_right", KEY_D)
	_bind_key_action("interact", KEY_E)
	_bind_key_action("dodge", KEY_SPACE)
	if not InputMap.has_action("attack"):
		InputMap.add_action("attack", 0.5)
	var click := InputEventMouseButton.new()
	click.button_index = MOUSE_BUTTON_LEFT
	click.pressed = true
	if not InputMap.action_get_events("attack").any(func(e): return e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT):
		InputMap.action_add_event("attack", click)


func _bind_key_action(action: String, keycode: Key) -> void:
	if not InputMap.has_action(action):
		InputMap.add_action(action, 0.5)
	var ev := InputEventKey.new()
	ev.physical_keycode = keycode
	var exists := false
	for e in InputMap.action_get_events(action):
		if e is InputEventKey and e.physical_keycode == keycode:
			exists = true
			break
	if not exists:
		InputMap.action_add_event(action, ev)


## 触屏移动：摇杆写入；有输入时优先于键盘 WASD。
var mobile_move_vector: Vector2 = Vector2.ZERO
var mobile_move_active: bool = false

var _touch_attack_pending: bool = false


func set_mobile_move(analog: Vector2) -> void:
	mobile_move_vector = analog
	mobile_move_active = analog.length_squared() > 0.0001


func clear_mobile_move() -> void:
	mobile_move_vector = Vector2.ZERO
	mobile_move_active = false


func request_touch_attack() -> void:
	_touch_attack_pending = true


func consume_touch_attack() -> bool:
	if _touch_attack_pending:
		_touch_attack_pending = false
		return true
	return false


func clear_mobile_state() -> void:
	clear_mobile_move()
	_touch_attack_pending = false


## 当前范围内可交谈的 NPC，由玩家每帧更新；离开试玩场景时应清空。
var interact_target: Node = null


func try_interact() -> void:
	if DialogueManager.is_dialogue_open():
		DialogueManager.advance_line()
		return
	if interact_target != null and interact_target.has_method("on_interact"):
		interact_target.call("on_interact")


const GROUP_PLAYER := "player"
const GROUP_NPC := "npc"
const GROUP_ENEMY := "enemy"
const GROUP_HURTBOX := "hurtbox"
const GROUP_PICKUP := "pickup"

const SCENE_MAIN_MENU := "res://scenes/ui/main_menu.tscn"
const SCENE_PLAYGROUND := "res://scenes/levels/playground.tscn"

func go_to_main_menu() -> void:
	get_tree().change_scene_to_file(SCENE_MAIN_MENU)


func go_to_playground() -> void:
	get_tree().change_scene_to_file(SCENE_PLAYGROUND)


func quit_game() -> void:
	get_tree().quit()
