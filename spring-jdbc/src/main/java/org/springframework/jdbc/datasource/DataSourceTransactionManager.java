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

package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.Assert;


@SuppressWarnings("serial")
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager
		implements ResourceTransactionManager, InitializingBean {

	@Nullable
	private DataSource dataSource;

	private boolean enforceReadOnly = false;


	/**
	 * Create a new DataSourceTransactionManager instance.
	 * A DataSource has to be set to be able to use it.
	 * @see #setDataSource
	 */
	public DataSourceTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	/**
	 * Create a new DataSourceTransactionManager instance.
	 * @param dataSource the JDBC DataSource to manage transactions for
	 */
	public DataSourceTransactionManager(DataSource dataSource) {
		this();
		setDataSource(dataSource);
		afterPropertiesSet();
	}


	/**
	 * Set the JDBC DataSource that this instance should manage transactions for.
	 * <p>This will typically be a locally defined DataSource, for example an
	 * Apache Commons DBCP connection pool. Alternatively, you can also drive
	 * transactions for a non-XA J2EE DataSource fetched from JNDI. For an XA
	 * DataSource, use JtaTransactionManager.
	 * <p>The DataSource specified here should be the target DataSource to manage
	 * transactions for, not a TransactionAwareDataSourceProxy. Only data access
	 * code may work with TransactionAwareDataSourceProxy, while the transaction
	 * manager needs to work on the underlying target DataSource. If there's
	 * nevertheless a TransactionAwareDataSourceProxy passed in, it will be
	 * unwrapped to extract its target DataSource.
	 * <p><b>The DataSource passed in here needs to return independent Connections.</b>
	 * The Connections may come from a pool (the typical case), but the DataSource
	 * must not return thread-scoped / request-scoped Connections or the like.
	 * @see TransactionAwareDataSourceProxy
	 * @see org.springframework.transaction.jta.JtaTransactionManager
	 */
	public void setDataSource(@Nullable DataSource dataSource) {
		if (dataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform transactions
			// for its underlying target DataSource, else data access code won't see
			// properly exposed transactions (i.e. transactions for the target DataSource).
			this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
		}
		else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Return the JDBC DataSource that this instance manages transactions for.
	 */
	@Nullable
	public DataSource getDataSource() {
		return this.dataSource;
	}

	/**
	 * Obtain the DataSource for actual use.
	 * @return the DataSource (never {@code null})
	 * @throws IllegalStateException in case of no DataSource set
	 * @since 5.0
	 */
	protected DataSource obtainDataSource() {
		/**
		 * 注解@Bean的时候，创建的
		 *
		 */
		DataSource dataSource = getDataSource();
		Assert.state(dataSource != null, "No DataSource set");
		return dataSource;
	}

	/**
	 * Specify whether to enforce the read-only nature of a transaction
	 * (as indicated by {@link TransactionDefinition#isReadOnly()}
	 * through an explicit statement on the transactional connection:
	 * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
	 * <p>The exact treatment, including any SQL statement executed on the connection,
	 * can be customized through {@link #prepareTransactionalConnection}.
	 * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
	 * hint that Spring applies by default. In contrast to that standard JDBC hint,
	 * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
	 * where data manipulation statements are strictly disallowed. Also, on Oracle,
	 * this read-only mode provides read consistency for the entire transaction.
	 * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
	 * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
	 * this strong enforcement needs to be applied explicitly, e.g. through this flag.
	 * @since 4.3.7
	 * @see #prepareTransactionalConnection
	 */
	public void setEnforceReadOnly(boolean enforceReadOnly) {
		this.enforceReadOnly = enforceReadOnly;
	}

	/**
	 * Return whether to enforce the read-only nature of a transaction
	 * through an explicit statement on the transactional connection.
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	public boolean isEnforceReadOnly() {
		return this.enforceReadOnly;
	}

	@Override
	public void afterPropertiesSet() {
		if (getDataSource() == null) {
			throw new IllegalArgumentException("Property 'dataSource' is required");
		}
	}


	@Override
	public Object getResourceFactory() {
		return obtainDataSource();
	}

	@Override
	protected Object doGetTransaction() {

		/**
		 * 创建数据库事务对象
		 *
		 * 封装有数据库链接
		 * 是否只读
		 * 隔离级别
		 * 是否允许创建回滚点
		 *
		 */
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();

		/**
		 * 默认设置为true的
		 */
		txObject.setSavepointAllowed(isNestedTransactionAllowed());


		/**
		 * 这里是从ThreadLocal中获取连接对象conn
		 *
		 * 第一次进入，拿到的conHolder是一个null
		 */
		ConnectionHolder conHolder =
				(ConnectionHolder) TransactionSynchronizationManager.getResource(
						//获取数据源对象，创建DataSourceTransactionManager时候，设置进去的
						obtainDataSource()
				);

		/**
		 * 放入事务对象（第一次为null，标识也放成false）
		 *
		 * 如果不是第一次来拿，肯定可以拿到对象
		 * 但是此时对象，状态newConnectionHolder会被设定为false（除非新创建的，才会是true）
		 *
		 */
		txObject.setConnectionHolder(conHolder, false);
		return txObject;
	}

	@Override
	protected boolean isExistingTransaction(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		//如果事务对象中有链接且是active的，则说明之前存在事务
		return (txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive());
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {


		//事务对象
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {

			/**
			 * 如果事务对象中没有链接（第一次进入是空）
			 */
			if (!txObject.hasConnectionHolder() ||
					txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
				/**
				 * 从事务管理器中拿到datasource对象
				 * 从datasource对象中拿链接对象、
				 * 这里可能是AbstractRoutingDataSource的数据源
				 */
				Connection newCon = obtainDataSource().getConnection();
				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}

				/**
				 * 将链接对象设置到事务对象中
				 * 并设置标识为true
				 */
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
			}

			// 设置有事务标识
			txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
			con = txObject.getConnectionHolder().getConnection();

			/**
			 * 设置是否只读链接
			 * 设置事务隔离级别
			 */
			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
			txObject.setPreviousIsolationLevel(previousIsolationLevel);
			txObject.setReadOnly(definition.isReadOnly());

			// Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
			// so we don't want to do it unnecessarily (for example if we've explicitly
			// configured the connection pool to set it already).
			/**
			 * 是否自动提交
			 */
			if (con.getAutoCommit()) {
				txObject.setMustRestoreAutoCommit(true);
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				/**
				 * 关闭自动提交
				 * 因为提交要交给spring来做
				 */
				con.setAutoCommit(false);
			}

			/**
			 * 执行只读事务命令
			 */
			prepareTransactionalConnection(con, definition);

			/**
			 * 把事务状态设置为true
			 */
			txObject.getConnectionHolder().setTransactionActive(true);

			/**
			 * 获取事务超时时间
			 */
			int timeout = determineTimeout(definition);
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}


			/**
			 * 判断是否是一个新的事务
			 * 如果是新的，这建立数据源对象和链接对象的绑定关系，
			 * 并且把板顶关系的map，放入ThreadLocal
			 *
			 * Bind the connection holder to the thread.
			 *
			 */
			if (txObject.isNewConnectionHolder()) {
				/**
				 * 建立绑定关系
				 */
				TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
			}
		}

		catch (Throwable ex) {
			if (txObject.isNewConnectionHolder()) {
				DataSourceUtils.releaseConnection(con, obtainDataSource());
				txObject.setConnectionHolder(null, false);
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	/**
	 *
	 * 挂起老的对象处理方式
	 *
	 */
	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		/**
		 * 把链接对象设置为空
		 */
		txObject.setConnectionHolder(null);
		/**
		 * 并解除绑定关系
		 */
		return TransactionSynchronizationManager.unbindResource(obtainDataSource());
	}

	@Override
	protected void doResume(@Nullable Object transaction, Object suspendedResources) {
		/**
		 * 对事务进行绑定
		 */
		TransactionSynchronizationManager.bindResource(obtainDataSource(), suspendedResources);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Committing JDBC transaction on Connection [" + con + "]");
		}
		try {
			/**
			 * 事务提交-5
			 *
			 * 拿到链接对象，进行提交
			 */
			con.commit();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not commit JDBC transaction", ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.rollback();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
		}
	}

	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
					"] rollback-only");
		}

		/**
		 * 如果传播属性是默认的，
		 * 那么内层方法异常之后，
		 * 到这里，会设置同一个链接对象的全局回滚变量是true，
		 * 即使程序员内部写了try{} catch,也会造成最外层的全局回滚
		 *
		 */
		txObject.setRollbackOnly();
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// Remove the connection holder from the thread, if exposed.
		if (txObject.isNewConnectionHolder()) {
			TransactionSynchronizationManager.unbindResource(obtainDataSource());
		}

		// Reset connection.
		Connection con = txObject.getConnectionHolder().getConnection();
		try {
			if (txObject.isMustRestoreAutoCommit()) {
				con.setAutoCommit(true);
			}
			DataSourceUtils.resetConnectionAfterTransaction(
					con, txObject.getPreviousIsolationLevel(), txObject.isReadOnly());
		}
		catch (Throwable ex) {
			logger.debug("Could not reset JDBC Connection after transaction", ex);
		}

		if (txObject.isNewConnectionHolder()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
			}
			DataSourceUtils.releaseConnection(con, this.dataSource);
		}

		txObject.getConnectionHolder().clear();
	}


	/**
	 * Prepare the transactional {@code Connection} right after transaction begin.
	 * <p>The default implementation executes a "SET TRANSACTION READ ONLY" statement
	 * if the {@link #setEnforceReadOnly "enforceReadOnly"} flag is set to {@code true}
	 * and the transaction definition indicates a read-only transaction.
	 * <p>The "SET TRANSACTION READ ONLY" is understood by Oracle, MySQL and Postgres
	 * and may work with other databases as well. If you'd like to adapt this treatment,
	 * override this method accordingly.
	 * @param con the transactional JDBC Connection
	 * @param definition the current transaction definition
	 * @throws SQLException if thrown by JDBC API
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
			throws SQLException {

		if (isEnforceReadOnly() && definition.isReadOnly()) {
			try (Statement stmt = con.createStatement()) {
				stmt.executeUpdate("SET TRANSACTION READ ONLY");
			}
		}
	}


	/**
	 * DataSource transaction object, representing a ConnectionHolder.
	 * Used as transaction object by DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

		private boolean newConnectionHolder;

		private boolean mustRestoreAutoCommit;

		public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
			super.setConnectionHolder(connectionHolder);
			this.newConnectionHolder = newConnectionHolder;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		public void setRollbackOnly() {
			/**
			 * 如果传播属性是默认的，
			 * 那么内层方法异常之后，
			 * 到这里，会设置同一个链接对象的全局回滚变量是true，
			 * 即使程序员内部写了try{} catch,也会造成最外层的全局回滚
			 *
			 */
			getConnectionHolder().setRollbackOnly();
		}

		@Override
		public boolean isRollbackOnly() {
			return getConnectionHolder().isRollbackOnly();
		}

		@Override
		public void flush() {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationUtils.triggerFlush();
			}
		}
	}

}
