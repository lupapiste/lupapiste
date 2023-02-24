(ns lupapalvelu.conversion.kuntagml-converter
  (:require [clojure.string :refer [includes?]]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [info infof warn errorf]]))

(sc/defn ^:always-validate store-backend-id
  "Adds a verdict draft with the given `backend-id`."
  [application-id :- ssc/NonBlankStr backend-id :- ssc/NonBlankStr]
  (mongo/update-by-id :applications application-id
                      {$push {:verdicts (verdict/backend-id->verdict backend-id)}}))

(defn- convert-application-from-xml [{:keys [municipality conversion-doc
                                             force-terminal-state?]}
                                     command operation organization xml app-info location-info]
  (let [{:keys [hakijat]}        app-info
        buildings-and-structures (building-reader/->buildings-and-structures xml
                                                                             {:include-personal-owner-info? true})
        document-datas           (prev-permit/schema-datas app-info buildings-and-structures)
        {backend-id :backend-id
         app-id     :LP-id}      conversion-doc
        command                  (-> command
                                     (dissoc :application-id)
                                     (update-in [:data] merge
                                                {:operation operation :infoRequest false :messages []}
                                                location-info))
        operations               (:toimenpiteet app-info)
        ;; So that regex checks on this don't throw errors, should the field be empty.
        description              (or (building-reader/->asian-tiedot xml)
                                     "")
        primary-op-name          (if (seq operations)
                                   (conv-util/deduce-operation-type backend-id description (first operations))
                                   (conv-util/deduce-operation-type backend-id description))

        schema-name (-> primary-op-name conv-util/op-name->schema-name name)

        manual-schema-datas {schema-name (app/sanitize-document-datas (schemas/get-schema 1 schema-name)
                                                                      (first document-datas))}

        secondary-op-names (map (partial conv-util/deduce-operation-type
                                         backend-id
                                         description)
                                (rest operations))

        make-app-info {:id             app-id
                       :organization   organization
                       :operation-name primary-op-name
                       :location       (app/->location (:x location-info) (:y location-info))
                       :propertyId     (:propertyId location-info)
                       :address        (:address location-info)
                       :municipality   municipality}

        created-application (-> (app/make-application make-app-info
                                                      []            ; messages
                                                      (:user command)
                                                      (:created command)
                                                      manual-schema-datas)
                                (assoc :attachments []) ; No operation attachments
                                (conv-util/remove-empty-party-documents))

        new-parties (remove empty?
                            (concat (map prev-permit/suunnittelija->party-document (:suunnittelijat app-info))
                                    (map prev-permit/osapuoli->party-document (:muutOsapuolet app-info))
                                    (map prev-permit/hakija->party-document hakijat)
                                    (when (includes? backend-id "TJO")
                                      (map prev-permit/tyonjohtaja->tj-document (:tyonjohtajat app-info)))))

        location-document (->> xml
                               building-reader/->rakennuspaikkatieto
                               (conv-util/rakennuspaikkatieto->rakennuspaikka-kuntagml-doc backend-id)
                               vector)

        structure-descriptions (map :description buildings-and-structures)
        other-building-docs    (map (partial app/document-data->op-document created-application)
                                    (rest document-datas)
                                    secondary-op-names)
        secondary-ops          (mapv #(assoc (-> %1 :schema-info :op) :description %2 :name %3)
                                     other-building-docs
                                     (rest structure-descriptions)
                                     secondary-op-names)
        structures             (->> xml
                                    krysp-reader/->rakennelmatiedot
                                    (map conv-util/rakennelmatieto->kaupunkikuvatoimenpide))

        ;; Siirretaan lausunnot luonnos-tilasta "lausunto annettu"-tilaan
        given-statements (for [lausuntotieto (filter :viranomainen (krysp-reader/->lausuntotiedot xml))
                               :let          [statement   (prev-permit/lausuntotieto->statement lausuntotieto)
                                              puoltotieto (some-> statement
                                                                  (get-in [:metadata :puoltotieto])
                                                                  (clojure.string/replace #"\s" "-"))]
                               :when         (map? statement)]
                           ;; Normalization, since the input data from KuntaGML contains
                           ;; values like 'ei huomautettavaa' (should be
                           ;; 'ei-huomautettavaa') etc.
                           (try
                             (statement/give-statement statement
                                                       (:saateText statement)
                                                       puoltotieto
                                                       (mongo/create-id)
                                                       (mongo/create-id)
                                                       false
                                                       (date/timestamp (:lausuntoPvm lausuntotieto)))
                             (catch Exception e
                               (errorf "Moving statement to statement given -state failed: %s" (.getMessage e)))))

        history-array (conv-util/generate-history-array xml created-application)

        submitted-time (some->> history-array
                                (map :ts)
                                (filter pos?)
                                seq
                                (apply min))

        tyonjohtaja? (includes? backend-id "TJO")

        created-application (-> created-application
                                (assoc-in [:primaryOperation :description] (first structure-descriptions))
                                ;; Add poikkeamat and kuvaus to the "hankkeen-kuvaus" doc.
                                (conv-util/add-description-and-deviation-info xml document-datas)
                                ;; Remove empty rakennuspaikka-document that comes from the template
                                conv-util/remove-empty-rakennuspaikka
                                ;; Add timestamps for different state changes
                                (conv-util/add-timestamps history-array)
                                ;; Assemble the documents-array
                                (update-in [:documents] concat other-building-docs new-parties structures
                                           (when-not tyonjohtaja? location-document))
                                (update-in [:secondaryOperations] concat secondary-ops)
                                (update-in [:documents] (partial remove conv-util/is-empty-party-document?))
                                (assoc :statements (or given-statements [])
                                       :opened (or submitted-time (:created command))
                                       :history history-array
                                       :submitted submitted-time
                                       :facta-imported true)
                                conv-util/remove-illegal-states
                                conv-util/set-to-right-state)


        ;; Add op-element to schema-info, failing to do this breaks mongochecks.
        created-application (if-not tyonjohtaja?
                              created-application
                              (update created-application
                                      :documents
                                      (partial map (fn [doc]
                                                     (if (util/=as-kw :tyonjohtaja-v2
                                                                      (get-in doc [:schema-info :name]))
                                                       (assoc-in doc
                                                                 [:schema-info :op]
                                                                 (app/make-op
                                                                   (:operation-name created-application)
                                                                   (:created created-application)))
                                                       doc)))))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]
    ;; Skip faulty applications where the document data is missing.
    (if (and (= 1 (count (:documents created-application)))
             (= "tyonjohtajan-nimeaminen-v2" (get-in created-application [:primaryOperation :name])))
      (errorf "Skipping foreman application %s. Not enough data." backend-id)
      (logging/with-logging-context {:applicationId (:id created-application)}
        ;; The application has to be inserted first, because it is assumed to be in the
        ;; database when checking for verdicts (and their attachments).
        (app/insert-application created-application)
        (infof "Inserted converted app: org=%s kuntalupatunnus=%s"
               (:organization created-application) backend-id)
        ;; Get verdicts for the application
        (if-let [updates (verdict/find-verdicts-from-xml command xml false)]
          (when (util/not=as-kw :canceled (:state created-application))
            (action/update-application command updates)
            (verdict-date/update-verdict-date (:id created-application)))
          ;; No verdicts, let's store the backend-id
          (store-backend-id (:id created-application) backend-id))

        (let [updated-application (mongo/by-id :applications (:id created-application))
              {:keys [updates added-tasks-with-updated-buildings attachments-by-task-id]}
              (review/read-reviews-from-xml usr/batchrun-user-data (:created command) updated-application xml
                                            {:do-not-include-state-updates? true
                                             :skip-task-validation?         true})
              review-command      (assoc (action/application->command updated-application (:user command))
                                         :action "prev-permit-review-updates")
              update-result       (if (seq updates)
                                    (review/save-review-updates review-command
                                                                updates
                                                                added-tasks-with-updated-buildings
                                                                attachments-by-task-id true)
                                    {:ok false :desc "No review updates found"})]
          (if (:ok update-result)
            (info "Saved review updates")
            (infof "Reviews were not saved: %s" (:desc update-result))))

        (let [fetched-application              (mongo/by-id :applications (:id created-application))
              tasks                            (:tasks fetched-application)
              should-be-set-in-terminal-state? (and (or force-terminal-state?
                                                        (conv-util/final-review-done? tasks))
                                                    (not= (:state fetched-application)
                                                          (conv-util/determine-terminal-state fetched-application)))
              {:keys [state history]}          (conv-util/set-to-terminal-state fetched-application)
              state                            (if should-be-set-in-terminal-state?
                                                 state
                                                 (:state fetched-application))
              history                          (if should-be-set-in-terminal-state?
                                                 history
                                                 (:history fetched-application))
              app-links                        (krysp-reader/->viitelupatunnukset xml)]
          (mongo/update-by-id :applications
                              (:id fetched-application)
                              (-> (meta-fields/applicant-index-update fetched-application)
                                  (assoc-in ["$set" :history] history)
                                  (assoc-in ["$set" :state] state)))
          (mongo/update-by-id :conversion
                              (:id conversion-doc)
                              {$set  {:converted true
                                      :linked    false
                                      :LP-id     (:id fetched-application)
                                      :app-links app-links}
                               $push {:conversion-timestamps (:created command)}})
          fetched-application)))))

(sc/defschema LocationInfo
  {:address    ssc/NonBlankStr
   :x          (sc/pred pos? "Positive X (E)")
   :y          (sc/pred pos? "Positive Y (N)")
   :propertyId ssc/Kiinteistotunnus})

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

(defn default-location [{:keys [location-overrides location-fallback]} backend-id organization-id mode]
  (case mode
    :override (get location-overrides backend-id)
    :fallback (or location-fallback (get fallback-location-infos organization-id))))

(defn run-conversion!
  "Run the conversion pipeline for the given organization.
  Always uses a local KuntaGML file:
  1) The local MongoDB has to contain the location info for the municipality in question.
  2) The `target` must have a `:filename` key that points to the KuntaGML message."
  [options {{:keys [target organizationId permitType]} :data :as command}]
  (let [kuntalupatunnus          (:id target)
        operation                "konversio"
        filename                 (:filename target)
        xml                      (krysp-fetch/get-local-application-xml-by-filename filename permitType)
        {location-info :rakennuspaikka
         :as           app-info} (krysp-reader/get-app-info-from-message xml kuntalupatunnus
                                                                         (partial default-location options
                                                                                  kuntalupatunnus organizationId))
        organization          (org/get-organization organizationId)
        validation-result     (permit/validate-verdict-xml permitType xml organization)
        no-proper-applicants? (not-any? prev-permit/get-applicant-type (:hakijat app-info))]
    (when validation-result
      (warn "Has invalid verdict: " (:text validation-result)))
    (cond
      (empty? app-info)
      (error-and-fail! "No app-info available" :error.no-previous-permit-found-from-backend)

      (not location-info)
      (error-and-fail! "No location info" :error.more-prev-app-info-needed)

      (not (:propertyId location-info))
      (error-and-fail! "No property-id" :error.previous-permit-no-propertyid)

      :else
      (let [{id :id} (convert-application-from-xml options
                                                   command
                                                   operation
                                                   organization
                                                   xml
                                                   app-info
                                                   location-info)]
        (infof "Conversion: Backend-id %s (%s) -> %s"
               kuntalupatunnus (or filename "remote") (or id "Could not be converted."))
        (if no-proper-applicants?
          (ok :id id :text :error.no-proper-applicants-found-from-previous-permit)
          (ok :id id))))))
