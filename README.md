# Consul-Config
This is an extension to Typesafe Config to allow for the loading of one or more Config objects from paths in Consul KV.  It is merely
a convience to avoid having to have the command line installed and remembering to fetch the KV values to files before launching your app.

# Usage
## Bootstrapping
The information about how to connect to your Consul server is read from a bootrap *Config* object.  An example bootstrap
configuration file looks as follows:

``` conf

consul {
  configkeys          =   ["conf1", "conf2"]
  url                 =   "localhost:8500"
}

```

The value of *configkeys* is a list of keys in Consul that will be loaded in a first-come, first-served fashion
from Consul and applied.  The loading mechanism follows the standard override mechanism for System and Environmental
variables.

## Getting Consul Config into your Application 
Application integration follows the standard ConfigFactory approach:

``` java

    // For standard configuration (finds bootstrap values in application.conf)
    Config conf = ConsulConfigFactory.load();

    // With a custom classloader and bootstrap conf location
    ClassLoader cl = ...
    Config conf = ConsulConfigFactory.load(cl, ConfigFactory.parseFile(new File("custom.conf")));


```

## Building
With Gradle

````bash
$> gradlew build

````

## TODO
- [x]  Basic HTTP Support
- [ ]  Support HTTPS and advanced ConsulClient Options in Bootstrap Config
