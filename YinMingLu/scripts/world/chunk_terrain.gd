extends Node3D
## 单块地表：由 WorldStreamer 设置尺寸与网格坐标，顶面约在 y=0。


func setup(chunk_size: float, coord: Vector2i) -> void:
	var body: StaticBody3D = $StaticBody3D
	body.collision_layer = 1
	body.collision_mask = 0
	body.position = Vector3(0.0, -0.5, 0.0)

	var mi: MeshInstance3D = body.get_node("MeshInstance3D") as MeshInstance3D
	var box_m := BoxMesh.new()
	box_m.size = Vector3(chunk_size, 1.0, chunk_size)
	mi.mesh = box_m

	var col: CollisionShape3D = body.get_node("CollisionShape3D") as CollisionShape3D
	var box_s := BoxShape3D.new()
	box_s.size = Vector3(chunk_size, 1.0, chunk_size)
	col.shape = box_s

	var mat := StandardMaterial3D.new()
	var hue := fmod(float(coord.x * 31 + coord.y * 17) * 0.071, 1.0)
	mat.albedo_color = Color.from_hsv(hue, 0.12, 0.24)
	mat.roughness = 0.92
	mi.material_override = mat
