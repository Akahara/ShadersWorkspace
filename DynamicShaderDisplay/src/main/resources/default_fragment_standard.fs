#version 330 core

uniform vec2 u_resolution;
uniform float u_time;

layout(location=0) out vec4 color;

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*u_resolution.xy)/u_resolution.y;
  
  vec3 col = .5+.5*cos(u_time+uv.xyx+vec3(2, 4, 0));
  
  color = vec4(col, 1);
}
