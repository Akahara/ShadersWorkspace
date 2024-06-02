package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1i;

import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.type.ImString;
import wonder.shaderdisplay.display.Texture;

class TextureUniform extends Uniform {

	private final int location;
	private final Texture texture;
	private final String textureName;
	private final int textureIndex;
	
	// there is a memory leak here, when texture caching is disabled texture uniforms are not
	// properly deleted and there textures are not freed.
	
	TextureUniform(int program, int textureIndex, String name, Texture texture, String textureName) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
		this.texture = texture;
		this.textureName = textureName;
		this.textureIndex = textureIndex;
	}
	
	@Override
	public void apply() {
		glUniform1i(location, textureIndex);
		texture.bind(textureIndex);
	}
	
	@Override
	public void renderControl() {
		ImGui.beginDisabled();
		ImGui.inputText(name, new ImString(textureName));
		ImGui.endDisabled();
		if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
	        ImGui.setTooltip(
					"""
							You can specify the texture path as such:
							  uniform sampler2D u_texture; // texturepath.png
							Or use one of the built-in textures by giving its id:
							  uniform sampler2D u_texture; // builtin 0
							Or use render targets:
							  uniform sampler2D u_texture; // target 0
							  where 0 is the previous frame and 1+ are targets you can fill by adding
							  layout(location=1) out vec4 target1;  and writing to target1 each frame.
							Additionally you can add "input or " to use the input texture first when running with "dsd image"
							  uniform sampler2D u_texture; // input or builtin 0""");
		}
	}

	@Override
	public String toUniformString() {
		return "uniform sampler2D " + name + "; // " + textureName;
	}
	
}