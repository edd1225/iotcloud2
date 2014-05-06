package cgl.iotcloud.examples.chat;

import cgl.iotcloud.core.*;
import cgl.iotcloud.core.client.SensorClient;
import cgl.iotcloud.core.msg.SensorTextMessage;
import cgl.iotcloud.core.sensorsite.SensorDeployDescriptor;
import cgl.iotcloud.core.sensorsite.SiteContext;
import cgl.iotcloud.core.transport.Channel;
import cgl.iotcloud.core.transport.Direction;
import cgl.iotcloud.core.transport.MessageConverter;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class RabbitMQSensor extends AbstractSensor {
    private static Logger LOG = LoggerFactory.getLogger(RabbitMQSensor.class);
    @Override
    public Configurator getConfigurator(Map conf) {
        return new RabbitConfigurator();
    }

    @Override
    public void open(SensorContext context) {
        final Channel sendChannel = context.getChannel("rabbitmq", "sender");
        final Channel receiveChannel = context.getChannel("rabbitmq", "receiver");

        startChannel(sendChannel, new MessageSender() {
            @Override
            public boolean loop(BlockingQueue queue) {
                try {
                    queue.put(new SensorTextMessage("Hello"));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return false;
            }
        }, 100);

        startChannel(receiveChannel, new MessageReceiver() {
            @Override
            public void onMessage(Object message) {
                if (message instanceof SensorTextMessage) {
                    System.out.println(((SensorTextMessage) message).getText());
                } else {
                    System.out.println("Unexpected message");
                }
            }
        });
    }

    private class RabbitConfigurator extends AbstractConfigurator {
        @Override
        public SensorContext configure(SiteContext siteContext, Map conf) {
            SensorContext context = new SensorContext(new SensorId("rabbitChat", "general"));

            Map sendProps = new HashMap();
            sendProps.put("exchange", "test");
            sendProps.put("routingKey", "test1");
            sendProps.put("queueName", "test");
            Channel sendChannel = createChannel("sender", sendProps, Direction.OUT, 1024, new TextToByteConverter());

            Map receiveProps = new HashMap();
            receiveProps.put("queueName", "test");
            Channel receiveChannel = createChannel("receiver", receiveProps, Direction.IN, 1024, new ByteToTextConverter());

            context.addChannel("rabbitmq", sendChannel);
            context.addChannel("rabbitmq", receiveChannel);

            return context;
        }
    }

    private class ByteToTextConverter implements MessageConverter {
        @Override
        public Object convert(Object input, Object context) {
            if (input instanceof byte[]) {
                ByteArrayInputStream in = new ByteArrayInputStream((byte[]) input);
                ObjectInputStream is = null;
                try {
                    is = new ObjectInputStream(in);
                    return is.readObject();
                } catch (Exception e) {
                    LOG.error("E");
                }
            }
            return null;
        }
    }

    private class TextToByteConverter implements MessageConverter {

        @Override
        public Object convert(Object input, Object context) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = null;
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(input);
                return bos.toByteArray();
            } catch (IOException e) {
                LOG.error("Error", e);
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ignore) {
                    // ignore close exception
                }
                try {
                    bos.close();
                } catch (IOException ignore) {
                    // ignore close exception
                }
            }
            return null;
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void activate() {

    }

    @Override
    public void deactivate() {

    }

    public static void main(String[] args) {
        // read the configuration file
        Map conf = Utils.readConfig();
        SensorClient client;
        try {
            client = new SensorClient(conf);

            List<String> sites = new ArrayList<String>();
            sites.add("local");

            SensorDeployDescriptor deployDescriptor = new SensorDeployDescriptor("iotcloud-examples-1.0-SNAPSHOT.jar", "cgl.iotcloud.examples.chat.RabbitMQSensor");
            deployDescriptor.addDeploySites(sites);

            client.deploySensor(deployDescriptor);
        } catch (TTransportException e) {
            e.printStackTrace();
        }
    }
}