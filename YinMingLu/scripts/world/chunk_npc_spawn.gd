class_name ChunkNpcSpawn
extends Resource
## 描述在某个块内生成一个 NPC（或任意 PackedScene）的局部变换。
## local_position：相对块根节点；块根在世界中的原点为该块中心、地面约 y=0。

@export var npc_scene: PackedScene
@export var local_position: Vector3 = Vector3.ZERO
@export var rotation_y_degrees: float = 0.0
