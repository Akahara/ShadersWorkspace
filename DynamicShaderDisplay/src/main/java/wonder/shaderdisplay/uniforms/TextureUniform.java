package wonder.shaderdisplay.uniforms;

import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.type.ImString;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.EntryImage;
import wonder.shaderdisplay.scene.SceneUniform;

import static org.lwjgl.opengl.GL20.glUniform1i;

class TextureUniform extends NonEditableUniform {

	private final int location;
	private final boolean useImagePassInputIfPossible;
	private final Texture texture;
	private final String textureName;

	private String currentlyBoundTextureName;
	
	// there is a memory leak here, when texture caching is disabled texture uniforms are not
	// properly deleted and their textures are not freed.
	
	TextureUniform(int program, String name, boolean useImagePassInputIfPossible, Texture texture, String textureName) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
		this.useImagePassInputIfPossible = useImagePassInputIfPossible;
		this.texture = texture;
		this.textureName = textureName;
	}

	TextureUniform(int program, String name) {
		this(program, name, false, null, null);
	}
	
	@Override
	public void apply(UniformApplicationContext context) {
		int bindingSlotIndex = context.getNextTextureSlot();
		glUniform1i(location, bindingSlotIndex);
		Texture boundTexture = null;
		SceneUniform defaultUniformValue = context.getDefaultUniform(name);
		if (useImagePassInputIfPossible && EntryImage.isImagePass()) {
			boundTexture = EntryImage.getImagePassInputTexture();
			currentlyBoundTextureName = "image pass input";
		}
		if (boundTexture == null && defaultUniformValue != null) {
			boundTexture = context.getRenderTargetReadableTexture(name, defaultUniformValue.value);
			currentlyBoundTextureName = "render target '" + defaultUniformValue.value + "'";
		}
		if (boundTexture == null) {
			boundTexture = texture;
			currentlyBoundTextureName = textureName;
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