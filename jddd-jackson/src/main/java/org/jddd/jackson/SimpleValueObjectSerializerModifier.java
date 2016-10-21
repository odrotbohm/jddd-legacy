/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jddd.jackson;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.jddd.annotation.ValueObject;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerBuilder;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * {@link BeanSerializerModifier} to serialize properties that are {@link ValueObject}s which in turn only carry a
 * single attribute as just that attribute.
 * 
 * @author Oliver Gierke
 */
public class SimpleValueObjectSerializerModifier extends BeanSerializerModifier {

	/* 
	 * (non-Javadoc)
	 * @see com.fasterxml.jackson.databind.ser.BeanSerializerModifier#updateBuilder(com.fasterxml.jackson.databind.SerializationConfig, com.fasterxml.jackson.databind.BeanDescription, com.fasterxml.jackson.databind.ser.BeanSerializerBuilder)
	 */
	@Override
	public BeanSerializerBuilder updateBuilder(SerializationConfig config, BeanDescription beanDesc,
			BeanSerializerBuilder builder) {

		for (BeanPropertyWriter writer : builder.getProperties()) {

			JavaType propertyType = writer.getMember().getType();
			Class<?> type = propertyType.getRawClass();
			List<BeanPropertyDefinition> properties = getProperties(propertyType, config);

			Optional.ofNullable(AnnotationUtils.findAnnotation(type, ValueObject.class))//
					.filter(it -> properties.size() == 1)//
					.flatMap(it -> properties.stream().findFirst())//
					.ifPresent(it -> writer.assignSerializer(new PropertyAccessingSerializer(it)));
		}

		return builder;
	}

	private static List<BeanPropertyDefinition> getProperties(JavaType type, SerializationConfig config) {

		ClassIntrospector classIntrospector = config.getClassIntrospector();
		BeanDescription description = classIntrospector.forSerialization(config, type, config);

		return description.findProperties();
	}

	private static class PropertyAccessingSerializer extends StdSerializer<Object> {

		private static final long serialVersionUID = 271400108881186699L;

		private final BeanPropertyDefinition property;

		public PropertyAccessingSerializer(BeanPropertyDefinition property) {

			super(Object.class);
			this.property = property;
		}

		/* 
		 * (non-Javadoc)
		 * @see com.fasterxml.jackson.databind.ser.std.StdSerializer#serialize(java.lang.Object, com.fasterxml.jackson.core.JsonGenerator, com.fasterxml.jackson.databind.SerializerProvider)
		 */
		@Override
		public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {

			AnnotatedMember accessor = property.getAccessor();
			makeAccessible(accessor.getMember());

			Object propertyValue = accessor.getValue(value);

			provider.findValueSerializer(propertyValue.getClass()).serialize(propertyValue, gen, provider);
		}

		private static void makeAccessible(Member member) {

			if (member instanceof Field) {
				ReflectionUtils.makeAccessible((Field) member);
			}

			if (member instanceof Method) {
				ReflectionUtils.makeAccessible((Method) member);
			}
		}
	}
}
