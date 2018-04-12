# Introduction to message queues

# JMS - Java Message Service API

JMS gives good encapsulation for basic messaging needs. 

JMS is supported by large variety of brokers, which enables to change the underlying message broker without affecting client implementations.

# ActiveMQ Artemis

Lupapiste currently uses [ActiveMQ Artemis](https://activemq.apache.org/artemis/index.html) as message broker. It's results of merging ActiveMQ and HornetQ into one product.

> The overall objective for working toward feature parity between ActiveMQ 5.x and Artemis is for Artemis to eventually become ActiveMQ 6.x.

Artemis itself is [protocol agnostic](https://activemq.apache.org/artemis/docs/latest/architecture.html) and doesn't actually know JMS, but JMS semantics are implemented as facede in client side.


# Development

Locally an embedded ActiveMQ Artemis broker is started, this can be controlled with `:embedded-broker` feature flag. See `artemis-server` namespace.


# JMS client FAQ

## Difference between persistent and non-persistent delivery?

Non-persistent deliveries are not saved on disk in the broker, while persitent messages are. More at [ActiveMQ site](http://activemq.apache.org/what-is-the-difference-between-persistent-and-non-persistent-delivery.html).





