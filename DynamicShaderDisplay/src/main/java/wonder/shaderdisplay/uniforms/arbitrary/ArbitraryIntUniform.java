package wonder.shaderdisplay.uniforms.arbitrary;

import imgui.ImGui;
import wonder.shaderdisplay.controls.ImGuiSystem;
import wonder.shaderdisplay.uniforms.*;
import wonder.shaderdisplay.uniforms.GLUniformType.IntUniformControl;

public class ArbitraryIntUniform extends EditableUniform implements ArbitraryUniform {
	
	private final UniformLocationCache locationCache;
	private final GLUniformType type;
	private final IntUniformControl control;
	
	private final int[][] values;
	
	public ArbitraryIntUniform(int program, String name,
			GLUniformType type,
			IntUniformControl control,
			int[][] initialValues) {
		super(name);
		this.locationCache = initialValues.length > 1 ? new ArrayLocationCache(program, name) : new ValueLocationCache(program, name);
		this.control = control;
		this.type = type;
		this.values = initialValues;
	}
	
	@Override
	public void apply(UniformApplicationContext context) {
		for(int i = 0; i < values.length; i++)
			type.intSetterFunction.setUniform(locationCache.getLocation(i), values[i]);
	}
	
	@Override
	public void renderControl() {
		if(values.length > 1) {
			ImGuiSystem.copyToClipboardBtn(name+"_all", this::getValueAsGLSLString);
			ImGui.sameLine();
			ImGui.text(name);
			for(int i = 0; i < values.length; i++)
				control.renderControl(name + '[' + i + ']', values[i]);
		} else {
			control.renderControl(name, values[0]);
		}
	}
	
	@Override
	public ArbitraryIntUniform copy(Number[][] oldValues) {
		int copyRows = Math.min(oldValues.length, values.length);
		int copyCols = copyRows == 0 ? 0 : Math.min(oldValues[0].length, values[0].length);
		for(int i = 0; i < copyRows; i++)
			for(int j = 0; j < copyCols; j++)
				values[i][j] = oldValues[i][j].intValue();
		return this;
	}

	@Override
	public Number[][] getValues() {
		Number[][] packedValues = new Number[values.length][values[0].length];
		for(int i = 0; i < values.length; i++) {
			for(int j = 0; j < values[i].length; j++)
				packedValues[i][j] = values[i][j];
		}
		return packedValues;
	}

	@Override
	public String toUniformString() {
		boolean isArray = values.length > 1;
		StringBuilder sb = new StringBuilder();
		sb.append("uniform ");
		sb.append(type.name);
		sb.append(' ');
		sb.append(name);
		if(isArray) sb.append("[").append(values.length).append("]");
		sb.append(" = ");
		sb.append(getValueAsGLSLString());
		sb.append(';');
		return sb.toString();
	}
	
	private String getValueAsGLSLString() {
		int valueSize = values[0].length;
		boolean isArray = values.length > 1;
		StringBuilder sb = new StringBuilder();
		if(isArray) sb.append(type.name).append("[](");
		for(int i = 0; i < values.length; i++) {
			if(valueSize == 1) {
				sb.append(values[i][0]);
			} else {
				sb.append(type.name);
				sb.append('(');
				for(int j = 0; j < valueSize; j++) {
					sb.append(values[i][j]);
					if(j != valueSize-1)
						sb.append(',');
				}
				sb.append(')');
			}
			if(i != values.length-1)
				sb.append(", ");
		}
		if(isArray) sb.append(')');
		return sb.toString();
	}
	
}