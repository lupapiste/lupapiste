(ns lupapalvelu.asiointi.notifications.registration
  "AsiointiAPI users may register to receive (application) notifications

  NOTE: The CRUD operations on registrations are invoked by user over
  the AsiointiAPI. Thus, most of the methods return either `(ok)` or `(fail)`
  responses to the caller."
  (:require [lupapalvelu.asiointi.notifications.common :as common]
            [lupapalvelu.asiointi.shared-schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [malli.core :as m]
            [malli.experimental :as mx]
            [malli.transform :as malli-transform]
            [malli.util :as mu]
            [monger.operators :refer [$set]]
            [sade.core :refer [fail ok]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [com.mongodb DuplicateKeyException]
           [java.net MalformedURLException URI URISyntaxException]
           [java.util Date]))

(set! *warn-on-reflection* true)

(def Registration
  "A user has registered to receive notifications regarding an organization
  (and the applications within)."
  ;; TODO: support pausing and resuming a registration?
  (m/schema [:map {:closed true}
             [:id schemas/ObjectIdField]
             [:created-by schemas/NonBlankStr]
             [:created-at schemas/DateField]
             [:modified-by {:optional true} schemas/NonBlankStr]
             [:modified-at {:optional true} schemas/DateField]
             [:organization-id schemas/NonBlankStr]
             [:target [:map {:closed true}
                       [:url schemas/NonBlankStr]]]
             [:ignored {:optional true}
              [:set
               (into [:enum] common/notification-types)]]]))

(def ^:private collection :asiointiapi-notification-registrations)

(def ^:private valid? (m/validator Registration))

(def ^:private coerce! (m/coercer Registration malli-transform/json-transformer))

(def ^:private ModifiableFields (mu/select-keys Registration #{:target :ignored}))

(def ^:private valid-modifiable? (m/validator ModifiableFields))

(defn- split-to-set
  [s]
  (let [parts (some-> s ss/trim-or-nil (ss/split #","))
        parts (keep ss/trim-or-nil parts)]
    (set parts)))

(def ^:private url-host-whitelist (split-to-set (env/value :asiointiapi :notify :url :whitelist-host)))

(def ^:private url-protocol-whitelist (split-to-set (env/value :asiointiapi :notify :url :whitelist-protocol)))

(defn- valid-url
  "Validate target URL.
  Returns a success or error map."
  [{{:keys [url]} :target :as registration}
   {:keys [url-host-whitelist
           url-protocol-whitelist]}]
  (try
    ;; Note that `ssc/HttpUrl` already verifies the URL partially
    (let [parsed-url (.toURL (URI. url))]
      (cond
        (not (contains? url-host-whitelist (.getHost parsed-url)))         (fail :error.forbidden-host :url url)
        (not (contains? url-protocol-whitelist (.getProtocol parsed-url))) (fail :error.forbidden-protocol :url url)
        :else                                                              (ok :registration registration)))
    (catch MalformedURLException _
      (fail :error.invalid-url :url url))
    (catch URISyntaxException _
      (fail :error.invalid-uri :url url))
    (catch Exception _
      (fail :error.invalid-uri :url url))))

(defn- valid-registration
  "Validate registration.
  Returns a success or error map."
  [registration ctx]
  (cond
    (valid? registration) (valid-url registration ctx)
    :else                 (fail :error.invalid-registration)))

(defn- ->registration
  "Build a registration.
  Returns a success or error map."
  [{:keys [^long created organization-id target user ignored]} ctx]
  (-> (util/assoc-when {:created-at      (some-> created (Date.))
                        :created-by      (:username user)
                        :id              (mongo/create-object-id)
                        :organization-id organization-id
                        :target          target}
                       :ignored (not-empty (set ignored)))
      (valid-registration ctx)))

(defn- on-success
  [result f]
  (if (:ok result)
    (f result)
    result))

(def ^:dynamic *do-insert* mongo/insert)

(mx/defn insert!
  "Store a registration.
  Returns `nil` on success or an error map."
  {:malli/always true
   :private      true}
  [registration :- Registration]
  (try
    (*do-insert* collection registration)
    (ok :registration registration)
    (catch DuplicateKeyException _
      (fail :error.duplicate-registration))))

(defn create!
  "Create and store a registration.
  Returns a success or an error map."
  ([registration]
   (create! registration (util/mmap url-host-whitelist url-protocol-whitelist)))
  ([registration ctx]
   (-> (->registration registration ctx)
       (on-success (fn [{:keys [registration]}]
                     (insert! registration))))))

(defn- valid-modification
  "Validate registration modification.
  Returns a success or error map."
  [changes ctx]
  (cond
    (valid-modifiable? changes) (valid-url changes ctx)
    :else                       (fail :error.invalid-registration)))

(def ^:dynamic *do-update* mongo/update-one-and-return)

(mx/defn update!
  "Update a registration.
  Returns a success or an error map."
  {:malli/always true}
  ([registration-id :- schemas/ObjectIdField
    organization-id :- :string
    changes]
   (update! registration-id
            organization-id
            changes
            (util/mmap url-host-whitelist url-protocol-whitelist)))
  ([registration-id :- schemas/ObjectIdField
    organization-id :- :string
    changes
    ctx]
   (try
     (-> (valid-modification changes ctx)
         (on-success (fn [{:keys [registration]}]
                       (if-let [registration (some-> (*do-update* collection
                                                                  {:_id             registration-id
                                                                   :organization-id organization-id}
                                                                  {$set registration})
                                                     mongo/with-id
                                                     coerce!)]
                         (ok :registration registration)
                         (fail :error.not-found)))))
     (catch DuplicateKeyException _
       (fail :error.duplicate-registration)))))

(mx/defn delete!
  "Delete a registration.
  Returns a success or an error map."
  {:malli/always true}
  [registration-id :- schemas/ObjectIdField
   organization-id :- :string]
  (if (and (mongo/any? collection {:_id             registration-id
                                   :organization-id organization-id})
           (mongo/remove collection registration-id))
    (ok)
    (fail :error.not-found)))

(defn find-registration
  [org-id registration-id]
  (some-> (mongo/select-one collection
                            {:_id             registration-id
                             :organization-id org-id})
          coerce!))

(defn get-registration
  "Return registration or throw."
  [registration-id]
  (-> (or (mongo/select-one collection
                            {:_id registration-id})
          (throw (ex-info "No registration found" {:id registration-id})))
      coerce!))

(defn exists?
  [registration-id]
  (mongo/any? collection {:_id registration-id}))

(defn org-id->registrations
  [org-id]
  (->> (mongo/select collection {:organization-id org-id})
       (map coerce!)
       (into [])))
