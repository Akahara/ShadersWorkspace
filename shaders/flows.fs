#version 330 core

uniform vec2 iResolution;
uniform float iTime;

layout(location=0) out vec4 color;

#define RAYS 6

void main(void) {
  vec2 uv = (gl_FragCoord.xy-.5*iResolution.xy)/iResolution.y;

  vec3 col = vec3(0);  

  for(int i = -1; i <= 1; i++) {
    float ySection = floor(uv.y*RAYS)+i;
    float w = 1.*abs(sin(uv.x+iTime+ySection));
    float intensity = .4+w*w;
    float yOffset = ySection+sin(uv.x*5+ySection+iTime*ySection*.3)*.1;
    float g = intensity/abs((uv.y*RAYS-.5-yOffset)*200/RAYS);
    float t = iTime;
    float cOffset = ySection+uv.x;
    vec3 rayColor = vec3(abs(sin(cOffset+t)), abs(sin(cOffset+1+t)), abs(sin(cOffset+2+t)));
    col += rayColor*clamp(g, 0, 1);
  }
  
  color = vec4(col, 1);
}
