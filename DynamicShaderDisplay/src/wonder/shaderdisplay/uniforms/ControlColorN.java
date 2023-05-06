package wonder.shaderdisplay.uniforms;

import java.util.Arrays;

import imgui.ImGui;

class ControlColorN implements UniformControl {
	
	private final int size;
	
	public ControlColorN(int vecSize) {
		this.size = vecSize;
		if(vecSize < 3 || vecSize > 4)
			throw new IllegalArgumentException();
	}
	
	@Override
	public void renderControl(String name, float[] value) {
		if(ImGui.button("C##" + name))
			ImGui.setClipboardText("vec" + size + Arrays.toString(value).replace('[', '(').replace(']', ')'));
		if(ImGui.isItemHovered())
			ImGui.setTooltip("Copy to clipboard");
		ImGui.sameLine();
		switch(size) {
		case 3: ImGui.colorEdit3(name, value); break;
		case 4: ImGui.colorEdit4(name, value); break;
		}
	}
}