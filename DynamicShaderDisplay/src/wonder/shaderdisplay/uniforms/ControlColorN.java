package wonder.shaderdisplay.uniforms;

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
		switch(size) {
		case 3: ImGui.colorEdit3(name, value); break;
		case 4: ImGui.colorEdit4(name, value); break;
		}
	}
}