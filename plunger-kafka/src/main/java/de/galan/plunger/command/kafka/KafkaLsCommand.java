package de.galan.plunger.command.kafka;

import static de.galan.commons.util.Sugar.*;
import static org.apache.commons.lang3.StringUtils.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;

import de.galan.plunger.command.CommandException;
import de.galan.plunger.command.generic.AbstractLsCommand;
import de.galan.plunger.domain.PlungerArguments;
import de.galan.plunger.domain.Target;


/**
 * Lists all destinations on a Kafka broker.
 */
public class KafkaLsCommand extends AbstractLsCommand {

	private KafkaConsumer<String, String> consumer;

	private JMXConnector connector;
	private MBeanServerConnection connection;

	String groupId;


	@Override
	protected void initialize(PlungerArguments pa) throws CommandException {
		Target target = pa.getTarget();
		String port = defaultIfBlank(target.getParameterValue("jmxPort"), "9999");
		String url = "service:jmx:rmi:///jndi/rmi://" + target.getHost() + ":" + port + "/jmxrmi";
		try {
			connector = JMXConnectorFactory.connect(new JMXServiceURL(url));
			connector.connect();
			connection = connector.getMBeanServerConnection();
		}
		catch (IOException ex) {
			throw new CommandException("Failed initializing Kafka JMX connector (" + url + ")", ex);
		}

		groupId = optional(pa.getTarget().getParameterValue("groupId")).orElse("plunger"); //.orElse("plunger-" + UUID.randomUUID().toString());

		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, pa.getTarget().getHost() + ":" + pa.getTarget().getPort());
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		//props.put(ConsumerConfig.CLIENT_ID_CONFIG, "your_client_id");
		//props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG, "true");
		//props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 5000);
		//props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10001);
		//props.put("controller.socket.timeout.ms", 10000);
		consumer = new KafkaConsumer<>(props);
	}


	@Override
	protected void process(PlungerArguments pa) throws CommandException {
		Map<String, List<PartitionInfo>> topics = consumer.listTopics();
		for (String topic: topics.keySet()) {
			if (StringUtils.equals("__consumer_offsets", topic)) {
				continue;
			}
			long size = getSize(topic, topics.get(topic).size());
			/*
			List<PartitionInfo> infos = topics.get(topic);
			for (PartitionInfo info: infos) {
				TopicPartition partition = new TopicPartition(topic, info.partition());
				try {
					long position = consumer.position(partition);
					Output.println("a" + position);
				}
				catch (Exception ex) {
					Output.println("Partition xxx: " + partition.partition());
				}
			}
			*/

			printDestination(pa, topic, 0, size, true);
		}
	}


	private long getSize(String topic, int paritions) {
		long result = 0;
		try {
			for (int i = 0; i < paritions; i++) {
				ObjectName nameStart = new ObjectName("kafka.log:type=Log,name=LogStartOffset,topic=" + topic + ",partition=" + i);
				Long attributeStart = (Long)connection.getAttribute(nameStart, "Value");

				ObjectName nameEnd = new ObjectName("kafka.log:type=Log,name=LogEndOffset,topic=" + topic + ",partition=" + i);
				Long attributeEnd = (Long)connection.getAttribute(nameEnd, "Value");
				result += attributeEnd - attributeStart;
			}
		}
		catch (Exception ex) {
			result = -1L;
		}
		return result;
	}


	@Override
	protected void close() {
		if (consumer != null) {
			consumer.close();
		}
		try {
			if (connector != null) {
				connector.close();
			}
		}
		catch (IOException ex) {
			// nada
		}
	}

}