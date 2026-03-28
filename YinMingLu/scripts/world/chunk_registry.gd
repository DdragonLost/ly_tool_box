class_name ChunkRegistry
extends Resource
## 所有「有内容的块」登记在此；未登记的块仅生成空地表（由 WorldStreamer 决定）。

@export var chunks: Array[ChunkDefinition] = []


func get_definition(coord: Vector2i) -> ChunkDefinition:
	for c in chunks:
		if c.chunk_coord == coord:
			return c
	return null
