#version 430 core

uniform vec2 u_resolution;
uniform float u_time;

layout(location=0) out vec4 color;

mat3 cameraProjection(vec3 eye, vec3 target, vec3 up, float fov) {
  vec3 w = normalize(target-eye);
  vec3 u = -normalize(cross(up, w)) * fov;
  vec3 v = -normalize(cross(w, u)) * fov;
  return mat3(u, v, w);
}

float hit_sphere(vec3 center, float radius, vec3 origin, vec3 dir) {
  vec3 u = origin-center;
  float a = dot(dir, dir);
  float b = 2*dot(u, dir);
  float c = dot(u, u) - radius*radius;
  float d = b*b-4*a*c;
  if(d < 0)
    return -1;
  return -(b+sqrt(d))/(2*a);
}

void main(void) {
  vec2 uv = (gl_FragCoord.xy*2-u_resolution.xy)/u_resolution.y;
  vec3 col = vec3(0);

  float t = u_time;
  vec3 eye = vec3(cos(t)*3, cos(t), sin(u_time)*3);
  vec3 target = vec3(0, 0, 0);
  
  mat3 camera = cameraProjection(eye, target, vec3(0, 1, 0), 1);
  vec3 ray = normalize(camera * vec3(uv, 1));
  
  vec3 sphereCenter = vec3(0);
  float d = hit_sphere(sphereCenter, 1, eye, ray);
  if(d >= 0) {
    // color according to the sphere normal
    vec3 p = eye + ray*d;
    vec3 normal = normalize(p-sphereCenter);
    col += normal*.5+.5;
    // add red bands on the sphere
    vec3 sp = p-sphereCenter;
    float H = .01;
    if(abs(sp.x) < H || abs(sp.y) < H || abs(sp.z) < H)
      col = 1 - col;
  }

  color = vec4(col, 1);
}
