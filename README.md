Akka Batteries (*Beta*\)
========================

A collection of useful utilities for Akka cluster in production.

Akka Cluster Discover
------------------------

To discover cluster seed nodes from external source. Cluster will be formed programatically by calling `ClusterDiscovery(system).start()`. It supports a few cluster discovery providers: `self` for local development and debugging only in which the seed will join itself to form the cluster. `network` which uses a provided set of seeds `ip:port`. `elb` which will use AWS ELB for seed discovery where all seed nodes must register with the same elb. When the expected number of configured seed nodes is reached, the cluster will be formed.

*Note:* When using AWS ECS, if the seed service is configured with elb, the registration will happen automatically.

#### Example

To use the cluster discovery, add configuration to your project. You can configure only the discovery provider you need. It is useful to have `network` or `self` for local development or testing and `elb` for production

```
// application.conf or reference.configured

akka {
  cluster {
    discovery {
      provider = network
      provider = ${?DISCOVERY_PROVIDER} // network, elb or self
      modules {

        network {
          seed-hosts = ["127.0.0.1:2551"]
        }

        elb {
          name = "YOUR-SEED-ELB-NAME"
          name = ${?DISCOVERY_ELB_NAME}
        }
      }
    }
  }
}
```

It is better to override configuration with environment variables so it can be changed during operation after deployment.

In your code whenever you need to join the Akka cluster add the following (assuming `system` is your actor system variable):

```
ClusterDiscovery(system).start()
```

It is better to only invoke the above code once your actor is fully initialized and ready to serve, so it only joins the cluster when it is fully in service.

Local Affinity Router
------------------------

A cluster-aware group router that favors components on the same server but if target component is not on the same exists then the router will fallback to provided routing logic (i.e. RoundRobin or Random). This router helps in reducing network bandwidth between the servers that run the akka cluster which is important for very high throughput services.

#### Example

```
akka {
  actor{
    deployment {
      /myRouter {
        router = local-affinity-round-robin-group
        router = ${?MY_ROUTER}
        routees.paths = ["/user/myService"]
        cluster {
          enabled = on
          allow-local-routees = on
          use-role = my-service // to limit the search space
        }
      }
    }
  }
}
```

The router can be used from the code as following:

```
val myRouter = system.actorOf(FromConfig.props(), name = "myRouter")
```

Role-based Split Brain Resolver
----------------------------------

The split brain heal the cluster when a network partition occurs or when a node does not leave the cluster cleanly. To ensure the entire system is function, mandatory roles can be configured. The resolver will maintain the partition with all the mandatory roles even if they are the minority. If both partitions contain the mandatory roles, then keep majority will be applied. Tie will be broken by keeping the partition with the oldest running node. If both partitions does not have all the essential roles, then the entire cluster will shutdown. An external process recovery should be configured to restart the JVM and re-establish the cluster.

#### Example

```
akka {
  cluster {
    downing-provider-class =
     "com.paytmlabs.akka.cluster.sbr.RoleBasedSplitBrainResolverProvider"
  }
}

app {
  cluster {
    stable-after = 10 seconds
    essential-roles = ["seed", "service1", "service2"]
  }
}
```

`stable-after` is the cool down period the resolver will wait after the last change in cluster membership before acting.

TODO
----

These utilities have been running in our production systems with a high throughput (sustained `6,000+ req/sec`) and low latency (less than `50 millis`).

-	Add unit and multi-jvm testing
-	Support more discovery providers (e.g., consul, etcd, s3)
-	Setup a public CI/CD pipeline
-	Publish artifacts to central repository
-	Add full example projects

Contributions will be appreciated, please open a github issue first to discuss your potential PR before working on it to ensure there is no duplication in effort.

LICENSE
-------

```
Copyright 2017 Paytm Labs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
