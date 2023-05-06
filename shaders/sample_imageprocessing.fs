// make sure outdir/ exists
// image shader.fs someimage.jpg someotherimage.jpg yetanotherimage.png -o outdir -rs

#version 330 core

uniform vec2 iResolution;
uniform float iTime;
uniform sampler2D u_texture; // input or builtin 1

layout(location=0) out vec4 color;


void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*iResolution.xy)/iResolution.xy;
  uv += .5;
  
  color = texture(u_texture, uv);
  color = 1-color;
  color.a = 1;
}
