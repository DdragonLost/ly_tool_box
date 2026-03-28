class_name ChunkDefinition
extends Resource
## 单个网格块 (chunk_coord) 的策划数据：NPC、日后可扩展传送点、氛围等。

@export var chunk_coord: Vector2i = Vector2i.ZERO
@export var npc_spawns: Array[ChunkNpcSpawn] = []
@export var enemy_spawns: Array[ChunkNpcSpawn] = []
