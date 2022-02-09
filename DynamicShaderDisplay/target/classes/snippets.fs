/* Universal constants */
#define PI 3.141592

/* Resolution uniform */
uniform vec2 iResolution;
/* Time uniform */
uniform float iTime;

/* Texture uniforms, add the texture path as a comment */
uniform sampler2D someTexture; // someTexture.png

/* Geometry shader instanciations */
#version 400 core
layout(triangles) in;
layout(invocations = 32) in;
layout(line_strip, max_vertices=24) out;

/* 2x2 Rotation matrix */
mat2 rot(float rad) {
  return mat2(
     cos(rad), sin(rad),
    -sin(rad), cos(rad)
  );
}

/* Perspective projection matrix */
mat4 perspectiveProjectionMatrix(float fov, float near, float far) {
  float scale = 1 / tan(fov * PI / 360);
  float zmap = far/(near-far);
  return mat4(
    scale, 0, 0, 0,
    0, scale, 0, 0,
    0, 0, zmap, -1,
    0, 0, zmap*near, 0);
}

/* Orthographic projection matrix */
mat4 orthographicProjectionMatrix(float r, float l, float t, float b, float f, float n) {
	return transpose(mat4(
		2/(r-l), 0, 0, (r+l)/(l-r),
		0, 2/(t-b), 0, (t+b)/(b-t),
		0, 0, 2/(f-n), (f+n)/(n-f),
		0, 0, 0, 1
	));
}

/* Translation matrix */
mat4 translationMatrix(vec3 v) {
	return transpose(mat4(
		1, 0, 0, v.x,
		0, 1, 0, v.y,
		0, 0, 1, v.z,
		0, 0, 0, 1));
}

/* Arbitrary rotation matrix */
mat4 rotationMatrix(float yaw, float pitch, float roll) {
  float cy = cos(yaw), sy = sin(yaw);
  float cr = cos(roll), sr = sin(roll);
  float cp = cos(pitch), sp = sin(pitch);
  return mat4(
    cy, -sy, 0, 0,
    sy, cy, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1
  ) * mat4(
    cp, 0, sp, 0,
    0, 1, 0, 0,
    -sp, 0, cp, 0,
    0, 0, 0, 1
  ) * mat4(
    1, 0, 0, 0,
    0, cr, -sr, 0,
    0, sr, cr, 0,
    0, 0, 0, 1
  );
}

/* Random function */
float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

/* Random vector */
vec3 randomUnitVector(vec2 u) {
	float z = u.x;
	float phi = u.y;
	float r = sqrt(1-z*z);
	return vec3(z, r*cos(phi), r*sin(phi));
}

/* Normalize uv (-1..1, -1..1) */
vec2 uv = gl_FragCoord.xy/iResolution.xy*2.-1.;
/* Normalize uv (0..1, 0..1) */
vec2 uv = gl_FragCoord.xy/iResolution.xy;
/* Normalize uv (-w/h..w/h, -1..1) */
vec2 uv = (gl_FragCoord.xy*2-iResolution.xy)/iResolution.y;

/* Un-normalize vertex
   maps any point in range (-1..1, -1..1) into
   the largest square that fits in the window. */
vec4 toGLCoords(vec4 u) {
	float m = min(iResolution.y,iResolution.x)-1;
	return vec4(u.x/iResolution.x*m, u.y*m/iResolution.y, u.z, u.w);
}

/* Cartesian to polar coordinates */
vec2 toPolar(vec2 uv) {
	float theta = uv.x == 0 ? sign(uv.y)*PI/2 : atan(uv.y/uv.x);
	float xs = (sign(uv.x)+1)*.5;
	float ys = (sign(uv.y)+1)*.5;
	theta += (1-xs)*PI+xs*(1-ys)*PI*2;
	float r = length(uv);
	return vec2(r, theta);
}

/* Cube vertices and indices */
vec3[] vertices = vec3[](
	vec3(-.5, -.5, -.5),
	vec3(-.5, -.5, +.5),
	vec3(-.5, +.5, -.5),
	vec3(-.5, +.5, +.5),
	vec3(+.5, -.5, -.5),
	vec3(+.5, -.5, +.5),
	vec3(+.5, +.5, -.5),
	vec3(+.5, +.5, +.5)
);
int[] indices = int[](
	0, 1,
	0, 2,
	1, 3,
	2, 3,
	0, 4,
	1, 5,
	2, 6,
	3, 7,
	4, 5,
	4, 6,
	5, 7,
	6, 7
);