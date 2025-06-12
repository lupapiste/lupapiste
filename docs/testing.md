# Testing

## Integration testing

### Remote fixtures

#### How do we manage databases during integration tests?

1. The `lupapalvelu.itest-util` namespace
   1. defines a unique database name for the whole test run (not per-test)
      - `(defonce test-db-name (str "test_" (now)))`
   1. stores the generated database name in a cookie
      - `(def test-db-cookie (c/->lupa-cookie "test_db_name" test-db-name))`
   1. provides the fixture function: `apply-remote-minimal`
1. The `apply-remote-minimal` function makes a request to `$server/dev/fixture/minimal` and includes the cookie
   - When running tests locally, `$server` will be `http://localhost:8000`.
1. On the server side, `lupapalvelu.mongo` provides a `db-selection-middleware` middleware, which chooses the active database by either
   - reading the `test_db_name` cookie from the request
   - or falling back to `default-db-name`

#### Do we cleanup these old test databases?

The `lupapiste-ansible` project contains a `drop-test-databases.js` file and a `cron` job, which periodically removes all test databases.

## Frontend testing

Tests for basic frontend logic (not rendering) can be tested with `shadow-cljs`. This includes using `day8.re-frame/test` to test `re-frame` events and subscriptions.

```bash
lein front:test
```

The tests will include the `.cljs` and `.cljc` tests (ie. namespaces ending in "-test") found on the classpath and are executed using `nodejs`. As a consequence, only a limited set of `js` objects are present. The source code may refer to e.g. `js/window`, `js/document`, or any Lupapiste JavaScript variables without problems, but actually evaluating or invoking them while running the tests will result in an error.

Frontend tests can _also_ be executed in the browser for better developer experience. The `test-browser` target will host the results on [[http://localhost:8021]]. Shadow-cljs will re-run the tests if you modify the tests or the code. As mentioned before, the capabilities of the browser may exceed `nodejs`; be mindful not to write tests that work in the browser but fail on the command line with `nodejs`.
