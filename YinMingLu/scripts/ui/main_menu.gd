extends Control

@onready var _btn_play: Button = %BtnPlay
@onready var _btn_quit: Button = %BtnQuit


func _ready() -> void:
	_btn_play.pressed.connect(_on_play_pressed)
	_btn_quit.pressed.connect(_on_quit_pressed)


func _on_play_pressed() -> void:
	Game.go_to_playground()


func _on_quit_pressed() -> void:
	Game.quit_game()
