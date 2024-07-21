#version 330 core

layout(location=0) in vec4 i_pos;
layout(location=1) in vec3 i_normal;
layout(location=2) in vec2 i_uv;

out vec2 v_uv;
out vec3 v_normal;

void main(void) {
  gl_Position = i_pos;
  v_uv = i_uv;
  v_normal = i_normal;
}
