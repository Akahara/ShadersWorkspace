#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*iResolution.xy)/iResolution.xy;
  
  vec3 col = .5+.5*cos(iTime+uv.xyx+vec3(2, 4, 0));
  
  color = vec4(col, 1);
}
