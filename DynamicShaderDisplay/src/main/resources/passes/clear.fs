#version 330 core

#ifdef CLEAR_TARGET_0
out vec4 color0;
#endif
#ifdef CLEAR_TARGET_1
out vec4 color1;
#endif
#ifdef CLEAR_TARGET_2
out vec4 color2;
#endif
#ifdef CLEAR_TARGET_3
out vec4 color3;
#endif
#ifdef CLEAR_TARGET_4
out vec4 color4;
#endif
#ifdef CLEAR_TARGET_5
out vec4 color5;
#endif

#ifndef CLEAR_COLOR
#define CLEAR_COLOR vec4(0,0,0,1)
#endif

void main() {
    #ifdef CLEAR_TARGET_0
    color0 = CLEAR_COLOR;
    #endif
    #ifdef CLEAR_TARGET_1
    color1 = CLEAR_COLOR;
    #endif
    #ifdef CLEAR_TARGET_2
    color2 = CLEAR_COLOR;
    #endif
    #ifdef CLEAR_TARGET_3
    color3 = CLEAR_COLOR;
    #endif
    #ifdef CLEAR_TARGET_4
    color4 = CLEAR_COLOR;
    #endif
    #ifdef CLEAR_TARGET_5
    color5 = CLEAR_COLOR;
    #endif
}