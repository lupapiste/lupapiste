# Integrant

Instead of accessing resources or side-effecting components as independent global vars, we use `integrant` to build a complete system and then pass a subset of this system where needed.

## Configuration

Previously, the system configuration was implemented using `sade.env` as a global constant based on environmental variables. The actual code was/is littered with `(sade.env/value :foo :bar)` to grab values from this global constant.

With `integrant`, the configuration is managed in `lupapalvelu.system.config`, ideally completely. To ease transition, we still use the `sade.env` as the source of truth but instead construct a single configuration map of the system. The rest of the system should not interact with `sade.env` but rather the configuration map if necessary. We are not there yet, but eventually we ought to be able to remove all references to `sade.env` from the application code, but there's a lot of work to be done.

## Building the system

The `lupapalvelu.system.build` namespace contains the implementation to turn the system configuration into an `integrant` blueprint for building an actual system.

### Abstracting components

Sometimes (e.g. for testing) we want to abstract the concrete component implementation behind an "interface". For example, the system blueprint uses the `:laundry/client` keyword to identify "some" Laundry implementation. When using the proper Laundry implementation, we derive the concrete implementation with `:lupapalvelu.laundry.client/client-impl`. Tests, on the other hand, derive the mock Laundry instance using `:lupapalvelu.itest-system/mock-laundry`.

### Starting Partial Systems

Sometimes we only need a partial system temporarily; e.g. batchrun, migration or sheriff scripts might only need a MongoDB connection and not much else. For these cases the `lupapalvelu.system.system-util` namespace contains some utility macros for easily running a block of code within a system.

The `user` namespace also contains some utilities (e.g. `connect-mongo!`, `disconnect-mongo!`, and the macro `s`) to interact with the system with minimal ceremony.

## Application code

The individual `integrant` components are needed when our route handlers (e.g. `action`s) or pubsub subscribers need to perform side-effects. Instead of passing the whole system as-is, the `lupapalvelu.system.util-components` provides a `::common` component, which provides a curated set of components to the rest of the application. This includes side-effecting stuff like `:mongo` or `:elastic` but also the original configuration under `:config`. The rest of the system can use this common abstraction without needing to know the detailed structure of the system map. The namespace also provides plenty of accessors for accessing specific pieces of the system (e.g. `system->elastic-config`). Note that in the application code this subset of the whole `integrant` system is also called `system`!

Note that the MongoDB connection is handled a bit differently. As we have hundreds of call sites, passing the `system` to each would require quite a bit of modifications. At the moment the MongoDB connection is passed to the call sites using the `lupapalvelu.mongo/*connection*` dynamic var instead of an actual parameter.

## Testing

### Testing configuration

As mentioned above, the system configuration was previously handled in `sade.env` as a global constant and the same global configuration was active when testing. This sometimes resulted in tests failing as they were written using one environment but executed in another. To combat this, various tools/macros were used to either stabilize the configuration or overwrite it as necessary on a per test basis.

Similar to the main `integrant` configuration in `lupapalvelu.system.config`, the tests specify their own separate configuration in `lupapalvelu.itest-system`. This test configuration still partially relies on `sade.env` to grab values such as the test database credentials.

### Testing system

The existing `lupapalvelu.itest-util` namespace contains two tools for running integration tests within a `integrant` system; `with-test-system` and `local-fixture-factory`. The former takes a test option map and a body of code to execute. It creates a suitable test system, starts it, runs the given test code and finally shuts down the system. The `with-test-system` macro can be used with both `midje` and `clojure.test` tests. The latter `local-fixture-factory` function creates a `clojure.test` fixture for running "local" tests.

#### Local and Remote Tests

Historically there had been a significant difference between so called "local" and "remote" integration tests when running in CI. It used to be that
- local tests would invoke `action`s as functions on the local JVM and could modify the local database
- remote tests would invoke `action`s on a remote Lupapiste host using HTTP and could not (directly) access the target database
- tests would use either special cookies (`test_db_name`), macros (`mongo/with-db`), or they would encode the database name into PubSub messages to make sure the test accessed the correct database

This distinction later became less obvious, as the remote tests were altered to use HTTP to invoke the web server running on the local JVM (iow. `http://localhost:8080`) instead of another host.

Introducing `integrant` only slightly alters this convention:
- the test system is started and stopped for each test separately, using the configuration and components defined by the test itself
- the test system only knows about the test database so there is no need to indicate which database to use
- the test system can co-exist with any development system running on the same JVM, as they use their own resources (separate HTTP servers, separate PubSub queue names..)
