# Shaders Workspace

A collection of shaders and a tool for real-time rendering and developping for shaders!

## Some shaders

| ![could not load image](screens/analogic.png) | ![could not load image](screens/not_voronoi.png) |
|-|-|
| ![could not load image](screens/summer_butterfly.png) | ![could not load image](screens/flows.png) |
| These are animated when run | |

| ![could not load image](screens/environment.png) |
| - |
| The controls you get with `java -jar dsd.jar run` |


# DSD

The DSD (dynamic shader display) tool is a renderer for fragment shaders, you can use it to run any shader in this repository.

It supports OpenGL shaders like you would find on [shadertoy](https://shadertoy.com). *DSD is not an editor*, you may use any text or shader editor, Using VSCode + a glsl extension works great for me.

To run dsd, first download or build the .jar file, then use one of the following commands:

```bash
# Simply open a window displaying the shader
java -jar dsd.jar run shader.fs

# Generate a 10s video
java -jar dsd.jar video shader.fs -d 10
# Apply a shader to some images
java -jar dsd.jar image shader.fs someimage1.png someimage2.jpg
# Save a single frame
java -jar dsd.jar screenshot shader.fs output.png

# Run dsd with all shader stages
java -jar dsd.jar -c compute.cs -g geometry.gs -v vertex.gs fragment.fs

# Get help
java -jar dsd.jar --help <command>
```

Java 16+ is needed in order to run dsd.

DSD has been tested on Windows 10, it should also work on OSX/linux but no guaranties are made (if you encounter crashes with ImGui add `--no-gui`).

Note that on Mac OS X you will need to add `-XstartOnFirstThread` before `-jar`, also you may need to specify the version of OpenGL using `--force-gl-version <version>` because Metal is not supported.

### Using uniforms

- Uniforms of type `mat2/3/4`, `vec2/3/4`, `float`, `int` and `bool` will appear with controls in the HUD.
- Uniforms of type `vec3/4` and with a name starting with `c_` will have color controls instead.
- Uniform arrays are supported, but non-square matrices are not.
- 2D textures are supported (see the next section).

Some uniforms are predefined:
| Name & type | Value |
|---|---|
| `float u_time` | Run time in seconds |
| `int/float u_frame/iFrame` | `=u_time/fps`, might go up by more than 1 unless `-e` is used |
| `(i)vec2 u_resolution/iResolution` | Width/Height of the screen |
| `mat4 u_view` | View matrix for 3D scenes, move by clicking and pressing `wasd` |
| `vec3 u_viewPosition` | Position of the camera (see u_view) |
| `vec3 u_viewDirection` | Direction of the camera (see u_view) |
| `float u_framerate` | Expected fps (60 by default, change with `--fps`) |
| `int u_click` | 0 if the mouse is not pressed, goes up by 1 each frame |
| `(i)vec2 u_mouse/u_cursor` | Position of the mouse in pixels |

### Using textures

Textures are defined directly in code, using a comment to specify the data source

```glsl
/* Load a texture directly from files */
uniform sampler2D u_texture; // path/to/image.png
/* Use one of the built-in textures */
uniform sampler2D u_texture; // builtin 1
/* When running with the "image" command, use the input image the fallback is any of the above options */
uniform sampler2D u_texture; // input or <fallback>
```

### Scene description

When a single shader is not enough, use many! Scene files can be used to describe how to run many shaders one after the other.

Example scene file:
```json
{
  "$schema": "scene.schema.json",
  "version": "1.0",
  "macros": [
    // "INDEV",
    // "FREECAM",
  ],
  "render_targets": [
    { "name": "comet" },
    { "name": "main", "width": 1, "height": 1 },
    { "name": "bloom0", "width": 1, "height": 1 },
    { "name": "bloom1", "width": 0.5, "height": 0.5 },
    { "name": "bloom2", "width": 0.25, "height": 0.25 },
    { "name": "depth", "type": "depth" },
  ],
  "layers": [
    {
      "pass": "clear",
      "targets": [ "screen", "main", "depth" ],
    },
    {
      "fragment": "ground.fs",
      "vertex": "ground.vs",
      "geometry": "splittriangles.gs",
      "model": "gen_trigrid.obj",
      "targets": [ "main", "depth" ],
      "depth_test": true,
      "depth_write": true,
      "culling": "back",
    },
    {
      "fragment": "comet.fs",
      "vertex": "blitz.vs",
      "targets": [ "main", "comet", "depth" ],
      "blending": "additive",
      "macros": [ "DRAW" ],
      "depth_test": true,
    },
    // Many more layers omitted
    {
      "pass": "blit",
      "source": "main",
      "targets": "screen",
    }
  ],
}
```

Most fields are simple enough to use, simple create a .json file and make the `"$schema"` point to `scene.schema.json`, using VSCode you will get intellisense.

To run a scene, simply run `dsd run yourscene.json` as you would for a simple fragment shader.

### Including files

glsl does not support `#include`s by default, DSD does. It does not protect against circular inclusions however.
```glsl
#include "myothershader.glsl"
#include "../myotherothershader.glsl"
```
If there is an error in one of the files, the line number in the error message will be correct but you won't know which file is wrong...
Run with `--debug-resolved-shaders` to see exactly what the shader compiler processes.

### Debugging

DSD includes debugging tools to know where things went wrong:
```glsl
#include "dsd_debug.glsl"

void main() {
  DSD_DEBUG_INIT();
  DSD_DEBUG_CURSOR(); // To make only the pixel under the cursor output anything, you will need "uniform vec2 u_cursor;"
  // Alternatively use DSD_DEBUG_COND(enabled)

  vec2 somevar = ...;
  DSD_DEBUG(somevar, somevar);
  DSD_DEBUG(somevarminusone, somevar-1);
}
```
The value of `somevar` and `somevar-1` will show up in the Debug window.

It also works in non-fragment shaders, but `DSD_DEBUG_CURSOR` wont work, you will have to rely on `DSD_DEBUG_COND`.

### Render targets

It is possible to read and write to multiple render targets, it goes like this:

```json
{
  "render_targets": [ { "name": "offscreen" } ],
  "layers": [
    {
        "fragment": "shader.fs",
        "uniforms": [
          { "name": "u_previousTarget", "value": "screen" },
          { "name": "u_target1", "value": "offscreen" }
        ],
        "targets": [ "screen", "offscreen" ],
    },
  ]
}
```

```glsl
// shader.fs
uniform sampler2D u_previousFrame;
uniform sampler2D u_target1;

layout(location=0) out vec4 color;
layout(location=1) out vec4 target1out;
```

All `out` targets must be written to every frame, render targets are double-buffered, if you write to `color` you won't see a change to `u_previousFrame` until next frame.

You may want to add the `-rt` flags when using render targets:
- `-r`: Resets time uniforms when shaders are updated
- `-t`: Clears the render target textures when shaders are updated
- `-e`: Prevents time uniforms to "catch-up" when something slowed down a frame, `u_frame` will always go up by 1

### Generating videos and images

**Common options**: `--fps <fps>`, `--width/-w <w>`, `--height/-h <h>`, `--output/-o <file>`\
**Video options**: `--first-frame/-f <f>`, `--last-frame/-l <f>`, `--duration/-d <d>` (in seconds), `--ffmpeg-path <path>`\
**Image options**: `--size-to-image/-s`\
**Screenshot options**: `--screenshot-frame <f>`, `--run-from <firstframe>`

See the help for individual commands `java -jar dsd.jar ? video`.

[ffmpeg](https://ffmpeg.org/download.html) is required to generate videos.

Image and screenshot are very similar, but image works on multiple input images but runs for a single frame per image, use screenshot if you are using render targets.

### Snippets

Snippets can be accessed at runtime by entering `snippets` or `snippets <filter>`, add `-c` to print their codes instead of the snippets list and `-o <file>` to write the snippets to a file.

Snippets file are searched for when starting the app, all files ending in `.snippets` near the execution directory will be scanned for snippets. Snippet syntax and example:
```
BEGIN <name>
<snippet body>
EOS

BEGIN Universal constants
#define PI 3.141592
EOS
```

### Non-fragment shaders

Geometry and vertex shaders can be used either from a scene file or using `-g`/`-v`.

Compute shaders can only be used with a scene file, to pass data from one layer to another use storage buffers:
```glsl
{
  "storage_buffers": [
    {
      "name": "geometrygen",
      "size": 4096
    }
  ],
  "layers": [
    {
      {
        "pass": "compute",
        "compute": "tools/generate_cubes.cs",
        "storage_buffers": [ { "name": "geometrygen" } ],
        "dispatch": [ 10, 10, 10 ],
      },
    }
  ]
}
```

## Build instructions:

All dependencies are managed using maven, just run `mvn package`.
