(ns lupapalvelu.ryhti.api.validation
  (:require [clojure.pprint :as pp]
            [clojure.walk :as cljwalk]
            [lupapalvelu.document.all-schemas]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ryhti.api.malli-schema :as malli-schema]
            [lupapalvelu.ryhti.api.ryhti-api :as ryhti-api]
            [lupapalvelu.ryhti.api.schema-utils :as schema-utils]
            [lupapalvelu.ryhti.datamodel.common :as common]
            [lupapalvelu.ryhti.datamodel.issue :as issue]
            [malli.core :as m]
            [sade.core :refer [fail]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]))

(defn application->ryhti [_org app {:keys [bim-models] :as options}]
  (let [app          (if (:mockDecision options)
                       (assoc app :pate-verdicts [{:ryhti {:buildingPermitDecisionKey (str (random-uuid))}
                                                   :data  {:handler         "Pertti Päättäjä"
                                                           :k-verdict-date  (date/today)
                                                           :k-lainvoimainen (date/today)
                                                           :k-kuulutus      (date/today)}}])
                       app)
        context      {:from                    :lupapiste
                      :to                      :ryhti
                      :transform               :->ryhti
                      :application             app
                      ;; Document (and field) accessors based on type, flags, etc.
                      :documents-by-type       (->> (:documents app)
                                                    (group-by (comp keyword :type :schema-info)))
                      :documents-by-subtype    (->> (:documents app)
                                                    (group-by (comp keyword :subtype :schema-info)))
                      :fields-by-flag          (schema-utils/fields-per-flag (:documents app))
                      ;; FIXME: One hardcoded file-id that was uploaded to Ryhti
                      ;; Presumably later this is something like Lupapiste attachment id -> file-id mapping
                      :->file-id               (fn [_] (:tempFileId options))
                      ;; At the moment our BIM models target operations, but maybe later we can
                      ;; target individual building/structure sections, just like Ryhti does.
                      :bim-models-by-operation (reduce (fn [acc {:keys [target-entity] :as bim-model}]
                                                         (if-let [op-id (-> target-entity :operation-id)]
                                                           (update acc op-id (fnil conj []) bim-model)
                                                           acc))
                                                       {}
                                                       bim-models)}
        control-case (operations/application->control-case app)
        ;; Would be nice to do this in the control-case configuration, but causes
        ;; quite a bit of circular dependencies
        mapping      (case control-case
                       :ryhti/building       issue/building-permit-issue-mapping
                       :ryhti/demolition     issue/demolition-permit-issue-mapping
                       :ryhti/deviation      issue/deviation-permit-issue-mapping
                       :ryhti/landscape-work issue/landscape-work-permit-issue-mapping)]
    (common/apply-mapping mapping context)))

;; TODO: Call validation from some application "lifecycle" steps,
;; before certain state changes.

(def RootSchema
  (m/schema [:schema {:registry malli-schema/registry}
             [:ref :ryhti/BuildingPermitIssue]]))

(defn malli-validate [ryhti-permit]
  ;; Validate should reuse cached validator if schema is an Schema instance?
  ;; Could also just manually def validator at ns top level.
  (m/validate RootSchema ryhti-permit))

(defn to-ryhti [app-id options]
  (let [app (mongo/by-id :applications app-id)
        org (org/get-organization (:organization app))]
    (application->ryhti org app options)))

(defn ryhti-validate [org-id municipality ryhti-permit]
  (let [resp (ryhti-api/request "/BuildingPermit/Validate"
                                (ryhti-api/with-ryhti-headers! org-id municipality
                                  {:body (json/encode ryhti-permit)
                                   :throw-exceptions false}))
        status      (:status resp)]
    ;; Parse response on success and expected error cases
    (if (#{200 201 400 422} status)
      (-> resp :body (json/decode true))
      resp)))

(def ignored-errors
  #{;; Permenent IDs - retrieved by municipality system later
    "quality__req_apartment_apartmentIdentifier"
    "quality__req_permanentApartmentIdentifier_must_exist"
    "quality__req_building_permanentBuildingIdentifier"
    "quality__req_building_permanentBuildingIdentifier_must_exist"

    ;; Life cycle state
    ;; Decision given - ignore always or based on mockDecision option?
    "quality__req_builtenviromentPermitMatter_lifeCycleState"

    ;; Broken currently in Ryhti - reported
    "quality__req_future_date_not_allowed"

    ;; Temporary?
    "quality__req_buildingObject_property_municipality"
    "quality__req_location_pointLocation_property"})

(defn select-relevant-errors [resp]
  (->> resp
       :errors
       (remove (fn [{:keys [ruleId]}]
                 (contains? ignored-errors ruleId)))))

(defn parse-error-path
  "Parse error instance path.

  E.g. buildingPermitIssue.constructionAction[0].areaToBeBuiltForSpecificActivities.address[0].addressName

  Field names are parsed to keywords, and indices into numbers."
  [error-path]
  (let [parts (ss/split error-path #"\.")]
    (mapcat (fn [part]
              (let [[_ k ks] (re-matches #"(.*?)((?:\[.*?\])*)" part)
                    idxs (re-seq #"\[(.*?)\]" ks)]
                (into [(keyword k)] (map (comp parse-long second) idxs))))
            parts)))

(defn add-error-values
  "Given errors, parse error instance path and use that to retrieve the value
  from the request we built.

  Non values are formatted with pr-str, e.g. language-strings."
  [errors ryhti-permit]
  (into (empty errors)
        (map (fn [error]
               (let [;; Drop first item from path, which is buildingPermitIssue
                     parsed-path (rest (parse-error-path (:instance error)))
                     v (get-in ryhti-permit parsed-path "<<no value>>")]
                 (assoc error :value (if (string? v)
                                       v
                                       (pr-str v)))))
             errors)))

(defn upload-file! [org-id municipality file]
  (ryhti-api/upload-file! org-id municipality {:file file
                                               :content-type "application/pdf"}))

(defn validate-on-approve
  "Called on approve-application command, will call Validate endpoint on
  Ryhti with the BuildingPermitIssue built from Lupapiste Application.

  Enabled with a feature flag and if the organization has the Ryhti API
  configration for the municipality."
  [{:keys [application organization] :as _command}]
  (when (and (env/feature? :ryhti-validate-on-approve)
             (ryhti-api/organization-has-config? (force organization) (:municipality application)))
    (let [organization (force organization)
          org-id (:id organization)
          municipality-id (:municipality application)
          ;; TODO: Maybe also send incorrect file-id and just filter out those errors?
          ryhti-permit (application->ryhti organization application {:tempFileId "aed8e702-ca06-4266-ac98-f7a3c52b1a8a"
                                                                     :mockDecision true})
          validation-results (ryhti-validate org-id municipality-id ryhti-permit)
          errors (select-relevant-errors validation-results)
          errors (add-error-values errors ryhti-permit)]
      ;; TODO: We would also likely want to store the errors somewhere,
      ;; so we can display the validation status even without calling approve-application again?
      (when (seq errors)
        (fail :error.integration.create-message
              ;; i18n?
              :details "Ryhti validation failed"
              :errors errors)))))

(defn -cleanup [x]
  (cljwalk/postwalk (fn [m]
                      (if (map? m)
                        (dissoc m :localizedMessage)
                        m))
                    x))

(defn -debug-prn [x]
  (pp/pprint (-cleanup x)))

(comment
  (mongo/update-by-id :organizations "186-R" {:$set {:ryhti-api-configuration [{:municipality "186"
                                                                                :client-id ""
                                                                                :client-secret ""}]}})

  (get-and-save-permanent-building-permit-id! "186-R" "186" "LP-186-2026-90001")

  (get-and-save-permanent-building-permit-id!"186-R" "186" "LP-186-2026-90002")

  (get-and-save-permanent-building-permit-id! "186-R" "186" "LP-186-2026-90003")
  ;; TODO: Get permenent apartment id for all apartments

  (require '[clojure.java.io :as io])

  *e
  (ryhti-api/upload-file! "186-R" "186" {:file (io/file "../Ryhti_rakennustietojen_API_tietomallit.pdf")
                                         :content-type "application/pdf"})
  ;; Result aed8e702-ca06-4266-ac98-f7a3c52b1a8a

  (-debug-prn (mongo/by-id :applications "LP-837-2026-90002"))
  (format-date (:created (mongo/by-id :applications "LP-753-2026-90001")))
  (malli-validate (to-ryhti "LP-186-2026-90001" {}))

  (-debug-prn (-> (dissoc (m/explain RootSchema (to-ryhti "LP-186-2026-90003" {:tempFileId "aed8e702-ca06-4266-ac98-f7a3c52b1a8a"})) :schema :value)
                  (update :errors (fn [errors]
                                    (into (empty errors)
                                          (map (fn [error]
                                                 (dissoc error :schema))
                                               errors))))))

  (-debug-prn (to-ryhti "LP-186-2026-90001" {})) ;; ETVRA
  (-debug-prn (to-ryhti "LP-186-2026-90002" {})) ;; Structure
  (-debug-prn (to-ryhti "LP-186-2026-90003" {})) ;; Building
  *e

  (pp/pprint (ryhti-validate "186-R" "186" (to-ryhti "LP-186-2026-90003" {:tempFileId "aed8e702-ca06-4266-ac98-f7a3c52b1a8a"})))
  (pp/pprint (select-relevant-errors (ryhti-validate "186-R" "186" (to-ryhti "LP-186-2026-90003" {:tempFileId "aed8e702-ca06-4266-ac98-f7a3c52b1a8a"}))))
  (count (select-relevant-errors (ryhti-validate "186-R" "186" (to-ryhti "LP-186-2026-90003" {:tempFileId "aed8e702-ca06-4266-ac98-f7a3c52b1a8a"}))))
  )
