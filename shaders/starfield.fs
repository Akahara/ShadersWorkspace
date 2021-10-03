#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

#define PI 3.141592
#define GRID_SIZE 3


mat2 rot(float theta) {
  return mat2(
    cos(theta), sin(theta),
    -sin(theta), cos(theta)
  );
}

float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

float star(vec2 uv) {
  vec2 uv1 = rot(.5) * uv;
  vec2 uv2 = rot(.5+PI/4) * uv;
  return smoothstep(.1, .05, abs(uv1.x*uv1.y)*30)*smoothstep(.16, .09, dot(uv, uv));
}

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*iResolution.xy)/iResolution.y;
  // color = vec4(vec3(star(uv)), 1);

  uv += iTime*vec2(.02, .005);

  vec2 gridLoc = floor(uv*GRID_SIZE);
  
  color = vec4(0);

  for(float dx = -1; dx <= 1; dx++) {
    for(float dy = -1; dy <= 1; dy++) {
      vec2 d = vec2(dx, dy);
      vec2 gl = gridLoc+d;
      vec2 gc = fract(uv*GRID_SIZE);
      vec2 displacement = vec2(rand(gl), rand(gl+100));
      vec2 guv = gc-displacement-d;

      float period = displacement.x+1;
      float periodOffset = displacement.y*PI;
      float maxOpacity = displacement.x;
      float size = (displacement.x*2+.5)*2;
      color += star(guv*size)*maxOpacity*(abs(cos(iTime*period+periodOffset))+.1);
    }
  }
}
