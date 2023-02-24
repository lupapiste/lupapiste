(ns lupapalvelu.digitizer
  (:require [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.building :as building]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.prev-permit :as pp]
            [lupapalvelu.property :as prop]
            [lupapalvelu.property-location :as prop-info]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

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

(defn- resolve-municipality!
  "Returns municipality code or fail! if the code is not supported by the organization."
  [{scope :scope} property-id]
  (let [municipality (prop/municipality-by-property-id property-id)]
    ;; Currently only R permit type is supported by the digitizer.
    (if (util/find-by-keys {:municipality municipality :permitType "R"} scope)
      municipality
      (fail! :error.bad-municipality))))

(defn do-create-archiving-application
  [{{:keys [operation x y address propertyId propertyIdSource messages]} :data :keys [user created]} manual-schema-datas organization]
  {:pre [(some? organization)]}
  (let [municipality     (resolve-municipality! organization propertyId)
        id               (make-application-id municipality)
        application-info (util/assoc-when-pred
                           {:id             id
                            :organization   organization
                            :operation-name operation
                            :location       (app/->location x y)
                            :propertyId     propertyId
                            :address        address}
                           ss/not-blank?
                           :propertyIdSource propertyIdSource
                           :municipality municipality)]
    (app/make-application application-info
                          messages
                          user
                          created
                          manual-schema-datas)))

(defn schema-datas [{:keys [rakennusvalvontaasianKuvaus] :as app-info} buildings]
  (if (empty? buildings)
    (schema-datas app-info {:data []})
    (map
      (fn [{:keys [data]}]
        (remove empty? (conj [[[:valtakunnallinenNumero] (:valtakunnallinenNumero data)]
                              [[:kaytto :kayttotarkoitus] (get-in data [:kaytto :kayttotarkoitus])]
                              [[:kaytto :rakennusluokka] (get-in data [:kaytto :rakennusluokka])]]
                             (when-not (or (ss/blank? (:rakennusnro data))
                                           (= "000" (:rakennusnro data)))
                               [[:tunnus] (:rakennusnro data)])
                             (when-not (ss/blank? rakennusvalvontaasianKuvaus)
                               [[:kuvaus] rakennusvalvontaasianKuvaus]))))
      buildings)))

(defn fetch-building-xml [organization permit-type property-id]
  (when (and organization permit-type property-id)
    (when-let [{url :url credentials :credentials} (org/get-building-wfs {:_id organization} permit-type)]
      (building-reader/building-xml url credentials property-id))))

(defn update-buildings-array! [application buildings all-buildings]
  (let [primary-building-id (:buildingId (first buildings))
        buildings (if (true? all-buildings)
                    buildings
                    (filter #(= (:buildingId %) primary-building-id) buildings))
        find-op-id (fn [nid]
                     (->> (filter #(= (:national-id %) nid) (building/building-ids application))
                          first
                          :operation-id))
        updated-buildings (map
                            (fn [{:keys [nationalId] :as bldg}]
                              (-> (select-keys bldg [:localShortId :buildingId :localId :nationalId :location-wgs84 :location])
                                  (assoc :operationId (find-op-id nationalId))))
                            buildings)]
    (when (seq updated-buildings)
      (mongo/update-by-id :applications
                          (:id application)
                          {$set {:buildings updated-buildings}}))))

(defn- parse-buildings! [org-id property-id]
  (let [building-xml      (fetch-building-xml org-id "R" property-id)
        buildings-summary (some->> building-xml
                                   building-reader/->buildings-summary
                                   (remove #(some-> % :location coord/validate-coordinates)))
        build-ids         (set (keep :nationalId buildings-summary))]
    (if (empty? build-ids)
      (fail! :error.no-buildings-found)
      {:buildings         (->> building-xml
                               building-reader/buildings-for-documents
                               (filter (util/fn->> :data :valtakunnallinenNumero
                                                   (contains? build-ids))))
       :buildings-summary buildings-summary
       :build-ids         build-ids})))

(defn fetch-buildings
  "Update application buildings and the corresponding operations and documents. The fetched
  buildings are synchronized with the current application buildings: old buildings that
  are not included in the fetch results are removed together with the linked operations
  and documents. New buildings result in new documents and operations.

  Buildings with invalid coordinates are ignored.

  Fail!s immediately if there are no buildings. Thus, the old buildings are not
  removed. While this can result in wrong buildings in the wrong property, it is still
  safer than nuking all the buildings when backing system glitches and returns an empty
  response in error. In practise, the change location dialog shows an error."
  [{:keys [application] :as command} propertyId]
  (let [{:keys [buildings buildings-summary
                build-ids]} (parse-buildings! (:organization application)
                                              propertyId)
        ;; List of :national-id, :operation-id, :short-id maps
        keep-id-data        (some->> (building/building-ids application)
                                     (filter (comp build-ids :national-id))
                                     not-empty)
        keep-opids          (some->> keep-id-data (map :operation-id))
        keep-bids           (some->> keep-id-data (map :national-id))
        rm-docs             (remove (util/fn->> :schema-info :op :id (util/includes-as-kw? keep-opids))
                                    (domain/get-documents-by-name application "archiving-project"))
        [new-docs
         build-op-ids]      (some->> buildings
                                     (remove (util/fn->> :data :valtakunnallinenNumero (util/includes-as-kw? keep-bids)))
                                     not-empty
                                     (map (fn [build]
                                            (let [doc (->> (schema-datas nil [build])
                                                           first
                                                           (app/document-data->op-document application))]
                                              {:doc      (assoc-in doc [:schema-info :op :description] (:description build))
                                               :build-id (-> build :data :valtakunnallinenNumero)
                                               :op-id    (-> doc :schema-info :op :id)})))
                                     (reduce (fn [[docs ids] {:keys [doc build-id op-id]}]
                                               [(conj docs doc)
                                                (assoc ids build-id op-id)])
                                             [[] {}]))
        build-op-ids        (merge build-op-ids (zipmap keep-bids keep-opids))
        ops                 (concat  (filter #(util/includes-as-kw? keep-opids (:id %))
                                             (cons (:primaryOperation application)
                                                   (:secondaryOperations application)))
                                     (map (util/fn-> :schema-info :op) new-docs))]
    (run! #(doc-persistence/remove! command %) rm-docs)
    (action/update-application
      command
      (cond-> {$set {:primaryOperation    (first ops)
                     :secondaryOperations (rest ops)
                     :buildings           (map (fn [{:keys [nationalId] :as bldg}]
                                                 (-> bldg
                                                     (select-keys [:localShortId :buildingId
                                                                   :localId :nationalId
                                                                   :location-wgs84 :location])
                                                     (assoc :operationId (get build-op-ids nationalId))))
                                               buildings-summary)}}
        (seq new-docs)
        (assoc $push {:documents {$each new-docs}})))))

(defn remove-secondary-buildings [{:keys [application] :as command}]
  (let [building-docs (domain/get-documents-by-name application "archiving-project")
        primary-op-id (get-in application [:primaryOperation :id])
        secondary-building-docs (filter #(not= (-> % :schema-info :op :id) primary-op-id) building-docs)
        secondary-buildings (filter #(not= (:operationId %) primary-op-id) (:buildings application))]
    (run! #(doc-persistence/remove! command  %) secondary-building-docs)
    (action/update-application command {$pull {:buildings {:buildingId {$in (map :buildingId secondary-buildings)}}}})))

(defn add-other-building-docs [created-application document-datas structure-descriptions]
  (let [;; make secondaryOperations for buildings other than the first one in case there are many
        other-building-docs (map (partial app/document-data->op-document created-application) (rest document-datas))
        secondary-ops (mapv #(assoc (-> %1 :schema-info :op) :description %2) other-building-docs (rest structure-descriptions))

        created-application (update-in created-application [:documents] concat other-building-docs)
        created-application (update-in created-application [:secondaryOperations] concat secondary-ops)]
    created-application))

(defn create-archiving-project-application!
  [command organization buildings-and-structures app-info app-xml buildings-summary backend-id refreshBuildings]
  (let [{:keys [hakijat]} app-info
        document-datas (schema-datas app-info buildings-and-structures)
        manual-schema-datas {"archiving-project" (first document-datas)}
        organization-id (get-in command [:data :organizationId])
        structure-descriptions (map :description buildings-and-structures)
        created-application (-> (do-create-archiving-application command manual-schema-datas organization)
                                (assoc-in [:primaryOperation :description] (first structure-descriptions))
                                (assoc-in [:archived :initial] (:created command)) ;;LPK-5202 to force export to salesforce
                                (assoc :drawings (:drawings app-info)))
        created-application (cond-> created-application
                                    (usr/user-has-role-in-organization? (:user command) organization-id [:digitization-project-user])
                                    (assoc :non-billable-application true)

                                    (true? refreshBuildings)
                                    (add-other-building-docs document-datas structure-descriptions))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]
    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (app/insert-application created-application)

    (let [updates (or (when app-info (verdict/find-verdicts-from-xml command app-xml false))
                      (verdict/backend-id-mongo-updates {} [backend-id] false))]
      (action/update-application command updates))

    (add-applicant-documents command hakijat)

    (let [fetched-application (mongo/by-id :applications (:id created-application))]
      (mongo/update-by-id :applications (:id fetched-application) (meta-fields/applicant-index-update fetched-application))
      (when buildings-summary
        (update-buildings-array! fetched-application buildings-summary refreshBuildings))
      fetched-application)))


(defn default-location [organization lang]
  {:x          (get-in organization [:default-digitalization-location :x])
   :y          (get-in organization [:default-digitalization-location :y])
   :address    (i18n/localize lang "digitizer.location.missing")
   :propertyId (ss/join (concat (first (split-at 3 (:id organization))) "00000000000"))})

(defn- good-buildings
  "Filter out buildings with invalid coordinates. Nil if no good buildings."
  [buildings-summary]
  (seq (remove #(some-> % :location coord/validate-coordinates) buildings-summary)))

(defn fetch-or-create-archiving-project!
  [{{:keys [lang organizationId kuntalupatunnus createWithoutPreviousPermit createWithoutBuildings createWithDefaultLocation refreshBuildings]} :data :as command}]
  (let [operation             "archiving-project"
        permit-type           "R" ; No support for other permit types currently
        dummy-application     {:id "" :permitType permit-type :organization organizationId}
        xml                   (krysp-fetch/get-application-xml-by-backend-id dummy-application kuntalupatunnus)
        {:keys [rakennuspaikka]
         :as   app-info}      (krysp-reader/get-app-info-from-message xml kuntalupatunnus
                                                                      (:data command))
        ;; Primarily get building information from kuntalupatunnus XML, if able
        app-bldgs-and-structs (when app-info
                                (building-reader/->buildings-and-structures xml
                                                                            {:include-personal-owner-info? true}))
        app-buildings-summary (when app-info (seq (building-reader/->buildings-summary xml)))
        organization          (org/get-organization organizationId)
        {:keys [propertyId x y]
         :as   location-info} (cond
                                createWithDefaultLocation (default-location organization lang)
                                rakennuspaikka            rakennuspaikka
                                :else
                                (krysp-reader/resolve-valid-location nil (:data command)))
        command               (update-in command [:data] merge
                                         {:operation operation :infoRequest false :messages []}
                                         location-info)
        ;; Building fallback if createWithoutPreviousPermit is true, and no XML was found for kuntalupatunnus
        building-xml          (when-not app-info (fetch-building-xml organizationId permit-type propertyId))
        bldgs-and-structs     (or app-bldgs-and-structs (building-reader/buildings-for-documents building-xml))
        buildings-summary     (good-buildings (or app-buildings-summary
                                                  (building-reader/->buildings-summary building-xml)))
        location-fail         (coord/validate-coordinates [x y])]
    (cond
      (and (empty? app-info)
           (not createWithoutPreviousPermit)) (fail :error.no-previous-permit-found-from-backend :permitNotFound true)
      (not location-info)                     (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
      (and (not (:propertyId location-info))
           (not createWithDefaultLocation))   (fail :error.previous-permit-no-propertyid)
      (and (empty? bldgs-and-structs)
           (not createWithoutBuildings))      (fail :error.no-buildings-found-from-backend :buildingsNotFound true)
      location-fail                           location-fail
      :else                                   (let [{id :id} (create-archiving-project-application! command
                                                                                                    organization
                                                                                                    bldgs-and-structs
                                                                                                    app-info
                                                                                                    xml
                                                                                                    buildings-summary
                                                                                                    kuntalupatunnus
                                                                                                    refreshBuildings)]
                                                (ok :id id)))))

(defn- set-paatospvm [verdict ts]
  (if (get-in verdict [:paatokset 0 :poytakirjat 0])
    (assoc-in verdict [:paatokset 0 :poytakirjat 0 :paatospvm] ts)
    (util/deep-merge verdict {:paatokset [{:poytakirjat [{:paatospvm ts}]}]})))

(defn- resolve-verdict-updates [current-verdicts verdicts]
  (let [old-ids           (->> current-verdicts (map :id) set)
        modified-verdicts (->> verdicts
                               (filter #(contains? old-ids (:id %)))
                               (reduce (fn [acc {:keys [id kuntalupatunnus verdictDate]
                                                 :as   v}]
                                         (let [verdict  (util/find-by-id id (concat acc current-verdicts))
                                               back-id? (contains? v :kuntalupatunnus)
                                               date?    (contains? v :verdictDate)]
                                           (conj acc
                                                 (cond-> verdict
                                                   back-id? (assoc :kuntalupatunnus kuntalupatunnus)
                                                   date?    (set-paatospvm verdictDate)))))
                                       []))
        new-backend-id?       (fn [xs back-id]
                                (not (util/find-by-key :kuntalupatunnus back-id xs)))]
    (->> verdicts
         (remove :id)
         (reduce (fn [acc {:keys [kuntalupatunnus]}]
                   (cond-> acc
                     (new-backend-id? acc kuntalupatunnus)
                     (conj (verdict/backend-id->verdict kuntalupatunnus false))))
                 modified-verdicts))))

(defn update-verdicts
  "Upserts application backing-system/dummy verdicts. `verdicts` includes `:id`, `:kuntalupatunnus`
  and `:verdictDate`. For new verdicts, `:id` is nil (in other words, if a 'new' verdict has a
  non-nil id, it is ignored).

  Note 1: New verdicts and created blank with only `:kuntalupatunnus`

  Note 2: Any old (current) verdict that is not included in `verdicts` is removed."
  [{:keys [application] :as command} verdicts]
  (action/update-application command
                             {$set {:verdicts (resolve-verdict-updates (:verdicts application)
                                                                       verdicts)}}))

(defn update-verdict-date [command date]
  (action/update-application command
                             {$set {:verdicts.0.paatokset [{:id (mongo/create-id)
                                                            :poytakirjat [{:paatospvm date}]}]}}))
