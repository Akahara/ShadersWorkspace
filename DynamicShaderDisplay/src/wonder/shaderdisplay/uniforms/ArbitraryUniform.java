package wonder.shaderdisplay.uniforms;

public interface ArbitraryUniform {
	
	public Number[][] getValues();
	public ArbitraryUniform copy(ArbitraryUniform old);
	
}
