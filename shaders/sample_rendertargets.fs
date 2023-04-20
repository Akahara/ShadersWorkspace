#version 330 core

uniform vec2 iResolution;
uniform float iTime;
uniform sampler2D u_texture; // 3
uniform sampler2D u_previousFrame; // target 0

layout(location=0) out vec4 color;
layout(location=1) out vec4 target0out;


void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*iResolution.xy)/iResolution.xy;
  uv += .5;
  
  vec2 offset = iTime < 1 ? vec2(.005,0) : vec2(0);
  vec4 current = texture(u_texture, uv);
  vec4 previous = texture(u_previousFrame, uv+offset);
  color = mix(current, previous, iTime);
}
