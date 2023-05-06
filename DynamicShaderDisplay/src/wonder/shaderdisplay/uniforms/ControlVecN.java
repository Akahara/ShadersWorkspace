package wonder.shaderdisplay.uniforms;

import java.util.Arrays;

import imgui.ImGui;

class ControlVecN implements UniformControl {
	
	private final int size;
	
	public ControlVecN(int vecSize) {
		this.size = vecSize;
		if(vecSize < 1 || vecSize > 4)
			throw new IllegalArgumentException();
	}
	
	@Override
	public void renderControl(String name, float[] value) {
		if(ImGui.button("C##" + name))
			ImGui.setClipboardText(size == 1 ?
					String.valueOf(value[0]) :
					"vec" + size + Arrays.toString(value).replace('[', '(').replace(']', ')'));
		if(ImGui.isItemHovered())
			ImGui.setTooltip("Copy to clipboard");
		ImGui.sameLine();
		switch(size) {
		case 1: ImGui.dragFloat (name, value, .01f); break;
		case 2: ImGui.dragFloat2(name, value, .01f); break;
		case 3: ImGui.dragFloat3(name, value, .01f); break;
		case 4: ImGui.dragFloat4(name, value, .01f); break;
		}
	}
}