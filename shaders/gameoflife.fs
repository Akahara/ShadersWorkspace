#version 430 core

uniform vec2 iResolution;
uniform float iTime;
uniform int iFrame;
uniform sampler2D u_previousFrame; // target 0
uniform sampler2D u_previousState; // target 1

layout(location=0) out vec4 color;
layout(location=1) out vec4 state;

#define UPDATE_COOLDOWN 1
#define REFILL_COOLDOWN 1000
#define REFILL_FRACTION .08
//#define SMOOTH .1
//#define BIG_PICTURE

float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*iResolution.xy)/iResolution.xy;
  uv += .5;
  
  ivec2 p = ivec2(gl_FragCoord.xy);
  ivec3 d = ivec3(1,-1,0);
  
#ifdef BIG_PICTURE
  vec2 u = uv;
#else
  vec2 u = uv*.4 + iTime*.05*vec2(1,.5);
#endif
#ifdef SMOOTH
  color = mix(texelFetch(u_previousFrame, p, 0), texture(u_previousState, u), SMOOTH);
#else
  color = texture(u_previousState, u);
#endif
  
  if(iFrame % REFILL_COOLDOWN == 0) {
	state = color = vec4(step(1-REFILL_FRACTION, rand(uv)));
	return;
  } else if(iFrame % UPDATE_COOLDOWN != 0) {
	int c = int(texelFetch(u_previousState, p, 0).r);
	state = vec4(c);
	return;
  }
  
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
