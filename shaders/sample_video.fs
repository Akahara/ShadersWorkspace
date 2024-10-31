#version 430 core

// Run with "dsd run sample_video.fs yourvideo.mp4"

uniform sampler2D u_frame; // input 0
uniform vec2 u_resolution;
uniform vec2 u_sv = vec2(.35, 1);
uniform float u_edgeRadius = 1;
uniform int u_sampleCount = 17;

#define AA 1
#define PI 3.1415926535

layout(location=0) out vec4 color;

mat2 rot2D(float rad) {
  return mat2(
     cos(rad), sin(rad),
    -sin(rad), cos(rad)
  );
}

vec3 hsv2rgb(vec3 c)
{
  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 edgeDetectC(vec2 uv) {
  vec3 avg = vec3(0);
  float N = u_sampleCount;
  vec2 o = vec2(1, u_resolution.y / u_resolution.x) * u_edgeRadius;
  for (float i = 0; i < N; i++) {
    float t = i/N;
    float w = texture(u_frame, (uv + rot2D(t*2*PI) * o) / u_resolution).x;
    vec3 k = w * hsv2rgb(vec3(t, u_sv.x, u_sv.y));
    avg += k;
  }
  avg /= N;
  vec3 m = vec3(0);
  for (float i = 0; i < N; i++) {
    float t = i/N;
    float w = texture(u_frame, (uv + rot2D(t*2*PI) * o) / u_resolution).x;
    vec3 k = w * hsv2rgb(vec3(t, u_sv.x, u_sv.y));
    m += (k - avg) * (k - avg);
  }
  return (m-.5)*2;
}

vec3 render(vec2 uv) {
  return edgeDetectC(uv);
}

void main(void) {
  vec2 uv = gl_FragCoord.xy;
  for(int N = -AA; N <= AA; N++) {
    for(int M = -AA; M <= AA; M++) {
      vec2 o = uv + vec2(N, M);
      color.rgb += render(o);
    }
  }
  color.rgb /= (2*AA+1)*(2*AA+1);
  color.a = 1;
}
