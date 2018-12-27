(ns lupapalvelu.conversion.kuntagml-converter
  (:require [taoensso.timbre :refer [info infof warn error errorf]]
            [clojure.string :refer [includes?]]
            [sade.core :refer :all]
            [sade.util :as util]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]))

(defn convert-application-from-xml [command operation organization xml app-info location-info authorize-applicants]
  (let [{:keys [hakijat]} app-info
        municipality "092"
        buildings-and-structures (building-reader/->buildings-and-structures xml)
        document-datas (prev-permit/schema-datas app-info buildings-and-structures)
        command (update-in command [:data] merge
                           {:operation operation :infoRequest false :messages []}
                           location-info)

        operations (:toimenpiteet app-info)
        kuntalupatunnus (krysp-reader/xml->kuntalupatunnus xml)
        id (conv-util/make-converted-application-id kuntalupatunnus)
        description (or (building-reader/->asian-tiedot xml)
                        "") ;; So that regex checks on this don't throw errors, should the field be empty.
        primary-op-name (if (seq operations)
                          (conv-util/deduce-operation-type kuntalupatunnus description (first operations))
                          (conv-util/deduce-operation-type kuntalupatunnus description))

        schema-name (-> primary-op-name conv-util/op-name->schema-name name)

        manual-schema-datas {schema-name (app/sanitize-document-datas (schemas/get-schema 1 schema-name) (first document-datas))}

        secondary-op-names (map (partial conv-util/deduce-operation-type kuntalupatunnus description) (rest operations))

        make-app-info {:id              id
                       :organization    organization
                       :operation-name  primary-op-name
                       :location        (app/->location (:x location-info) (:y location-info))
                       :propertyId      (:propertyId location-info)
                       :address         (:address location-info)
                       :municipality    municipality}

        created-application (app/make-application make-app-info
                                                  []            ; messages
                                                  (:user command)
                                                  (:created command)
                                                  manual-schema-datas)

        new-parties (remove empty?
                            (concat (map prev-permit/suunnittelija->party-document (:suunnittelijat app-info))
                                    (map prev-permit/osapuoli->party-document (:muutOsapuolet app-info))
                                    (when (includes? kuntalupatunnus "TJO")
                                      (map prev-permit/tyonjohtaja->tj-document (:tyonjohtajat app-info)))))

        location-document (->> xml
                               building-reader/->rakennuspaikkatieto
                               (conv-util/rakennuspaikkatieto->rakennuspaikka-kuntagml-doc kuntalupatunnus)
                               (conj []))

        structure-descriptions (map :description buildings-and-structures)

        other-building-docs (map (partial app/document-data->op-document created-application) (rest document-datas) secondary-op-names)

        secondary-ops (mapv #(assoc (-> %1 :schema-info :op) :description %2 :name %3) other-building-docs (rest structure-descriptions) secondary-op-names)

        structures (->> xml krysp-reader/->rakennelmatiedot (map conv-util/rakennelmatieto->kaupunkikuvatoimenpide))

        statements (->> xml krysp-reader/->lausuntotiedot (map prev-permit/lausuntotieto->statement))

        state-changes (krysp-reader/get-sorted-tilamuutos-entries xml)

        ;; Siirretaan lausunnot luonnos-tilasta "lausunto annettu"-tilaan
        given-statements (for [st statements
                               :when (map? st)]
                           (try
                             (statement/give-statement st
                                                       (:saateText st)
                                                       (get-in st [:metadata :puoltotieto])
                                                       (mongo/create-id)
                                                       (mongo/create-id)
                                                       false)
                             (catch Exception e
                               (errorf "Moving statement to statement given -state failed: %s" (.getMessage e)))))

        history-array (conv-util/generate-history-array xml)

        created-application (-> created-application
                                (assoc-in [:primaryOperation :description] (first structure-descriptions))
                                (conv-util/add-description xml) ;; Add descriptions from asianTiedot to the document.
                                conv-util/remove-empty-rakennuspaikka ;; Remove empty rakennuspaikka-document that comes from the template
                                (conv-util/add-timestamps history-array) ;; Add timestamps for different state changes
                                (update-in [:documents] concat other-building-docs new-parties structures ;; Assemble the documents-array
                                           (when-not (includes? kuntalupatunnus "TJO") location-document))
                                (update-in [:secondaryOperations] concat secondary-ops)
                                (assoc :statements given-statements
                                       :opened (:created command)
                                       :history history-array
                                       :state :closed ;; Asetetaan hanke "päätös annettu"-tilaan
                                       :facta-imported true))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]
    (logging/with-logging-context {:applicationId (:id created-application)}
      ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
      (app/insert-application created-application)
      (infof "Inserted prev-permit app: org=%s kuntalupatunnus=%s authorizeApplicants=%s"
             (:organization created-application)
             (get-in command [:data :kuntalupatunnus])
             authorize-applicants)
      ;; Get verdicts for the application
      (when-let [updates (verdict/find-verdicts-from-xml command xml false)]
        (action/update-application command updates))

      (prev-permit/invite-applicants command hakijat authorize-applicants)
      (infof "Processed applicants, processable applicants count was: %s" (count (filter prev-permit/get-applicant-type hakijat)))

      (let [updated-application (mongo/by-id :applications (:id created-application))
            {:keys [updates added-tasks-with-updated-buildings attachments-by-task-id]} (review/read-reviews-from-xml usr/batchrun-user-data (now) updated-application xml false true)
            review-command (assoc (action/application->command updated-application (:user command)) :action "prev-permit-review-updates")
            update-result (review/save-review-updates review-command updates added-tasks-with-updated-buildings attachments-by-task-id)]
        (if (:ok update-result)
          (info "Saved review updates")
          (infof "Reviews were not saved: %s" (:desc update-result))))

      ;; The database may already include the same kuntalupatunnus as in the to be imported application
      ;; (e.g., the application has been imported earlier via previous permit (paperilupa) mechanism).
      ;; This kind of application 1) has the same kuntalupatunnus and 2) :facta-imported is falsey.
      ;; After import, the two applications are linked (viitelupien linkkaus).
      (let [app-links (map conv-util/normalize-permit-id (krysp-reader/->viitelupatunnukset xml))
            duplicate-ids (conv-util/get-duplicate-ids kuntalupatunnus)
            all-links (clojure.set/union (set app-links) (set duplicate-ids))]
        (infof (format "Linking %d app-links to application %s" (count all-links) (:id created-application)))
        (doseq [link all-links]
          (try
            (app/do-add-link-permit created-application link)
            (catch Exception e
              (error "Adding app-link %s -> %s failed: %s" (:id created-application) link (.getMessage e))))))

      (let [fetched-application (mongo/by-id :applications (:id created-application))]
        (mongo/update-by-id :applications (:id fetched-application) (meta-fields/applicant-index-update fetched-application))
        fetched-application))))

(def supported-import-types #{:TJO :A :B :C :D :E :P :Z :AJ :AL :MAI :BJ :PI :BL :DJ :CL :PJ})

(defn- validate-permit-type [permittype]
  (when-not (contains? supported-import-types (keyword permittype))
    (error-and-fail! (str "Unsupported import type " permittype) :error.unsupported-permit-type)))

(defn fetch-prev-local-application!
  "A variation of `lupapalvelu.prev-permit/fetch-prev-local-application!` that exists for conversion
  and testing purposes. Creates an application from Krysp message in a local file. To use a local Krysp
  file:
  1) The local MongoDB has to contain the location info for the municipality in question (here Vantaa)
  2) this function needs to be called from prev-permit-api/create-application-from-previous-permit instead of
  prev-permit/fetch-prev-application!"
  [{{:keys [kuntalupatunnus authorizeApplicants]} :data :as command}]
  (let [organizationId        "092-R" ;; Vantaa, bypass the selection from form
        destructured-permit-id (conv-util/destructure-permit-id kuntalupatunnus)
        operation             "konversio"
        filename              (format "%s/%s.xml" (:resource-path conv-util/config) kuntalupatunnus ".xml")
        permit-type           "R"
        xml                   (krysp-fetch/get-local-application-xml-by-filename filename permit-type)
        app-info              (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        location-info         (or (prev-permit/get-location-info command app-info)
                                  prev-permit/default-location-info)
        organization          (org/get-organization organizationId)
        validation-result     (permit/validate-verdict-xml permit-type xml organization)
        no-proper-applicants? (not-any? prev-permit/get-applicant-type (:hakijat app-info))]
    (validate-permit-type (:tyyppi destructured-permit-id))
    (when validation-result
      (warn "Has invalid verdict: " (:text validation-result)))
    (cond
      (empty? app-info)                 (error-and-fail! "No app-info available" :error.no-previous-permit-found-from-backend)
      (not location-info)               (error-and-fail! "No location info" :error.more-prev-app-info-needed)
      (not (:propertyId location-info)) (error-and-fail! "No property-id" :error.previous-permit-no-propertyid)
      :else                             (let [{id :id} (convert-application-from-xml command
                                                                                     operation
                                                                                     organization
                                                                                     xml
                                                                                     app-info
                                                                                     location-info
                                                                                     authorizeApplicants)]
                                          (if no-proper-applicants?
                                            (ok :id id :text :error.no-proper-applicants-found-from-previous-permit)
                                            (ok :id id))))))

(defn debug [command]
  (fetch-prev-local-application! command))

(defn batch-convert [kuntalupatunnukset]
  (doseq [k kuntalupatunnukset]
    (try
      (fetch-prev-local-application! {:created (now)
                                      :data {:kuntalupatunnus k}
                                      :user {:email "sonja.sibbo@sipoo.fi"
                                             :firstName "Sonja"
                                             :id "777777777777777777000023"
                                             :language "fi"
                                             :lastName "Sibbo"
                                             :role "authority"
                                             :username "sonja"}})
      (catch Exception e
        (info (.getMessage e))))))

(def testset
  ["12-0093-13-P"
   "12-0189-13-P"
   "12-0696-13-MAI"
   "13-0161-13-BJ"
   "14-0006-13-D"
   "14-0010-13-C"
   "14-1296-13-BL"
   "16-0474-13-BJ"
   "16-0822-13-MAI"
   "17-0219-13-Z"
   "18-0012-13-D"
   "18-0483-13-PI"
   "20-0062-13-Z"
   "21-0150-13-BJ"
   "23-0023-13-C"
   "23-0033-13-AJ"
   "23-0441-13-Z"
   "24-0021-13-D"
   "24-0058-13-B"
   "33-0036-13-C"
   "33-0280-13-Z"
   "33-0440-13-Z"
   "40-0049-13-P"
   "40-0066-13-AJ"
   "40-0185-13-AL"
   "40-0403-13-PI"
   "40-0593-13-PJ"
   "41-0057-13-AL"
   "41-0077-13-MAI"
   "41-0809-13-CL"
   "41-1129-13-BL"
   "50-0002-13-D"
   "50-0008-13-D"
   "50-0211-13-P"
   "51-0039-13-B"
   "51-0104-13-B"
   "51-0516-13-BL"
   "51-0629-13-DJ"
   "51-0965-13-BL"
   "52-0102-13-B"
   "52-0120-13-AL"
   "52-0839-13-BL"
   "60-0026-13-C"
   "60-0103-13-AL"
   "61-0016-13-D"
   "61-0017-13-D"
   "61-0055-13-P"
   "61-1234-13-DJ"
   "62-0065-13-D"
   "62-0461-13-BJ"
   "64-0060-13-Z"
   "64-0115-13-AL"
   "65-0210-13-MAI"
   "65-0851-13-MAI"
   "66-0384-13-BJ"
   "68-0037-13-C"
   "68-0153-13-P"
   "68-0437-13-BL"
   "68-1147-13-BL"
   "70-0292-13-BJ"
   "70-0703-13-CL"
   "71-0004-13-C"
   "71-0575-13-BJ"
   "72-0379-13-Z"
   "73-0401-13-PI"
   "73-0661-13-MAI"
   "73-0840-13-PI"
   "73-0905-13-PI"
   "74-0007-13-D"
   "74-0136-13-BL"
   "74-0178-13-AL"
   "75-0014-13-C"
   "75-0022-13-C"
   "75-0029-13-B"
   "75-0367-13-PI"
   "75-0383-13-BJ"
   "75-0530-13-DJ"
   "80-0699-13-PI"
   "81-0050-13-AJ"
   "81-1004-13-PI"
   "83-0031-13-C"
   "83-0038-13-C"
   "83-0092-13-B"
   "83-0246-13-P"
   "83-0525-13-BL"
   "83-0553-13-BJ"
   "84-0130-13-B"
   "85-0253-13-PI"
   "86-0001-13-AJ"
   "86-0003-13-AJ"
   "86-0025-13-AJ"
   "86-0028-13-AJ"
   "86-0081-13-AJ"
   "86-0123-13-AJ"
   "86-0497-13-MAI"
   "90-0243-13-P"
   "92-0018-13-B"
   "96-0020-13-A"
   "96-0117-13-P"
   "96-0326-13-BJ"
   "96-0349-13-Z"
   "97-0034-13-AL"
   "97-0044-13-D"
   "97-0087-13-AL"
   "98-0067-13-AJ"
   "98-0133-13-P"
   "98-0177-13-AL"
   "98-0183-13-Z"
   "98-0190-13-Z"
   "98-1159-13-DJ"])
