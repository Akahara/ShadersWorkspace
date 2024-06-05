#version 330 core

#define CHECKERBOARD_SIZE 50

uniform sampler2D u_texture;
uniform bool u_background;

layout(location=0) out vec4 color;

void main(void) {
  float t = abs(int(gl_FragCoord.x/CHECKERBOARD_SIZE)%2 + int(gl_FragCoord.y/CHECKERBOARD_SIZE)%2 - 1);
  color = float(u_background) * vec4(vec3(mix(0.234, .5, step(t, .5))), 1);
  vec4 c = texelFetch(u_texture, ivec2(gl_FragCoord.xy), 0);
  color = c + (1-c.a) * color;
  color.a = 1;
}
