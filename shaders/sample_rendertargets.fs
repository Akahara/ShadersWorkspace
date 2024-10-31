#version 330 core

// Run with "dsd run sample_rendertargets.json"

uniform vec2 u_resolution;
uniform float u_time;
uniform sampler2D u_texture; // builtin 3
uniform sampler2D u_previousFrame;

layout(location=0) out vec4 color;

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*u_resolution.xy)/u_resolution.xy;
  uv += .5;
  
  vec2 offset = u_time < 1 ? vec2(.005,0) : vec2(0);
  vec4 current = texture(u_texture, uv);
  vec4 previous = texture(u_previousFrame, uv+offset);
  color = mix(current, previous, u_time);
}
