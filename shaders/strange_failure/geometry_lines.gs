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

vec4 snapInBounds(vec4 v) {
	v.xy *= (iResolution-1)/iResolution;
	return v;
}

void main(void) {
	vec3 loc = vec3(0, 0, - 5);
	mat4 proj =
	  perspectiveProjectionMatrix(90, .01, 100) *
//      orthographicProjectionMatrix(-2, 2, -2, 2, -100, 100) *
	  translationMatrix(loc) *
	  rotationMatrix(iTime, iTime, 0);
	gl_Position = snapInBounds(proj * gl_in[0].gl_Position);
	EmitVertex();
	gl_Position = snapInBounds(proj * gl_in[1].gl_Position);
	EmitVertex();
	EndPrimitive();
}
