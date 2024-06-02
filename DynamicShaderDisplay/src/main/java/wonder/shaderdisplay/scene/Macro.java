package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Macro {

    public final String name;
    public final String value;

    public Macro(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @JsonCreator
    public Macro(String name) {
        this(name, "");
    }

    @JsonCreator
    public Macro(Object[] delegate) {
        if (delegate.length != 2 || !(delegate[0] instanceof String) || delegate[1] == null)
            throw new IllegalArgumentException("Macros can be either \"MACRO_NAME\" or [\"MACRO_NAME\", MACRO_VALUE]");
        name = (String)delegate[0];
        value = delegate[1].toString();
    }

}
