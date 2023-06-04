package wonder.shaderdisplay.uniforms;

import java.util.List;

class ArbitraryUniform extends Uniform {
	
	private final UniformLocationCache locationCache;
	private final GlUniformFloatFunction applyFunction;
	private final String type;
	private final boolean isArray;
	@SuppressWarnings("unused")
	private final int valueSize;
	private final UniformControl control;
	
	private List<float[]> values;
	
	public ArbitraryUniform(int program, String name,
			String type,
			GlUniformFloatFunction uniformFunction,
			UniformControl control,
			boolean isArray,
			int valueSize,
			List<float[]> initialValues) {
		super(name);
		this.locationCache = isArray ? new ArrayLocationCache(program, name) : new ValueLocationCache(program, name);
		this.applyFunction = uniformFunction;
		this.control = control;
		this.valueSize = valueSize;
		this.type = type;
		this.isArray = isArray;
		this.values = initialValues;
	}
	
	@Override
	public void apply() {
		for(int i = 0; i < values.size(); i++)
			applyFunction.setUniform(locationCache.getLocation(i), values.get(i));
	}
	
	@Override
	public void renderControl() {
		if(isArray) {
			for(int i = 0; i < values.size(); i++)
				control.renderControl(name + '[' + i + ']', values.get(i));
		} else {
			control.renderControl(name, values.get(0));
		}
	}
	
	public ArbitraryUniform copy(Uniform old) {
		if(!(old instanceof ArbitraryUniform))
			return this;
		ArbitraryUniform aold = (ArbitraryUniform) old;
		int copyRows = Math.min(aold.values.size(), values.size());
		int copyCols = copyRows == 0 ? 0 : Math.min(aold.values.get(0).length, values.get(0).length);
		for(int i = 0; i < copyRows; i++)
			for(int j = 0; j < copyCols; j++)
				values.get(i)[j] = aold.values.get(i)[j];
		return this;
	}

	@Override
	public String toUniformString() {
		StringBuilder sb = new StringBuilder();
		sb.append("uniform ");
		sb.append(type);
		sb.append(' ');
		sb.append(name);
		if(isArray) sb.append("[" + values.size() + "]");
		sb.append(" = ");
		if(isArray) sb.append(type + "[](");
		for(int i = 0; i < values.size(); i++) {
			if(valueSize == 1) {
				sb.append(values.get(i)[0]);
			} else {
				sb.append(type);
				sb.append('(');
				for(int j = 0; j < valueSize; j++) {
					sb.append(values.get(i)[j]);
					if(j != valueSize-1)
						sb.append(',');
				}
				sb.append(')');
			}
			if(i != values.size()-1)
				sb.append(", ");
		}
		if(isArray) sb.append(')');
		sb.append(';');
		return sb.toString();
	}
	
}