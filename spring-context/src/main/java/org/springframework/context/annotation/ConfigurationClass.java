/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.context.annotation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Represents a user-defined {@link Configuration @Configuration} class.
 * Includes a set of {@link Bean} methods, including all such methods
 * defined in the ancestry of the class, in a 'flattened-out' manner.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 * @see BeanMethod
 * @see ConfigurationClassParser
 */
final class ConfigurationClass {

	private final AnnotationMetadata metadata;

	private final Resource resource;

	@Nullable
	private String beanName;

	/**
	 * 当前这个类，对应的父容器，是由哪个外部类导入到这个类的
	 */
	private final Set<ConfigurationClass> importedBy = new LinkedHashSet<>(1);

	/**
	 * 当前这个类，
	 * 注解@Configuration/@Compontent内部的@bean的方法
	 * 区别：注解@Configuration可以保证通过@Bean注入的内部的方法都是一个单例对象（因为@Configuration生成了代理），而@Component无法保证
	 * 方法的信息会包装到BeanMethod
	 */
	private final Set<BeanMethod> beanMethods = new LinkedHashSet<>();

	/**
	 * 当前这个类，有@ImportResource注解导入的xml配置
	 * key:xml对应的地址
	 * value：@ImportResource注解的reader属性对应的BeanDefinitionReader
	 */
	private final Map<String, Class<? extends BeanDefinitionReader>> importedResources =
			new LinkedHashMap<>();

	/**
	 * 当前这个类（本测试用例中的com.ljw.spring.source.s1.beans.scanbean.Scanner,有@Import注解），
	 * 注解@Import的类中有实现了ImportBeanDefinitionRegistrar接口
	 * key:是@Import的几个类中有实现ImportBeanDefinitionRegistrar接口的实例对象，（不过接口的实例对象并没有放入spring容器）
	 * value:当前类的的另一种封装形式SourceClass对象的成员变量-Metadata对象
	 */
	private final Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> importBeanDefinitionRegistrars =
			new LinkedHashMap<>();

	final Set<String> skippedBeanMethods = new HashSet<>();


	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param metadataReader reader used to parse the underlying {@link Class}
	 * @param beanName must not be {@code null}
	 * @see ConfigurationClass#ConfigurationClass(Class, ConfigurationClass)
	 */
	public ConfigurationClass(MetadataReader metadataReader, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = metadataReader.getAnnotationMetadata();
		this.resource = metadataReader.getResource();
		this.beanName = beanName;
	}

	/**
	 * Create a new {@link ConfigurationClass} representing a class that was imported
	 * using the {@link Import} annotation or automatically processed as a nested
	 * configuration class (if importedBy is not {@code null}).
	 * @param metadataReader reader used to parse the underlying {@link Class}
	 * @param importedBy the configuration class importing this one or {@code null}
	 * @since 3.1.1
	 */
	public ConfigurationClass(MetadataReader metadataReader, @Nullable ConfigurationClass importedBy) {
		this.metadata = metadataReader.getAnnotationMetadata();
		this.resource = metadataReader.getResource();
		this.importedBy.add(importedBy);
	}

	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param clazz the underlying {@link Class} to represent
	 * @param beanName name of the {@code @Configuration} class bean
	 * @see ConfigurationClass#ConfigurationClass(Class, ConfigurationClass)
	 */
	public ConfigurationClass(Class<?> clazz, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = AnnotationMetadata.introspect(clazz);
		this.resource = new DescriptiveResource(clazz.getName());
		this.beanName = beanName;
	}

	/**
	 * Create a new {@link ConfigurationClass} representing a class that was imported
	 * using the {@link Import} annotation or automatically processed as a nested
	 * configuration class (if imported is {@code true}).
	 * @param clazz the underlying {@link Class} to represent
	 * @param importedBy the configuration class importing this one (or {@code null})
	 * @since 3.1.1
	 */
	public ConfigurationClass(Class<?> clazz, @Nullable ConfigurationClass importedBy) {
		this.metadata = AnnotationMetadata.introspect(clazz);
		this.resource = new DescriptiveResource(clazz.getName());
		/**
		 * 初始化父容器对应的外部类
		 */
		this.importedBy.add(importedBy);
	}

	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param metadata the metadata for the underlying class to represent
	 * @param beanName name of the {@code @Configuration} class bean
	 * @see ConfigurationClass#ConfigurationClass(Class, ConfigurationClass)
	 */
	public ConfigurationClass(AnnotationMetadata metadata, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = metadata;
		this.resource = new DescriptiveResource(metadata.getClassName());
		this.beanName = beanName;
	}


	public AnnotationMetadata getMetadata() {
		return this.metadata;
	}

	public Resource getResource() {
		return this.resource;
	}

	public String getSimpleName() {
		return ClassUtils.getShortName(getMetadata().getClassName());
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	@Nullable
	public String getBeanName() {
		return this.beanName;
	}

	/**
	 * Return whether this configuration class was registered via @{@link Import} or
	 * automatically registered due to being nested within another configuration class.
	 * @since 3.1.1
	 * @see #getImportedBy()
	 */
	public boolean isImported() {
		return !this.importedBy.isEmpty();
	}

	/**
	 * Merge the imported-by declarations from the given configuration class into this one.
	 * @since 4.0.5
	 */
	public void mergeImportedBy(ConfigurationClass otherConfigClass) {
		this.importedBy.addAll(otherConfigClass.importedBy);
	}

	/**
	 * Return the configuration classes that imported this class,
	 * or an empty Set if this configuration was not imported.
	 * @since 4.0.5
	 * @see #isImported()
	 */
	public Set<ConfigurationClass> getImportedBy() {
		return this.importedBy;
	}

	public void addBeanMethod(BeanMethod method) {
		this.beanMethods.add(method);
	}

	public Set<BeanMethod> getBeanMethods() {
		return this.beanMethods;
	}

	public void addImportedResource(String importedResource, Class<? extends BeanDefinitionReader> readerClass) {
		this.importedResources.put(importedResource, readerClass);
	}

	public void addImportBeanDefinitionRegistrar(ImportBeanDefinitionRegistrar registrar, AnnotationMetadata importingClassMetadata) {
		this.importBeanDefinitionRegistrars.put(registrar, importingClassMetadata);
	}

	public Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> getImportBeanDefinitionRegistrars() {
		return this.importBeanDefinitionRegistrars;
	}

	public Map<String, Class<? extends BeanDefinitionReader>> getImportedResources() {
		return this.importedResources;
	}

	public void validate(ProblemReporter problemReporter) {
		// A configuration class may not be final (CGLIB limitation) unless it declares proxyBeanMethods=false
		Map<String, Object> attributes = this.metadata.getAnnotationAttributes(Configuration.class.getName());
		if (attributes != null && (Boolean) attributes.get("proxyBeanMethods")) {
			if (this.metadata.isFinal()) {
				problemReporter.error(new FinalConfigurationProblem());
			}
			for (BeanMethod beanMethod : this.beanMethods) {
				beanMethod.validate(problemReporter);
			}
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ConfigurationClass &&
				getMetadata().getClassName().equals(((ConfigurationClass) other).getMetadata().getClassName())));
	}

	@Override
	public int hashCode() {
		return getMetadata().getClassName().hashCode();
	}

	@Override
	public String toString() {
		return "ConfigurationClass: beanName '" + this.beanName + "', " + this.resource;
	}


	/**
	 * Configuration classes must be non-final to accommodate CGLIB subclassing.
	 */
	private class FinalConfigurationProblem extends Problem {

		public FinalConfigurationProblem() {
			super(String.format("@Configuration class '%s' may not be final. Remove the final modifier to continue.",
					getSimpleName()), new Location(getResource(), getMetadata()));
		}
	}

}
