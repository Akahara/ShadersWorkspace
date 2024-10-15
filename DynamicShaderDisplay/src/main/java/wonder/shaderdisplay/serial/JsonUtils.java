package wonder.shaderdisplay.serial;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;

import java.io.IOException;

public class JsonUtils {

    public static final ObjectMapper JSON_MAPPER = new ObjectMapper(new JsonFactoryBuilder()
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build())
            .addHandler(new DeserializationProblemHandler() {
                @Override
                public boolean handleUnknownProperty(DeserializationContext c, JsonParser p, JsonDeserializer<?> d, Object beanOrClass, String propertyName) throws IOException {
                    if(propertyName.startsWith("_")) {
                        p.skipChildren();
                        return true;
                    }
                    return false;
                }
            });

}
