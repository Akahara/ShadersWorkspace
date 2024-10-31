#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

#define PI 3.141592

#define PC 3
#define GRID_SIZE 2.5
#define PETALS_COLORS_COUNT 3

const vec3[] WINGS_COLORS = vec3[](
  vec3(0.78,0.133,0.788),
  vec3(0.882,0.188,0.89),
  vec3(0.914,0.259,0.922),
  vec3(0.824,0.282,0.831)
);

const vec3[] PETALS_COLORS = vec3[PETALS_COLORS_COUNT](
  vec3(0.988,0.631,0.012),
  vec3(0.988,0.435,0.012),
  vec3(0.831,0.286,0.031)
);

mat2 rot(float rad) {
  return mat2(
     cos(rad), sin(rad),
    -sin(rad), cos(rad)
  );
}

float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 petal(vec2 uv, float blur) {
  uv.x = abs(uv.x);
  float petal = smoothstep(blur*.5, -blur*.5, uv.x-(-.8*uv.y*(uv.y-1)));
  return vec2(petal, petal);
}

vec2 wing(vec2 uv) {
  uv.x = abs(uv.x);
  float petal = petal(uv, .01).x;
  float hole = smoothstep(.05, .03, length(uv-vec2(0, .7)))
              + smoothstep(.03, .01, length(uv-vec2(.05, .2)));
  return vec2(petal-hole, petal);
}

vec4 combineColor(vec4 col, vec2 draw, vec3 color) {
  return col + (1-col.a)*vec4(draw.x*color, draw.y);
}

void main(void) {
  vec2 uv = (gl_FragCoord.xy*2-iResolution.xy)/iResolution.y;
  vec4 col = vec4(0);

  // col += vec4(displacement, 0, 0);

  // for(float i = 0.; i <= PC; i++) {
  //   float s = i;
  //   float t = fract(iTime*.2);
  //   col += petal(rot(t*PI*2*s) * uv);
  // }


  // vec2 p1 = petal(uv);
  // vec2 p2 = petal(rot(PI/4)*(uv-vec2(0, .2)));
  // col += (1-col.a)*vec4(p2.x*vec3(1), p2.y);
  // col += (1-col.a)*vec4(p1.x*vec3(1), p1.y);
  // col = vec4(col.a);

  const float period = 8.;
  const float delta = 5.;
  const float d = (period*.5-delta)/PC/2;
  vec2 butterflyUV = rot(PI*.125)*(uv-vec2(-.3*iResolution.x/iResolution.y, -.4));
  butterflyUV.x = abs(butterflyUV.x);
  for(int i = PC; i >= 0; i--) {
    float t = mod(iTime, period);
    t = cos(iTime);
    t = 1-(pow(abs(t), 100)+t*t*.25);
    vec2 u = vec2(0, t*.3+(smoothstep(0, .4, mod(iTime, PI))-smoothstep(0, 1, mod(iTime, PI)))*.3);
    t = (t+.25)*.4+.6;
    vec2 v = rot(PI/4)*rot(PI*(i+1)*.1*t)*(butterflyUV-u);
    vec2 w = wing(v*(1+.2*i));
    col = combineColor(col, w, WINGS_COLORS[i]);
  }

  vec2 petalsDisp = uv+iTime*.6;
  vec2 gridLoc = floor(petalsDisp*GRID_SIZE);
  for(float dx = -1; dx <= 1; dx++) {
    for(float dy = -1; dy <= 1; dy++) {
      vec2 d = vec2(dx, dy);
      vec2 gl = gridLoc+d;
      vec2 gc = fract(petalsDisp*GRID_SIZE);
      vec2 random = vec2(rand(gl), rand(gl+100));
      vec2 displacement = random;
      vec3 petalColor = PETALS_COLORS[int(random.x*PETALS_COLORS_COUNT)];
      float petalRotation = PI*(.25+cos(iTime+random.y*PI*2)*.07);
      vec2 guv = gc-displacement-d;

      vec2 p = petal(rot(petalRotation)*guv*1.3, .07);
      col = combineColor(col, p, petalColor);
    }
  }

  color = vec4(col.rgb, 1);
}
