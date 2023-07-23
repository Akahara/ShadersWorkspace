package wonder.shaderdisplay.uniforms;

public interface ArbitraryUniform {
	
	Number[][] getValues();
	ArbitraryUniform copy(Number[][] copied);
	
}
