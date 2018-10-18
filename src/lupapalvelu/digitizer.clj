(ns lupapalvelu.digitizer
  (:require [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.prev-permit :as pp]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.application :as app]
            [lupapalvelu.action :as action]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.organization :as org]
            [lupapalvelu.property :as prop]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.i18n :as i18n]
            [monger.operators :refer :all]))

(defn- get-applicant-type [applicant]
  (-> applicant (select-keys [:henkilo :yritys]) keys first))

(defn- add-applicant-documents [{:keys [created application] :as command} applicants]
  (let [applicants (filter get-applicant-type applicants)]
    (dorun
      (->> applicants
           (map-indexed
             (fn [i applicant]
               ;; Set applicants' user info to Hakija documents
               (let [document (if (zero? i)
                                (domain/get-applicant-document (:documents application))
                                (doc-persistence/do-create-doc! command (operations/get-applicant-doc-schema-name application)))
                     applicant-type (get-applicant-type applicant)
                     user-info (case applicant-type
                                 :henkilo {:firstName (get-in applicant [:henkilo :nimi :etunimi])
                                           :lastName (get-in applicant [:henkilo :nimi :sukunimi])
                                           :turvakieltokytkin (:turvakieltoKytkin applicant)}

                                 :yritys {:name (get-in applicant [:yritys :nimi])})]

                 (doc-persistence/set-subject-to-document application document user-info (name applicant-type) created))))))))

(defn make-application-id [municipality]
  (let [year (str (year (local-now)))
        sequence-name (str "archivals-" municipality "-" year)
        counter (if (env/feature? :prefixed-id)
                  (format "9%04d" (mongo/get-next-sequence-value sequence-name))
                  (format "%05d"  (mongo/get-next-sequence-value sequence-name)))]
    (str "LX-" municipality "-" year "-" counter)))

(defn do-create-application
  [{{:keys [operation x y address propertyId propertyIdSource messages]} :data :keys [user created]} manual-schema-datas permit-type]
  (let [municipality      (prop/municipality-by-property-id propertyId)
        organization      (org/resolve-organization municipality permit-type)
        organization-id   (:id organization)]

    (when-not organization-id
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))

    (let [id (make-application-id municipality)
          application-info (util/assoc-when-pred
                             {:id id
                              :organization organization
                              :operation-name operation
                              :location (app/->location x y)
                              :propertyId propertyId
                              :address address}
                             ss/not-blank?
                             :propertyIdSource propertyIdSource
                             :municipality municipality)]
      (app/make-application application-info
                            messages
                            user
                            created
                            manual-schema-datas))))

(defn document-data->op-document [{:keys [schema-version] :as application} data]
  (let [op (app/make-op :archiving-project (now))
        doc (doc-persistence/new-doc application (schemas/get-schema schema-version "archiving-project") (now))
        doc (assoc-in doc [:schema-info :op] op)
        doc-updates (lupapalvelu.document.model/map2updates [] data)]
    (lupapalvelu.document.model/apply-updates doc doc-updates)))

(defn fetch-building-xml [organization permit-type property-id]
  (when (and organization permit-type property-id)
    (when-let [{url :url credentials :credentials} (org/get-building-wfs {:_id organization} permit-type)]
      (building-reader/building-xml url credentials property-id))))


(defn add-other-building-docs [created-application document-datas structure-descriptions]
  (let [;; make secondaryOperations for buildings other than the first one in case there are many
        other-building-docs (map (partial app/document-data->op-document created-application) (rest document-datas))
        secondary-ops (mapv #(assoc (-> %1 :schema-info :op) :description %2) other-building-docs (rest structure-descriptions))

        created-application (update-in created-application [:documents] concat other-building-docs)
        created-application (update-in created-application [:secondaryOperations] concat secondary-ops)]
    created-application))

(defn create-archiving-project-application!
  [command operation buildings-and-structures app-info location-info permit-type building-xml backend-id refreshBuildings]
  (let [{:keys [hakijat]} app-info
        document-datas (app/schema-datas app-info buildings-and-structures)
        manual-schema-datas {"archiving-project" (first document-datas)}
        command (update-in command [:data] merge
                           {:operation operation :infoRequest false :messages []}
                           location-info)
        created-application (do-create-application command manual-schema-datas permit-type)

        structure-descriptions (map :description buildings-and-structures)
        created-application (assoc-in created-application [:primaryOperation :description] (first structure-descriptions))

        created-application (if (true? refreshBuildings)
                              (add-other-building-docs created-application document-datas structure-descriptions)
                              created-application)

        created-application (assoc created-application :drawings (:drawings app-info))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]
    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (app/insert-application created-application)

    (let [updates (or (verdict/find-verdicts-from-xml command building-xml false)
                      (verdict/backend-id-mongo-updates {} [backend-id]))]
      (action/update-application command updates))

    (add-applicant-documents command hakijat)

    (let [fetched-application (mongo/by-id :applications (:id created-application))]
      (mongo/update-by-id :applications (:id fetched-application) (meta-fields/applicant-index-update fetched-application))
      (app/update-buildings-array! building-xml fetched-application refreshBuildings)
      fetched-application)))

(defn get-location-info [{data :data :as command} app-info]
  (let [rakennuspaikka-exists? (and (:rakennuspaikka app-info)
                                    (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))]
    (cond
      rakennuspaikka-exists?                             (:rakennuspaikka app-info)
      (pp/enough-location-info-from-parameters? command) (select-keys data [:x :y :address :propertyId]))))

(defn default-location [organization lang]
  {:x          (get-in organization [:default-digitalization-location :x])
   :y          (get-in organization [:default-digitalization-location :y])
   :address    (i18n/localize lang "digitizer.location.missing")
   :propertyId (ss/join (concat (first (split-at 3 (:id organization))) "00000000000"))})

(defn fetch-or-create-archiving-project!
  [{{:keys [lang organizationId kuntalupatunnus createWithoutPreviousPermit createWithoutBuildings createWithDefaultLocation refreshBuildings]} :data :as command}]
  (let [operation         "archiving-project"
        permit-type       "R"                                ; No support for other permit types currently
        dummy-application {:id "" :permitType permit-type :organization organizationId}
        xml               (krysp-fetch/get-application-xml-by-backend-id dummy-application kuntalupatunnus)
        app-info          (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        organization      (org/get-organization organizationId)
        {:keys [propertyId] :as location-info} (if createWithDefaultLocation
                                                 (default-location organization lang)
                                                 (get-location-info command app-info))
        building-xml      (if app-info xml (app/fetch-building-xml organizationId permit-type propertyId))
        bldgs-and-structs (or (when app-info (building-reader/->buildings-and-structures xml))
                              (building-reader/buildings-for-documents building-xml))]
    (cond
      (and (empty? app-info)
           (not createWithoutPreviousPermit)) (fail :error.no-previous-permit-found-from-backend :permitNotFound true)
      (not location-info)                     (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
      (and (not (:propertyId location-info))
           (not createWithDefaultLocation))   (fail :error.previous-permit-no-propertyid)
      (and (empty? bldgs-and-structs)
           (not createWithoutBuildings))      (fail :error.no-buildings-found-from-backend :buildingsNotFound true)
      :else                                   (let [{id :id} (create-archiving-project-application! command
                                                                                                    operation
                                                                                                    bldgs-and-structs
                                                                                                    app-info
                                                                                                    location-info
                                                                                                    permit-type
                                                                                                    building-xml
                                                                                                    kuntalupatunnus
                                                                                                    refreshBuildings)]
                                            (ok :id id)))))

(defn update-verdicts [{:keys [application] :as command} verdicts]
  (let [current-verdicts (:verdicts application)
        modified-verdicts (filter (fn [verdict]
                                    (some #(and (= (:id verdict) (:id %))
                                                (or (not= (:kuntalupatunnus verdict) (:kuntalupatunnus %))
                                                    (and  (not= (:verdictDate verdict) nil)
                                                          (not= (:verdictDate verdict) (:paatospvm (first (:poytakirjat (first (:paatokset %))))))))
                                                ) current-verdicts))
                                  verdicts)
        removed-verdicts (remove #(contains? (set (map :id verdicts)) (:id %)) current-verdicts)
        new-verdicts (filter #(nil? (:id %)) verdicts)]
    (doseq [{:keys [id kuntalupatunnus verdictDate]} modified-verdicts]
      (action/update-application command
                                 {:verdicts {$elemMatch {:id id}}}
                                 {$set {:verdicts.$.kuntalupatunnus kuntalupatunnus
                                        :verdicts.$.paatokset.0.poytakirjat.0.paatospvm verdictDate}}))
    (doseq [{:keys [id]} removed-verdicts]
      (action/update-application command
                                 {$pull {:verdicts {:id id}}}))
    (some->> (seq (map :kuntalupatunnus new-verdicts))
             (verdict/backend-id-mongo-updates application)
             (action/update-application command))))

(defn update-verdict-date [command date]
  (action/update-application command
                             {$set {:verdicts.0.paatokset [{:id (mongo/create-id)
                                                            :poytakirjat [{:paatospvm date}]}]}}))
