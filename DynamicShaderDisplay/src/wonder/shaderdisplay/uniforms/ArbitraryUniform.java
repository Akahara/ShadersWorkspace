package wonder.shaderdisplay.uniforms;

class ArbitraryUniform extends Uniform {
	
	private final UniformLocationCache locationCache;
	private final GLUniformType type;
	private final UniformControl control;
	
	private float[][] values;
	
	public ArbitraryUniform(int program, String name,
			GLUniformType type,
			UniformControl control,
			float[][] initialValues) {
		super(name);
		this.locationCache = initialValues.length > 1 ? new ArrayLocationCache(program, name) : new ValueLocationCache(program, name);
		this.control = control;
		this.type = type;
		this.values = initialValues;
	}
	
	@Override
	public void apply() {
		for(int i = 0; i < values.length; i++)
			type.setterFunction.setUniform(locationCache.getLocation(i), values[i]);
	}
	
	@Override
	public void renderControl() {
		if(values.length > 1) {
			for(int i = 0; i < values.length; i++)
				control.renderControl(name + '[' + i + ']', values[i]);
		} else {
			control.renderControl(name, values[0]);
		}
	}
	
	public ArbitraryUniform copy(Uniform old) {
		if(!(old instanceof ArbitraryUniform))
			return this;
		ArbitraryUniform aold = (ArbitraryUniform) old;
		int copyRows = Math.min(aold.values.length, values.length);
		int copyCols = copyRows == 0 ? 0 : Math.min(aold.values[0].length, values[0].length);
		for(int i = 0; i < copyRows; i++)
			for(int j = 0; j < copyCols; j++)
				values[i][j] = aold.values[i][j];
		return this;
	}

	@Override
	public String toUniformString() {
		int valueSize = values[0].length;
		boolean isArray = values.length > 1;
		StringBuilder sb = new StringBuilder();
		sb.append("uniform ");
		sb.append(type.name);
		sb.append(' ');
		sb.append(name);
		if(isArray) sb.append("[" + values.length + "]");
		sb.append(" = ");
		if(isArray) sb.append(type.name + "[](");
		for(int i = 0; i < values.length; i++) {
			if(valueSize == 1) {
				sb.append(values[i][0]);
			} else {
				sb.append(type.name);
				sb.append('(');
				for(int j = 0; j < valueSize; j++) {
					sb.append(values[i][j]);
					if(j != valueSize-1)
						sb.append(',');
				}
				sb.append(')');
			}
			if(i != values.length-1)
				sb.append(", ");
		}
		if(isArray) sb.append(')');
		sb.append(';');
		return sb.toString();
	}
	
}