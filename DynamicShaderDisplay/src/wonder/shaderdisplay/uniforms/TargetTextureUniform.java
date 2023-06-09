package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1i;

import imgui.ImGui;
import wonder.shaderdisplay.UserControls;

public class TargetTextureUniform extends Uniform {

	private final int location;
	private final int target;
	private final int textureIndex;
	
	public TargetTextureUniform(int program, int textureIndex, String name, int target) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
		this.textureIndex = textureIndex;
		this.target = target;
	}

	@Override
	public void apply() {
		glUniform1i(location, textureIndex);
	}

	@Override
	public void renderControl() {
		ImGui.text(name);
		ImGui.sameLine();
		if(ImGui.button("Target" + target))
			ImGui.setWindowFocus(UserControls.RENDER_TARGETS_WINDOW);
	}
	
	public int getTextureIndex() {
		return textureIndex;
	}

	@Override
	public String toUniformString() {
		return "uniform sampler2D " + name + "; // target " + target;
	}

}
