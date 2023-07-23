package wonder.shaderdisplay.uniforms;

import java.util.Arrays;

import imgui.ImGui;
import wonder.shaderdisplay.UserControls;
import wonder.shaderdisplay.uniforms.GLUniformType.FloatUniformControl;

class ControlFloatVecN implements FloatUniformControl {
	
	private final int size;
	
	public ControlFloatVecN(int vecSize) {
		this.size = vecSize;
		if(vecSize < 1 || vecSize > 4)
			throw new IllegalArgumentException();
	}
	
	@Override
	public void renderControl(String name, float[] value) {
		UserControls.copyToClipboardBtn(name, () -> size == 1 ?
			String.valueOf(value[0]) :
			"vec" + size + Arrays.toString(value).replace('[', '(').replace(']', ')')
		);
		ImGui.sameLine();
		switch (size) {
		case 1 -> ImGui.dragFloat(name, value, .01f);
		case 2 -> ImGui.dragFloat2(name, value, .01f);
		case 3 -> ImGui.dragFloat3(name, value, .01f);
		case 4 -> ImGui.dragFloat4(name, value, .01f);
		}
	}
	
}