#version 430 core

#ifndef DSD_DEBUG_GLSL
#define DSD_DEBUG_GLSL

layout(std430, binding = 0) buffer _dsdd
{
    int _dsdd_bufLength;
    int _dsdd_nextWrite;
    int _dsdd_buf[];
};

#define _DSDD_TYPE_INT1 1
#define _DSDD_TYPE_INT2 2
#define _DSDD_TYPE_INT3 3
#define _DSDD_TYPE_INT4 4
#define _DSDD_TYPE_FLOAT1 5
#define _DSDD_TYPE_FLOAT2 6
#define _DSDD_TYPE_FLOAT3 7
#define _DSDD_TYPE_FLOAT4 8
#define _DSDD_TYPE_BITS 8

int _dsdd_alloc(int type, int id, int size) {
    int reserved = 1+size;
    int off = atomicAdd(_dsdd_nextWrite, reserved);
    if (off + reserved > _dsdd_bufLength)
        return -1;
    _dsdd_buf[off] = type | (id<<_DSDD_TYPE_BITS);
    return off+1;
}

void _dsdd_write_converted(int type, int id, int x) {
    int off = _dsdd_alloc(type, id, 1);
    if (off >= 0)
        _dsdd_buf[off] = x;
}
void _dsdd_write_converted(int type, int id, ivec2 x) {
    int off = _dsdd_alloc(type, id, 2);
    if (off >= 0) {
        _dsdd_buf[off] = x.x;
        _dsdd_buf[off+1] = x.y;
    }
}
void _dsdd_write_converted(int type, int id, ivec3 x) {
    int off = _dsdd_alloc(type, id, 3);
    if (off >= 0) {
        _dsdd_buf[off] = x.x;
        _dsdd_buf[off+1] = x.y;
        _dsdd_buf[off+2] = x.z;
    }
}
void _dsdd_write_converted(int type, int id, ivec4 x) {
    int off = _dsdd_alloc(type, id, 4);
    if (off >= 0) {
        _dsdd_buf[off] = x.x;
        _dsdd_buf[off+1] = x.y;
        _dsdd_buf[off+2] = x.z;
        _dsdd_buf[off+3] = x.w;
    }
}

void _dsdd_write(int id, int x) { _dsdd_write_converted(_DSDD_TYPE_INT1, id, x); }
void _dsdd_write(int id, float x) { _dsdd_write_converted(_DSDD_TYPE_FLOAT1, id, floatBitsToInt(x)); }
void _dsdd_write(int id, ivec2 x) { _dsdd_write_converted(_DSDD_TYPE_INT2, id, x); }
void _dsdd_write(int id, vec2 x) { _dsdd_write_converted(_DSDD_TYPE_FLOAT2, id, floatBitsToInt(x)); }
void _dsdd_write(int id, ivec3 x) { _dsdd_write_converted(_DSDD_TYPE_INT3, id, x); }
void _dsdd_write(int id, vec3 x) { _dsdd_write_converted(_DSDD_TYPE_FLOAT3, id, floatBitsToInt(x)); }
void _dsdd_write(int id, ivec4 x) { _dsdd_write_converted(_DSDD_TYPE_INT4, id, x); }
void _dsdd_write(int id, vec4 x) { _dsdd_write_converted(_DSDD_TYPE_FLOAT4, id, floatBitsToInt(x)); }

#define DSD_DEBUG_INIT() bool _dsdd_enabled = true
#define DSD_DEBUG_COND(enabled) _dsdd_enabled = enabled
#define DSD_DEBUG_CURSOR() DSD_DEBUG_COND(ivec2(gl_FragCoord.xy) == ivec2(u_cursor))
#define DSD_DEBUG(name, val) if (_dsdd_enabled) _dsdd_write(_DSDD_NAME_##name, val)

// List of debug names (populated by the shader compiler)
// _DSD_DEBUG_NAMES

#endif