// Make sure outdir/ exists and run with
// dsd image sample_imageprocessing.fs someimage.jpg someotherimage.jpg yetanotherimage.png -o outdir -rs

#version 330 core

uniform vec2 u_resolution;
uniform sampler2D u_texture; // input or builtin 1

layout(location=0) out vec4 color;

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*u_resolution.xy)/u_resolution.xy;
  uv += .5;
  
  color = texture(u_texture, uv);
  color = 1-color;
  color.a = 1;
}
