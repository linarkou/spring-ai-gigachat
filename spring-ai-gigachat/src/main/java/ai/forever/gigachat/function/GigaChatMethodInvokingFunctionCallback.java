package ai.forever.gigachat.function;

import static org.springframework.util.StringUtils.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.MethodInvokingFunctionCallback;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Копия {@link MethodInvokingFunctionCallback} с доработками для GigaChat.
 *
 * @author  Christian Tzolov
 * @author  Abzaltdinov Linar
 */
public class GigaChatMethodInvokingFunctionCallback implements GigaChatFunctionCallback {

    private static final Logger logger = LoggerFactory.getLogger(GigaChatMethodInvokingFunctionCallback.class);

    /**
     * Object instance that contains the method to be invoked. If the method is static
     * this object can be null.
     */
    private final Object functionObject;

    /**
     * The method to be invoked.
     */
    private final Method method;

    /**
     * Description to help the LLM model to understand worth the method does and when to
     * use it.
     */
    private final String description;

    /**
     * Internal ObjectMapper used to serialize/deserialize the method input and output.
     */
    private final ObjectMapper mapper;

    /**
     * The JSON schema generated from the method input parameters.
     */
    private final String inputSchema;

    /**
     * Flag indicating if the method accepts a {@link ToolContext} as input parameter.
     */
    private boolean isToolContextMethod = false;

    /**
     * Optional function name. If not provided the method name is used as the function.
     */
    private final String name;

    private final Function<Object, String> responseConverter;

    private final List<FewShotExample> fewShotExamples;

    private final String outputTypeSchema;

    public GigaChatMethodInvokingFunctionCallback(
            Object functionObject,
            Method method,
            String description,
            ObjectMapper mapper,
            String name,
            Function<Object, String> responseConverter,
            List<FewShotExample> fewShotExamples) {

        Assert.notNull(method, "Method must not be null");
        Assert.notNull(mapper, "ObjectMapper must not be null");
        Assert.hasText(description, "Description must not be empty");
        Assert.notNull(responseConverter, "Response converter must not be null");

        this.method = method;
        this.description = description;
        this.mapper = mapper;
        this.functionObject = functionObject;
        this.name = name;
        this.responseConverter = responseConverter;
        this.fewShotExamples = fewShotExamples == null ? Collections.emptyList() : fewShotExamples;

        Assert.isTrue(
                this.functionObject != null || Modifier.isStatic(this.method.getModifiers()),
                "Function object must be provided for non-static methods!");

        // Generate the JSON schema from the method input parameters
        Map<String, Class<?>> methodParameters = Stream.of(method.getParameters())
                .collect(Collectors.toMap(param -> param.getName(), param -> param.getType()));

        this.inputSchema = this.generateJsonSchema(methodParameters);

        logger.debug("Generated input JSON Schema: {}", this.inputSchema);

        // Generate the JSON schema from the method return type
        Class<?> outputType = method.getReturnType();

        this.outputTypeSchema = this.generateJsonSchema(outputType);

        logger.debug("Generated output JSON Schema: {}", this.outputTypeSchema);
    }

    @Override
    public String getName() {
        return hasText(this.name) ? this.name : this.method.getName();
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getInputTypeSchema() {
        return this.inputSchema;
    }

    @Override
    public String call(String functionInput) {
        return this.call(functionInput, null);
    }

    @Override
    public String call(String functionInput, ToolContext toolContext) {
        try {

            // If the toolContext is not empty but the method does not accept ToolContext
            // as
            // input parameter then throw an exception.
            if (toolContext != null
                    && !CollectionUtils.isEmpty(toolContext.getContext())
                    && !this.isToolContextMethod) {
                throw new IllegalArgumentException("Configured method does not accept ToolContext as input parameter!");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> map = this.mapper.readValue(functionInput, Map.class);

            // ReflectionUtils.findMethod
            Object[] methodArgs = Stream.of(this.method.getParameters())
                    .map(parameter -> {
                        Class<?> type = parameter.getType();
                        if (ClassUtils.isAssignable(type, ToolContext.class)) {
                            return toolContext;
                        }
                        Object rawValue = map.get(parameter.getName());
                        return this.toJavaType(rawValue, type);
                    })
                    .toArray();

            Object response = ReflectionUtils.invokeMethod(this.method, this.functionObject, methodArgs);

            var returnType = this.method.getReturnType();
            if (returnType == Void.TYPE) {
                return "Done";
            } else if (returnType == Class.class
                    || returnType.isRecord()
                    || returnType == List.class
                    || returnType == Map.class) {
                return ModelOptionsUtils.toJsonString(response);
            }

            return this.responseConverter.apply(response);
        } catch (Exception e) {
            ReflectionUtils.handleReflectionException(e);
            return null;
        }
    }

    /**
     * Generates a JSON schema from the given named classes.
     * @param namedClasses The named classes to generate the schema from.
     * @return The generated JSON schema.
     */
    protected String generateJsonSchema(Map<String, Class<?>> namedClasses) {
        try {
            JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(this.mapper);

            ObjectNode rootNode = this.mapper.createObjectNode();
            rootNode.put("$schema", "https://json-schema.org/draft/2020-12/schema");
            rootNode.put("type", "object");
            ObjectNode propertiesNode = rootNode.putObject("properties");

            for (Map.Entry<String, Class<?>> entry : namedClasses.entrySet()) {
                String className = entry.getKey();
                Class<?> clazz = entry.getValue();

                if (ClassUtils.isAssignable(clazz, ToolContext.class)) {
                    // Skip the ToolContext class from the schema generation.
                    this.isToolContextMethod = true;
                    continue;
                }

                JsonSchema schema = schemaGen.generateSchema(clazz);
                JsonNode schemaNode = this.mapper.valueToTree(schema);
                propertiesNode.set(className, schemaNode);
            }

            return this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a JSON schema from the given class.
     * @param clazz The class to generate the schema from.
     * @return The generated JSON schema.
     */
    protected String generateJsonSchema(Class<?> clazz) {
        try {
            JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(this.mapper);
            JsonSchema schema = schemaGen.generateSchema(clazz);
            return this.mapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts the given value to the specified Java type.
     * @param value The value to convert.
     * @param javaType The Java type to convert to.
     * @return Returns the converted value.
     */
    protected Object toJavaType(Object value, Class<?> javaType) {

        if (value == null) {
            return null;
        }

        javaType = ClassUtils.resolvePrimitiveIfNecessary(javaType);

        if (javaType == String.class) {
            return value.toString();
        } else if (javaType == Integer.class) {
            return Integer.parseInt(value.toString());
        } else if (javaType == Long.class) {
            return Long.parseLong(value.toString());
        } else if (javaType == Double.class) {
            return Double.parseDouble(value.toString());
        } else if (javaType == Float.class) {
            return Float.parseFloat(value.toString());
        } else if (javaType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        } else if (javaType.isEnum()) {
            return Enum.valueOf((Class<Enum>) javaType, value.toString());
        }

        try {
            String json = this.mapper.writeValueAsString(value);
            return this.mapper.readValue(json, javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getOutputTypeSchema() {
        return this.outputTypeSchema;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return this.fewShotExamples;
    }
}
