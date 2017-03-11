package com.nextinstruction.config;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetBinaryValue;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;


public class ConsulConfigFactory {

    ConsulConfigFactory(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    private ConsulClient consulClient;
    private static final Logger LOG = LoggerFactory.getLogger(ConsulConfigFactory.class);
    private static final int DEFAULT_CONSUL_HTTP_PORT = 8500;
    private static final String CONSUL_CONF_ROOT = "consul";
    private static final String CONSUL_CONFIGKEYS = "configkeys";

    private static File createTempConfigFile(String configPath, String contents) {
        String normalizedTmpPath = configPath.replaceAll("\\s+|[\\\\/]", "_");
        try {
            File tmpFile = File.createTempFile(normalizedTmpPath + "-", ".conf");
            Writer writer = new PrintWriter(tmpFile);
            BufferedReader br = new BufferedReader(new StringReader(contents));
            String line = null;
            while ((line = br.readLine()) != null) {
                writer.append(line);
                writer.append(System.lineSeparator());
            }
            writer.close();
            br.close();
            tmpFile.deleteOnExit();
            return tmpFile;
        } catch (IOException ioe) {
            LOG.error("Unable to create temporary config file: {}", normalizedTmpPath);
            throw new RuntimeException("tmpfile creation error", ioe);
        }

    }
    

    private static ConsulClient createConsulClientFromConfig(Config baseConf) {
        Config consulConf = baseConf.withOnlyPath(CONSUL_CONF_ROOT);
        String urlStr = consulConf.getString("url");
        if (!urlStr.startsWith("http")) {
            urlStr = "http://" + urlStr;
        }
        try {
            URL consulUrl = URI.create(urlStr).toURL();
            int port = consulUrl.getPort();
            if (port <= 0) {
                port = DEFAULT_CONSUL_HTTP_PORT;
            }
            return new ConsulClient(consulUrl.getHost(), port);
        } catch (MalformedURLException ex) {
            LOG.error("Bad Consul URL: [{}]!", urlStr);
            throw new RuntimeException(ex);
        }
    }

    protected Config doLoad(ClassLoader classLoader, Config bootstrapConf) {
        final Config consulConf = bootstrapConf.withOnlyPath(CONSUL_CONF_ROOT);
        final List<String> consulPaths  = consulConf.getStringList(String.format("%s.%s",CONSUL_CONF_ROOT, CONSUL_CONFIGKEYS));
        Config consulConfig = ConfigFactory.empty("Consul paths: " + consulPaths);

        LOG.info("Loading Config from Consul...");
        for (String consulPath : consulPaths) {
            boolean loaded = false;

            final Response<GetBinaryValue> response = consulClient.getKVBinaryValue(consulPath);
            if (response.getValue() != null) {
                if (response.getValue().getValue() != null) {
                    String confData = new String(response.getValue().getValue(),
                            Charset.forName("UTF-8"));

                    // Copy the contents of the current config key to a tempfile
                    final File tmpConfFile = createTempConfigFile(consulPath, confData);

                    // load a Config object from the contents of the Consul key that were
                    // copied to a tmpFile
                    final Config currConfig = ConfigFactory.parseFile(tmpConfFile);

                    // each successive config is a fallback to prior ones (first config in list wins)
                    consulConfig = consulConfig.withFallback(currConfig);
                    loaded = true;
                }
            }
            if (!loaded) {
                LOG.error("Config for Consul path [" + consulPath + "] could not be loaded");
                throw new RuntimeException("Unable to load Consul Configuration: " + consulPath);
            }

        }
        LOG.info("Loaded [{}] Consul Configs from {}", consulPaths.size(), consulPaths);

        return ConfigFactory.defaultOverrides(classLoader)
                .withFallback(consulConfig);
    }


    /**
     *  Client methods
     */
    
    public static Config load(ClassLoader classLoader, Config bootstrapConf) {
        return new ConsulConfigFactory(createConsulClientFromConfig(bootstrapConf))
                .doLoad(classLoader, bootstrapConf);
    }

    public static Config load(Config bootstrapConf) {
        return load(Thread.currentThread().getContextClassLoader(), bootstrapConf);
    }

    public static Config load(ClassLoader classLoader) {
        return load(classLoader, ConfigFactory.load());
    }

    public static Config load() {
        return load(Thread.currentThread().getContextClassLoader(), ConfigFactory.load());
    }
    

}