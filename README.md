# Shaders Workspace

A collection of shaders I made along with a small renderer (dsd).
You can browse the shader collections and/or use the renderer to make your own.

## Shader examples

![could not load image](https://github.com/Akahara/ShadersWorkspace/blob/master/screens/summer_butterfly.png?raw=true)
![](https://github.com/Akahara/ShadersWorkspace/blob/master/screens/flows.png?raw=true)

These are animated when run, see more at `/screens`.

## dsd

The dsd (dynamic shader display) tool is a renderer for fragment shaders, you can use it to run any shader in this repository.

To run dsd, first download or build the .jar file, then use one of the following commands:

```bash
# Simply open a window displaying the shader
java -jar dsd.jar run <shader file>

# Also take a screenshot
java -jar dsd.jar run -s <screenshot file> (-d <delay in seconds>) <shader file>

# Create a file full of useful snippets
java -jar dsd.jar snippets <snippets file>

# Get help
java -jar dsd.jar ? (or --help)
```

Note that on Mac OS X you will need to add `-XstartOnFirstThread` before `-jar`, otherwise you might get unexpected crashes.
Java 13 is needed in order to run dsd. If you build it yourself you should be able to downgrade JRE compliance by removing unecessary libraries.
dsd has been tested on both Mac OS X Lion and Windows 10, it should also work on linux but no garanties are made.

## Build instructions:

Most build dependencies are located in `dsd/lib`, `fr.wonder.commons.systems` is also required but I have not made this repository public yet (comming soon), you should easily be able to remove the code relying on it.
This project was successfully built using the Eclipse IDE, it was stripped of its project files.

## TODOs

- [ ] Handle more than just fragment shaders
- [ ] Handle more uniforms (textures...)
