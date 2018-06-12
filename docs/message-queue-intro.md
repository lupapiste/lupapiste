# Introduction to message queues

# JMS - Java Message Service API

JMS gives good encapsulation for basic messaging needs. 

JMS is supported by large variety of brokers, which enables to change the underlying message broker without affecting client implementations.

A [jms-client wrapper](https://github.com/lupapiste/jms-client) for common JMS stuff is used at project, feel free to contribute.

# ActiveMQ Artemis

Lupapiste currently uses [ActiveMQ Artemis](https://activemq.apache.org/artemis/index.html) as message broker. It's a results of merging ActiveMQ and HornetQ into one product.

> The overall objective for working toward feature parity between ActiveMQ 5.x and Artemis is for Artemis to eventually become ActiveMQ 6.x.

Artemis itself is [protocol agnostic](https://activemq.apache.org/artemis/docs/latest/architecture.html) and doesn't actually know JMS, but JMS semantics are implemented as facede in client side library.


# Development

Locally an embedded ActiveMQ Artemis broker is started in dev mode (default mode). See `artemis-server` namespace.

Alternatively you can fire up local instance by [installing Artemis](https://activemq.apache.org/artemis/download.html), and then setting JMS properties. See example in [local.properties](../resources/local.properties).

## JMS client library

The [jms-client](https://github.com/lupapiste/jms-client) library provides common stuff for working with JMS stuff in Clojure.

For example if you wan't to connect to broker of your choice, you can create an appropriate ConnectionFactory and juse jms/create-connection to create actual javax.jms.Connection for you.

Client side must deal with state, the provided client is just a thing wrapper for common stuff.   

# TODOs

* Server side re-delivery delay

# JMS client FAQ

## Session modes

Session mode defines how messages are acknowledged to the broker. There are for modes avaialble in general:

1. AUTO_ACKNOWLEDGE
2. CLIENT_ACKNOWLEDGE
3. DUPS_OK_ACKNOWLEDGE
4. SESSION_TRANSACTED

Most likely in Lupapiste cases, AUTO_ACKNOWLEDGE or SESSION_TRANSACTED is selected. If performance is critical in broker side, DUPS_OK_ACKNOWLEDGE could be used. Short descriptions of each:

### AUTO_ACKNOWLEDGE

When message is received and handled, message is automatically acknowledged to the broker.

More precisly:

>If the receiver uses the MessageListener interface, the message is automatically acknowledged when it successfully returns from the onMessage() method.

~~If an exception is raised during onMessage, message is automatically rolled back to broker for redelivery.~~

~~Thus implementing client doesn't need any special "acknowledging" code: handling callback function successfully does the trick.~~

EDIT: After many tests and research, it seems it's NOT ok to throw exception from consumer in search for redeliveries. 

So use AUTO_ACKNOWLEDGE for simple messaging tasks, that don't need "transactional" semantics. If you need to conditionally send messages back to queue, use SESSION_TRANSACTED. 

### CLIENT_ACKNOWLEDGE

With this mode, the client application needs to explicitly call `message.acknowledge()` for a received message, to acknowledge broker that the message is delivered successfully.

If this mode is used, client should be ensured to acknowledge message in reasonable time, as it might cause performance problems when acknowledgements are queued.

### DUPS_OK_ACKNOWLEDGE

Acknowledgements sent back to broker are batched to reduce network roundtrips and increase performance. Thus it's possible that broker re-delivers messages, if it doesn't receive acknowledgement in time.

Client application code needs to check, if received message is already handled. If that needs possibly slow database queries, the JMS message can be inspected with `message.getJMSRedelivered()` or `message.getIntProperty("JMSXDeliveryCount")` to see if the message in question is already redelivered.

### SESSION_TRANSACTED

A transacted session can be created. Transaction is never excplicitly started, but `session.commit()` and `session.rollback()` methods can be used to control wheter message(s) should be rolled back or acknowledged to broker. 

>The transacted session uses a chained-transaction model. In a chained-transaction model, an application does not explicitly start a transaction.

Transaction logic is provided in consumer side code. See `lupapalvelu.xml.krysp.http` namespace for example.

Good explanation _Transacted session_ can be found from [JavaWolrd](https://www.javaworld.com/article/2074123/java-web-development/transaction-and-redelivery-in-jms.html?page=2).

## Difference between persistent and non-persistent delivery?

Non-persistent deliveries are not saved on disk in the broker, while persistent messages are. More at [ActiveMQ site](http://activemq.apache.org/what-is-the-difference-between-persistent-and-non-persistent-delivery.html).

## Shared or individual connection/session per message?

This seems to be source of some debate. 

Majority of sources (eg. [ActiveMQ](http://activemq.apache.org/how-do-i-use-jms-efficiently.html)) suggest to share connections/sessions as long as possible.

Some others (eg. Spring JMS template) have implementations where connections and sessions are created and closed for each message. 
For example there is a [comment in Stackoverflow](https://stackoverflow.com/a/24494739) supporting this kind of approach.

There are some considerations on [ActiveMQ site](http://activemq.apache.org/spring-support.html), about using pooling connections to overcome some limitations with this JMS template approach.

> Note: while the PooledConnectionFactory does allow the creation of a collection of active consumers, it does not 'pool' consumers. 
> Pooling makes sense for connections, sessions and producers, which can be seldom-used resources, are expensive to create and can remain idle a minimal cost. 
> Consumers, on the other hand, are usually just created at startup and left going, handling incoming messages as they come.   
  
When reusing connections there are couple of things to consider:

1. During a JMS Session, only one action can be done at a time or else you will face a exception. 
For example if you share session with producer and consumer, and you produce messages in a loop, consumer side will throw exception because same session is used simultaneously both to produce and consume.
Solution to this problem is to separate consumer and producer sessions.
2. If remote broker connection is disconnected (for example network problems or boot), ~~all the sessions get disconnected. It seems those sessions can't be restarted, but instead new ones should be created upon reconnect.~~
EDIT: new infromation, previously described disconnection of sessions happens if the JMS connection factory is not configured for reconnect. But by default we initialize ActiveMQJMSConnectionFactory in a way that it will automatically reconnect when broker is back online. See `jms.clj`.

## What happens if we send message to broker that's offline?

When connection is lost, the Artemis JMS Client issues us warning and starts reconnecting:
```
WARN 2018-04-17 14:50:46,756 [org.apache.activemq.artemis.core.client] - <AMQ212037: Connection failure has been detected: AMQ119015: The connection was disconnected because of server shutdown [code=DISCONNECTED]>
```

If during lost connection we try to send a message to producer, it will block until timeout is reached:
```clojure
; foo1 is producer created by `create-producer`
(foo1 "test")
; ...blocks for 30 secs...
; => ActiveMQConnectionTimedOutException AMQ119014: Timed out after waiting 30,000 ms for response when sending packet 71  org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl.sendBlocking (ChannelImpl.java:415)
```

So the client should have some kind of recovery for these situations.

## Message redelivery

If for some reason message deliveryn fails or message is rolled back, by default message is redelivered by the broker 10 times. If all redeliveries are unsuccessful, the broker will throw message to DLQ (Dead Letter Queue).

From DLQ, messages can be rerouted to other queue using management console UI (manual retry for example)

Default setup does 10 redeliveries with delay of 0. Adding some redelivery delay for queues is couraged.

More information about configuring from [Artemis docs](https://activemq.apache.org/artemis/docs/latest/undelivered-messages.html).

See also the `lupapiste-ansible` repository for `broker.xml` configuration.

# ActiveMQ Artemis server FAQ

## Auto-create and auto-delete addresses?

By default Artemis server is provisioned with "auto-create" set to true. This means it will auto-create queue, when message is sent to it. This is good for dynamic queues.
On the contrary, also "auto-delete" feature is set to true by default. This means queue is deleted from broker when there are 0 consumers and 0 messages. In general this is fine too, as queue is re-created when consumers connect to it or producer procudes a message.

~~It seems it's not possible to retain dynamically created queues between server restarts. When server is restarted, client consumers don't receive messages anymore to queues they subscribed. 
Instead an exception is raised on server side when consumer re-connects to it with message "Queue X does not exists".~~ This might be a bug in Artemis UPDATE: yes it's a bug: https://issues.apache.org/jira/browse/ARTEMIS-1818.

This bug has been fixed in release 2.6.0 release of Artemis.

Read more about config possibilities from [Artemis documentation](https://activemq.apache.org/artemis/docs/latest/address-model.html#automatic-addressqueue-management).
