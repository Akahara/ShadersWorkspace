package wonder.shaderdisplay.uniforms;

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
		switch(size) {
		case 1: ImGui.dragFloat (name, value, .01f); break;
		case 2: ImGui.dragFloat2(name, value, .01f); break;
		case 3: ImGui.dragFloat3(name, value, .01f); break;
		case 4: ImGui.dragFloat4(name, value, .01f); break;
		}
	}
}