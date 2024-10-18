package wonder.shaderdisplay.uniforms.arbitrary;

public interface ArbitraryUniform {
	
	Number[][] getValues();
	ArbitraryUniform copy(Number[][] copied);
	
}
