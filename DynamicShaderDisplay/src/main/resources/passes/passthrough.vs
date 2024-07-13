#version 330 core

layout(location=0) in vec4 i_pos;

void main(void) {
  gl_Position = i_pos;
}
