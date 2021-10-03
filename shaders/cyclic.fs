#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

#define PI 3.141592

mat2 rot(float rad) {
  return mat2(
     cos(rad), sin(rad),
    -sin(rad), cos(rad)
  );
}

float draw(float theta, float l) {
  l *= iResolution.y/40;
  theta /= 2*PI;
  float sector = floor(l);

  float t = iTime*.6;

  if(l < 1)
    return 1;

  float delta = mod(sin(sector+t)+sin(t), 1);

  return smoothstep(0, .05/sector, .2-abs(theta-delta)) *
    smoothstep(.05, .15, 1 - 2*abs(l-sector-.5));
}

void main(void) {
  vec2 uv = (gl_FragCoord.xy*2-iResolution.xy)/iResolution.y;
  
  float theta = uv.x == 0 ? sign(uv.y)*PI/2 : atan(uv.y/uv.x);
  float xs = (sign(uv.x)+1)*.5;
  float ys = (sign(uv.y)+1)*.5;
  theta += (1-xs)*PI+xs*(1-ys)*PI*2;

  vec3 col = vec3(
    draw(theta, length(uv)) +
    draw(theta-PI*2, length(uv)) +
    draw(theta+PI*2, length(uv)));
  // if(col.x < 0 || col.x > 1)
  //   col = vec3(.4, .6, .1);
  
  color = vec4(col, 1);
}
