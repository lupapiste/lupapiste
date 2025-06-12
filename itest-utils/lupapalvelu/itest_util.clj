(ns lupapalvelu.itest-util
  "Utilities for writing integration tests."
  (:require [clj-http.conn-mgr]
            [clj-ssh.cli :as ssh-cli]
            [clojure.data.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [lupapalvelu.action :refer [*created-timestamp-for-test-actions*]]
            [lupapalvelu.api-common :as api-common]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.cookie :as c]
            [lupapalvelu.document.document-api]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :as log]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.server]
            [lupapalvelu.sftp.client :as sftp]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xml.validator :refer [validate]]
            [malli.dev :as malli-dev]
            [malli.dev.pretty :as malli-pretty]
            [midje.config :as midje-config]
            [midje.sweet :refer :all]
            [noir.request :refer [*request*]]
            [ring.util.codec :as codec]
            [sade.core :refer [def- now]]
            [sade.dummy-email-server]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]])
  (:import [com.jcraft.jsch ChannelSftp$LsEntry]
           [java.io File]
           [org.apache.http.client CookieStore]
           [org.apache.http.cookie Cookie]))

(set! *warn-on-reflection* true)

;;;; General Constants
;;;; ===================================================================================================================

(def- dev-password "Lupapiste")

(defonce test-db-name (str "test_" (now)))

;;;; Minimal Fixture Shortcuts
;;;; ==========================

(def kaino min-util/kaino)
(def kaino-id min-util/kaino-id)
(def erkki min-util/erkki)
(def erkki-id min-util/erkki-id)
(def pena min-util/pena)
(def pena-id min-util/pena-id)
(def mikko min-util/mikko)
(def mikko-id min-util/mikko-id)
(def teppo min-util/teppo)
(def teppo-id min-util/teppo-id)
(def sven min-util/sven)
(def sven-id min-util/sven-id)
(def veikko min-util/veikko)
(def veikko-id min-util/veikko-id)
(def sonja min-util/sonja)
(def sonja-id min-util/sonja-id)
(def sonja-muni min-util/sonja-muni)
(def ronja min-util/ronja)
(def ronja-id min-util/ronja-id)
(def luukas min-util/luukas)
(def luukas-id min-util/luukas-id)
(def kosti min-util/kosti)
(def laura min-util/laura)
(def laura-id min-util/laura-id)
(def sipoo min-util/sipoo)
(def sipoo-ya min-util/sipoo-ya)
(def tampere min-util/tampere)
(def tampere-ya min-util/tampere-ya)
(def naantali min-util/naantali)
(def lupasihteeri-naantali min-util/lupasihteeri-naantali)
(def oulu min-util/oulu)
(def dummy min-util/dummy)
(def admin min-util/admin)
(def admin-id min-util/admin-id)
(def raktark-jarvenpaa min-util/raktark-jarvenpaa)
(def raktark-jarvenpaa-id min-util/raktark-jarvenpaa-id)
(def lupasihteeri-jarvenpaa min-util/lupasihteeri-jarvenpaa)
(def kuopio min-util/kuopio)
(def velho min-util/velho)
(def velho-muni min-util/velho-muni)
(def jarvenpaa min-util/jarvenpaa)
(def olli min-util/olli)
(def olli-id min-util/olli-id)
(def raktark-helsinki min-util/raktark-helsinki)
(def jussi min-util/jussi)
(def jussi-id min-util/jussi-id)
(def digitoija min-util/digitoija)
(def torsti min-util/torsti)
(def vantaa-ya min-util/vantaa-ya)
(def esa min-util/esa)
(def esa-id min-util/esa-id)
(def digitization-project-user min-util/digitization-project-user)
(def vantaa-r-biller min-util/vantaa-r-biller)
(def arto min-util/arto)
(def pori min-util/pori)
(def priscilla min-util/priscilla)
(def priscilla-id min-util/priscilla-id)

(def sipoo-property-id "75300000000000")
(def jarvenpaa-property-id "18600000000000")
(def tampere-property-id "83700000000000")
(def vantaa-property-id "09200000000000")
(def kuopio-property-id "29700000000000")
(def oir-property-id "43300000000000")
(def oulu-property-id "56400000000000")
(def no-backend-property-id oulu-property-id)

(def sipoo-general-handler-id "abba1111111111111111acdc")
(def jarvenpaa-general-handler-id "abba11111111111111111186")
(def sipoo-ya-general-handler-id "abba1111111111111111b753")
(def vantaa-ya-general-handler-id "abba1111111111111111a092")

(defn organization-from-minimal-by-id
  [org-id]
  (let [port (-> (test-system/get-common-components)
                 components/system->http-port)]
    (some #(when (= (:id %) org-id) %) (minimal/organizations {:http-port port}))))

;;;; Environment Inspection
;;;; =======================

(def server-address test-system/get-server-address)

(def dev-env? env/dev-env?)

(def get-files-from-sftp-server? dev-env?)

;;;; HTTP Response JSON Decoding
;;;; ============================

(defn decode-response [resp]
  (update resp :body json/decode true))

(defn ^:private check-content-type
  [{:keys [headers] :as req}]
  (midje-config/at-print-level :print-normally
    (fact "expect response is JSON"
      (get headers "Content-Type") => "application/json;charset=utf-8"))
  req)

(defn stream-decoding-response [http-fn uri request]
  (-> (http-fn uri (assoc request :as :stream))
      check-content-type
      (update :body (fn [body] (with-open [body (io/reader body)]
                                 (json/decode-stream body true))))))

(defn decode-body [resp]
  (json/decode (:body resp) true))

(defn stream-decoding-body [http-fn uri request]
  (let [{:keys [body]} (http-fn uri (assoc request :as :stream))]
    (with-open [body (io/reader body)]
      (json/decode-stream body true))))

;;;; HTTP Client Cookie Store
;;;; =========================

(defn ->cookie-store ^CookieStore [store]
  (proxy [CookieStore] []
    (getCookies [] (or (vals @store) []))
    (addCookie [^Cookie cookie] (swap! store assoc (.getName cookie) cookie))
    (clear [] (reset! store {}))
    (clearExpired [_date] false)))

(def test-db-cookie (c/->lupa-cookie "test_db_name" test-db-name))

(defn get-anti-csrf [store-atom]
  (when-let [^Cookie cookie (get @store-atom "anti-csrf-token")]
    (-> cookie .getValue codec/url-decode)))

;;;; HTTP Client Wrappers
;;;; =====================

(defn http [verb url options]
  (let [store                (atom {})
        ^CookieStore cookies (or (:cookie-store options) (->cookie-store store))]
    (.addCookie cookies (if (:test-db-name options)
                          (c/->lupa-cookie "test_db_name" (:test-db-name options))
                          test-db-cookie))
    (verb url (assoc options :cookie-store cookies))))

(def http-get (partial http http/get))
(def http-post (partial http http/post))
(def http-put (partial http http/put))
(def http-delete (partial http http/delete))

;;;; Using Actions
;;;; ==============

(defprotocol LupisClient
  (-query [self apikey query-name args])
  (-command [self apikey command-name args])
  (-raw [self apikey action-name args])
  (-upload-file [self apikey action-name file cookie-store]))

;;;; Remotely
;;;; ---------

(defn raw [apikey action & args]
  (let [params  (apply hash-map args)
        options (util/assoc-when {:oauth-token        apikey
                                  :query-params       (dissoc params :as :cookie-store)
                                  :throw-exceptions   false
                                  :redirect-strategy  :none
                                  :cookie-store       (:cookie-store params)
                                  :connection-manager (test-system/get-connection-manager)}
                                 :as (:as params))]
    (http-get (str (server-address) "/api/raw/" (name action)) options)))

(defn raw-query [apikey query-name & args]
  (let [params (apply hash-map args)]
    (stream-decoding-response http-get
                              (str (server-address) "/api/query/" (name query-name))
                              {:headers            {"accepts" "application/json;charset=utf-8"}
                               :oauth-token        apikey
                               :query-params       (dissoc params :as :cookie-store)
                               :redirect-strategy  :none
                               :cookie-store       (:cookie-store params)
                               :throw-exceptions   false
                               :connection-manager (test-system/get-connection-manager)})))

(defn decoded-get [url params]
  (stream-decoding-response http-get url (assoc params :connection-manager (test-system/get-connection-manager))))

(defn decoded-simple-post [url params]
  (stream-decoding-response http-post url (assoc params :connection-manager (test-system/get-connection-manager))))

(defn ->arg-map [args]
  (if (map? (first args))
    (first args)
    (apply hash-map args)))

(defn decode-post [action-type apikey command-name & args]
  (stream-decoding-response http-post (str (server-address) "/api/" (name action-type) "/" (name command-name))
                            (let [args         (->arg-map args)
                                  cookie-store (:cookie-store args)
                                  test-db-name (:test-db-name args)
                                  args         (dissoc args :cookie-store :test-db-name)]
                              (util/assoc-when
                                {:headers            {"content-type" "application/json;charset=utf-8"}
                                 :body               (json/encode args)
                                 :redirect-strategy  :none
                                 :cookie-store       cookie-store
                                 :test-db-name       test-db-name
                                 :throw-exceptions   false
                                 :connection-manager (test-system/get-connection-manager)}
                                :oauth-token apikey))))

(defn raw-command [apikey command-name & args]
  (apply decode-post :command apikey command-name args))

(defn datatables [apikey query-name & args]
  (let [{status :status body :body} (apply decode-post :datatables apikey query-name args)]
    (when (= status 200)
      body)))

(deftype RemoteLupisClient []
  LupisClient
  (-query [_ apikey query-name args]
    (let [{status :status body :body} (apply raw-query apikey query-name args)]
      (when (= status 200)
        body)))

  (-command [_ apikey command-name args]
    (let [{status :status body :body} (apply raw-command apikey command-name args)]
      (if (= status 200)
        body
        (log/error status body))))

  (-raw [_ apikey action-name args]
    (let [{status :status body :body} (apply raw apikey action-name args)]
      (if (= status 200)
        body
        (log/error status body))))

  (-upload-file [_ apikey action-name file cookie-store]
    (stream-decoding-response http-post (str (server-address) "/api/raw/" (name action-name))
                              {:oauth-token      apikey
                               :multipart        [{:name "files[]" :content file}]
                               :throw-exceptions false
                               :cookie-store     cookie-store})))

(def- remote-client-instance (->RemoteLupisClient))

;;;; Locally
;;;; --------

(defn- make-local-request [apikey]
  (let [fname (or fixture/*fixture* :minimal)]
    {:scheme "http"
     :user   (usr/session-summary (fixture/find-user-by-apikey fname apikey))
     :system (test-system/get-common-components)}))

(defn- execute-local [apikey web-fn action & args]
  (let [params (->arg-map args)]
    (i18n/with-lang (or (:lang params)
                        ;; HACK: This is a workaround if we want the lang only to the command but not to the data params.
                        (:with-lang params))
      (binding [*request* (make-local-request apikey)]
        (web-fn (name action) (dissoc params :with-lang) *request*)))))

(defn local-query-with-timestamp [ts apikey query-name & args]
  (binding [*created-timestamp-for-test-actions* ts]
    (apply execute-local apikey api-common/execute-query query-name args)))

(deftype LocalLupisClient []
  LupisClient
  (-query [_ apikey query-name args] (apply execute-local apikey api-common/execute-query query-name args))
  (-command [_ apikey command-name args] (apply execute-local apikey api-common/execute-command command-name args))
  (-raw [_ apikey action-name args] (apply execute-local apikey api-common/execute-raw action-name args))
  (-upload-file [_ apikey action-name file cookie-store]
    (execute-local apikey api-common/execute-raw action-name
                   :files [{:filename     (.getName ^File file)
                            :content-type "application/octet-stream"
                            :tempfile     file
                            :size         (.length ^File file)}]
                   :cookie-store cookie-store)))

(def- local-client-instance (->LocalLupisClient))

;;;; High-level Action Usage
;;;; ------------------------

(def- ^:dynamic *lupis-client* remote-client-instance)

(defmacro with-local-actions [& body]
  `(binding [*lupis-client* @#'local-client-instance]
     ~@body))

(defn query [apikey query-name & args]
  (-query *lupis-client* apikey query-name args))

(defn local-query [apikey query-name & args]
  (-query local-client-instance apikey query-name args))

(defn local-raw [apikey action-name & args]
  (-raw local-client-instance apikey action-name args))

(defn command [apikey command-name & args]
  (-command *lupis-client* apikey command-name args))

(defn local-command [apikey command-name & args]
  (-command local-client-instance apikey command-name args))

;;;; Using Dev HTTP APIs
;;;; ====================

(defn feature? [feature]
  (-> (query pena :features)
      :features
      (get feature)
      boolean))

(defn sleep-ms
  "Sleeps for `ms` milliseconds."
  [^long ms]
  (Thread/sleep ms))

(defn- pubsub?
  "True if pubsub integration message queue is in use."
  []
  ;; TODO: should really check the test system config
  (and (env/feature? :integration-message-queue)
       (= (env/value :integration-message-queue) "pubsub")))

(defn pubsub-wait
  "Sleeps for `seconds` seconds if pubsub is in use."
  ([seconds]
   (when (pubsub?)
     (log/debugf "Pubsub in use. Wait for %d seconds." seconds)
     (sleep-ms (* seconds 1000))))
  ([] (pubsub-wait 5)))

(defn get-by-id [collection id & args]
  (stream-decoding-response http-get (str (server-address) "/dev/by-id/" (name collection) "/" id)
                            (apply hash-map args)))

(defn integration-messages [app-id & args]
  (:data (stream-decoding-body http-get (str (server-address) "/dev/integration-messages/" app-id)
                               (apply hash-map args))))

(defn clear-collection [collection]
  (let [resp (stream-decoding-response http-get (str (server-address) "/dev/clear/" collection) {})]
    (assert (-> resp :body :ok) (str "Response not ok: clearing collection: \"" collection
                                     "\": response: " (pr-str resp)))))

;;;; Use Action Prechecks
;;;; =====================

(defn allowed?
  "Returns true if the given action is allowed for the given user,
  the error keyword otherwise"
  [apikey action & {:as args}]
  (let [{error :text action-ok :ok} (query apikey :allowed-action :action (name action) :args (or args {}))]
    (or action-ok
        (keyword error))))

;;;; Test predicates
;;;; ================

(defn success [resp]
  (midje-config/at-print-level :print-normally
    (fact (:text resp) => nil))
  (:ok resp))

(defn invalid-csrf-token? [{:keys [status body]}]
  (and
    (= status 403)
    (= (:ok body) false)
    (= (:text body) "error.invalid-csrf-token")))

(fact "invalid-csrf-token?"
  (invalid-csrf-token? {:status 403 :body {:ok false :text "error.invalid-csrf-token"}}) => true
  (invalid-csrf-token? {:status 403 :body {:ok false :text "error.SOME_OTHER_REASON"}}) => false
  (invalid-csrf-token? {:status 200 :body {:ok true}}) => false)

;; ------------------------
;; Convenience shortcuts
;; ------------------------

(def unauthorized? test-util/unauthorized?)
(def not-accessible? test-util/not-accessible?)
(def missing-parameters test-util/missing-parameters?)
(def schema-error? test-util/schema-error?)
(def err test-util/err)
(def midje? test-util/midje?)

(def expected-failure? test-util/expected-failure?)
(def in-text test-util/in-text)
(def dummy-doc test-util/dummy-doc)
(def replace-in-schema test-util/replace-in-schema)

(defn in-state? [state]
  (fn [application] (= (:state application) (name state))))

(fact "in-state?"
  ((in-state? :open) {:state "open"}) => true
  ((in-state? :open) {:state "closed"}) => false)

(def ok? test-util/ok?)
(def fail? test-util/fail?)

(fact "ok?"
  (ok? {:ok true}) => true
  (ok? {:ok false}) => false)

(defn http200? [{:keys [status]}]
  (= status 200))

(defn http302? [{:keys [status]}]
  (= status 302))

(defn http303? [{:keys [status]}]
  (= status 303))

(defn http400? [{:keys [status]}]
  (= status 400))

(defn http401? [{:keys [status]}]
  (= status 401))

(defn http404? [{:keys [status]}]
  (= status 404))

(defn redirects-to [to {headers :headers :as resp}]
  (and (http302? resp) (ss/ends-with? (headers "location") to)))

(defn pdf-response? [{:keys [body headers]} & [additional-headers]]
  (midje-config/at-print-level :print-normally
    (fact "Body contains a PDF-like response"
      (some? (re-find #"^%PDF" body)) => true)
    (fact "Headers contain at least PDF content type"
      headers => (contains (merge {"Content-Type" "application/pdf"}
                                  additional-headers)))))

(defn fails-with?
  "True if the `_result` is a fail (`:ok` false) with error (`:text`) `error`."
  [error {:keys [ok text] :as _result}]
  (and (not ok) (util/=as-kw error text)))

;;;; DSLs
;;;; =====

(defn remove-krysp-xml-overrides [apikey org-id permit-type & provided-args]
  (let [org  (organization-from-minimal-by-id org-id)
        args (select-keys (get-in org [:krysp permit-type]) [:url :version])
        args (assoc args :organizationId org-id :permitType permit-type :username "" :password "")]
    (command apikey :set-krysp-endpoint
             (merge args (->arg-map provided-args)))))

(defn override-krysp-xml [apikey org-id permit-type overrides & provided-args]
  (let [org         (organization-from-minimal-by-id org-id)
        current-url (get-in org [:krysp permit-type :url])
        new-url     (str current-url "?overrides=" (json/encode overrides))
        args        (select-keys (get-in org [:krysp permit-type]) [:version])
        args        (assoc args :organizationId org-id :permitType permit-type :url new-url :username "" :password "")]
    (command apikey :set-krysp-endpoint
             (merge args (->arg-map provided-args)))))

;;;; Anti-CSRF
;;;; ==========

(defn anti-csrf? []
  (not (feature? :disable-anti-csrf)))

(defn set-anti-csrf! [value]
  (midje-config/at-print-level
    :print-normally
    (fact "set-anti-csrf"
      (let [resp (command pena :set-feature :feature "disable-anti-csrf" :value (not value))]
        resp => ok?
        (try
          (when (not (ok? resp))
            (throw (ex-info "set-anti-csrf failed" {:resp  resp
                                                    :value value})))
          (catch Exception e
            (log/error e)))))))

(defmacro with-anti-csrf [& body]
  `(let [old-value# (feature? :disable-anti-csrf)]
     (set-anti-csrf! true)
     (try
       ~@body
       (finally (set-anti-csrf! (not old-value#))))))

(defmacro without-anti-csrf [& body]
  `(let [old-value# (feature? :disable-anti-csrf)]
     (set-anti-csrf! false)
     (try
       ~@body
       (finally (set-anti-csrf! (not old-value#))))))

;;;; Creating Applications
;;;; ======================

(def create-app-default-args {:operation  "kerrostalo-rivitalo"
                              :propertyId "75312312341234"
                              :x          444444 :y 6666666
                              :address    "foo 42, bar"})

(defn create-app-with-fn [f apikey & args]
  (let [params (->> (apply hash-map args)
                    (merge create-app-default-args)
                    (mapcat seq))]
    (apply f apikey :create-application params)))

;;;; Application Utils
;;;; ==================

(sc/defn comment-application
  ([apikey id] (comment-application apikey id false nil))
  ([apikey id open? :- sc/Bool] (comment-application apikey id open? nil))
  ([apikey id open? to]
   (command apikey :add-comment :id id :text "hello" :to to :target {:type "application"} :openApplication open?
            :roles [])))

(defn query-application
  "Fetch application from server.
   Asserts that application is found and that the application data looks sane.
   Takes an optional query function (query or local-query)"
  ([apikey id] (query-application query apikey id))
  ([f apikey id]
   {:pre  [apikey id]
    :post [(:id %)
           (:created %) (pos? (:created %))
           (:modified %) (pos? (:modified %))
           (contains? % :opened)
           (:permitType %)
           (contains? % :permitSubtype)
           (contains? % :infoRequest)
           (contains? % :openInfoRequest)
           (:primaryOperation %)
           (:secondaryOperations %)
           (:state %)
           (:municipality %)
           (:location %)
           (:organization %)
           (:address %)
           (:propertyId %)
           (:title %)
           (:auth %) (pos? (count (:auth %)))
           (:comments %)
           (:schema-version %)
           (:documents %)
           (:attachments %)
           (every? (fn [a] (or (empty? (:versions a)) (= (:latestVersion a) (last (:versions a))))) (:attachments %))]}
   (let [{:keys [application ok text]} (f apikey :application :id id)]
     (assert ok (str "not ok: " text))
     application)))

(defn query-bulletin
  "Fetch application bulletin from server.
   Asserts that bulletin is found and that the bulletin data looks sane.
   Takes an optional query function (query or local-query)"
  ([api apikey id] (query-bulletin query api apikey id))
  ([f api apikey id]
   {:pre  [id]
    :post [(:id %)
           (:modified %) (pos? (:modified %))
           (:primaryOperation %)
           (:state %)
           (:bulletinState %)
           (:municipality %)
           (:location %)
           (:address %)
           (:propertyId %)
           (every? (fn [a] (or (empty? (:versions a)) (= (:latestVersion a) (last (:versions a))))) (:attachments %))]}
   (let [{:keys [bulletin ok text]} (f apikey api :bulletinId id)]
     (assert ok (str "not ok: " text))
     bulletin)))

(defn ^:private test-application-create-successful [resp app-id]
  (midje-config/at-print-level :print-normally
    (fact "Application created"
      resp => ok?
      app-id => truthy)))

(defn create-app
  "Runs the create-application command, returns reply map. Use ok? to check it."
  [apikey & args]
  (apply create-app-with-fn command apikey args))

(defn create-app-id
  "Verifies that an application was created and returns it's ID"
  [apikey & args]
  {:post [%]}
  (let [{app-id :id :as resp} (apply create-app apikey args)]
    (test-application-create-successful resp app-id)
    app-id))

(defn create-application
  "Runs the create-application command, returns application."
  [apikey & args]
  (let [{app-id :id :as resp} (apply create-app apikey args)]
    (test-application-create-successful resp app-id)
    (query-application apikey app-id)))

(defn create-and-open-application
  "Creates a new application, opens it, and returns the application map"
  [apikey & args]
  (let [id (apply create-app-id apikey args)]
    (comment-application apikey id true)
    (query-application apikey id)))

(defn create-and-submit-application
  "Returns the application map"
  [apikey & args]
  (midje-config/at-print-level :print-normally
    (let [id   (apply create-app-id apikey args)
          resp (command apikey :submit-application :id id)]
      (fact "Submit OK" resp => ok?)
      (query-application apikey id))))

(defn create-and-send-application
  "Returns the application map"
  [apikey & args]
  (midje-config/at-print-level :print-normally
    (let [id   (apply create-app-id apikey args)
          _    (command apikey :submit-application :id id)
          _    (command apikey :update-app-bulletin-op-description :id id :description "otsikko julkipanoon")
          resp (command apikey :approve-application :id id :lang "fi")]
      (fact "Submit OK" resp => ok?)
      (query-application apikey id))))

(defn set-application-submitted
  "Change application submission date in test.

  Alternatively you can use `change-bulletin-process` command to choose whether
  to follow julkipano- or kuulutusmenettely."
  [app-id submitted]
  (let [{:keys [headers body]} (http-post (format "%s/dev/set-application-timestamp/%s/submitted/%s"
                                                  (server-address) app-id submitted) {})
        json?                  (ss/starts-with-i? (get headers "Content-Type")
                                                  "application/json")]
    (cond-> body
      json? (json/decode keyword))))

;;;; Local Application Utils
;;;; ========================

(defn create-local-app
  "Runs the create-application command locally, returns reply map. Use ok? to check it."
  [apikey & args]
  (apply create-app-with-fn local-command apikey args))

(defn create-and-submit-local-application
  "Returns the application map"
  [apikey & args]
  (midje-config/at-print-level :print-normally
    (let [id (:id (apply create-local-app apikey args))]
      (fact "Submit OK" (local-command apikey :submit-application :id id) => ok?)
      (query-application local-query apikey id))))

;;;; Email Mock Usage
;;;; =================

(defn last-email
  "Returns the last email (or nil) and clears the inbox"
  ([] (last-email true))
  ([reset]
   {:post [(or (nil? %)
               (and (:to %) (:subject %) (not (.contains ^String (:subject %) "???"))
                    (-> % :body :html))
               (println %))]}
   (Thread/sleep 20) ; A little wait to allow mails to be delivered
   (let [{:keys [ok message]} (query pena :last-email :reset reset)] ; query with any user will do
     (assert ok)
     message)))

(defn sent-emails
  "Returns a list of emails and clears the inbox"
  []
  (Thread/sleep 20) ; A little wait to allow mails to be delivered
  (let [{:keys [ok messages]} (query admin :sent-emails :reset true)] ; query with any user will do
    (assert ok)
    messages))

(defn contains-application-link? [application-id role {body :body}]
  (let [[_ r a-id] (re-find #"(?sm)http.+/app/fi/(applicant|authority)#!/application/([A-Za-z0-9-]+)" (:plain body))]
    (and (= role r) (= application-id a-id))))

(defn contains-application-link-with-tab? [application-id tab role {body :body}]
  (let [[_ r a-id a-tab] (re-find #"(?sm)http.+/app/fi/(applicant|authority)#!/application/([A-Za-z0-9-]+)/([a-z]+)"
                                  (:plain body))]
    (and (= role r) (= application-id a-id) (= tab a-tab))))

;;;; Foreman Applications
;;;; =====================

(defn create-foreman-application [project-app-id apikey userId role difficulty]
  (let [{foreman-app-id :id} (command apikey :create-foreman-application :id project-app-id :taskId "" :foremanRole role
                                      :foremanEmail "")
        foreman-app (query-application apikey foreman-app-id)
        foreman-doc (domain/get-document-by-name foreman-app "tyonjohtaja-v2")]
    (command apikey :set-user-to-document :id foreman-app-id :documentId (:id foreman-doc) :userId userId :path "")
    (command apikey :update-doc :id foreman-app-id :doc (:id foreman-doc)
             :updates [["patevyysvaatimusluokka" difficulty]])
    foreman-app-id))

(defn default-foreman-updates [& {:keys [difficulty]}]
  [["patevyysvaatimusluokka" (or difficulty "A")]
   ["henkilotiedot.hetu" "010101-0101"]
   ["vastattavatTyotehtavat.sisapuolinenKvvTyo" true]
   ["patevyys-tyonjohtaja.valmistumisvuosi" "1942"]
   ["patevyys-tyonjohtaja.valvottavienKohteidenMaara" "42"]
   ["patevyys-tyonjohtaja.kokemusvuodet" "42"]
   ["patevyys-tyonjohtaja.koulutusvalinta" "insinööri"]])

(defn create-and-invite-foreman [project-app-id apikey foremanUser role difficulty]
  (midje-config/at-print-level :print-normally
    (facts "create-and-invite-foreman"
      (command apikey :create-and-invite-foreman-by-id :id project-app-id :foremanUser foremanUser :foremanRole role) => ok?
      (let [app         (query-application apikey project-app-id)
            foreman-doc (domain/get-document-by-name app "tyonjohtaja-v3")]
        (command apikey :set-user-to-document :id project-app-id :documentId (:id foreman-doc) :userId foremanUser :path "") => ok?
        (command apikey :update-doc :id project-app-id :doc (:id foreman-doc) :updates (default-foreman-updates :difficulty difficulty)) => ok?
        (command apikey :foreman-consent :id project-app-id :foreman-doc-id (:id foreman-doc) :consent true)
        => ok?))))

(defn finalize-foreman-app
  [apikey authority foreman-app-id application?]
  (midje-config/at-print-level :print-normally
    (facts "Finalize foreman application"
      (command apikey :change-permit-sub-type :id foreman-app-id
               :permitSubtype (if application? "tyonjohtaja-hakemus" "tyonjohtaja-ilmoitus")) => ok?
      (command apikey :submit-application :id foreman-app-id) => ok?
      (if application?
        (command authority :check-for-verdict :id foreman-app-id)
        (command authority :approve-application :lang :fi :id foreman-app-id)) => ok?)))

;;;; Invitation Tokens
;;;; ==================

(defn invite-company-and-accept-invitation [apikey app-id company-id company-admin-apikey]
  (command apikey :company-invite :id app-id :company-id company-id) => ok?
  (command company-admin-apikey :approve-invite :id app-id :invite-type :company))

(defn http-token-call
  ([token body] (let [url (str (server-address) "/api/token/" token)]
                  (http-post url {:redirect-strategy :none
                                  :throw-exceptions false
                                  :content-type     :json
                                  :body             (json/encode body)})))
  ([token]
   (midje-config/at-print-level
     :print-normally
     (fact "Call api/token"
       (http-token-call token {:ok true}) => (contains {:status 200})))))

(sc/defn token-from-email
  ([email] (token-from-email email (last-email)))
  ([email :- ssc/NonBlankStr email-data]
   (midje-config/at-print-level
     :print-normally
     (fact {:midje/description (str "Read email for " email)}
       (s/index-of (:to email-data) email) => (complement neg?)))
   (->> email-data :body :plain (re-find #"http.+/app/fi/welcome#!/.+/([A-Za-z0-9-]+)") last)))

(sc/defn activation-email->token [email-address :- ssc/NonBlankStr email]
  (midje-config/at-print-level
    :print-normally
    (fact {:midje/description (str "Read email for " email-address)}
      (s/index-of (:to email) email-address) => (complement neg?)))
  (->> email :body :plain (re-find #"http.+/app/security/activate/([A-Za-z0-9-]+)") last))

;;;; Login
;;;; ======

(defn login
  ([u p] (login u p {}))
  ([u p params] (stream-decoding-body http-post (str (server-address) "/api/login")
                                      (merge
                                        {:redirect-strategy :none
                                         :throw-exceptions false
                                         :form-params      {:username u :password p}}
                                        params))))

;;;; VTJ-PRT
;;;; ========

(defn api-update-building-data-call [application-id params]
  (http-post (format "%s/rest/application/%s/update-building-data" (server-address) application-id)
             (assoc params :throw-exceptions false)))

;;;; Attachments
;;;; ============

(defn sign-attachment [apikey id attachmentId password]
  (midje-config/at-print-level :print-normally
    (let [uri  (str (server-address) "/api/command/sign-attachments")
          resp (http-post uri
                          {:headers          {"content-type" "application/json;charset=utf-8"}
                           :oauth-token      apikey
                           :body             (json/encode {:id            id
                                                           :attachmentIds [attachmentId]
                                                           :password      password})
                           :redirect-strategy :none
                           :throw-exceptions false})]
      (facts "Signed succesfully"
        (fact "Status code" (:status resp) => 200)))))

(defn upload-attachment
  "Returns attachment ID"
  [apikey application-id {attachment-id :id attachment-type :type operation-id :op-id} expect-to-succeed
   & {:keys [filename text] :or {filename "dev-resources/test-gif-attachment.gif", text ""}}]
  (midje-config/at-print-level :print-normally
    (let [uploadfile (io/file filename)
          ;; FIXME replace deprecated endpoint
          uri        (str (server-address) "/api/upload/attachment")
          resp       (http-post uri
                                {:oauth-token      apikey
                                 :throw-exceptions false
                                 :multipart        (remove nil?
                                                           [{:name "applicationId" :content application-id}
                                                            {:name "text" :content text}
                                                            {:name "Content/type" :content "image/gif"}
                                                            {:name    "attachmentType"
                                                             :content (str (get attachment-type :type-group "muut") "."
                                                                           (get attachment-type :type-id "muu"))}
                                                            {:name "operationId" :content (or operation-id "")}
                                                            (when attachment-id {:name "attachmentId" :content attachment-id})
                                                            {:name "upload" :content uploadfile}])})
          [_ parsed-id] (re-find #"Upload OK, id: (\S+)" (:body resp))]
      (if expect-to-succeed
        (and
          (facts "Upload succesfully"
            (fact "Status code" (:status resp) => 200))
          (fact "Parsed id matches"
            (when attachment-id
              parsed-id => attachment-id))
          ; Return the attachment id, can be new or existing attachment
          parsed-id)
        (and
          (facts "Upload should fail"
            (fact "Status code" (:status resp) => 400))
          ; Return the original id
          attachment-id)))))

(defn upload-attachment-to-target
  "Returns attachment ID"
  [apikey application-id attachment-id expect-to-succeed? target-id target-type & [attachment-type]]
  {:pre [target-id target-type]}
  (midje-config/at-print-level :print-normally
    (let [filename   "dev-resources/test-attachment.txt"
          uploadfile (io/file filename)
          uri        (str (server-address) "/api/upload/attachment")
          resp       (http-post uri
                                {:oauth-token apikey
                                 :multipart   (remove nil?
                                                      [{:name "applicationId" :content application-id}
                                                       {:name "Content/type" :content "text/plain"}
                                                       {:name "attachmentType" :content (or attachment-type "muut.muu")}
                                                       (when attachment-id {:name "attachmentId" :content attachment-id})
                                                       {:name "upload" :content uploadfile}
                                                       {:name "targetId" :content target-id}
                                                       {:name "targetType" :content target-type}])})
          [_ parsed-id] (re-find #"Upload OK, id: (\S+)" (:body resp))]
      (if expect-to-succeed?
        (do
          (facts "upload to target succesfully"
            (fact "Status code" (:status resp) => 200))
          parsed-id)
        (do
          (facts "upload to target should fail"
            (fact "Status code" (:status resp) => 400))
          attachment-id)))))

(defn upload-user-attachment [apikey attachment-type expect-to-succeed & [filename]]
  (midje-config/at-print-level :print-normally
    (let [filename   (or filename "dev-resources/test-attachment.txt")
          uploadfile (io/file filename)
          uri        (str (server-address) "/api/upload/user-attachment")
          resp       (stream-decoding-response http-post uri
                                               {:oauth-token apikey
                                                :multipart   [{:name "attachmentType" :content attachment-type}
                                                              {:name "files[]" :content uploadfile}]})
          body       (:body resp)]
      (if expect-to-succeed
        (facts "successful"
          resp => http200?
          body => ok?)
        (facts "should fail"
          body => fail?))
      body)))

(defn get-attachment-ids [application] (->> application :attachments (map :id)))

(defn get-attachment-by-id [apikey application-id attachment-id]
  (att/get-attachment-info (query-application apikey application-id) attachment-id))

(defn upload-attachment-to-all-placeholders [apikey application]
  (doseq [attachment (:attachments application)]
    (upload-attachment apikey (:id application) attachment true)))

;; NOTE: For this to work properly,
;;       the :operations-attachments must be set correctly for organization (in minimal.clj)
;;       and also the :attachments for operation (in operations.clj).
(defn generate-attachment [{id :id :as application} apikey password]
  (let [aloitusoikeus? (= (-> application :primaryOperation :name) "aloitusoikeus")]
    (when-let [first-attachment (or
                                  (get-in application [:attachments 0])
                                  (if aloitusoikeus?
                                    {:type {:type-group "paapiirustus", :type-id "asemapiirros"}}))]
      (upload-attachment apikey id first-attachment true)
      (when-not aloitusoikeus?
        (sign-attachment apikey id (:id first-attachment) password)))))

(defn generate-construction-time-attachment [{id :id} authority-apikey password]
  (midje-config/at-print-level :print-normally
    (let [attachment-type {:type-group "muut" :type-id "muu"}
          resp            (command authority-apikey :create-attachments :id id :attachmentTypes [attachment-type] :group nil)
          attachment-id   (-> resp :attachmentIds first)]
      (fact "attachment created"
        resp => ok?)
      (upload-attachment authority-apikey id {:id attachment-id :type attachment-type} true)
      (sign-attachment authority-apikey id attachment-id password)
      (fact "set attachment as construction time"
        (command sonja :set-attachment-as-construction-time :id id :attachmentId attachment-id :value true)))) => ok?)

;;;; File Upload
;;;; ============

(defn upload-file
  "Upload file to raw upload-file endpoint."
  [apikey filename & {:keys [cookie-store]}]
  (let [{:keys [body]} (-upload-file *lupis-client*
                                     apikey
                                     (if apikey :upload-file-authenticated :upload-file)
                                     (io/file filename)
                                     cookie-store)]
    (cond-> body
      (string? body) (json/decode true))))

(defn- job-done? [resp] (= (get-in resp [:job :status]) "done"))

(defn poll-job [apikey command id version limit]
  (midje-config/at-print-level :print-normally
    (loop [version version retries 0]
      (let [{:keys [ok] :as resp} (query apikey (keyword command) :jobId id :version (str version))]
        (fact "Polling was successful" ok => truthy)
        (if ok
          (cond
            (job-done? resp)  (do
                                (log/info "Polling succeeded!")
                                resp)
            (< limit retries) (do
                                (log/warn "Polling limit exceeded!")
                                (merge resp {:ok false :desc "Retry limit exceeded"}))
            :else             (do (Thread/sleep 200)
                                  (log/infof "Re-polling job %d/%d, status '%s'."
                                                retries limit (-> resp :job :status))
                                  (recur (or (get-in resp [:job :version])
                                             version)
                                         (inc retries))))
          (do
            (log/warn "Failed to poll!")
            resp))))))

(defn upload-file-and-bind
  "Uploads file and then bind using bind-attachments. To upload new file, specify metadata using filedata.
  If upload to existing attachment, filedata can be empty but :attachment-id should be defined."
  [apikey id filedata & {:keys [fails attachment-id draft?]}]
  (midje-config/at-print-level :print-normally
    (let [file-id (get-in (upload-file apikey (or (:filename filedata) "dev-resources/test-attachment.txt"))
                          [:files 0 :fileId])
          data    (if (ss/not-blank? attachment-id)
                    {:attachmentId attachment-id}
                    (select-keys filedata [:type :group :target :contents :constructionTime :sign]))
          {job :job :as resp} (command apikey
                                       (if draft?
                                         :bind-draft-attachments
                                         :bind-attachments)
                                       :id id :filedatas [(assoc data :fileId file-id)])]
      (if-not fails
        (do
          (fact "Bind-attachments command OK" resp => ok?)
          (fact "Job id is returned" (:id job) => truthy))
        (do
          (fact "Bind-attachment should fail" resp => fail?)
          (when (or (string? fails) (keyword? fails))
            (fact "Bind-attachments expected failure" resp => (partial test-util/expected-failure? fails)))))
      (when (and (:ok resp) (not= "done" (:status job)))
        (poll-job apikey :bind-attachments-job (:id job) (:version job) 25) => ok?)
      file-id)))

(defn upload-version-and-bind
  "Upload new attachment version using bind-attachment"
  [apikey id attachment-id & [filepath]]
  (midje-config/at-print-level :print-normally
    (let [file-id (get-in (upload-file apikey (or filepath "dev-resources/test-attachment.txt")) [:files 0 :fileId])
          {job :job :as resp} (command apikey :bind-attachment
                                       :id id
                                       :attachmentId attachment-id
                                       :fileId file-id)]
      (fact "Bind-attachment command OK" resp => ok?)
      (fact "Job id is returned" (:id job) => truthy)
      (when (and (:ok resp) (not= "done" (:status job)))
        (poll-job apikey :bind-attachments-job (:id job) (:version job) 25) => ok?)
      file-id)))

(defn upload-area [apikey org-id & [filename]]
  (let [filename   (or filename "dev-resources/sipoon_alueet.zip")
        uploadfile (io/file filename)
        uri        (str (server-address) "/api/raw/organization-area")]
    (http-post uri
               {:oauth-token      apikey
                :query-params     {:organizationId org-id}
                :multipart        [{:name "files[]" :content uploadfile}]
                :throw-exceptions false})))

(defn upload-application-shapefile [apikey app-id & [filename]]
  (let [filename   (or filename "dev-resources/sipoon_alueet.zip")
        uploadfile (io/file filename)
        uri        (str (server-address) "/api/raw/upload-application-shapefile")]
    (http-post uri
               {:oauth-token      apikey
                :query-params     {:id app-id}
                :multipart        [{:name "files[]" :content uploadfile}]
                :throw-exceptions false})))

;;;; File actions
;;;; =============

(defn get-local-filename [directory file-prefix-or-pred]
  (let [pred (if (fn? file-prefix-or-pred)
               file-prefix-or-pred
               (fn [^File file]
                 (and (.startsWith (.getName file) file-prefix-or-pred)
                      (.endsWith (.getName file) ".xml"))))]
    (log/infof "Iterating over files in %s" directory)
    (if-let [filename (some->> (file-seq (io/file directory))
                               (filter pred)
                               (sort-by #(.getName ^File %))
                               last
                               ((fn [^File file] (.getName file))))]
      (let [target (str directory filename)]
        (log/infof "Found target file: %s" target)
        target)
      (throw (AssertionError. (str "File not found: " directory file-prefix-or-pred ".xml"))))))

(defn file-name-accessor [file]
  (if get-files-from-sftp-server?
    (.getFilename ^ChannelSftp$LsEntry file)
    (.getName ^File file)))

(defn get-file-from-server
  ([user server file-to-get target-file-name]
   (let [auth {:username user :password dev-password}]
     (sftp/get-file server auth file-to-get target-file-name))
   target-file-name)
  ([user server file-prefix-or-some-pred target-file-name path-to-file]
   (let [auth {:username user :password dev-password}]
     (sftp/get-file-with-pred server auth path-to-file file-prefix-or-some-pred target-file-name))))

(defn delete-sftp-file
  [user server file-to-delete]
  (ssh-cli/sftp server :rm
                file-to-delete
                :username user
                :password dev-password
                :strict-host-key-checking :no))

(defn get-app-id-filename-pred
  "Selects the filename that starts with the app-id and has only timestamp and possible suffix after it"
  [app-id & [file-suffix]]
  (fn [file]
    (let [filename (file-name-accessor file)
          matcher (if file-suffix
                    (re-matches (re-pattern (str "^" (java.util.regex.Pattern/quote app-id) "_.{13}_" file-suffix "\\.xml$")) filename)
                    (re-matches (re-pattern (str "^" (java.util.regex.Pattern/quote app-id) "_.{13}\\.xml$")) filename))]
      (when (and (ss/starts-with? filename app-id)
                 matcher)
        filename))))

(defn get-app-id-verdict-filename-pred
  "Selects the filename that starts with the app-id and ends with '_verdict.xml'"
  [app-id]
  (get-app-id-filename-pred app-id "verdict"))

(defn get-valid-krysp-xml [application filename-prefix-or-pred]
  (midje-config/at-print-level
    :print-normally
    (let [permit-type      (keyword (permit/permit-type application))
          organization     (organization-from-minimal-by-id (:organization application))
          sftp-user        (get-in organization [:krysp permit-type :ftpUser])
          krysp-version    (get-in organization [:krysp permit-type :version])
          permit-type-dir  (permit/get-sftp-directory permit-type)
          output-dir       (ss/join-file-path (env/value :outgoing-directory)
                                              sftp-user permit-type-dir "/")
          sftp-server      (subs (env/value :fileserver-address) 7)
          target-file-name (str "target/Downloaded-" (:id application) "-" (now) ".xml")
          _                (log/infof "Fetching file from %s (env: %s, mode: %s, pred: %s)"
                                  (if get-files-from-sftp-server? "SFTP" "local disk")
                                  env/target-env
                                  env/mode
                                  filename-prefix-or-pred)
          xml-file         (if get-files-from-sftp-server?
                             (io/file (get-file-from-server
                                        sftp-user
                                        sftp-server
                                        filename-prefix-or-pred
                                        target-file-name
                                        (str permit-type-dir "/")))
                             (io/file (get-local-filename output-dir filename-prefix-or-pred)))
          xml-as-string    (slurp xml-file)
          xml              (parse xml-file)]

      (fact "Correctly named xml file is created" (.exists xml-file) => true)
      (fact "XML file is valid"
        (validate xml-as-string (:permitType application) krysp-version))
      xml)))

;;;; Statements
;;;; ===========

(defn upload-attachment-for-statement [apikey application-id attachment-id expect-to-succeed statement-id]
  (upload-file-and-bind apikey
                        application-id
                        {:target {:type "statement" :id statement-id}
                         :type   {:type-group :ennakkoluvat_ja_lausunnot
                                  :type-id    :lausunto}}
                        :attachment-id attachment-id
                        :fails (not expect-to-succeed)))

(defn get-statement-by-user-id [application user-id]
  (some #(when (= user-id (get-in % [:person :userId])) %) (:statements application)))

;; This has a side effect which generates an attachment to appliction
(defn generate-statement [application-id apikey]
  (midje-config/at-print-level :print-normally
    (facts* {:midje/description (str "Generate statement for " application-id)}
      (let [resp                (query sipoo :get-organizations-statement-givers :organizationId "753-R")
            =>                  ok?
            statement-giver     (->> resp :data (some #(when (= (min-util/email-for-key apikey) (:email %)) %)))
            =>                  truthy
            _                   (command apikey :request-for-statement
                                         :functionCode nil
                                         :id application-id
                                         :selectedPersons [statement-giver]
                                         :saateText "saate")
            =>                  ok?
            updated-application (query-application apikey application-id)
            statement-id        (:id (get-statement-by-user-id updated-application
                                                               (min-util/id-for-key apikey)))
            _                   (upload-attachment-for-statement apikey application-id "" true statement-id)
            _                   (command apikey :give-statement
                                         :id application-id
                                         :statementId statement-id
                                         :status "puollettu"
                                         :lang "fi"
                                         :text "Annanpa luvan urakalle.")])))
  (query-application apikey application-id))

;;;; Document Data
;;;; ==============

(defn update-document! [apikey application-id document-id updates & [local?]]
  (midje-config/at-print-level :print-normally
    (let [f (if local? local-command command)]
      (fact "Document is updated"
        (f apikey :update-doc
           :id application-id
           :doc document-id
           :updates updates) => ok?))))

(defn generate-documents! [application apikey & [local?]]
  (doseq [document (:documents application)]
    (let [user-role (:role (min-util/find-user-from-minimal-by-apikey apikey))
          data      (tools/create-document-data (model/get-document-schema document)
                                                (partial tools/dummy-values
                                                         (min-util/id-for-key apikey)))
          updates   (->> data
                         tools/path-vals
                         (map (fn [[p v]] [(->> p
                                                butlast
                                                (map name)
                                                (s/join "."))
                                           v]))
                         (filterv (fn [[path _]]
                                    (try
                                      (let [splitted-path (ss/split path #"\.")]
                                        (doc-persistence/validate-against-whitelist! document [splitted-path] user-role
                                                                                     application)
                                        (doc-persistence/validate-readonly-updates! document [splitted-path])
                                        (doc-persistence/validate-flagged-input-updates! {:application application} document [splitted-path]))
                                      true
                                      (catch Exception _
                                        false)))))]

      (update-document! apikey (:id application) (:id document) updates local?))))

;;;; Vetuma
;;;; =======

(defn vetuma! [data]
  (stream-decoding-body http-get (str (server-address) "/dev/api/vetuma")
                        {:query-params (select-keys data [:userid :firstName :lastName
                                                          :eidasId :eidasFamilyName])}))

;;;; Verdicts
;;;; =========

(defn fetch-verdicts [& [{:keys [use-queue? wait-ms] :or {wait-ms 2000}}]]
  (let [resp (batchrun/fetch-verdicts-default (test-system/get-common-components)
                                              {:use-queue? use-queue?})]
    (when use-queue?
      (sleep-ms wait-ms))
    resp))

(defn fetch-foreman-verdicts [& [{:keys [use-queue? wait-ms] :or {wait-ms 2000}}]]
  (let [resp (batchrun/fetch-foreman-verdicts-default (test-system/get-common-components)
                                                      {:use-queue? use-queue?})]
    (when use-queue?
      (sleep-ms wait-ms))
    resp))

(defn sleep-and-log [ms message]
  (log/info message)
  (sleep-ms ms))

(defn appeals-for-verdict [apikey app-id verdict-id]
  (-> (query apikey :appeals :id app-id) :data (get (keyword verdict-id))))

;;;; Assignments
;;;; ============

(defn create-assignment [from to application-id targets desc]
  (command from :create-assignment
           :id application-id
           :recipientId to
           :targets targets
           :description desc))

(defn update-assignment [who id assignment-id recipient description]
  (command who :update-assignment
           :id id
           :assignmentId assignment-id
           :recipientId recipient
           :description description))

(defn complete-assignment [user assignment-id]
  (command user :complete-assignment :assignmentId assignment-id))

(defn get-user-assignments [apikey]
  (midje-config/at-print-level :print-normally
    (let [resp (query apikey :assignments)]
      (fact "assignments query ok"
        resp => ok?)
      (:assignments resp))))

(def ely-filter-id "dead1111111111111111beef")
(def aita-filter-id "dead1111111111111112beef")

(defn upsert-automatic-assignment-filter
  "Params is the submap of the `:filter` path for the `UpsertParams`. Can be empty. Name and rank are
  given sensible defaults, if needed. Returns filter-id."
  [apikey org-id params]
  (midje-config/at-print-level :print-normally
    (let [{filter-id :id} (:filter (command apikey :upsert-automatic-assignment-filter
                                            :organizationId org-id
                                            :filter (merge {:name (str (gensym "Filter "))
                                                            :rank 0}
                                                           params)))]
      (fact "Automatic assignment filter upserted successfully"
        filter-id => truthy)
      filter-id)))

(defn automatic-assignment-filter-ids
  "Name - id maps for the organization automatic assignment filters."
  [apikey org-id]
  (->> (query apikey :organization-by-user :organizationId org-id)
       :organization
       :automatic-assignment-filters
       (map (fn [{:keys [name id]}]
              [name id]))
       (into {})))

;; ----------------
;; State change
;; ----------------

(defn state-change-integration
  [org-id]
  (midje-config/at-print-level :print-normally
    (fact "Setup dummy state change endpoint and enable it"
      (command admin :upsert-dmcity-configuration
               :organizationId org-id
               :configuration {:username "test-user"
                               :password "test-secret"
                               :vault    "test-vault"
                               :url      (str (server-address) "/dev/dummy-dmcity")
                               :targets  {:R     true
                                          :state true}})
      => ok?)))

;; -------------------
;; Fake buildings
;; -------------------

(defn fake-buildings
  "Returns true on success, false otherwise."
  [app-id]
  (try
    (http-get (str (server-address) "/dev/fake-buildings/" app-id) nil)
    true
    (catch Exception _
      false)))

(defn set-application-buildings [app-id buildings]
  (http-post (str (server-address) "/dev/set-application-buildings/" app-id)
             {:headers {"content-type" "application/json;charset=utf-8"}
              :body    (json/encode buildings)}))

;; -------------------
;; Update organization
;; -------------------

(defn update-rakennusluokat
  [org enabled]
  (midje-config/at-print-level :print-normally
    (fact "update rakennusluokat"
      (command admin :set-organization-boolean-attribute
               :enabled enabled
               :organizationId org
               :attribute :rakennusluokat-enabled) => ok?)))

(defn set-krysp-version
  [org permit-type user krysp-version]
  (midje-config/at-print-level :print-normally
    (fact "Update krysp version"
      (command user :set-krysp-endpoint {:organizationId org
                                         :url            (str (server-address) "/dev/krysp")
                                         :username       ""
                                         :password       ""
                                         :version        krysp-version
                                         :permitType     permit-type}))))

(def minimal-pori-ad-login
  (util/find-first #(some (comp #{"609-R"} :id)
                          (:organizations %))
                   minimal/ad-logins))

(defn update-pori-ad-login [data]
  (command admin :update-ad-login-settings
           :ad-login-id (:id minimal-pori-ad-login)
           :data        (-> minimal-pori-ad-login
                            (dissoc :id)
                            (merge data))))

(defschema TestSystemOpts
  (st/merge
    {(sc/optional-key :fixture)            ssc/NonBlankStr
     (sc/optional-key :schema-validation?) sc/Bool}
    test-system/TestSystemOpts))

(def valid-opts! (sc/validator TestSystemOpts))

(defmacro with-test-system
  "Start (and stop) a test system while executing `body`

  * `fixture` -- Run database fixture before runnin `body`.
  * `schema-validation?` -- Turn on schema validation while running `body`.

  See `lupapalvelu.itest-system/init!` for further options."
  {:style/indent 1
   :arglists     '([{:keys [db-name extra-system-keys fixture schema-validation? system-keys update-config]}
                    & body])}
  [opts & body]
  (let [{:keys [line]} (meta &form)]
    `(let [start#              (System/currentTimeMillis)
           opts#               ~opts
           fixture#            (:fixture opts#)
           schema-validation?# (get opts# :schema-validation? true)
           validation-on?#     (sc/fn-validation?)
           system-opts#        (dissoc opts# :fixture :schema-validation?)]
       (log/infof "Starting test from %s:%s" ~*file* ~line)
       (valid-opts! opts#)
       (malli-dev/start! {:report (malli-pretty/thrower)})
       (when schema-validation?#
          (sc/set-fn-validation! true))
        (try
         (test-system/init! system-opts#)
         (binding [mongo/*connection* (test-system/get-mongo)
                   fixture/*fixture*  (some-> fixture# keyword)]
           (when fixture#
             (fixture/apply-fixture fixture# (test-system/get-common-components)))
           (try
             (do ~@body)
          (finally
               ;; TODO: If pubsub messages leak between tests, we could consider
               ;; applying `remove-subscription!` on any created subscriptions.
               ;; But first we must clearly separate test and development subscriptions.
               (test-system/halt!)
               (let [end# (System/currentTimeMillis)]
                 (log/infof "Test %s:%s took %s ms"
                            ~*file* ~line (- end# start#))))))
         (finally
           (when schema-validation?#
             (sc/set-fn-validation! validation-on?#)))))))

(defn local-fixture-factory
  "Local mongo fixture with the given fixture or minimal (default) and local
  actions. `db-name` must start with 'test_' (default name uses timestamp)."
  [& {:keys [db-name] :as options}]
  {:arglists '([& {:keys [db-name extra-system-keys fixture schema-validation? system-keys update-config]}])
   :pre      [(or (nil? db-name)
                  (ss/starts-with? db-name "test_"))]}
  (fn [f]
    (with-test-system (update options :fixture (fnil identity "minimal"))
      (with-local-actions
        (f)))))

;; approve-doc for designers now checks that the document data is valid
(def suunnittelija-required-data
  {"kuntaRoolikoodi" "rakennussuunnittelija"
   "suunnittelutehtavanVaativuusluokka" "A"
   "osoite.katu" "Testi A"
   "osoite.postinumero" "00400"
   "osoite.postitoimipaikannimi" "Aaa"
   "henkilotiedot.etunimi" "Testi"
   "henkilotiedot.sukunimi" "Testi"
   "henkilotiedot.hetu" "170646-934T" ;; generated, 900 are temporary numbers
   "yhteystiedot.puhelin" "000"
   "yhteystiedot.email" "pena@example.com"
   "patevyys.koulutusvalinta" "insinööri"
   "patevyys.valmistumisvuosi" "1990"
   "patevyys.patevyysluokka" "A"})

(def paasuunnittelija-required-data
  (dissoc suunnittelija-required-data "kuntaRoolikoodi"))

(defn data->updates [x]
  (into [] x))
