package wonder.shaderdisplay.uniforms;

import java.util.Arrays;

import imgui.ImGui;
import wonder.shaderdisplay.UserControls;
import wonder.shaderdisplay.uniforms.GLUniformType.FloatUniformControl;

class ControlColorN implements FloatUniformControl {
	
	private final int size;
	
	public ControlColorN(int vecSize) {
		this.size = vecSize;
		if(vecSize < 3 || vecSize > 4)
			throw new IllegalArgumentException();
	}
	
	@Override
	public void renderControl(String name, float[] value) {
		UserControls.copyToClipboardBtn(name, () -> "vec" + size + Arrays.toString(value).replace('[', '(').replace(']', ')'));
		ImGui.sameLine();
		switch(size) {
		case 3: ImGui.colorEdit3(name, value); break;
		case 4: ImGui.colorEdit4(name, value); break;
		}
	}
}