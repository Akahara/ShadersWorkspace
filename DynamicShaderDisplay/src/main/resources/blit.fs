#version 330 core

#define CHECKERBOARD_SIZE 50

#ifdef DEPTH_INPUT
uniform vec2 u_zrange;
#endif

uniform sampler2D u_texture;
uniform bool u_background;

layout(location=0) out vec4 color;

void main(void) {
#ifdef DEPTH_INPUT
  float d = texelFetch(u_texture, ivec2(gl_FragCoord.xy), 0).r;
  color = vec4((d - u_zrange.x) / (u_zrange.y - u_zrange.x));
#else
  vec4 c = texelFetch(u_texture, ivec2(gl_FragCoord.xy), 0);
  float t = abs(int(gl_FragCoord.x/CHECKERBOARD_SIZE)%2 + int(gl_FragCoord.y/CHECKERBOARD_SIZE)%2 - 1);
  color.rgb = c.rgb + (1-c.a) * float(u_background) * vec3(vec3(mix(0.234, .5, step(t, .5))));
  color.a = 1;
#endif
}
