package wonder.shaderdisplay.uniforms;

import java.util.Arrays;

import imgui.ImGui;

class ControlMatN implements UniformControl {
	
	private final int matrixSize;
	
	public ControlMatN(int matrixSize) {
		this.matrixSize = matrixSize;
	}
	
	@Override
	public void renderControl(String name, float[] value) {
		float[] p = new float[1];
		if(ImGui.button("C##" + name))
			ImGui.setClipboardText("mat" + matrixSize + Arrays.toString(value).replace('[', '(').replace(']', ')'));
		if(ImGui.isItemHovered())
			ImGui.setTooltip("Copy to clipboard");
		ImGui.sameLine();
		ImGui.text(name);
		ImGui.beginTable("#a", matrixSize);
		for(int i = 0; i < matrixSize; i++) {
			ImGui.tableNextRow();
			for(int j = 0; j < matrixSize; j++) {
				p[0] = value[i*matrixSize+j];
				ImGui.tableNextColumn();
				ImGui.dragFloat("##"+value+","+i+","+j, p, .1f);
				value[i*matrixSize+j] = p[0];
			}
		}
		ImGui.endTable();
	}
}