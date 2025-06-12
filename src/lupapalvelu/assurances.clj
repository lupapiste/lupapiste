(ns lupapalvelu.assurances
  "Assurances are clauses defined by the authorityAdmin that the applicant must
   agree to when submitting the application.
   Assurance templates are predefined structures within the organization’s data.
   Actual assurances are stored in the application upon submission."
  (:require [clojure.set :refer [intersection]]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.assurance-schemas :as assurance-schemas]
            [lupapalvelu.permit :as permit]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]))

(defn get-application-submit-assurance-templates
  "Returns all relevant submit assurance templates for application from organization"
  [organization application]
  (when (and (not (permit/archiving-project? application))
             (not (:infoRequest application))
             (not (:facta-imported application)))
    (let [app-ops (->> application
                       app-utils/get-operations
                       (map :name)
                       set)]
      (->> (force organization)
           :application-submit-assurance-templates
           (filterv #(or (:all-operations %)
                         (seq (intersection app-ops (-> % :operations set)))))))))

(defn required-assurances-filled?
  "Verifies that the user has checked all the required assurances."
  [organization-assurances assurances]
  (let [assurance-ids-set (->> assurances (map :id) set)]
    (->> organization-assurances
         (filter :required)
         (every? #(contains? assurance-ids-set (:id %))))))

(defn assurances-expired?
  "Returns true (meaning that error(s) exist) if any of the assurances' contents
  that user checked were changed between the user fetched the templates and
  submitted the application.

  Also returns true if assurances contain items that do not exist in the templates"
  [organization-assurances assurances]
  (->> assurances
       (some #(not= (:modified %)
                    (some->> organization-assurances
                             (util/find-by-id (:id %))
                             :modified)))
       boolean))

(defn assurance-validation-error
  "Receives a command as an argument and validates assurances from command data
   against the organization's assurances that concern the application at hand.
   Returns an error keyword if assurances are invalid. Otherwise, returns nil"
  [{:keys [application organization] {:keys [assurances]} :data}]
  (let [organization-assurances (get-application-submit-assurance-templates organization application)]
    (when (or assurances organization-assurances)
      (let [assurance-expiration-error?     (assurances-expired? organization-assurances assurances)
            required-assurances-not-filled? (not (required-assurances-filled? organization-assurances assurances))]
        (cond
          assurance-expiration-error? :error.assurances-expired
          required-assurances-not-filled? :error.required-assurances-not-filled)))))


(sc/defn ^:always-validate generate-assurance-data :- (sc/maybe assurance-schemas/ApplicationSubmitAssurancesData)
  "Receives a command as an argument and generates the assurance data to
   be stored in the application document."
  [{:keys                                        [application organization user created]
    {user-checked-assurances :assurances
     lang                    :assuranceLanguage} :data}]
  (let [organization-assurances (get-application-submit-assurance-templates organization application)
        user-checked-ids        (->> user-checked-assurances (map :id) set)
        assurances              (->> organization-assurances
                                     (filterv (comp user-checked-ids :id)))]
    (when (seq assurances)
      {:user       (select-keys user [:id :email])
       :ts         created
       :language   lang
       :assurances assurances})))
