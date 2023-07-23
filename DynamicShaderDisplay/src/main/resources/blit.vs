#version 330 core

const vec2[] uvs = vec2[](
	vec2(-1,-1),
	vec2(1,-1),
	vec2(1,1),
	vec2(-1,1)
);

void main(void) {
  gl_Position = vec4(uvs[gl_VertexID], 0, 1);
}
