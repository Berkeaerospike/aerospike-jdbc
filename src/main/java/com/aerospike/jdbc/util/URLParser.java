package com.aerospike.jdbc.util;

import com.aerospike.client.Host;
import com.aerospike.client.Value;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.jdbc.scan.EventLoopProvider;

import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class URLParser {

    private static final Logger logger = Logger.getLogger(URLParser.class.getName());

    private static final String defaultAerospikePort = "3000";
    private static final int defaultRecordsPerSecond = 512;

    private static final Pattern AS_JDBC_URL = Pattern.compile("^jdbc:aerospike:(?://)?([^/?]+)");
    private static final Pattern AS_JDBC_SCHEMA = Pattern.compile("/([^?]+)");

    private static Host[] hosts;
    private static String schema;
    private static Properties clientInfo;
    private static ClientPolicy clientPolicy;
    private static WritePolicy writePolicy;
    private static ScanPolicy scanPolicy;

    public static Host[] getHosts() {
        return hosts;
    }

    public static String getSchema() {
        return schema;
    }

    public static Properties getClientInfo() {
        return clientInfo;
    }

    public static ClientPolicy getClientPolicy() {
        return clientPolicy;
    }

    public static WritePolicy getWritePolicy() {
        return writePolicy;
    }

    public static ScanPolicy getScanPolicy() {
        return scanPolicy;
    }

    public static void parseUrl(String url, Properties props) {
        logger.info("URL properties: " + props);
        hosts = parseHosts(url);
        schema = parseSchema(url);
        clientInfo = parseClientInfo(url, props);
        clientPolicy = copy(clientInfo, new ClientPolicy());
        clientPolicy.eventLoops = EventLoopProvider.getEventLoops();

        writePolicy = copy(clientInfo, new WritePolicy());
        scanPolicy = copy(clientInfo, new ScanPolicy());
        if (scanPolicy.recordsPerSecond == 0) {
            scanPolicy.recordsPerSecond = defaultRecordsPerSecond;
        }
        Value.UseBoolBin = Optional.ofNullable(props.getProperty("useBoolBin"))
                .map(Boolean::parseBoolean).orElse(true);
        logger.info("Value.UseBoolBin = " + Value.UseBoolBin);
    }

    public static <T> T copy(Properties props, T object) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) object.getClass();
        props.forEach((key, value) -> {
            try {
                clazz.getField((String) key).set(object, value);
            } catch (ReflectiveOperationException e1) {
                // ignore it; this property does not belong to the object
            }
        });
        return object;
    }

    private static Host[] parseHosts(String url) {
        Matcher m = AS_JDBC_URL.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse URL " + url);
        }
        return Arrays.stream(m.group(1).split(","))
                .map(p -> p.split(":"))
                .map(a -> a.length > 1 ? a : new String[]{a[0], defaultAerospikePort})
                .map(hostPort -> new Host(hostPort[0], Integer.parseInt(hostPort[1])))
                .toArray(Host[]::new);
    }

    private static String parseSchema(String url) {
        Matcher m = AS_JDBC_SCHEMA.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static Properties parseClientInfo(String url, Properties props) {
        Properties all = new Properties();
        all.putAll(props);
        int questionPos = url.indexOf('?');
        if (questionPos > 0 && questionPos < url.length() - 1) {
            Arrays.stream(url.substring(questionPos + 1).split("&")).forEach(p -> {
                String[] kv = p.split("=");
                if (kv.length > 1) {
                    all.setProperty(kv[0], kv[1]);
                }
            });
        }
        return all;
    }
}
