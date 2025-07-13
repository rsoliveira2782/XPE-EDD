package org.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.example.DTO.AmountReduce;
import org.example.DTO.Transaction;
import org.example.JSON.JSONDeserializerTransaction;
import java.sql.Timestamp;

public class DataStreamJob {

	static String jdbcUrl = "jdbc:postgresql://172.18.0.2:5432/postgres";
	static String username = "postgres";
	static String password = "postgres";

	public static void main(String[] args) throws Exception {

		// Sets up the execution environment, which is the main entry point
		// to building Flink applications.
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		String topicName = "sales-transactions";

		KafkaSource<Transaction> source = KafkaSource.<Transaction>builder()
												.setBootstrapServers("192.168.0.245:9092")
												.setTopics(topicName)
												.setGroupId("flink-group")
												.setStartingOffsets(OffsetsInitializer.earliest())
												.setValueOnlyDeserializer(new JSONDeserializerTransaction())
												.build();

		//busca os dados do Kafka
		DataStream<Transaction> transactionStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

//		transactionStream.print();

		//opcoes para o banco de dados
		JdbcExecutionOptions executionOptions = new JdbcExecutionOptions.Builder()
												.withBatchSize(1000)
												.withBatchIntervalMs(200)
												.withMaxRetries(5)
												.build();

		//conectar ao banco de dados
		JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
												.withUrl(jdbcUrl)
												.withDriverName("org.postgresql.Driver")
												.withUsername(username)
												.withPassword(password)
												.build();

		//cria a tabela de transações
		transactionStream.addSink(JdbcSink.sink(
					"CREATE TABLE IF NOT EXISTS transactions (" +
							"transaction_id VARCHAR(255) PRIMARY KEY, " +
							"product_id VARCHAR(255), " +
							"product_name VARCHAR(255), " +
							"product_category VARCHAR(255), " +
							"product_price DOUBLE PRECISION, " +
							"product_quantity INTEGER, " +
							"product_brand VARCHAR(255), " +
							"total_amount DOUBLE PRECISION, " +
							"currency VARCHAR(255), " +
							"customer_id VARCHAR(255), " +
							"transaction_date TIMESTAMP, " +
							"payment_method VARCHAR(255) " +
							")", (JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {

				}, executionOptions, connectionOptions)).name("A tabela de transação foi criada com sucesso");

		//insere na tabela de transações
		transactionStream.addSink(JdbcSink.sink(
					"INSERT INTO transactions(transaction_id, product_id, product_name, product_category, product_price, " +
							"product_quantity, product_brand, total_amount, currency, customer_id, transaction_date, payment_method) " +
							"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
							"ON CONFLICT (transaction_id) DO UPDATE SET " +
							"product_id = EXCLUDED.product_id, " +
							"product_name  = EXCLUDED.product_name, " +
							"product_category  = EXCLUDED.product_category, " +
							"product_price = EXCLUDED.product_price, " +
							"product_quantity = EXCLUDED.product_quantity, " +
							"product_brand = EXCLUDED.product_brand, " +
							"total_amount  = EXCLUDED.total_amount, " +
							"currency = EXCLUDED.currency, " +
							"customer_id  = EXCLUDED.customer_id, " +
							"transaction_date = EXCLUDED.transaction_date, " +
							"payment_method = EXCLUDED.payment_method " +
							"WHERE transactions.transaction_id = EXCLUDED.transaction_id",
				(JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {
						preparedStatement.setString(1, transaction.getTransactionId());
						preparedStatement.setString(2, transaction.getProductId());
						preparedStatement.setString(3, transaction.getProductName());
						preparedStatement.setString(4, transaction.getProductCategory());
						preparedStatement.setDouble(5, transaction.getProductPrice());
						preparedStatement.setInt(6, transaction.getProductQuantity());
						preparedStatement.setString(7, transaction.getProductBrand());
						preparedStatement.setDouble(8, transaction.getTotalAmount());
						preparedStatement.setString(9, transaction.getCurrency());
						preparedStatement.setString(10, transaction.getCustomerId());
						preparedStatement.setTimestamp(11, transaction.getTransactionDate());
						preparedStatement.setString(12, transaction.getPaymentMethod());
				}, executionOptions, connectionOptions))
					.name("A transação foi inserida com sucesso");

		//cria a tabela de vendas_por_categoria
		transactionStream.addSink(JdbcSink.sink(
			"CREATE TABLE IF NOT EXISTS sales_per_category (" +
					"sales_per_category_id TIMESTAMPTZ PRIMARY KEY, " +
					"product_category VARCHAR(255), " +
					"total_amount DOUBLE PRECISION " +
					")", (JdbcStatementBuilder<Transaction>) (preparedStatement, salesTransaction) -> {

			}, executionOptions, connectionOptions)).name("A tabela de vendas_por_categoria foi criada com sucesso");

		//filtra por mês e consolida o total
		transactionStream.map(transaction -> {
			return transaction;
		}).keyBy(Transaction::getMonth).reduce(new AmountReduce());

		//insere na tabela de vendas_por_categoria
		transactionStream.addSink(JdbcSink.sink(
			"INSERT INTO sales_per_category(sales_per_category_id, product_category, total_amount) " +
					"VALUES (?, ?, ?) " +
					"ON CONFLICT (sales_per_category_id) DO UPDATE SET " +
					"sales_per_category_id = EXCLUDED.sales_per_category_id, " +
					"product_category  = EXCLUDED.product_category, " +
					"total_amount  = EXCLUDED.total_amount " +
					"WHERE sales_per_category.sales_per_category_id = EXCLUDED.sales_per_category_id",
		(JdbcStatementBuilder<Transaction>) (preparedStatement, salesTransaction) -> {
			preparedStatement.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))));
			preparedStatement.setString(2, salesTransaction.getProductCategory());
			preparedStatement.setDouble(3, salesTransaction.getTotalAmount());
		}, executionOptions, connectionOptions))
			.name("A transação foi inserida com sucesso");

		// Execute program, beginning computation.
		env.execute("Flink Java API Skeleton");
	}
}