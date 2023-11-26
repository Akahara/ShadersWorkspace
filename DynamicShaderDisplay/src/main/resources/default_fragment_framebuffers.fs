#version 430 core

uniform vec2 u_resolution;
uniform int u_frame;
uniform sampler2D u_previousFrame; // target 0
uniform sampler2D u_previousState; // target 1

layout(location=0) out vec4 color;
layout(location=1) out vec4 state;

float rand(vec2 u) {
  return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

void main(void) {
  vec2 uv = gl_FragCoord.xy/u_resolution.xy;
  
  color = texture(u_previousState, uv);
  
  if(u_frame % 100 == 0) {
	state = vec4(step(.96, rand(uv + u_frame*.2513)));
	return;
  }
  
  ivec2 p = ivec2(gl_FragCoord.xy);
  ivec3 d = ivec3(1,-1,0);
  int c = int(texelFetch(u_previousState, p, 0).r);
  int n =
    int(texelFetch(u_previousState, p+d.xx, 0).r) +
    int(texelFetch(u_previousState, p+d.xy, 0).r) +
    int(texelFetch(u_previousState, p+d.xz, 0).r) +
    int(texelFetch(u_previousState, p+d.yx, 0).r) +
    int(texelFetch(u_previousState, p+d.yy, 0).r) +
    int(texelFetch(u_previousState, p+d.yz, 0).r) +
    int(texelFetch(u_previousState, p+d.zx, 0).r) +
    int(texelFetch(u_previousState, p+d.zy, 0).r);
  
  bool newState = c == 1 ? (n==2||n==3) : (n==3);
  state = vec4(newState);
}
