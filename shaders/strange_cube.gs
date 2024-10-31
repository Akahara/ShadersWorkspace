#version 400 core

// Run with "dsd run sample_geometry.fs -g strange_cube.gs"

#define INSTANCES 32
#define PERIOD 5.5

layout(triangles) in;
layout(invocations = INSTANCES) in;

layout(line_strip, max_vertices=24) out;

#define PI 3.14159

uniform float iTime;
uniform vec2 iResolution;

mat4 perspectiveProjectionMatrix(float fov, float near, float far) {
	float scale = 1 / tan(fov * PI / 360);
	float zmap = far/(near-far);
	return mat4(
		scale, 0, 0, 0,
	  0, scale, 0, 0,
		0, 0, zmap, -1,
		0, 0, zmap*near, 0);
}

mat4 worldProjectionMatrix(vec3 cam) {
	return transpose(mat4(
		1, 0, 0, cam.x,
		0, 1, 0, cam.y,
		0, 0, 1, cam.z,
		0, 0, 0, 1));
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

vec3[] edges = vec3[](
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

vec4 toGLCoords(vec4 u) {
	float m = min(iResolution.y,iResolution.x)-1;
	return vec4(u.x/iResolution.x*m, u.y*m/iResolution.y, u.z, u.w);
}

void main(void) {
	float g = (gl_InvocationID+1.)/INSTANCES;
	float t = iTime*.5;

	// comment one line or the other
	g = pow(g, exp(mod(-t, PERIOD))-1);
	// g = mix(1, g, .2);

	mat4 proj = 
	 	perspectiveProjectionMatrix(60, .01, 100) *
		worldProjectionMatrix(vec3(0, 0, -2)) *
		rotationMatrix(t*.1+g, t-g, t+g);

	for(int i = 0; i < 12; i++) {
		gl_Position = proj * vec4(edges[indices[ 2*i ]] * g, 1);
		gl_Position = toGLCoords(gl_Position);
		EmitVertex();
		gl_Position = proj * vec4(edges[indices[2*i+1]] * g, 1);
		gl_Position = toGLCoords(gl_Position);
		EmitVertex();
		EndPrimitive();
	}
}
