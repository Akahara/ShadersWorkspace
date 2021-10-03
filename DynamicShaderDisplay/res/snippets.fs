/* Opengl Version declaration */
#version 330 core

/* Universal constants */
#define PI 3.141592

/* Available uniforms */
uniform vec2 iResolution;
uniform float iTime;

/* 2x2 Rotation matrix */
mat2 rot(float rad) {
  return mat2(
     cos(rad), sin(rad),
    -sin(rad), cos(rad)
  );
}

/* Random functions */
float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

/* Normalize uv (-1..1, -1..1) */
vec2 uv = gl_FragCoord.xy/iResolution.xy*2.-1.;
/* Normalize uv (0..1, 0..1) */
vec2 uv = gl_FragCoord.xy/iResolution.xy;
/* Normalize uv (-w/h..w/h, -1..1) */
vec2 uv = (gl_FragCoord.xy*2-iResolution.xy)/iResolution.y;

/* Cartesian to polar coordinates */
float theta = uv.x == 0 ? sign(uv.y)*PI/2 : atan(uv.y/uv.x);
float xs = (sign(uv.x)+1)*.5;
float ys = (sign(uv.y)+1)*.5;
theta += (1-xs)*PI+xs*(1-ys)*PI*2;
float r = length(uv);
