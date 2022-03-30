#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

void main(void) {
  color = vec4(1, 0, 0, 1);
}
