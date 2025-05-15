/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.forever.gigachat.function;

import ai.forever.gigachat.function.GigaChatFunctionCallback.GigaChatMethodInvokingSpec;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.DefaultFunctionCallbackBuilder;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.util.ParsingUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Копия {@link DefaultFunctionCallbackBuilder} с доработками параметров для GigaChat.
 *
 * @author Christian Tzolov
 * @author Abzaltdinov Linar
 */
public class GigaChatFunctionCallbackBuilder implements GigaChatFunctionCallback.Builder {

    private static final Logger logger = LoggerFactory.getLogger(GigaChatFunctionCallbackBuilder.class);

    @Override
    public <I, O> GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> function(
            String name, Function<I, O> function) {
        return new DefaultFunctionInvokingSpec<>(name, function);
    }

    @Override
    public <I, O> GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> function(
            String name, BiFunction<I, ToolContext, O> biFunction) {
        return new DefaultFunctionInvokingSpec<>(name, biFunction);
    }

    @Override
    public <O> GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<Void, O> function(
            String name, Supplier<O> supplier) {
        Function<Void, O> function = input -> supplier.get();
        return new DefaultFunctionInvokingSpec<>(name, function).inputType(Void.class);
    }

    public <I> GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, Void> function(
            String name, Consumer<I> consumer) {
        Function<I, Void> function = (I input) -> {
            consumer.accept(input);
            return null;
        };
        return new DefaultFunctionInvokingSpec<>(name, function);
    }

    @Override
    public GigaChatFunctionCallback.GigaChatMethodInvokingSpec method(String methodName, Class<?>... argumentTypes) {
        return new DefaultMethodInvokingSpec(methodName, argumentTypes);
    }

    private String generateDescription(String fromName) {

        String generatedDescription = ParsingUtils.reConcatenateCamelCase(fromName, " ");

        logger.info(
                "Description is not set! A best effort attempt to generate a description:'{}' from the:'{}'",
                generatedDescription,
                fromName);
        logger.info("It is recommended to set the Description explicitly! Use the 'description()' method!");

        return generatedDescription;
    }

    final class DefaultFunctionInvokingSpec<I, O>
            extends GigaChatCommonCallbackInvokingSpec<GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O>>
            implements GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> {

        private final String name;

        private Type inputType;

        private final BiFunction<I, ToolContext, O> biFunction;

        private final Function<I, O> function;

        /**
         * JSON-объект с описанием параметров, которые может вернуть ваша функция.
         */
        private String outputTypeSchema;

        /**
         * Тип возвращаемого значения функции.
         */
        private Type outputType;

        private DefaultFunctionInvokingSpec(String name, BiFunction<I, ToolContext, O> biFunction) {
            Assert.hasText(name, "Name must not be empty");
            Assert.notNull(biFunction, "BiFunction must not be null");
            this.name = name;
            this.biFunction = biFunction;
            this.function = null;
        }

        private DefaultFunctionInvokingSpec(String name, Function<I, O> function) {
            Assert.hasText(name, "Name must not be empty");
            Assert.notNull(function, "Function must not be null");
            this.name = name;
            this.biFunction = null;
            this.function = function;
        }

        @Override
        public GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> inputType(Class<?> inputType) {
            Assert.notNull(inputType, "InputType must not be null");
            this.inputType = inputType;
            return this;
        }

        @Override
        public GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> inputType(
                ParameterizedTypeReference<?> inputType) {
            Assert.notNull(inputType, "InputType must not be null");
            this.inputType = inputType.getType();
            return this;
        }

        @Override
        public GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> outputTypeSchema(String outputTypeSchema) {
            Assert.hasText(outputTypeSchema, "OutputTypeSchema must not be empty");
            this.outputTypeSchema = outputTypeSchema;
            return this;
        }

        @Override
        public GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> outputType(Class<?> outputType) {
            Assert.notNull(outputType, "OutputType must not be null");
            this.outputType = outputType;
            return this;
        }

        @Override
        public GigaChatFunctionCallback.GigaChatFunctionInvokingSpec<I, O> outputType(
                ParameterizedTypeReference<?> outputType) {
            Assert.notNull(outputType, "OutputType must not be null");
            this.outputType = outputType.getType();
            return this;
        }

        @Override
        public GigaChatFunctionCallback build() {
            Assert.notNull(this.getObjectMapper(), "ObjectMapper must not be null");
            Assert.hasText(this.name, "Name must not be empty");
            Assert.notNull(this.getResponseConverter(), "ResponseConverter must not be null");
            Assert.notNull(this.inputType, "InputType must not be null");

            if (this.getInputTypeSchema() == null) {
                boolean upperCaseTypeValues = schemaType == FunctionCallback.SchemaType.OPEN_API_SCHEMA;
                this.inputTypeSchema = ModelOptionsUtils.getJsonSchema(this.inputType, upperCaseTypeValues);
            }

            if (this.outputTypeSchema == null) {
                boolean upperCaseTypeValues = schemaType == FunctionCallback.SchemaType.OPEN_API_SCHEMA;
                if (outputType != null) {
                    this.outputTypeSchema = ModelOptionsUtils.getJsonSchema(this.outputType, upperCaseTypeValues);
                }
            }

            BiFunction<I, ToolContext, O> finalBiFunction =
                    (this.biFunction != null) ? this.biFunction : (request, context) -> this.function.apply(request);

            return new GigaChatFunctionInvokingFunctionCallback<>(
                    this.name,
                    this.getDescriptionExt(),
                    this.getInputTypeSchema(),
                    this.inputType,
                    (Function<O, String>) this.getResponseConverter(),
                    this.getObjectMapper(),
                    finalBiFunction,
                    this.getFewShotExamples(),
                    this.outputTypeSchema);
        }

        private String getDescriptionExt() {
            if (StringUtils.hasText(this.getDescription())) {
                return this.getDescription();
            }
            return generateDescription(this.name);
        }
    }

    final class DefaultMethodInvokingSpec
            extends GigaChatCommonCallbackInvokingSpec<GigaChatFunctionCallback.GigaChatMethodInvokingSpec>
            implements GigaChatFunctionCallback.GigaChatMethodInvokingSpec {

        private String name;

        private final String methodName;

        private Class<?> targetClass;

        private Object targetObject;

        private final Class<?>[] argumentTypes;

        private DefaultMethodInvokingSpec(String methodName, Class<?>... argumentTypes) {
            Assert.hasText(methodName, "Method name must not be null");
            Assert.notNull(argumentTypes, "Argument types must not be null");
            this.methodName = methodName;
            this.argumentTypes = argumentTypes;
        }

        public GigaChatFunctionCallback.GigaChatMethodInvokingSpec name(String name) {
            Assert.hasText(name, "Name must not be empty");
            this.name = name;
            return this;
        }

        public GigaChatFunctionCallback.GigaChatMethodInvokingSpec targetClass(Class<?> targetClass) {
            Assert.notNull(targetClass, "Target class must not be null");
            this.targetClass = targetClass;
            return this;
        }

        @Override
        public GigaChatFunctionCallback.GigaChatMethodInvokingSpec targetObject(Object methodObject) {
            Assert.notNull(methodObject, "Method object must not be null");
            this.targetObject = methodObject;
            this.targetClass = methodObject.getClass();
            return this;
        }

        @Override
        public GigaChatMethodInvokingSpec fewShotExample(GigaChatFunctionCallback.FewShotExample fewShotExample) {
            Assert.notNull(fewShotExample, "FewShotExample must not be null");
            fewShotExamples.add(fewShotExample);
            return this;
        }

        @Override
        public GigaChatFunctionCallback build() {
            Assert.isTrue(
                    this.targetClass != null || this.targetObject != null, "Target class or object must not be null");
            var method = ReflectionUtils.findMethod(this.targetClass, this.methodName, this.argumentTypes);
            Assert.notNull(
                    method,
                    "Method: '" + this.methodName + "' with arguments:" + Arrays.toString(this.argumentTypes)
                            + " not found!");
            return new GigaChatMethodInvokingFunctionCallback(
                    this.targetObject,
                    method,
                    this.getDescriptionExt(),
                    this.getObjectMapper(),
                    this.name,
                    this.getResponseConverter(),
                    this.getFewShotExamples());
        }

        private String getDescriptionExt() {
            if (StringUtils.hasText(this.getDescription())) {
                return this.getDescription();
            }

            return generateDescription(StringUtils.hasText(this.name) ? this.name : this.methodName);
        }
    }
}
