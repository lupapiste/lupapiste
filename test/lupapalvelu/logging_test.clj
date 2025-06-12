(ns lupapalvelu.logging-test
  (:require [clojure.string :as str]
            [lupapalvelu.action :refer [defcommand]]
            [lupapalvelu.api-common :as api-common]
            [lupapalvelu.json :as json]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.logging :as log]
            [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [sade.core :refer [fail fail! ok]]
            [sade.env :as env]
            [sade.strings :as ss]
            #_{:clj-kondo/ignore [:discouraged-namespace]}
            [taoensso.timbre :as timbre]))

(set! *warn-on-reflection* true)

(facts "sanitize"
  (fact "truncate"
    (log/sanitize 5 "12345") => "12345"
    (log/sanitize 2 "12345") => "12... (truncated)")

  (fact "new lines"
    (log/sanitize 100 "1\r2\n3\r\n4") => "1\\n2\\n3\\n4"))

(defmacro reset-logging-config
  "Takes a snapshot of the logging config, executes body and finally resets the logging config to the old value
  Returns the value of body."
  [& body]
  `(let [old-val# timbre/*config*]
     (let [result# (do ~@body)]
       (timbre/set-config! old-val#)
       result#)))

(facts "json output"
  (reset-logging-config
    (env/with-feature-value :log-json true
      ;; Reset logging setup using JSON
      (log/setup-logging! {:min-log-level :debug})
      (fact "no args"
        (-> (with-out-str
              (log/info "hello"))
            json/decode)
        => (just {"args"      {}
                  "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"  string?
                  "line"      int?
                  "message"   "hello"
                  "ns"        "lupapalvelu.logging-test"
                  "severity"  "info"
                  "thread"    string?
                  "timestamp" string?})
        (-> (with-out-str
              (log/info 42))
            json/decode)
        => (just {"args"      {"0" 42}
                  "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"  string?
                  "line"      int?
                  "message"   "42"
                  "ns"        "lupapalvelu.logging-test"
                  "severity"  "info"
                  "thread"    string?
                  "timestamp" string?}))
      (fact "two args"
        (-> (with-out-str
              (log/warn "hello" :foo 123))
            json/decode)
        => (just {"args"      {"0" "foo"
                               "1" 123}
                  "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"  string?
                  "line"      int?
                  "message"   "hello :foo 123"
                  "ns"        "lupapalvelu.logging-test"
                  "severity"  "warn"
                  "thread"    string?
                  "timestamp" string?}))
      (fact "non-string first arg"
        (-> (with-out-str
              (log/warn :foo 123 "hello"))
            json/decode)
        => (just {"args"      {"0" "foo"
                               "1" 123
                               "2" "hello"}
                  "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"  string?
                  "line"      int?
                  "message"   ":foo 123 hello"
                  "ns"        "lupapalvelu.logging-test"
                  "severity"  "warn"
                  "thread"    string?
                  "timestamp" string?}))
      (fact "string format variant"
        (-> (with-out-str
              (log/warnf "hello %s %d %s" "foo" 42 "bar"))
            json/decode)
        => (just {"file"      (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"  string?
                  "line"      int?
                  "message"   "hello foo 42 bar"
                  "ns"        "lupapalvelu.logging-test"
                  "severity"  "warn"
                  "thread"    string?
                  "timestamp" string?}))
      (fact "with logging context"
        (-> (with-out-str
              (log/with-logging-context
                {:bar  42
                 :quuz "asdf"}
                (log/warn {:foo 123 :txt "hello"})))
            json/decode)
        => (just {"args"      {"0" {"foo" 123
                                    "txt" "hello"}}
                  ;; values from the logging context
                  "bar"       42
                  "quuz"      "asdf"
                  "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"  string?
                  "line"      int?
                  "message"   "{:foo 123, :txt \"hello\"}"
                  "ns"        "lupapalvelu.logging-test"
                  "severity"  "warn"
                  "thread"    string?
                  "timestamp" string?}))
      (fact "exception stack trace"
        (-> (with-out-str
              (try
                (/ 0 0)
                (catch Exception e
                  (log/info e "Got an error!" {:foo 42}))))
            json/decode)
        => (just {"args"        {"0" {"foo" 42}}
                  "file"        (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                  "hostname"    string?
                  "line"        int?
                  "message"     "Got an error! {:foo 42}"
                  "ns"          "lupapalvelu.logging-test"
                  "severity"    "info"
                  "thread"      string?
                  "timestamp"   string?
                  ;; Produced by throwable->Map within `log-json-appender`
                  "exception"   map?
                  ;; Our own attribute for GCP error reporting
                  "stack_trace" (fn [s] (str/starts-with?
                                          s
                                          (str "java.lang.ArithmeticException: Divide by zero\n"
                                               " at clojure.lang.Numbers.divide (Numbers.java:190)\n"
                                               "    clojure.lang.Numbers.divide (Numbers.java:3915)\n")))})))))

(facts "json event output"
  (reset-logging-config
    (env/with-feature-value :log-json true
      ;; Reset logging setup using JSON
      (log/setup-logging! {:min-log-level :debug})
      (fact "batchrun event"
        (-> (with-out-str
              (log/log-event :info {:run-by     "Automatic bitcoin batchrun"
                                    :event      "Some event description"
                                    :random-ids #{1 2 3}}))
            json/decode)
        => (just {"event"     {"event"      "Some event description"
                               "random-ids" [1 3 2]
                               "run-by"     "Automatic bitcoin batchrun"}
                  "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging.clj"))
                  "hostname"  string?
                  "isEvent"   true
                  "line"      int?
                  "message"   "Some event description"
                  "ns"        "lupapalvelu.logging"
                  "severity"  "info"
                  "thread"    string?
                  "timestamp" string?}))
      (let [action {:ns                      "lupapalvelu.actions-api"
                    :type                    :query
                    :created                 1713851205851
                    :lang                    :fi
                    :action                  "allowed-actions"
                    :user                    {:role      "authority"
                                              :email     "sonja.sibbo@sipoo.fi"
                                              :username  "sonja"
                                              :firstName "Sonja"
                                              :orgAuthz  {:753-YA        #{:authority :approver}
                                                          :998-R-TESTI-2 #{:authority :approver}
                                                          :186-R         #{:tos-editor :tos-publisher
                                                                           :biller :authority :approver
                                                                           :archivist :digitizer}}
                                              :expires   1713851504697
                                              :language  "fi"
                                              :id        "777777777777777777000023"
                                              :lastName  "Sibbo"}
                    :web                     {:user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:124.0) Gecko/20100101 Firefox/124.0"
                                              :client-ip  "127.0.0.1"
                                              :host       "http://localhost:8000"
                                              :method     :get}
                    :session                 {:id      "13eb84cb-18b1-44f2-948f-edee2a616f80"
                                              :expires 1713865604564
                                              :user    {:role      "authority"
                                                        :email     "sonja.sibbo@sipoo.fi"
                                                        :username  "sonja"
                                                        :firstName "Sonja"
                                                        :orgAuthz  {:753-YA        #{:authority :approver}
                                                                    :998-R-TESTI-2 #{:authority :approver}
                                                                    :186-R         #{:tos-editor :tos-publisher
                                                                                     :biller :authority :approver
                                                                                     :archivist :digitizer}}
                                                        :expires   1713851504697
                                                        :language  "fi"
                                                        :id        "777777777777777777000023"
                                                        :lastName  "Sibbo"}}
                    :system                  {}
                    :data                    {:_        "1713851205683"
                                              :tempfile "IGNORED"}
                    ;; These fields are not logged in the event
                    :application             "IGNORED"
                    :application-assignments "IGNORED"
                    :application-bulletins   "IGNORED"
                    :organization            "IGNORED"
                    :user-organizations      "IGNORED"
                    :permissions             "IGNORED"
                    :document                "IGNORED"
                    :company                 "IGNORED"}]
        (fact "query-action"
          (-> (with-out-str
                ;; Sample command data grabbed from `lupapalvelu.action/executed`
                ;; when logging in as Sonja
                (log/log-event :info action))
              json/decode)
          => (just {"event"     {"action"  "allowed-actions"
                                 "created" 1713851205851
                                 "data"    {"_" "1713851205683"}
                                 "lang"    "fi"
                                 "session" {"expires" 1713865604564
                                            "id"      "13eb84cb-18b1-44f2-948f-edee2a616f80"
                                            "user"    {"email"     "sonja.sibbo@sipoo.fi"
                                                       "expires"   1713851504697
                                                       "firstName" "Sonja"
                                                       "id"        "777777777777777777000023"
                                                       "language"  "fi"
                                                       "lastName"  "Sibbo"
                                                       "orgAuthz"  {"186-R"         ["tos-editor"
                                                                                     "tos-publisher"
                                                                                     "biller"
                                                                                     "authority"
                                                                                     "approver"
                                                                                     "archivist"
                                                                                     "digitizer"]
                                                                    "753-YA"        ["authority" "approver"]
                                                                    "998-R-TESTI-2" ["authority"
                                                                                     "approver"]}
                                                       "role"      "authority"
                                                       "username"  "sonja"}}
                                 "type"    "query"
                                 "user"    {"email"     "sonja.sibbo@sipoo.fi"
                                            "expires"   1713851504697
                                            "firstName" "Sonja"
                                            "id"        "777777777777777777000023"
                                            "language"  "fi"
                                            "lastName"  "Sibbo"
                                            "orgAuthz"  {"186-R"         ["tos-editor"
                                                                          "tos-publisher"
                                                                          "biller"
                                                                          "authority"
                                                                          "approver"
                                                                          "archivist"
                                                                          "digitizer"]
                                                         "753-YA"        ["authority" "approver"]
                                                         "998-R-TESTI-2" ["authority" "approver"]}
                                            "role"      "authority"
                                            "username"  "sonja"}
                                 "web"     {"client-ip"  "127.0.0.1"
                                            "host"       "http://localhost:8000"
                                            "method"     "get"
                                            "user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:124.0) Gecko/20100101 Firefox/124.0"}}
                    "file"      (fn [s] (str/ends-with? s "lupapalvelu/logging.clj"))
                    "hostname"  string?
                    "isEvent"   true
                    "line"      int?
                    "message"   "Query: allowed-actions"
                    "ns"        "lupapalvelu.actions-api"
                    "severity"  "info"
                    "thread"    string?
                    "timestamp" string?}))
        (fact "query action without namespace"
          (-> (with-out-str
                (log/log-event :info (dissoc action :ns)))
              json/decode)
          => (contains {"ns" "lupapalvelu.logging"}))
        (fact "different logging levels"
          (fact "query action resulting in an warning"
            (-> (with-out-str
                  (log/log-event :warning action))
                json/decode)
            => (contains {"severity" "warn"}))
          (fact "query action resulting in an error"
            (-> (with-out-str
                  (log/log-event :error action))
                json/decode)
            => (contains {"severity" "error"}))
          (fact "query action resulting in debug"
            (-> (with-out-str
                  (log/log-event :debug action))
                json/decode)
            => (contains {"severity" "debug"}))
          (fact "query action with an unknown logging level"
            (-> (with-out-str
                  (log/log-event :what-i-dont-even action))
                json/decode)
            => (contains {"severity" "warn"})))))))


(defcommand "mock-command"
  {:description "I exists for tests"
   :user-roles  #{:anonymous}}
  [_]
  (log/debug "Some test logging")
  (ok))

(defcommand "mock-fail-command"
  {:description "I exists for tests"
   :user-roles  #{:anonymous}}
  [_]
  (fail :error.intentional))

(defcommand "mock-fail-throw-command"
  {:description "I exists for tests"
   :user-roles  #{:anonymous}}
  [_]
  (fail! :error.intentional))

(defcommand "mock-fail-precheck-command"
  {:description "I exists for tests"
   :user-roles  #{:anonymous}
   :pre-checks  [(constantly (fail :error.precheck))]}
  [_]
  (ok))

(facts "action json event output"
  (against-background [(lockdown/lockdown?) => false]
    (reset-logging-config
      (env/with-feature-value :log-json true
        ;; Reset logging setup using JSON
        ;; .. but don't pretty print, makes it easier to split individual JSON objects
        (log/setup-logging! {:pretty?       false
                             :min-log-level :debug})
        (fact "executing mock command"
          (let [user               {:email "mock.user@example.com"
                                    :id    (rand-int 50)}
                request            (-> (mock/request :post "/api/command/mock-command")
                                       (assoc :user user :system {}))
                out                (with-out-str
                                     (api-common/execute-command "mock-command" {} request))
                messages           (->> (ss/split out #"\n")
                                        (mapv json/decode))
                event?             (fn [{:strs [isEvent]}] (true? isEvent))
                [event :as events] (filter event? messages)
                [other]            (->> messages
                                        (remove event?)
                                        (filter (fn [{:strs [message]}]
                                                  (= "Some test logging" message))))]
            (fact "single event found"
              (count events) => 1)
            (fact "event is ok"
              event => (just {"action"      "mock-command"
                              "action-type" "command"
                              "context"     {"action"      "mock-command"
                                             "action-type" "command"
                                             "userId"      (:id user)}
                              "event"       (just {"action"  "mock-command"
                                                   "created" int?
                                                   "lang"    nil
                                                   "session" nil
                                                   "type"    "command"
                                                   "user"    {"email" (:email user)
                                                              "id"    (:id user)
                                                              "role"  nil}
                                                   "web"     {"client-ip"  "127.0.0.1"
                                                              "host"       "http://localhost"
                                                              "method"     "post"
                                                              "user-agent" ""}})
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/logging.clj"))
                              "hostname"    string?
                              "isEvent"     true
                              "line"        int?
                              "message"     "Command: mock-command"
                              "ns"          "lupapalvelu.logging-test"
                              "severity"    "info"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))
            ;; Executing this test action also does some basic logging
            (fact "other logging"
              other => (just {"action"      "mock-command"
                              "action-type" "command"
                              "args"        map?
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/logging_test.clj"))
                              "hostname"    string?
                              "line"        int?
                              "message"     "Some test logging"
                              "ns"          "lupapalvelu.logging-test"
                              "severity"    "debug"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))))
        (fact "executing mock command which fails"
          (let [user               {:email "mock.user@example.com"
                                    :id    (rand-int 50)}
                request            (-> (mock/request :post "/api/command/mock-fail-command")
                                       (assoc :user user :system {}))
                out                (with-out-str
                                     (api-common/execute-command "mock-fail-command" {} request))
                messages           (->> (ss/split out #"\n")
                                        (mapv json/decode))
                event?             (fn [{:strs [isEvent]}] (true? isEvent))
                [event :as events] (filter event? messages)]
            (fact "single event found"
              (count events) => 1)
            (fact "event indicates a warning"
              event => (just {"action"      "mock-fail-command"
                              "action-type" "command"
                              "context"     {"action"      "mock-fail-command"
                                             "action-type" "command"
                                             "userId"      (:id user)}
                              "event"       (just {"action"  "mock-fail-command"
                                                   "created" int?
                                                   "lang"    nil
                                                   "session" nil
                                                   "type"    "command"
                                                   "user"    {"email" (:email user)
                                                              "id"    (:id user)
                                                              "role"  nil}
                                                   "web"     {"client-ip"  "127.0.0.1"
                                                              "host"       "http://localhost"
                                                              "method"     "post"
                                                              "user-agent" ""}})
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/logging.clj"))
                              "hostname"    string?
                              "isEvent"     true
                              "line"        int?
                              "message"     "Command: mock-fail-command"
                              "ns"          "lupapalvelu.logging-test"
                              ;; fail (but no exception) results in a warning
                              "severity"    "warn"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))))
        (fact "executing mock command which fails due to pre-checks"
          (let [user               {:email "mock.user@example.com"
                                    :id    (rand-int 50)}
                request            (-> (mock/request :post "/api/command/mock-fail-precheck-command")
                                       (assoc :user user :system {}))
                out                (with-out-str
                                     (api-common/execute-command "mock-fail-precheck-command" {} request))
                messages           (->> (ss/split out #"\n")
                                        (mapv json/decode))
                event?             (fn [{:strs [isEvent]}] (true? isEvent))
                [event :as events] (filter event? messages)
                [other]            (->> messages
                                        (filter (fn [{:strs [message]}]
                                                  (str/starts-with? message "Pre-checks failed"))))]
            (fact "single event found"
              (count events) => 1)
            (fact "event indicates a warning"
              event => (just {"action"      "mock-fail-precheck-command"
                              "action-type" "command"
                              "context"     {"action"      "mock-fail-precheck-command"
                                             "action-type" "command"
                                             "userId"      (:id user)}
                              "event"       (just {"action"  "mock-fail-precheck-command"
                                                   "created" int?
                                                   "lang"    nil
                                                   ;; Indicate that something went wrong during pre-checks
                                                   "phase"   "pre-checks"
                                                   "session" nil
                                                   "type"    "command"
                                                   "user"    {"email" (:email user)
                                                              "id"    (:id user)
                                                              "role"  nil}
                                                   "web"     {"client-ip"  "127.0.0.1"
                                                              "host"       "http://localhost"
                                                              "method"     "post"
                                                              "user-agent" ""}})
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/logging.clj"))
                              "hostname"    string?
                              "isEvent"     true
                              "line"        int?
                              "message"     "Command: mock-fail-precheck-command"
                              "ns"          "lupapalvelu.logging"
                              ;; fail (but no exception) results in a warning
                              "severity"    "warn"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))
            (fact "failure reason is logged"
              other => (just {"action"      "mock-fail-precheck-command"
                              "action-type" "command"
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/action.clj"))
                              "hostname"    string?
                              "line"        int?
                              "message"     "Pre-checks failed: {:ok false, :text \"error.precheck\"}"
                              "ns"          "lupapalvelu.action"
                              "severity"    "warn"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))))
        (fact "executing mock command which throws"
          (let [user               {:email "mock.user@example.com"
                                    :id    (rand-int 50)}
                request            (-> (mock/request :post "/api/command/mock-fail-throw-command")
                                       (assoc :user user :system {}))
                out                (with-out-str
                                     (api-common/execute-command "mock-fail-throw-command" {} request))
                messages           (->> (ss/split out #"\n")
                                        (mapv json/decode))
                event?             (fn [{:strs [isEvent]}] (true? isEvent))
                [event :as events] (filter event? messages)
                [other]            (->> messages
                                        (filter (fn [{:strs [message]}]
                                                  (str/starts-with? message "fail! in action:"))))]
            (fact "single event found"
              (count events) => 1)
            (fact "event indicates an error"
              event => (just {"action"      "mock-fail-throw-command"
                              "action-type" "command"
                              "context"     {"action"      "mock-fail-throw-command"
                                             "action-type" "command"
                                             "userId"      (:id user)}
                              "event"       (just {"action"  "mock-fail-throw-command"
                                                   "created" int?
                                                   "lang"    nil
                                                   "session" nil
                                                   "type"    "command"
                                                   "user"    {"email" (:email user)
                                                              "id"    (:id user)
                                                              ;; note that "role" nil is absent here
                                                              }
                                                   "web"     {"client-ip"  "127.0.0.1"
                                                              "host"       "http://localhost"
                                                              "method"     "post"
                                                              "user-agent" ""}})
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/logging.clj"))
                              "hostname"    string?
                              "isEvent"     true
                              "line"        int?
                              "message"     "Command: mock-fail-throw-command"
                              ;; no ns is provided for logging if an action fails due to an exception
                              "ns"          "lupapalvelu.logging"
                              ;; throwing results in an error
                              "severity"    "error"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))
            (fact "logging message indicates problem"
              other => (just {"action"      "mock-fail-throw-command"
                              "action-type" "command"
                              "file"        (fn [s] (str/ends-with? s "lupapalvelu/action.clj"))
                              "hostname"    string?
                              "line"        int?
                              "message"     (fn [m]
                                              (and (str/starts-with? m "fail! in action: \"mock-fail-throw-command\"")
                                                   (str/ends-with? m "error.intentional ({:ok false})")))
                              "ns"          "lupapalvelu.action"
                              "severity"    "error"
                              "thread"      string?
                              "timestamp"   string?
                              "userId"      (:id user)}))))))))
