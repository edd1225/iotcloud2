package cgl.iotcloud.core.transport;

import cgl.iotcloud.core.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractTransport implements Transport {
    private static Logger LOG = LoggerFactory.getLogger(AbstractTransport.class);

    /**
     * Id of the site
     */
    protected String siteId;

    /**
     * This gateway is connected to these brokers
     */
    protected List<BrokerHost> brokerHosts;

    /**
     * Every transport has a list of applications. A group has specific channels registers to
     * it by the sensors
     */
    protected Map<String, ChannelGroup> groups = new ConcurrentHashMap<String, ChannelGroup>();

    /**
     * The transport specific configurations
     */
    protected Map transportConfiguration;

    @Override
    public void configure(String siteId, Map properties) {
        this.siteId = siteId;
        this.transportConfiguration = (Map)properties.get(Configuration.TRANSPORT_PROPERTIES);
        Object urlProp = transportConfiguration.get(TransportConstants.PROP_URLS);

        if (urlProp == null || !(urlProp instanceof List)) {
            String message = "Url is required by the Transport";
            LOG.error(message);
            throw new RuntimeException(message);
        }

        for (Object o : (List)urlProp) {
            if (o instanceof String) {
                String url = (String) o;
                String tokens[] = url.split(":");

                if (tokens.length == 2) {
                    brokerHosts.add(new BrokerHost(tokens[0], Integer.parseInt(tokens[1])));
                } else {
                    throw new RuntimeException("Each broker URL should be of the format host:port");
                }
            }
        }

        configureTransport();
    }

    public abstract void configureTransport();

    @Override
    public void registerChannel(ChannelName name, Channel channel) {
        // check to see if we already have a group for this channel
        ChannelGroup group = groups.get(channel.getGroup());
        if (group == null) {
            group = new ChannelGroup(channel.getName(), brokerHosts);
            groups.put(channel.getGroup(), group);
        }

        BrokerHost host = group.addChannel(channel);

        if (channel.getDirection() == Direction.OUT) {
            registerProducer(host, channel);
        } else if (channel.getDirection() == Direction.IN) {
            registerConsumer(host, channel);
        }
    }

    public abstract void registerProducer(BrokerHost host, Channel channel);

    public abstract void registerConsumer(BrokerHost host, Channel channel);
}
