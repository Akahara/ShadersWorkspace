# Shaders Workspace

A collection of shaders I made along with a small renderer (dsd).
You can browse the shader collections and/or use the renderer to make your own.

## Shader examples

![could not load image](https://github.com/Akahara/ShadersWorkspace/blob/master/screens/summer_butterfly.png?raw=true)
![could not load image](https://github.com/Akahara/ShadersWorkspace/blob/master/screens/flows.png?raw=true)

These are animated when run, see more at `/screens`.

## dsd

The dsd (dynamic shader display) tool is a renderer for fragment shaders, you can use it to run any shader in this repository.

To run dsd, first download or build the .jar file, then use one of the following commands:

```bash
# Simply open a window displaying the shader
java -jar dsd.jar run <shader file>

# Create a file full of useful snippets
java -jar dsd.jar listsnippets
java -jar dsd.jar snippets >> snippets.fs

# Run dsd with all shader stages
java -jar dsd.jar -c compute.cs -g geometry.gs -v vertex.gs fragment.fs

# Get help
java -jar dsd.jar ? (or --help)
```

Note that on Mac OS X you will need to add `-XstartOnFirstThread` before `-jar`, otherwise you might get unexpected crashes.
Java 13 is needed in order to run dsd. If you build it yourself you should be able to downgrade JRE compliance by removing unecessary libraries.
dsd has been tested on both Mac OS X Lion and Windows 10, it should also work on linux but no garanties are made.

### Custom input data

To render things on the screen that cannot be completely generated through a fragment shader (such as points and lines) you can either:
- define your own vertices in a geometry shader
- use a compute shader
- load vertex data from a file
- generate the data on the fly using an external script

> Note that currently, only the vertices positions can be user-defined. Additional data must be derived if necessary, in the geometry shader for example.

#### The geometry shader

To add a geometry shader stage, use `--geometry <file>` or `-g <file>`.

The geometry shader can be used to take in vertex data and output some (possibly unrelated) other vertices. By default the input to your geometry shader will be the triangles vertices used by a rectangle covering the screen. To generate other primitives you may change the geometry shader's output type, for example:
```glsl
#version 400 core

#define INSTANCES 32

layout(triangles) in; // mandatory, even if you do not use the input vertices
layout(invocations = INSTANCES) in;
layout(line_strip, max_vertices=VERTICES) out;

void main(void) {
  // output the 2 points defining a line
  // the positions of the points may depend on the input data, and probably gl_InvocationID
  gl_Position = ...;
  EmitVertex();
  gl_Position = ...;
  EmitVertex();
  EndPrimitive();
}
```

In this snippet `INSTANCES` is the number of times the geometry shader will be called per frame, `gl_InvocationID` corresponds to the index of the invocation (from 0 to `INSTANCES` non inclusive).

See a full example at `/shaders/circled_sphere.gs`, use the default fragment shader.

#### The compute shader

To add a geometry shader stage, use `--compute <file>` or `-c <file>`.

The compute shader may be used to procedurally generate vertex data, the output of the compute shader is *directly* fed to the next stage (the geometry shader if it exists or the vertex shader).

Currently the only handled output format is the following, note that only triangles may be output:

```glsl
layout(std430, binding = 0) buffer VerticesBuffer
{
	vec4 vertices[];
};

layout(std430, binding = 1) buffer IndicesBuffer
{
	uint triangleCount;
	uint indices[];
};
```

To output other types of primitives use the geometry shader.

See a full example at `/shaders/compute`. Note that the compute shader is not the prefered way to input custom vertex data.

#### Using an input file

Raw input files may be used with `--input <file>` or `-i <file>`.

The input file is fully read at each reload and must respect the following format:
The file must start with the type of primitive it contains, either `points`, `lines` or `triangles`.
The remaining of the file will be read in groups of 4 floats, separated by any white character (spaces, line feeds, tabs...) or semi-column (.csv files should in theory be fully supported). Each group corresponding to a single vertex.

The first three values in a vertex are its xyz-position and the fourth should always be 1.

> For example, a valid input file would be:
> ```
> points
> -1 -.5 0 1
> 1 -.5 0 1
> 1.73 .5 0 1
> ```
> This makes an equilateral triangle, but will be displayed as points

The number of vertices must fit the primitive type (if the primitive type is `lines`, the number of vertices must be a multiple of 2, 3 for `triangles`...).

#### Using a script file

An external script can also be used with `--script <file>`. The executable will be run on each reload and its output will be parsed exactly as if it was an input file.

To debug the script output you can either simply run the script without dsd or use `--script-log <length>`.

For example, using python:
```python
#!/usr/bin/python3

# generates a random point in range (0, 0, 0, 1) to (1, 1, 0, 1)
def randpoint():
  ...

print("lines")
for i in range(100):
  print(randpoint())
  print(randpoint())
```

To tell the file it is executable (using the hashbang `#!`) you can use the `chmod` command on linux.

> chmod u+x script.py

To run dsd use

> java -jar dsd.jar --script script.py frag.fs

> The scripts are reexecuted each time a file changes, if it takes too long to run simply save the script output to a file and then run dsd with the file as input directly.

## Build instructions:

Most build dependencies are located in `dsd/lib` and the `pom.xml` file (there are both maven dependencies and jars).
This project was successfully built using the Eclipse IDE, it was stripped of its project files.
Alternatively check for the [latest release](https://github.com/Akahara/ShadersWorkspace/releases/latest).

## Roadmap

- [x] Handle geometry shaders
- [x] Handle vertex shaders
- [ ] Handle tessellation control and evaluation shaders (low priority)
- [x] Handle compute shaders
- [x] Handle more uniforms (textures...)
- [x] Find a way to input user defined vertex data
- [ ] Find a way to input more than vertex position in user defined data
