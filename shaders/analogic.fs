#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

#define ROWS 50

void main(void) {
  vec2 uv = (gl_FragCoord.xy*2-iResolution.xy)/iResolution.y;
  
  vec3 col = vec3(0);

  float t = iTime*.4;

  float ySection = floor(uv.y*ROWS);
  float direction = sin(ySection)*.3;
  direction += 1*sign(direction+.01);

  float xs = uv.x+sin(ySection+t)+t*direction;
  float r = rand(vec2(floor(xs*(2+sin(floor(xs)))), ySection));
  r = step(r, .5)*.7;
  float xs2 = uv.x+sin(ySection+t*1.2)+t*direction;
  float r2 = rand(vec2(floor(xs2*(5+sin(floor(xs2)))), ySection));
  r += step(r2, .5)*.5;

  float h = .8;
  col += step(1-h, abs(ySection-uv.y*ROWS))*r;

  color = vec4(col, 1);
}
