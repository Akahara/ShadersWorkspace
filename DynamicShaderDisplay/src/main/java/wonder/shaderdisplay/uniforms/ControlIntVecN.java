package wonder.shaderdisplay.uniforms;

import java.util.Arrays;

import imgui.ImGui;
import wonder.shaderdisplay.UserControls;
import wonder.shaderdisplay.uniforms.GLUniformType.IntUniformControl;

class ControlIntVecN implements IntUniformControl {
	
	private final int size;
	
	public ControlIntVecN(int vecSize) {
		this.size = vecSize;
		if(vecSize < 1 || vecSize > 4)
			throw new IllegalArgumentException();
	}
	
	@Override
	public void renderControl(String name, int[] value) {
		UserControls.copyToClipboardBtn(name, () -> size == 1 ?
			String.valueOf(value[0]) :
			"ivec" + size + Arrays.toString(value).replace('[', '(').replace(']', ')')
		);
		ImGui.sameLine();
		switch (size) {
		case 1 -> ImGui.dragInt(name, value);
		case 2 -> ImGui.dragInt2(name, value);
		case 3 -> ImGui.dragInt3(name, value);
		case 4 -> ImGui.dragInt4(name, value);
		}
	}
}