(ns lupapalvelu.digitizer
  (:require [sade.property :as p]
            [sade.core :refer :all]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.prev-permit :as pp]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]
            [lupapalvelu.application :as application]
            [sade.util :as util]
            [lupapalvelu.action :as action]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [sade.property :as prop]
            [sade.env :as env]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as operations]))

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
  (let [municipality      (prop/municipality-id-by-property-id propertyId)
        organization      (org/resolve-organization municipality permit-type)
        organization-id   (:id organization)]

    (when-not organization-id
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))

    (let [id (make-application-id municipality)]
      (application/make-application id
                                    operation
                                    x
                                    y
                                    address
                                    propertyId
                                    propertyIdSource
                                    municipality
                                    organization
                                    false
                                    false
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

(defn get-buildings [organization permit-type property-id]
  (when (and organization permit-type property-id)
    (when-let [{url :url credentials :credentials} (org/get-krysp-wfs {:_id organization} permit-type)]
      (->> (building-reader/building-xml url credentials property-id)
           building-reader/->buildings
           (map #(-> {:data %}))))))

(defn do-create-application-from-previous-permit
  [command operation buildings-and-structures app-info location-info permit-type]
  (let [{:keys [hakijat]} app-info
        document-datas (pp/schema-datas app-info buildings-and-structures)
        manual-schema-datas {"archiving-project" (first document-datas)}
        command (update-in command [:data] merge
                           {:operation operation :infoRequest false :messages []}
                           location-info)
        created-application (do-create-application command manual-schema-datas permit-type)

        structure-descriptions (map :description buildings-and-structures)
        created-application (assoc-in created-application [:primaryOperation :description] (first structure-descriptions))

        ;; make secondaryOperations for buildings other than the first one in case there are many
        other-building-docs (map (partial document-data->op-document created-application) (rest document-datas))
        secondary-ops (mapv #(assoc (-> %1 :schema-info :op) :description %2) other-building-docs (rest structure-descriptions))

        created-application (update-in created-application [:documents] concat other-building-docs)
        created-application (update-in created-application [:secondaryOperations] concat secondary-ops)

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]
    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (application/insert-application created-application)

    (add-applicant-documents command hakijat)

    (let [fetched-application (mongo/by-id :applications (:id created-application))]
      (mongo/update-by-id :applications (:id fetched-application) (meta-fields/applicant-index-update fetched-application))
      fetched-application)))

(defn get-location-info [{data :data :as command} app-info]
  (let [rakennuspaikka-exists? (and (:rakennuspaikka app-info)
                                    (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))]
    (cond
      rakennuspaikka-exists?                             (:rakennuspaikka app-info)
      (pp/enough-location-info-from-parameters? command) (select-keys data [:x :y :address :propertyId]))))

(defn fetch-or-create-archiving-project! [{{:keys [organizationId kuntalupatunnus createAnyway]} :data :as command}]
  (let [operation         :archiving-project
        permit-type       "R"                                ; No support for other permit types currently
        dummy-application {:id "" :permitType permit-type :organization organizationId}
        xml               (krysp-fetch/get-application-xml-by-backend-id dummy-application kuntalupatunnus)
        app-info          (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        {:keys [propertyId] :as location-info} (get-location-info command app-info)
        organization      (when propertyId
                            (organization/resolve-organization (p/municipality-id-by-property-id propertyId) permit-type))
        buildings-and-structures (or (when app-info (building-reader/->buildings-and-structures xml))
                                     (get-buildings organizationId permit-type propertyId))
        organizations-match?  (= organizationId (:id organization))]
    (cond
      (and (empty? app-info)
           (not createAnyway))          (fail :error.no-previous-permit-found-from-backend :permitNotFound true)
      (not location-info)               (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
      (not (:propertyId location-info)) (fail :error.previous-permit-no-propertyid)
      (not organizations-match?)        (fail :error.previous-permit-found-from-backend-is-of-different-organization)
      :else                             (let [{id :id} (do-create-application-from-previous-permit command
                                                                                                   operation
                                                                                                   buildings-and-structures
                                                                                                   app-info
                                                                                                   location-info
                                                                                                   permit-type)]
                                          (ok :id id)))))
