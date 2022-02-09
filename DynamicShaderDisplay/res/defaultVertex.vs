#version 330 core

layout(location=0) in vec4 i_pos;
out vec2 v_texcoord;

void main(void) {
  gl_Position = i_pos;
  v_texcoord = (i_pos.xy+1)/2.;
}
