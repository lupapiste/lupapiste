(ns lupapalvelu.conversion.kuntagml-converter
  (:require [taoensso.timbre :refer [info infof warn]]
            [sade.core :refer :all]
            [sade.util :as util]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.conversion.util :as conversion-util]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

(defn convert-application-from-xml [command operation organization xml app-info location-info authorize-applicants]
  ;;
  ;; Data to be deduced from xml:
  ;;   - building-site
  ;;   - operations and their respective document schemas
  ;;     - Some can be deduced from XML: uusi/laajennos/uudelleenrakentaminen/purkaminen/muuMuutosTyo/kaupunkikuvatoimenpide
  ;;     - Some types need to be selected by the permit-id type (TJO,VAK..)
  ;;     - which is primary?
  ;;   - buildings and structures
  ;;   - parties
  ;;      - hakijat, maksajat, asiamiehet, tyonjohtajat (vain TJO), suunnittelijat
  ;;   - statements
  ;;   - verdicts
  ;;   - reviews
  ;;   - app-links to :app-links collection (viitelupatieto)
  ;;   - :history array for the application (kasittelynTilatieto / tilamuutos) (get-sorted-tilamuutos-entries)
  ;;
  ;;  Other things to note:
  ;;    - linked permitIDs might be in funny order, check that it's normalised ('lupapalvelu.conversion.util/normalize-permit-id')
  ;;    - we need to generate LP id for conversion cases (do not use do-create-application)
  ;;
  ;;  Types that need special handling: VAK (not own thing, but adds data to linked application)
  ;;
  (let [{:keys [hakijat]} app-info
        municipality "092"
        buildings-and-structures (building-reader/->buildings-and-structures xml)
        document-datas (prev-permit/schema-datas app-info buildings-and-structures)
        manual-schema-datas {"aiemman-luvan-toimenpide" (first document-datas)}
        command (update-in command [:data] merge
                           {:operation operation :infoRequest false :messages []}
                           location-info)
        ;; TODO: should we check scope, that new-applications-enabled is true?
        id (app/make-application-id municipality)
        app-info {:id              id
                  :organization    organization
                  :operation-name  "aiemmalla-luvalla-hakeminen"
                  :location        (app/->location (:x location-info) (:y location-info))
                  :propertyId      (:propertyId location-info)
                  :address         (:address location-info)
                  :municipality    municipality}
        created-application (app/make-application app-info
                                                  []            ; messages
                                                  (:user command)
                                                  (:created command)
                                                  manual-schema-datas)
        new-parties (remove empty?
                            (concat (map prev-permit/suunnittelija->party-document (:suunnittelijat app-info))
                                    (map prev-permit/osapuoli->party-document (:muutOsapuolet app-info))))
        structure-descriptions (map :description buildings-and-structures)
        created-application (assoc-in created-application [:primaryOperation :description] (first structure-descriptions))

        ;; make secondaryOperations for buildings other than the first one in case there are many
        other-building-docs (map (partial prev-permit/document-data->op-document created-application) (rest document-datas))
        secondary-ops (mapv #(assoc (-> %1 :schema-info :op) :description %2) other-building-docs (rest structure-descriptions))

        created-application (-> created-application
                                (update-in [:documents] concat other-building-docs new-parties)
                                (update-in [:secondaryOperations] concat secondary-ops)
                                (assoc :opened (:created command)))

        ;; structures (-> xml krysp-reader/->rakennelmatieto conversion-util/rakennelmatieto->kaupunkikuvatoimenpide)
        ;; validation-result (model/validate created-application structures)

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
      (when-let [updates (verdict/find-verdicts-from-xml command xml)]
        (action/update-application command updates))

      (prev-permit/invite-applicants command hakijat authorize-applicants)
      (infof "Processed applicants, processable applicants count was: %s" (count (filter prev-permit/get-applicant-type hakijat)))

      (let [updated-application (mongo/by-id :applications (:id created-application))
            {:keys [updates added-tasks-with-updated-buildings attachments-by-task-id]} (review/read-reviews-from-xml usr/batchrun-user-data (now) updated-application xml)
            review-command (assoc (action/application->command updated-application (:user command)) :action "prev-permit-review-updates")
            update-result (review/save-review-updates review-command updates added-tasks-with-updated-buildings attachments-by-task-id)]
        (if (:ok update-result)
          (info "Saved review updates")
          (infof "Reviews were not saved: %s" (:desc update-result))))

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
  [{{:keys [organizationId kuntalupatunnus authorizeApplicants]} :data :as command}]
  (let [organizationId        "092-R" ;; Vantaa, bypass the selection from form
        destructured-permit-id (conversion-util/destructure-permit-id kuntalupatunnus)
        operation             "aiemmalla-luvalla-hakeminen"
        path                  "./src/lupapalvelu/conversion/test-data/"
        filename              (str path kuntalupatunnus ".xml")
        permit-type           "R"
        xml                   (krysp-fetch/get-local-application-xml-by-filename filename permit-type)
        app-info              (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        location-info         (prev-permit/get-location-info command app-info)
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
