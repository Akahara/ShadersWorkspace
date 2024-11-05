package wonder.shaderdisplay.uniforms.arbitrary;

import wonder.shaderdisplay.uniforms.Uniform;

public interface ArbitraryUniform {

	Number[][] getValues();
	ArbitraryUniform copy(Number[][] copied);
	default Uniform asUniform() { return (Uniform) this; }
	
}
