package wonder.shaderdisplay.uniforms.arbitrary;

import static org.lwjgl.opengl.GL20.glUniform1i;

import java.util.Arrays;

import imgui.ImGui;
import wonder.shaderdisplay.controls.ImGuiSystem;
import wonder.shaderdisplay.uniforms.*;

public class ArbitraryBoolUniform extends EditableUniform implements ArbitraryUniform {

	private final UniformLocationCache locationCache;
	private final boolean[] values;
	
	public ArbitraryBoolUniform(int program, String name, boolean[] initialValues) {
		super(name);
		this.values = initialValues;
		this.locationCache = initialValues.length > 1 ? new ArrayLocationCache(program, name) : new ValueLocationCache(program, name);
	}

	@Override
	public void apply(UniformApplicationContext context) {
		for(int i = 0; i < values.length; i++)
			glUniform1i(locationCache.getLocation(i), values[i]?1:0);
	}

	@Override
	public void renderControl() {
		if(values.length > 1) {
			ImGuiSystem.copyToClipboardBtn(name+"_all", () ->
				"bool[]" + Arrays.toString(values).replace('[', '(').replace(']', ')'));
			ImGui.sameLine();
			ImGui.text(name);
			for(int i = 0; i < values.length; i++) {
				final int ii = i;
				String name = this.name + '[' + i + ']';
				ImGuiSystem.copyToClipboardBtn(name, () -> String.valueOf(values[ii]));
				ImGui.sameLine();
				if(ImGui.checkbox(name, values[i]))
					values[i] = !values[i];
			}
		} else {
			ImGuiSystem.copyToClipboardBtn(name, () -> String.valueOf(values[0]));
			ImGui.sameLine();
			if(ImGui.checkbox(name, values[0]))
				values[0] = !values[0];
		}
	}
	
	@Override
	public ArbitraryBoolUniform copy(Number[][] oldValues) {
		int copyRows = Math.min(oldValues.length, values.length);
		for(int i = 0; i < copyRows; i++) {
			values[i] = false;
			for(int j = 0; j < oldValues[i].length; j++) {
				if(oldValues[i][j].floatValue() != 0) {
					values[i] = true;
					break;
				}
			}
		}
		return this;
	}

	@Override
	public Number[][] getValues() {
		Number[][] packed = new Number[values.length][1];
		for(int i = 0; i < values.length; i++)
			packed[i][0] = values[i] ? 1 : 0;
		return packed;
	}

	@Override
	public String toUniformString() {
		boolean isArray = values.length > 1;
		StringBuilder sb = new StringBuilder();
		sb.append("uniform bool ").append(name);
		if(isArray)
			sb.append("[").append(values.length).append("] = bool[](");
		else
			sb.append(" = ");
		for(int i = 0; i < values.length; i++) {
			sb.append(values[i]);
			if(i != values.length-1)
				sb.append(", ");
		}
		if(isArray)
			sb.append(')');
		sb.append(';');
		return sb.toString();
	}

}
