#version 330 core

#ifdef BLIT_TARGET_0
out vec4 color0;
#endif
#ifdef BLIT_TARGET_1
out vec4 color1;
#endif
#ifdef BLIT_TARGET_2
out vec4 color2;
#endif
#ifdef BLIT_TARGET_3
out vec4 color3;
#endif
#ifdef BLIT_TARGET_4
out vec4 color4;
#endif
#ifdef BLIT_TARGET_5
out vec4 color5;
#endif

uniform sampler2D u_source;

in vec2 v_uv;

void main() {
    vec4 col = texture(u_source, v_uv);
    #ifdef BLIT_TARGET_0
    color0 = col;
    #endif
    #ifdef BLIT_TARGET_1
    color1 = col;
    #endif
    #ifdef BLIT_TARGET_2
    color2 = col;
    #endif
    #ifdef BLIT_TARGET_3
    color3 = col;
    #endif
    #ifdef BLIT_TARGET_4
    color4 = col;
    #endif
    #ifdef BLIT_TARGET_5
    color5 = col;
    #endif
}