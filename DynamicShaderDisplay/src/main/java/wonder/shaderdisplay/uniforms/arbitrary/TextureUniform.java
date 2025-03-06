package wonder.shaderdisplay.uniforms.arbitrary;

import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.type.ImString;
import wonder.shaderdisplay.serial.InputFiles;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.scene.UniformDefaultValue;
import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

import static org.lwjgl.opengl.GL20.glUniform1i;

public class TextureUniform extends NonEditableUniform {

	private final int location;

	private final int inputTextureSlot;
	private final Texture fixedInputTexture;
	private final String fixedTextureName;
	private final String fixedRenderTargetName;

	private String currentlyBoundTextureName;
	
	// there is a memory leak here, when texture caching is disabled texture uniforms are not
	// properly deleted and their textures are not freed.
	
	public TextureUniform(int program, String name, Texture texture, String textureName) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
		this.fixedInputTexture = texture;
		this.inputTextureSlot = -1;
		this.fixedTextureName = textureName;
		this.fixedRenderTargetName = null;
	}

	public TextureUniform(int program, String name, String renderTargetName) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
		this.fixedInputTexture = null;
		this.inputTextureSlot = -1;
		this.fixedTextureName = null;
		this.fixedRenderTargetName = renderTargetName;
	}

	public TextureUniform(int program, String name, int inputTextureSlot) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
		this.fixedInputTexture = null;
		this.inputTextureSlot = inputTextureSlot;
		this.fixedTextureName = null;
		this.fixedRenderTargetName = null;
	}

	public TextureUniform(int program, String name) {
		this(program, name, null, null);
	}
	
	@Override
	public void apply(UniformApplicationContext context) {
		int bindingSlotIndex = context.getNextTextureSlot();
		glUniform1i(location, bindingSlotIndex);
		Texture boundTexture = null;
		UniformDefaultValue defaultUniformValue = context.getDefaultUniform(name);

		if (boundTexture == null && fixedRenderTargetName != null) {
			boundTexture = context.getRenderTargetReadableTexture(name, fixedRenderTargetName);
			currentlyBoundTextureName = "render target '" + fixedRenderTargetName + "'";
		}
		if (boundTexture == null && defaultUniformValue != null) {
			boundTexture = context.getRenderTargetReadableTexture(name, defaultUniformValue.value);
			currentlyBoundTextureName = "render target '" + defaultUniformValue.value + "'";
		}
		if (boundTexture == null && inputTextureSlot >= 0) {
			boundTexture = InputFiles.singleton.getInputTexture(inputTextureSlot);
			currentlyBoundTextureName = "input" + inputTextureSlot;
		}
		if (boundTexture == null) {
			boundTexture = fixedInputTexture;
			currentlyBoundTextureName = fixedTextureName;
		}
		if (boundTexture == null) {
			boundTexture = Texture.getMissingTexture();
			currentlyBoundTextureName = "missing!";
		}
		boundTexture.bind(bindingSlotIndex);
	}
	
	@Override
	public void renderControl() {
		ImGui.beginDisabled();
		ImGui.inputText(name, new ImString(currentlyBoundTextureName));
		ImGui.endDisabled();
		if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
			ImGui.setTooltip("""
				You can specify the texture path as such:
				  uniform sampler2D u_texture; // texturepath.png
				Or use one of the built-in textures by giving its id:
				  uniform sampler2D u_texture; // builtin 0
				Or to get the previous frame render target:
				  uniform sampler2D u_texture; // target 0
				  Only available when not using a scene file!
				Or use render targets using a scene file:
				  {
				    "rendertargets": [ { "name": "somerendertarget" } ],
				    "layers": [{
				      "fragment": "myshader.fs",
				      "uniforms": [ { "name": "u_texture", "value": "somerendertarget" ]
				    }]
				  }
				  See the documentation on render targets for more
				Additionally you can add "input or " to use the input texture first when running with "dsd image"
				  uniform sampler2D u_texture; // input or builtin 0""");
		}
	}
	
}