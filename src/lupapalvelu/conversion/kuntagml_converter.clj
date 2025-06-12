(ns lupapalvelu.conversion.kuntagml-converter
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.state :as conv-st]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.db :as db]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.foreman-application-util :as fau]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]))

(sc/defn ^:always-validate store-backend-ids
  "Adds a verdict draft with the given `backend-id`. Does not do anything if a verdict
  with `kuntalupatunnus` already exists."
  [application-id :- ssc/NonBlankStr {:keys [kuntalupatunnus vrk-lupatunnus]}]
  (mongo/update-by-query :applications
                         {:_id                      application-id
                          ;; Nil check does not work here since verdicts is an array.
                          :verdicts.kuntalupatunnus {$exists false}}
                         {$push {:verdicts (verdict/backend-id->verdict kuntalupatunnus
                                                                        vrk-lupatunnus
                                                                        true)}}))

(defn- application-xy [{:keys [location]}]
  (some->> (seq location) (zipmap [:x :y])))

(defn- make-application-skeleton
  ([{:keys [organization municipality conversion-doc user created]}
    location-info primary-op-name  manual-schema-datas]
   (-> {:id             (:LP-id conversion-doc)
        :organization   organization
        :operation-name primary-op-name
        :location       (app/->location (:x location-info) (:y location-info))
        :propertyId     (:propertyId location-info)
        :address        (:address location-info)
        :municipality   municipality}
       (app/make-application [] ;; messages
                             user
                             created
                             manual-schema-datas)
       ;; No operation attachments
       (assoc :attachments []
              :facta-imported true)))
  ([cfg location-info primary-op-name]
   (make-application-skeleton cfg location-info primary-op-name nil)))

(defn- add-party-documents [{:keys [permit-type]} app-info application]
  (let [parties (->> (concat (map prev-permit/suunnittelija->party-document (:suunnittelijat app-info))
                             (map prev-permit/osapuoli->party-document (:muutOsapuolet app-info))
                             (map #(prev-permit/hakija->party-document % (cond-> "hakija"
                                                                           (util/=as-kw permit-type :R)
                                                                           (str "-r")))
                                  (:hakijat app-info)))
                     (remove empty?)
                     seq)]
    (cond-> application
      parties (update :documents concat parties))))

(defn foreman-termination
  "Returns a very minimal foreman termination structure or nil. If `started` is after
  `ended`, nil is returned."
  ([started ended]
   (let [started (conv-util/valid-timestamp-or-nil started)
         ended   (conv-util/valid-timestamp-or-nil ended)]

     (if (and started ended (> started ended))
       (log/errorf "Foreman termination enddate %s is before startdate %s."
                   (date/finnish-date ended) (date/finnish-date started))
       (-> {:started started
            :ended   ended}
           util/strip-nils
           not-empty))))
  ([{started :alkamisPvm ended :paattymisPvm}]
   (foreman-termination started ended)))

(defn- update-foreman-info [app-info application]
  (if-let [tj-info (when (fau/foreman-app? application)
                     (some-> app-info :tyonjohtajat first))]
    (let [new-doc      (prev-permit/tyonjohtaja->tj-document tj-info)
          {doc-id :id} (->> application :documents
                            (util/find-first #(some-> % :schema-info :name (= "tyonjohtaja-v2"))))]
      (-> application
          (update :documents #(util/update-by-id doc-id
                                                 (fn [doc]
                                                   (update doc :data util/deep-merge (:data new-doc)))
                                                 %))
          (assoc :foreman (meta-fields/foreman-name-from-doc new-doc)
                 :foremanRole (meta-fields/foreman-role-from-doc new-doc)
                 :permitSubtype (if (:tyonjohtajaHakemusKytkin tj-info)
                                  :tyonjohtaja-hakemus :tyonjohtaja-ilmoitus))
          (util/assoc-when :foremanTermination (foreman-termination tj-info))))
    application))

(defn- add-site-document [{:keys [backend-id]} xml application]
  (let [site-doc (some->> xml
                          building-reader/->rakennuspaikkatieto
                          (conv-util/rakennuspaikkatieto->rakennuspaikka-kuntagml-doc backend-id))]
    (cond-> application
      site-doc (update :documents util/conjv site-doc))))

(defn- set-permit-subtype [{:keys [permit-type]} xml application]
  (let [subtype (cond
                  (util/not=as-kw permit-type :P)           nil
                  (xml/select1 xml [:Suunnittelutarveasia]) "suunnittelutarveratkaisu"
                  :else                                     "poikkeamislupa")]
    (cond-> application
      subtype (assoc :permitSubtype subtype))))

(defn- set-state-history
  "Add history array, application state and related timestamp properties"
  [{:keys [created] :as cfg} conversion-doc xml application]
  (let [history        (conv-st/generate-history-array cfg conversion-doc xml application)
        submitted-time (some (fn [{ts :ts}] (when (pos? ts) ts)) history)]
    (-> application
        ;; Add timestamps for different state changes with submitted fallback.
        (assoc :submitted submitted-time
               :opened (or submitted-time created)
               :history history
               :state (:state (last history)))
        (merge (conv-st/history-timestamps history)))))

(defn- add-statements [xml application]
  (let [statements (for [lausuntotieto (filter :viranomainen (krysp-reader/->lausuntotiedot xml))
                         :let          [statement   (prev-permit/lausuntotieto->statement lausuntotieto)
                                        ;; Normalization, since the input data from
                                        ;; KuntaGML contains values like 'ei
                                        ;; huomautettavaa' (should be 'ei-huomautettavaa')
                                        ;; etc.
                                        puoltotieto (some-> statement
                                                            (get-in [:metadata :puoltotieto])
                                                            ss/trim
                                                            (ss/replace #"\s+" "-"))]
                         :when         (map? statement)]
                     (try
                       (statement/give-statement statement
                                                 (:saateText statement)
                                                 puoltotieto
                                                 (mongo/create-id)
                                                 (mongo/create-id)
                                                 false
                                                 (date/timestamp (:lausuntoPvm lausuntotieto)))
                       (catch Exception e
                         (log/error "Moving statement to statement given -state failed:"
                                    (ex-message e)))))]
    (util/assoc-when application
                     :statements (some->> statements (remove nil?) seq))))

(defn- purge-skeleton-documents
  "Remove those `application` documents that are identical to their `_skeleton`
  counterparts (thus empty) AND are not bound to any operation. For foreman applications
  the `hankkeen-kuvaus` document is _always_ retained."
  [{:keys [documents] :as _skeleton} application]
  (let [skeledocs (zipmap (map :id documents) documents)]
    (update application
            :documents
            #(filter (fn [{:keys [id schema-info] :as doc}]
                       (or (some-> schema-info :op :id)
                           (not= doc (get skeledocs id))
                           (and (fau/foreman-app? application)
                                (some-> schema-info :subtype (util/=as-kw :hankkeen-kuvaus)))))
                     %))))

(defn update-info-document [{:keys [created]} xml application]
  (if-let [{:keys [description deviation]} (building-reader/->asian-tiedot xml)]
    (update application :documents
            #(map (fn [{:keys [schema-info] :as doc}]
                    (if (= (:subtype schema-info) :hankkeen-kuvaus)
                      (cond-> doc
                        description
                        (assoc-in [:data :kuvaus] {:value    description
                                                   :modified created})
                        deviation
                        (assoc-in [:data :poikkeamat] {:value    deviation
                                                       :modified created}))
                      doc))
                  %))
    application))

(defn- make-command [{:keys [organization user created system]} application]
  {:pre [(map? system)]}
  (assoc (action/application->command application user)
         :organization organization
         :created created
         :system system))

(defn update-verdicts
  "Reads a verdict from `xml`. The result can be a real or placeholder (draft)
  verdict. Returns true if a real verdict is created, nil otherwise."
  [{:keys [conversion-doc] :as cfg} xml {app-id :id :as application}]
  (let [command (make-command cfg application)]
    ;; pitääkö tässä huomioida, että find-verdicts-from-xml ei enää ylikirjoita päätöksiä?
    (if-let [updates (verdict/find-verdicts-from-xml command xml false)]
      (do
        (action/update-application command updates)
        (verdict-date/update-verdict-date app-id)
        ;; The `application` needs to be reloaded due to the verdict updates.
        (when-let [state (conv-st/forward-to-verdict-state-if-needed app-id)]
          (conv-doc/upsert-document :reset-state {:id          (:id conversion-doc)
                                                  :reset-state state}))
        true)
      ;; No verdicts, let's store the backend-id, if needed.
      (do
        (store-backend-ids app-id
                           (first (krysp-reader/->backend-ids xml)))
        nil))))

(defmulti ^:private convert-application-from-xml
  (fn [options & _]
    (-> options :conversion-doc :permit-type keyword)))

(defmethod convert-application-from-xml :P
  [{:keys [conversion-doc user] :as cfg} xml app-info location-info]
  (let [{app-id :LP-id} conversion-doc
        skeleton        (make-application-skeleton cfg location-info "poikkeamis")
        application     (->> skeleton
                             (update-info-document cfg xml)
                             (add-party-documents conversion-doc app-info)
                             (add-site-document conversion-doc xml)
                             (set-permit-subtype conversion-doc xml)
                             (set-state-history cfg conversion-doc xml)
                             (add-statements xml)
                             (purge-skeleton-documents skeleton))]

    ;; The application must be found in mongo when resolving verdicts.
    (app/insert-application application)
    ;; Whatever happens next, the application is at least somewhat converted
    (conv-doc/upsert-document :convert {:id             (:id conversion-doc)
                                        :reset-state    (:state application)
                                        :reset-location (application-xy application)})
    (log/with-logging-context {:applicationId app-id
                               :userId        (:id user)}
      (update-verdicts cfg xml application)
      (mongo/update-by-id :applications
                          app-id
                          (meta-fields/applicant-index-update application))
      app-id)))

(defn- combine-house-and-info-data [app-info
                                    {:keys [laajennus purkaminen muuMuutosTyo
                                            uudelleenrakentaminen]}
                                    house-or-structure]
  (let [map->updates     #(some->> % util/strip-nils not-empty (doc-model/map2updates []))
        op-extra-updates (->> {:laajennuksen-tiedot
                               (some-> laajennus
                                       (rename-keys {:laajennuksentiedot  :mitat
                                                     :perusparannusKytkin :perusparannuskytkin})
                                       (update-in [:mitat :huoneistoala]
                                                  (fn [v]
                                                    (cond
                                                      (map? v) {:0 v}

                                                      (sequential? v)
                                                      (into {} (map-indexed #(vector (util/make-kw %1) %2) v))))))
                               :poistumanSyy       (:purkamisenSyy purkaminen)
                               :poistumanAjankohta (some-> purkaminen :poistumaPvm date/finnish-date)}
                              (merge (select-keys (:data house-or-structure) [:osoite])
                                     (rename-keys (or muuMuutosTyo uudelleenrakentaminen)
                                                  {:perusparannusKytkin :perusparannuskytkin
                                                   ;; Note: currently muutostyolaji is
                                                   ;; always overriden by operation
                                                   ;; schema-data.
                                                   :muutostyonLaji      :muutostyolaji}))
                              map->updates)
        hos-updates      (if (contains? (:data house-or-structure) :tilapainenRakennelmaKytkin)
                      (some-> house-or-structure
                              :data
                              (rename-keys {:rakennelman-kuvaus :kuvaus
                                            :rakennusnro        :tunnus})
                              map->updates)
                      (some->> house-or-structure vector (prev-permit/schema-datas app-info) first))]
    (concat hos-updates op-extra-updates)))

(defn- make-r-application-skeleton
  [cfg app-info xml location-info]
  (let [[primary-op & secondary-ops
         :as app-info-ops]       (:toimenpiteet app-info)
        buildings-and-structures (building-reader/->buildings-and-structures
                                   xml
                                   {:include-personal-owner-info? true
                                    :include-empty?               true})

        [primary-data
         & secondary-datas]      (map #(combine-house-and-info-data app-info %1 %2)
                                 app-info-ops
                                 buildings-and-structures)

        ;; So that regex checks on this don't throw errors, should the field be empty.
        description              (or (:description (building-reader/->asian-tiedot xml)) "")
        resolve-op-name          (fn [op]
                              ;; Apply is needed since if op is nil, a two-parameter version is called.
                              (apply conv-util/deduce-operation-type (:conversion-doc cfg)
                                     description (cond-> [] op (conj op))))
        primary-op-name          (resolve-op-name primary-op)
        primary-schema-name      (-> primary-op-name conv-util/op-name->schema-name name)
        primary-doc-data         {primary-schema-name (app/sanitize-document-datas
                                                        (schemas/get-schema 1 primary-schema-name)
                                                        primary-data)}
        skeleton                 (make-application-skeleton cfg location-info
                                                       primary-op-name
                                                       primary-doc-data)

        ;; Secondary operations and documents
        secondary-op-names       (some->> (seq secondary-ops)
                                        (map resolve-op-name))
        secondary-descriptions   (map :description secondary-ops)
        secondary-docs           (map (partial app/document-data->op-document skeleton)
                                    secondary-datas
                                    secondary-op-names)
        secondary-ops            (mapv #(assoc (-> %1 :schema-info :op) :description %2 :name %3)
                                     secondary-docs
                                     secondary-descriptions
                                     secondary-op-names)]
    (cond-> skeleton
      (ss/not-blank? description) (assoc-in [:primaryOperation :description] description)
      secondary-op-names          (-> (assoc :secondaryOperations secondary-ops)
                                      (update :documents concat secondary-docs)))))

(defn update-reviews
  "Read reviews from `xml` and updates application `application-id` if needed. Returns
  task-ids of the added/updated reviews or nil."
  [{:keys [user created system]} xml application-id]
  (let [updated-application (mongo/by-id :applications application-id)
        {:keys [updates added-tasks-with-updated-buildings attachments-by-ids]}
        (review/read-reviews-from-xml conv-util/conversion-user-data
                                      created updated-application xml
                                      {:do-not-include-state-updates? true
                                       :skip-task-validation?         true
                                       :date-optional?                true})
        task-ids            (some->> added-tasks-with-updated-buildings seq (mapv :id))
        review-command      (assoc (action/application->command updated-application user)
                                   :action "conversion-review-updates"
                                   :system system)]
    (when (and task-ids
               (:ok (review/save-review-updates review-command
                                                updates
                                                added-tasks-with-updated-buildings
                                                attachments-by-ids true)))
      task-ids)))

(defn fetch-missing-review-attachments
  [{:keys [user] :as cfg} {app-id :LP-id :as conversion-doc}]
  (log/with-logging-context {:applicationId app-id
                             :userId        (:id user)}
    (let [xml         (conv-doc/read-xml conversion-doc)
          atts-by-ids (review/kuntagml-attachments-for-tasks app-id xml {:date-optional? true})]
      (when atts-by-ids
        (when-let [a (mongo/select-one :applications {:_id            app-id
                                                      ;; Just to make sure
                                                      :facta-imported true})]
          (review/fetch-missing-review-attachments (assoc cfg :application a)
                                                   atts-by-ids))))))

(defmethod convert-application-from-xml :R
  [{:keys [organization conversion-doc user] :as cfg}
   xml app-info location-info]
  (let [{:keys [backend-id]} conversion-doc
        skeleton             (make-r-application-skeleton cfg app-info xml location-info)
        created-application  (->> skeleton
                                  (update-info-document cfg xml)
                                  (update-foreman-info app-info)
                                  (add-party-documents conversion-doc app-info)
                                  (add-site-document conversion-doc xml)
                                  (set-state-history cfg conversion-doc xml)
                                  (add-statements xml)
                                  (purge-skeleton-documents skeleton))]

    (log/with-logging-context {:applicationId (:id created-application)
                               :userId        (:id user)}
      ;; The application has to be inserted first, because it is assumed to be in the
      ;; database when checking for verdicts (and their attachments).
      (app/insert-application created-application)
      ;; Whatever happens next, the application is at least somewhat converted
      (conv-doc/upsert-document :convert {:id             (:id conversion-doc)
                                          :reset-state    (:state created-application)
                                          :reset-location (application-xy created-application)})
      (log/infof "Inserted converted app: org=%s kuntalupatunnus=%s"
                 (:organization created-application) backend-id)
      ;; Get verdicts for the application
      (when (update-verdicts cfg xml created-application)
        ;; No reviews without a real verdict
        (update-reviews cfg xml (:id created-application)))

      (let [fetched-application (mongo/by-id :applications (:id created-application))
            app-links           (distinct
                                  (concat
                                    (db/application-ids-by-kuntalupatunnus
                                      (:id organization)
                                      backend-id
                                      {:permitType :ARK})
                                    (db/application-ids-by-kuntalupatunnus
                                      (:id organization)
                                      backend-id
                                      {:primaryOperation.name :aiemmalla-luvalla-hakeminen})
                                    (krysp-reader/->viitelupatunnukset xml)))]
        (mongo/update-by-id :applications
                            (:id fetched-application)
                            (meta-fields/applicant-index-update fetched-application))
        (conv-doc/upsert-document :app-links {:id          (:id conversion-doc)
                                              :app-links   app-links
                                              :reset-state (:state fetched-application)})
        (:id fetched-application)))))

(def fallback-location-infos
  {;; Default location info for Vantaa's conversion from Facta: the address and
   ;; coordinates point to the RAVA of Vantaa.
   "092-R" {:address    "Kielotie 20 C"
            :propertyId "09206101180002"
            :x          391513.021
            :y          6685671.373}
   ;; Kurikka
   "301-R" {:address    "Meijerin rantatie 15"
            :propertyId "30100899090001"
            :x          264636.0
            :y          6951244.0}})

(defn- default-location [{:keys [location-overrides location-fallback conversion-doc
                                 organization-id]} mode]
  (let [{:keys [backend-id location]} conversion-doc]
    (case mode
      :override (or location (get location-overrides backend-id))
      :fallback (or location-fallback (get fallback-location-infos organization-id)))))

(defn run-conversion!
  [{:keys [organization conversion-doc] :as options}]
  (let [kuntalupatunnus          (:backend-id conversion-doc)
        xml                      (conv-doc/read-xml conversion-doc)
        {location-info :rakennuspaikka
         :as           app-info} (krysp-reader/get-app-info-from-message
                                   xml kuntalupatunnus
                                   (partial default-location options))
        validation-result        (permit/validate-verdict-xml (:permit-type conversion-doc)
                                                              xml organization)
        no-proper-applicants?    (not-any? prev-permit/get-applicant-type (:hakijat app-info))
        nope!                    #(throw (ex-info %1 {:conversion-error %2}))]
    (when validation-result
      (log/warn "Has invalid verdict: " (:text validation-result)))
    (cond
      (empty? app-info)
      (nope! "No app-info available" :no-app-info)

      (not location-info)
      (nope! "No location info" :no-location-info)

      (not (:propertyId location-info))
      (nope! "No property-id" :no-property-id)

      :else
      (let [id (convert-application-from-xml options
                                             xml
                                             app-info
                                             location-info)]
        (if no-proper-applicants?
          (ok :id id :text :error.no-proper-applicants-found-from-previous-permit)
          (ok :id id))))))
