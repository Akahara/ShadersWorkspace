#version 400 core

// Run with "dsd run sample_geometry.fs -g sample_geometry.gs"

#define PI 3.141592
#define INSTANCES 30

layout(triangles) in;
layout(invocations = INSTANCES) in;

layout(line_strip, max_vertices=5) out;

uniform vec2 iResolution;
uniform float iTime;

mat2 rot(float a) {
	return mat2(
		cos(a), -sin(a),
		sin(a),  cos(a)
	);
}

vec2 rotPI2(vec2 v) {
	return vec2(-v.y, v.x);
}

vec4 toGLCoords(vec2 u) {
	float m = min(iResolution.y,iResolution.x)-1;
	return vec4(u.x/iResolution.x*m, u.y*m/iResolution.y, 0, 1);
}

void main(void) {
	vec2 l = vec2(-1, -1);
	float t = iTime * .1;
	for(int i = 0; i < gl_InvocationID; i++) {
		l = mix(l, rotPI2(l), vec2(mod(t, 1)));
	}
	gl_Position = toGLCoords(vec2(l)); EmitVertex(); l = rotPI2(l);
	gl_Position = toGLCoords(vec2(l)); EmitVertex(); l = rotPI2(l);
	gl_Position = toGLCoords(vec2(l)); EmitVertex(); l = rotPI2(l);
	gl_Position = toGLCoords(vec2(l)); EmitVertex(); l = rotPI2(l);
	gl_Position = toGLCoords(vec2(l)); EmitVertex(); l = rotPI2(l);
	EndPrimitive();
}
