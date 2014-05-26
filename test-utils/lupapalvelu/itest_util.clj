(ns lupapalvelu.itest-util
  (:require [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.core :refer [fail!]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.vetuma :as vetuma]
            [sade.util :refer [fn-> fn->>]]
            [sade.http :as http]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [swiss-arrows.core :refer [-<>>]]
            [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)])
  (:import org.apache.http.client.CookieStore
           org.apache.http.cookie.Cookie))

(defn find-user-from-minimal [username] (some #(when (= (:username %) username) %) minimal/users))
(defn find-user-from-minimal-by-apikey [apikey] (some #(when (= (get-in % [:private :apikey]) apikey) %) minimal/users))
(defn- id-for [username] (:id (find-user-from-minimal username)))
(defn apikey-for [username] (get-in (find-user-from-minimal username) [:private :apikey]))

(defn email-for [username] (:email (find-user-from-minimal username)))
(defn email-for-key [apikey] (:email (some #(when (= (-> % :private :apikey) apikey) %) minimal/users)))

(defn organization-from-minimal-by-id [org-id]
  (some #(when (= (:id %) org-id) %) minimal/organizations))

(defn- muni-for-user [user]
  (let [org (organization-from-minimal-by-id (first (:organizations user)))]
    (-> org :scope first :municipality)))

(defn muni-for [username] (muni-for-user (find-user-from-minimal username)))
(defn muni-for-key [apikey] (muni-for-user (find-user-from-minimal-by-apikey apikey)))


(def pena        (apikey-for "pena"))
(def pena-id     (id-for "pena"))
(def mikko       (apikey-for "mikko@example.com"))
(def mikko-id    (id-for "mikko@example.com"))
(def teppo       (apikey-for "teppo@example.com"))
(def teppo-id    (id-for "teppo@example.com"))
(def veikko      (apikey-for "veikko"))
(def veikko-id   (id-for "veikko"))
(def veikko-muni (muni-for "veikko"))
(def sonja       (apikey-for "sonja"))
(def sonja-id    (id-for "sonja"))
(def ronja-id    (id-for "ronja"))
(def sonja-muni  (muni-for "sonja"))
(def sipoo       (apikey-for "sipoo"))
(def tampere-ya  (apikey-for "tampere-ya"))
(def naantali    (apikey-for "admin@naantali.fi"))
(def dummy       (apikey-for "dummy"))
(def admin       (apikey-for "admin"))
(def admin-id    (id-for "admin"))
(def raktark-jarvenpaa (apikey-for "rakennustarkastaja@jarvenpaa.fi"))
(def jarvenpaa-muni    (muni-for "rakennustarkastaja@jarvenpaa.fi"))
(def arto       (apikey-for "arto"))

(defn server-address [] (System/getProperty "target_server" "http://localhost:8000"))

(defn decode-response [resp]
  (update-in resp [:body] (comp keywordize-keys json/decode)))

(defn printed [x] (println x) x)

(defn raw [apikey action & args]
  (http/get
    (str (server-address) "/api/raw/" (name action))
    {:headers {"authorization" (str "apikey=" apikey)}
     :query-params (apply hash-map args)
     :throw-exceptions false}))

(defn raw-query [apikey query-name & args]
  (decode-response
    (http/get
      (str (server-address) "/api/query/" (name query-name))
      {:headers {"authorization" (str "apikey=" apikey)
                 "accepts" "application/json;charset=utf-8"}
       :query-params (apply hash-map args)
       :follow-redirects false
       :throw-exceptions false})))

(defn query [apikey query-name & args]
  (let [{status :status body :body} (apply raw-query apikey query-name args)]
    (when (= status 200)
      body)))

(defn- decode-post [action-type apikey command-name & args]
  (decode-response
    (http/post
      (str (server-address) "/api/" (name action-type) "/" (name command-name))
      {:headers {"authorization" (str "apikey=" apikey)
                 "content-type" "application/json;charset=utf-8"}
       :body (json/encode (apply hash-map args))
       :follow-redirects false
       :throw-exceptions false})))

(defn raw-command [apikey command-name & args]
  (apply decode-post :command apikey command-name args))

(defn command [apikey command-name & args]
  (let [{status :status body :body} (apply raw-command apikey command-name args)]
    (when (= status 200)
      body)))

(defn datatables [apikey query-name & args]
  (let [{status :status body :body} (apply decode-post :datatables apikey query-name args)]
    (when (= status 200)
      body)))

(defn apply-remote-fixture [fixture-name]
  (let [resp (decode-response (http/get (str (server-address) "/dev/fixture/" fixture-name)))]
    (assert (-> resp :body :ok))))

(def apply-remote-minimal (partial apply-remote-fixture "minimal"))

(defn create-app
  "Runs the create-application command, returns reply map. Use ok? to check it."
  [apikey & args]
  (let [args (->> args
               (apply hash-map)
               (merge {:operation "asuinrakennus"
                       :propertyId "75312312341234"
                       :x 444444 :y 6666666
                       :address "foo 42, bar"
                       :municipality (or (muni-for-key apikey) sonja-muni)})
               (mapcat seq))]
    (apply command apikey :create-application args)))

(defn success [resp]
  (fact (:text resp) => nil)
  (:ok resp))

(defn invalid-csrf-token? [{:keys [status body]}]
  (and
    (= status 403)
    (= (:ok body) false)
    (= (:text body) "error.invalid-csrf-token")))

;;
;; Test predicates: TODO: comment test, add facts to get real cause
;;

(fact "invalid-csrf-token?"
  (invalid-csrf-token? {:status 403 :body {:ok false :text "error.invalid-csrf-token"}}) => true
  (invalid-csrf-token? {:status 403 :body {:ok false :text "error.SOME_OTHER_REASON"}}) => false
  (invalid-csrf-token? {:status 200 :body {:ok true}}) => false)

(defn expected-failure? [expected-text {:keys [ok text]}]
  (and (= ok false) (= text expected-text)))

(def unauthorized? (partial expected-failure? "error.unauthorized"))

(fact "unauthorized?"
  (unauthorized? {:ok false :text "error.unauthorized"}) => true
  (unauthorized? {:ok false :text "error.SOME_OTHER_REASON"}) => false
  (unauthorized? {:ok true}) => false)

(defn in-state? [state]
  (fn [application] (= (:state application) (name state))))

(fact "in-state?"
  ((in-state? :open) {:state "open"}) => true
  ((in-state? :open) {:state "closed"}) => false)

(defn ok? [resp]
  (= (:ok resp) true))

(def fail? (complement ok?))

(fact "ok?"
  (ok? {:ok true}) => true
  (ok? {:ok false}) => false)

(defn http200? [{:keys [status]}]
  (= status 200))

(defn http401? [{:keys [status]}]
  (= status 401))

(defn http404? [{:keys [status]}]
  (= status 404))

;;
;; DSLs
;;

(defn set-anti-csrf! [value] (query pena :set-feature :feature "disable-anti-csrf" :value (not value)))
(defn feature? [& feature]
  (boolean (-<>> :features (query pena) :features (into {}) (get <> (map name feature)))))

(defmacro with-anti-csrf [& body]
  `(let [old-value# (feature? :disable-anti-csrf)]
     (set-anti-csrf! true)
     (try
       (do ~@body)
       (finally
         (set-anti-csrf! (not old-value#))))))

(defn create-app-id
  "Verifies that an application was created and returns it's ID"
  [apikey & args]
  (let [resp (apply create-app apikey args)
        id   (:id resp)]
    resp => ok?
    id => truthy
    id))

(defn comment-application [id apikey open]
  (fact "comment is added succesfully"
    (command apikey :add-comment :id id :text "hello" :target {:type "application"} :openApplication open) => ok?))

(defn query-application
  "Fetch application from server.
   Asserts that application is found and that the application data looks sane."
  [apikey id]
  {:pre  [apikey id]
   :post [(:id %)
          (not (s/blank? (:applicant %)))
          (:created %) (pos? (:created %))
          (:modified %) (pos? (:modified %))
          (contains? % :opened)
          (:permitType %)
          (contains? % :permitSubtype)
          (contains? % :infoRequest)
          (contains? % :openInfoRequest)
          (:operations %)
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
          (:attachments %)]}
  (let [{:keys [application ok]} (query apikey :application :id id)]
    (assert ok)
    application))

(defn create-and-submit-application
  "Returns the application map"
  [apikey & args]
  (let [id    (apply create-app-id apikey args)
        resp  (command apikey :submit-application :id id)]
    resp => ok?
    (query-application apikey id)))

(defn allowed? [action & args]
  (fn [apikey]
    (let [{:keys [ok actions]} (apply query apikey :allowed-actions args)
          allowed? (-> actions action :ok)]
      (and ok allowed?))))

(defn last-email
  "Returns the last email (or nil) and clears the inbox"
  []
  {:post [(or (nil? %)
            (and (:to %) (:subject %) (not (.contains (:subject %) "???")) (-> % :body :html) (-> % :body :plain))
            (println %))]}
  (let [{:keys [ok message]} (query pena :last-email :reset true)] ; query with any user will do
    (assert ok)
    message))

(defn sent-emails
  "Returns a list of emails and clears the inbox"
  []
  (let [{:keys [ok messages]} (query pena :sent-emails :reset true)] ; query with any user will do
    (assert ok)
    messages))

(defn contains-application-link? [application-id {body :body}]
  (let [[href a-id a-id-again] (re-find #"(?sm)http.+/app/fi/applicant\?hashbang=!/application/([A-Za-z0-9-]+)#!/application/([A-Za-z0-9-]+)" (:plain body))]
    (= application-id a-id a-id-again)))

(defn contains-application-link-with-tab? [application-id tab {body :body}]
  (let [[href a-id a-tab a-id-again a-tab-again] (re-find #"(?sm)http.+/app/fi/applicant\?hashbang=!/application/([A-Za-z0-9-]+)/([a-z]+)#!/application/([A-Za-z0-9-]+)/([a-z]+)" (:plain body))]
    (and (= application-id a-id a-id-again) (= tab a-tab a-tab-again))))

;;
;; Stuffin' data in
;;

(defn upload-attachment [apikey application-id {attachment-id :id attachment-type :type} expect-to-succeed & {:keys [filename] :or {filename "dev-resources/test-attachment.txt"}}]
  (let [uploadfile  (io/file filename)
        uri         (str (server-address) "/api/upload/attachment")
        resp        (http/post uri
                      {:headers {"authorization" (str "apikey=" apikey)}
                       :multipart [{:name "applicationId"  :content application-id}
                                   {:name "Content/type"   :content "text/plain"}
                                   {:name "attachmentType" :content (str
                                                                      (:type-group attachment-type) "."
                                                                      (:type-id attachment-type))}
                                   {:name "attachmentId"   :content attachment-id}
                                   {:name "upload"         :content uploadfile}]})]
    (if expect-to-succeed
      (facts "Upload succesfully"
        (fact "Status code" (:status resp) => 302)
        (fact "location"    (get-in resp [:headers "location"]) => "/html/pages/upload-ok.html"))
      (facts "Upload should fail"
        (fact "Status code" (:status resp) => 302)
        (fact "location"    (.indexOf (get-in resp [:headers "location"]) "/html/pages/upload-1.13.html") => 0)))))

(defn upload-attachment-to-target [apikey application-id attachment-id expect-to-succeed target-id target-type & [attachment-type]]
  {:pre [target-id target-type]}
  (let [filename    "dev-resources/test-attachment.txt"
        uploadfile  (io/file filename)
        application (query-application apikey application-id)
        uri         (str (server-address) "/api/upload/attachment")
        resp        (http/post uri
                      {:headers {"authorization" (str "apikey=" apikey)}
                       :multipart (filter identity
                                    [{:name "applicationId"  :content application-id}
                                     {:name "Content/type"   :content "text/plain"}
                                     {:name "attachmentType" :content (or attachment-type "muut.muu")}
                                     (when attachment-id {:name "attachmentId"   :content attachment-id})
                                     {:name "upload"         :content uploadfile}
                                     {:name "targetId"       :content target-id}
                                     {:name "targetType"     :content target-type}])}
                      )]
    (if expect-to-succeed
      (facts "Statement upload succesfully"
        (fact "Status code" (:status resp) => 302)
        (fact "location"    (get-in resp [:headers "location"]) => "/html/pages/upload-ok.html"))
      (facts "Statement upload should fail"
        (fact "Status code" (:status resp) => 302)
        (fact "location"    (.indexOf (get-in resp [:headers "location"]) "/html/pages/upload-1.13.html") => 0)))))

(defn upload-attachment-for-statement [apikey application-id attachment-id expect-to-succeed statement-id]
  (upload-attachment-to-target apikey application-id attachment-id expect-to-succeed statement-id "statement"))

(defn get-attachment-ids [application] (->> application :attachments (map :id)))

(defn upload-attachment-to-all-placeholders [apikey application]
  (doseq [attachment (:attachments application)]
    (upload-attachment apikey (:id application) attachment true)))


(defn generate-documents [application apikey]
  (doseq [document (:documents application)]
    (let [data    (tools/create-document-data (model/get-document-schema document) tools/dummy-values)
          updates (tools/path-vals data)
          updates (map (fn [[p v]] [(butlast p) v]) updates)
          updates (map (fn [[p v]] [(s/join "." (map name p)) v]) updates)]
      (command apikey :update-doc
        :id (:id application)
        :doc (:id document)
        :updates updates) => ok?)))

;;
;; Vetuma
;;

(defn vetuma! [{:keys [userid firstname lastname] :as data}]
  (->
    (http/get
      (str (server-address) "/dev/api/vetuma")
      {:query-params (select-keys data [:userid :firstname :lastname])})
    decode-response
    :body))

(defn vetuma-stamp! []
  (-> {:userid "123"
       :firstname "Pekka"
       :lastname "Banaani"}
    vetuma!
    :stamp))

;;
;; HTTP Client cookie store
;;

(defn ->cookie-store [store]
  (proxy [org.apache.http.client.CookieStore] []
    (getCookies []       (or (vals @store) []))
    (addCookie [cookie]  (swap! store assoc (.getName cookie) cookie))
    (clear []            (reset! store {}))
    (clearExpired [])))
