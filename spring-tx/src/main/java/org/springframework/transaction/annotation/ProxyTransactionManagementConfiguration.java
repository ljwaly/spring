/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.transaction.annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans
 * necessary to enable proxy-based annotation-driven transaction management.
 *
 * @author Chris Beams
 * @author Sebastien Deleuze
 * @since 3.1
 * @see EnableTransactionManagement
 * @see TransactionManagementConfigurationSelector
 */
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyTransactionManagementConfiguration extends AbstractTransactionManagementConfiguration {
	/**
	 *
	 * 注解@Transactional解析类
	 */


	@Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor(
			TransactionAttributeSource transactionAttributeSource, TransactionInterceptor transactionInterceptor) {

		/**
		 * 创建事务切面advisor
		 * 内部有Pointcut和advice
		 */
		BeanFactoryTransactionAttributeSourceAdvisor advisor = new BeanFactoryTransactionAttributeSourceAdvisor();

		/**
		 * 切面里面设置处理事务属性对象
		 * 解析注解@Transaction类
		 */
		advisor.setTransactionAttributeSource(transactionAttributeSource);

		/**
		 * 设置切面的advice
		 */
		advisor.setAdvice(transactionInterceptor);

		/**
		 * 设置切面排序
		 */
		if (this.enableTx != null) {
			advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
		}


		return advisor;
	}

	/**
	 * 创建事务属性处理器
	 *
	 * @return
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public TransactionAttributeSource transactionAttributeSource() {
		/**
		 * 创建事务属性解析器
		 * 放入需要解析注解@Transactional的解析类
		 */
		return new AnnotationTransactionAttributeSource();
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public TransactionInterceptor transactionInterceptor(TransactionAttributeSource transactionAttributeSource) {

		/**
		 * 创建事务的增强advice
		 *
		 * 内部有链式代用的invoke方法
		 */
		TransactionInterceptor interceptor = new TransactionInterceptor();

		/**
		 * 事务属性解析器设置到advice中
		 */
		interceptor.setTransactionAttributeSource(transactionAttributeSource);


		/**
		 * 事务属性管理器设置到advice中
		 *
		 * 这里刚开始为空是可能的，
		 * 会在AbstractTransactionManagementConfiguration的setImportMetadata方法中初始化
		 */
		if (this.txManager != null) {
			/**
			 * txManager
			 * 父类内部定义的事务管理器
			 */
			interceptor.setTransactionManager(this.txManager);
		}

		return interceptor;
	}

}
