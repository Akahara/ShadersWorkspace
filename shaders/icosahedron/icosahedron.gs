#version 460 core

#define PI 3.1415926535

layout(lines) in;

layout(line_strip, max_vertices=2) out;

uniform vec2 iResolution;
uniform float iTime;

mat4 perspectiveProjectionMatrix(float fov, float near, float far) {
  float scale = 1 / tan(fov * PI / 360);
  float zmap = far/(near-far);
  return mat4(
    scale, 0, 0, 0,
    0, scale, 0, 0,
    0, 0, zmap, -1,
    0, 0, zmap*near, 0);
}

mat4 orthographicProjectionMatrix(float r, float l, float t, float b, float f, float n) {
	return transpose(mat4(
		2/(r-l), 0, 0, (r+l)/(l-r),
		0, 2/(t-b), 0, (t+b)/(b-t),
		0, 0, 2/(f-n), (f+n)/(n-f),
		0, 0, 0, 1
	));
}

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

mat4 translationMatrix(vec3 v) {
	return transpose(mat4(
		1, 0, 0, v.x,
		0, 1, 0, v.y,
		0, 0, 1, v.z,
		0, 0, 0, 1));
}

mat4 scaleMatrix(vec3 s) {
	return mat4(
		s.x, 0, 0, 0,
		0, s.y, 0, 0,
		0, 0, s.z, 0,
		0, 0, 0, 1
	);
}

float rand(vec2 u) {
    return fract(sin(dot(u, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 offsetIcoPoint(vec4 v) {
	float r = rand(v.xy+v.w);
	//float r = v.y*.5+.5-v.x*v.x;
	v.xyz *= mix((cos(iTime)+1)*.5, 1, r);
	return v;
}

vec4 snapInBounds(vec4 v) {
	v.xy *= (iResolution-1)/iResolution;
	return v;
}

void main(void) {
	vec3 loc = vec3(0, 0, 15);
	mat4 cameraProj =
	  orthographicProjectionMatrix(-1, 1, -1, 1, -1, 10000) *
	  translationMatrix(loc);
	mat4 objProj =
	  rotationMatrix(0, iTime, 0);
	vec4 p1 = objProj * offsetIcoPoint(gl_in[0].gl_Position);
	vec4 p2 = objProj * offsetIcoPoint(gl_in[1].gl_Position);
	//if(p1.z > 0 && p2.z > 0) {
		gl_Position = snapInBounds(cameraProj * p1);
		EmitVertex();
		gl_Position = snapInBounds(cameraProj * p2);
		EmitVertex();
	//}
	EndPrimitive();
}
