package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1i;

import imgui.ImGui;
import imgui.type.ImString;
import wonder.shaderdisplay.Texture;

class TextureUniform extends Uniform {

	private final int location;
	private final Texture texture;
	private final String textureName;
	private final int textureIndex;
	
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
	}
	
}