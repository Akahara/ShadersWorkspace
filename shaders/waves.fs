#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

#define WAVE_COUNT 5

const float[WAVE_COUNT] waveLengths = float[](10, 15, 18, 22, 1);
const float[WAVE_COUNT] waveSpeeds = float[](2, 1.7, 2.2, 1.5, 0);
const vec3 [WAVE_COUNT] waveColors = vec3[](
  vec3(0.039,0.533,0.624),
  vec3(0.071,0.667,0.631),
  vec3(0.035,0.369,0.576),
  vec3(0.016,0.125,0.325),
  vec3(0));

void main(void) {
  vec2 uv = gl_FragCoord.xy/iResolution.xy;
  
  vec3 col = vec3(0);
  for(int i = 0; i < WAVE_COUNT; i++) {
    float edge = cos(uv.x*waveLengths[i]+iTime*waveSpeeds[i]);
    float h = (uv.y*(WAVE_COUNT-1)*2-1)-i*2.2;
    float weight = (1-max(max(col.x, col.y), col.z));
    float s = smoothstep(edge+.08, edge, h);
    if(s > 0.01 && s < .5)
      weight *= -weight;
    col += weight * s * waveColors[i];
  }

  // vec3 col = vec3(0);
  // for(int i = 0; i < WAVE_COUNT; i++) {
  //   float edge = cos(uv.x*waveLengths[i]+iTime*waveSpeeds[i]);
  //   float h = (uv.y*(WAVE_COUNT-1)*2-1)-i*2.2;
  //   float weight = (1-max(max(col.x, col.y), col.z));
  //   float s = step(h, edge);
  //   col -= (1-s)*smoothstep(edge+.1, edge, h)*(WAVE_COUNT-i);
  //   col += weight * s * waveColors[i];
  // }
  
  color = vec4(col, 1);
}
