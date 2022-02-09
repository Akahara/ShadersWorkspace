#version 400 core

#define VERTICES 64
#define INSTANCES 32
#define PI 3.14159
#define TWOPI 2*PI

layout(triangles) in;
layout(invocations = INSTANCES) in;

layout(line_strip, max_vertices=VERTICES) out;

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

mat4 orthographicProjectionMatrix(float r, float l, float t, float b, float f, float n) {
	return transpose(mat4(
		2/(r-l), 0, 0, (r+l)/(l-r),
		0, 2/(t-b), 0, (t+b)/(b-t),
		0, 0, 2/(f-n), (f+n)/(n-f),
		0, 0, 0, 1
	));
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

vec4 toGLCoords(vec4 u) {
	float m = min(iResolution.y,iResolution.x)-1;
	return vec4(u.x/iResolution.x*m, u.y*m/iResolution.y, u.z, u.w);
}

vec4 point(vec3 u, float r, float x) {
	// return vec4(cos(x), sin(x), +x, 1);
	vec3 vx = vec3(1, 0, 0);
	if(abs(dot(u, vx)) == 1)
		vx = vec3(0, 1, 0);
	vx = cross(u, vx);
	vec3 vy = cross(u, vx);
	vx /= length(vx);
	vy /= length(vy);
	float vr = sqrt(1-r*r);
	
	return vec4(u*r + vr*cos(x)*vx + vr*sin(x)*vy, 1);
}

vec3 randomUnitVector(float s1, float s2) {
	float z = s1;
	float phi = s2;
	float r = sqrt(1-z*z);
	return vec3(z, r*cos(phi), r*sin(phi));
}

void main(void) {
	float t = iTime;
	float g = gl_InvocationID/INSTANCES.;

	mat4 proj = 
	 	// perspectiveProjectionMatrix(60, .01, 100)
		orthographicProjectionMatrix(-1.2, 1.2, -1.2, 1.2, .01, 100)
		* worldProjectionMatrix(vec3(0, 0, 2))
		;

	for(int i = 0; i < VERTICES/2; i++) {
		float x1 = i/VERTICES.*4*PI;
		float x2 = (i+1)/VERTICES.*4*PI;
		float r = .995+.004*cos(g*TWOPI);

		float uy = cos(g*TWOPI)+sin(2*g*TWOPI)+cos(g*PI)+sin(g*TWOPI);

		// vec3 u = randomUnitVector(cos(g+iTime), sin(g));
		// vec3 u = vec3(cos(t+g), 2*cos(t+g*.5), sin(t+g));
		vec3 u = vec3(cos(t+g*TWOPI), uy, sin(t+g*TWOPI));
		u /= length(u);

		vec4 p1 = point(u, r, x1);
		vec4 p2 = point(u, r, x2);
		if(p1.z < 0 || p2.z < 0)
			continue;
		gl_Position = toGLCoords(proj*p1);
		EmitVertex();
		gl_Position = toGLCoords(proj*p2);
		EmitVertex();
		EndPrimitive();
	}
}
