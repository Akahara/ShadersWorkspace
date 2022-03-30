#version 460 core

layout(points) in;

layout(points, max_vertices=1) out;

uniform vec2 iResolution;

vec4 snapInBounds(vec4 v) {
	v *= .4;
	v.w = 1;

	v.xy *= (iResolution-1)/iResolution;
	return v;
}

void main(void) {
	gl_Position = snapInBounds(gl_in[0].gl_Position);
	EmitVertex();
	EndPrimitive();
}
