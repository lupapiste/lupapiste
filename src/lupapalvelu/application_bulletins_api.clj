(ns lupapalvelu.application-bulletins-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [noir.response :as resp]
            [sade.core :refer :all]
            [slingshot.slingshot :refer [try+]]
            [sade.strings :as ss]
            [sade.property :as p]
            [lupapalvelu.action :refer [defquery defcommand defraw] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [monger.operators :refer :all]
            [lupapalvelu.states :as states]
            [lupapalvelu.application-search :refer [make-text-query dir]]))

(def bulletins-fields
  {:versions {$slice -1} :versions.bulletinState 1
   :versions.state 1 :versions.municipality 1
   :versions.address 1 :versions.location 1
   :versions.primaryOperation 1 :versions.propertyId 1
   :versions.applicant 1 :versions.modified 1
   :modified 1})

(def bulletin-page-size 10)

(defn- make-query [search-text municipality state]
  (let [text-query         (when-not (ss/blank? search-text)
                             (make-text-query (ss/trim search-text)))
        municipality-query (when-not (ss/blank? municipality)
                             {:versions.municipality municipality})
        state-query        (when-not (ss/blank? state)
                             {:versions.bulletinState state})
        queries            (filter seq [text-query municipality-query state-query])]
    (when-let [and-query (seq queries)]
      {$and and-query})))

(defn- get-application-bulletins-left [page searchText municipality state _]
  (let [query (make-query searchText municipality state)]
    (- (mongo/count :application-bulletins query)
       (* page bulletin-page-size))))

(def- sort-field-mapping {"bulletinState" :bulletinState
                          "municipality" :municipality
                          "address" :address
                          "applicant" :applicant
                          "modified" :modified})

(defn- make-sort [{:keys [field asc]}]
  (let [sort-field (sort-field-mapping field)]
    (cond
      (nil? sort-field) {}
      (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
      :else (array-map sort-field (dir asc)))))

(defn- get-application-bulletins [page searchText municipality state sort]
  (let [query (or (make-query searchText municipality state) {})
        apps (mongo/with-collection "application-bulletins"
                                    (query/find query)
                                    (query/fields bulletins-fields)
                                    (query/sort (make-sort sort))
                                    (query/paginate :page page :per-page bulletin-page-size))]
    (map
      #(assoc (first (:versions %)) :id (:_id %))
      apps)))

(defquery application-bulletins
  {:description "Query for Julkipano"
   :feature :publish-bulletin
   :parameters [page searchText municipality state sort]
   :input-validators [(partial action/number-parameters [:page])]
   :user-roles #{:anonymous}}
  [_]
  (let [parameters [page searchText municipality state sort]]
    (ok :data (apply get-application-bulletins parameters)
        :left (apply get-application-bulletins-left parameters))))

(defquery application-bulletin-municipalities
  {:description "List of distinct municipalities of application bulletins"
   :feature :publish-bulletin
   :parameters []
   :user-roles #{:anonymous}}
  [_]
  (let [municipalities (mongo/distinct :application-bulletins :versions.municipality)]
    (ok :municipalities municipalities)))

(defquery application-bulletin-states
  {:description "List of distinct municipalities of application bulletins"
   :feature :publish-bulletin
   :parameters []
   :user-roles #{:anonymous}}
  [_]
  (let [states (mongo/distinct :application-bulletins :versions.bulletinState)]
    (ok :states states)))

(defn- bulletin-exists! [bulletin-id]
  (let [bulletin (mongo/by-id :application-bulletins bulletin-id)]
    (when-not bulletin
      (fail! :error.invalid-bulletin-id))
    bulletin))

(defn- bulletin-version-is-latest! [bulletin bulletin-version-id]
  (let [latest-version-id (:id (last (:versions bulletin)))]
    (when-not (= bulletin-version-id latest-version-id)
      (fail! :error.invalid-version-id))))

(defn- comment-can-be-added! [bulletin-id bulletin-version-id comment]
  (when (ss/blank? comment)
    (fail! :error.empty-comment))
  (let [bulletin (bulletin-exists! bulletin-id)]
    (when-not (= (:bulletinState bulletin) "proclaimed")
      (fail! :error.invalid-bulletin-state))
    (bulletin-version-is-latest! bulletin bulletin-version-id)))

;; TODO user-roles Vetuma autheticated person
(defraw add-bulletin-comment
  {:description "Add comment to bulletin"
   :feature     :publish-bulletin
   :user-roles  #{:anonymous}}
  [{{files :files bulletin-id :bulletin-id comment :bulletin-comment-field bulletin-version-id :bulletin-version-id} :data created :created :as action}]
  (try+
    (comment-can-be-added! bulletin-id bulletin-version-id comment)
    (let [comment      (bulletins/create-comment comment created)
          stored-files (bulletins/store-files bulletin-id (:id comment) files)]
      (mongo/update-by-id :application-bulletins bulletin-id {$push {(str "comments." bulletin-version-id) (assoc comment :attachments stored-files)}})
      (->> {:ok true}
           (resp/json)
           (resp/content-type "application/json")
           (resp/status 200)))
    (catch [:sade.core/type :sade.core/fail] {:keys [text] :as all}
      (->> {:ok false :text text}
           (resp/json)
           (resp/content-type "application/json")
           (resp/status 200)))
    (catch Throwable t
      (error "Failed to store bulletin comment" t)
      (resp/status 400 :error.storing-bulletin-command-failed))))

(defn- get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

(defcommand publish-bulletin
  {:parameters [id]
   :feature :publish-bulletin
   :user-roles #{:authority}
   :states     (states/all-application-states-but :draft :open :submitted)}
  [{:keys [application created] :as command}]
  (let [app-snapshot (bulletins/create-bulletin-snapshot application)
        search-fields [:municipality :address :verdicts :_applicantIndex :bulletinState :applicant]
        search-updates (get-search-fields search-fields app-snapshot)
        updates (bulletins/snapshot-updates app-snapshot search-updates created)]
    (mongo/update-by-id :application-bulletins id updates :upsert true)
    (ok)))

(defquery bulletin
  {:parameters [bulletinId]
   :feature :publish-bulletin
   :user-roles #{:anonymous}}
  "return only latest version for application bulletin"
  (let [bulletin-fields (merge bulletins-fields
                               {:versions._applicantIndex 1
                                :versions.documents 1
                                :versions.id 1
                                :versions.attachments 1})]
    (if-let [bulletin (mongo/with-id (mongo/by-id :application-bulletins bulletinId bulletin-fields))]
      (let [latest-version   (-> bulletin :versions first)
            bulletin-version   (assoc latest-version :versionId (:id latest-version)
                                                     :id (:id bulletin))
            append-schema-fn (fn [{schema-info :schema-info :as doc}]
                               (assoc doc :schema (schemas/get-schema schema-info)))
            bulletin (-> bulletin-version
                         (update-in [:documents] (partial map append-schema-fn))
                         (assoc :stateSeq bulletins/bulletin-state-seq))]
        (ok :bulletin bulletin))
      (fail :error.bulletin.not-found))))

(defquery bulletin-versions
  "returns all bulletin versions for application bulletin with comments"
  {:parameters [bulletinId]
   :feature    :publish-bulletin
   :user-roles #{:authority}}
  (prn "perkele")
  (let [bulletin-fields (-> bulletin-fields
                            (dissoc :versions)
                            (merge {:comments 1}))
        bulletin (mongo/with-id (mongo/by-id :application-bulletins bulletinId bulletin-fields))]
    (ok :bulletin bulletin)))
