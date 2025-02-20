#version 430 core

layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

layout(std430, binding = 0) buffer buffer_0
{
  int bufferData[];
};

layout(std430, binding = 1) buffer indirectdraw {
  uint count;
  uint instanceCount;
  uint firstIndex;
  uint baseVertex;
  uint baseInstance;
};

void main()
{
  uint i = gl_LocalInvocationID.x + gl_WorkGroupID.x * gl_WorkGroupSize.x;
  atomicAdd(bufferData[0], 1);
}