#version 430

layout(local_size_x = 1, local_size_y = 1) in;

uniform float iTime;

layout(std430, binding = 0) buffer VerticesBuffer
{
	vec4 vertices[];
};

layout(std430, binding = 1) buffer IndicesBuffer
{
	uint triangleCount;
	uint indices[];
};

void main() {
	for(uint x = 0; x < 10; x++) {
		for(uint y = 0; y < 10; y++) {
			uint i = x*10+y;
			vertices[i] = vec4(cos(i + iTime), sin(i + iTime), 0, 1);
		}
	}
	for(uint i = 0; i < 10; i++) {
		for(uint j = 0; j < 3; j++) {
			indices[i*3+j] = int(2*i+j);
		}
	}
	triangleCount = 6;
}
