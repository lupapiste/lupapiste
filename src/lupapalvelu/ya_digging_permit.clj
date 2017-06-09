(ns lupapalvelu.ya-digging-permit
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as usr]
            [lupapalvelu.ya :as ya]
            [sade.core :refer [fail]]
            [sade.util :as util]))

;;; Helper functions

(defn digging-permit-can-be-created? [application]
  (and (ya/sijoittaminen? application)
       (app/verdict-given? application)))

(defn digging-permit-operation? [operation-name]
  (let [{:keys [permit-type subtypes]} (op/get-operation-metadata operation-name)]
    (and (= permit/YA permit-type)
         (boolean (some #(= :tyolupa %) subtypes)))))

(defn organization-digging-operations
  "Return an operation tree containing only the digging operations
  selected by the given organization"
  [organization]
  (op/selected-operations-for-organizations [organization]
                                            digging-permit-operation?))

;;; Validators

(defn validate-digging-permit-source [{:keys [application]}]
  (when (and application
             (not (digging-permit-can-be-created? application)))
    (fail :error.invalid-digging-permit-source
          {:id (:id application)
           :state (:state application)
           :primaryOperationName (-> application :primaryOperation :name)})))

(defn validate-digging-permit-operation [{{:keys [operation]} :data}]
  (when (and operation
             (not (digging-permit-operation? operation)))
    (fail :error.not-digging-permit-operation
          {:operation operation})))

;;; Creating digging permits

(def copied-document-names ["yleiset-alueet-maksaja" "hakija-ya"])

(defn- auth->user-summary [auth]
  (merge usr/user-skeleton
         (usr/summary auth)))

(defn- party-document->auth-invite
  [digging-document sijoitus-document app-id sijoitus-auth inviter timestamp]
  {:pre [(= (doc-tools/doc-type digging-document) :party)
         (= (doc-tools/doc-type sijoitus-document) :party)
         (= (doc-tools/doc-name digging-document)
            (doc-tools/doc-name sijoitus-document))]}

  ;; Find id from sijoitus document since ids cannot be copied to digging document
  (when-let [invited-id (doc-tools/party-doc-user-id sijoitus-document)]
    (when-let [invited-auth-entry (util/find-first #(= (copy-app/auth-id %)
                                                       invited-id)
                                                   sijoitus-auth)]
      ;; Copy user information from auth instead of from db to be
      ;; consistent between sijoitus and digging permits
      (auth/create-invite-auth inviter (auth->user-summary invited-auth-entry)
                               app-id :writer timestamp nil
                               (-> digging-document :schema-info :name)
                               (:id digging-document)))))

(defn- add-applicant-and-payer-auth [digging-app sijoitus-app user timestamp]
  (let [digging-documents (:documents digging-app)
        sijoitus-documents (:documents sijoitus-app)

        ;; Find matching pairs of documents from digging and sijoitus apps
        document-pairs (->> copied-document-names
                            (map #(map (fn [docs]
                                         (domain/get-document-by-name docs %))
                                       [digging-documents
                                        sijoitus-documents])))

        ;; Create invites using the document pairs
        invites (map (fn [[digging-doc sijoitus-doc]]
                       (party-document->auth-invite digging-doc sijoitus-doc
                                                    (:id digging-app)
                                                    (:auth sijoitus-app)
                                                    user
                                                    timestamp))
                     document-pairs)]
    ;; Add invites for those who are not already authorized
    (update digging-app :auth
            concat (filter (comp (copy-app/not-in-auth (:auth digging-app))
                                 copy-app/auth-id)
                           invites))))

(defn- copy-document [doc-name sijoitus-app digging-app organization]
  (-> (domain/get-document-by-name sijoitus-app doc-name)
      ;; Use auth from sijoitus application since the required auth
      ;; entry is not yet added to digging application
      (copy-app/preprocess-document (assoc digging-app :auth
                                           (:auth sijoitus-app))
                                    organization nil)))

(defn- copy-applicant-and-payer-documents [digging-app sijoitus-app organization]
  (update digging-app :documents
          (partial map (fn [document]
                         (if (contains? (set copied-document-names)
                                        (doc-tools/doc-name document))
                           (copy-document (doc-tools/doc-name document)
                                          sijoitus-app
                                          digging-app
                                          organization)
                           document)))))

(defn- copy-applicant-and-payer-information
  "Copy the contents of applicant and payer documents from the sijoitus
  application to the digging application, provided that the personal
  information. Add invites to parties in said documents."
  [digging-app sijoitus-app organization user timestamp]
  (-> digging-app
      (copy-applicant-and-payer-documents sijoitus-app organization)
      (add-applicant-and-payer-auth sijoitus-app user timestamp)))

(defn new-digging-permit
  [{:keys [address propertyId propertyIdSource] [x y] :location :as sijoitus-application}
   user created digging-operation organization]
  {:pre [(digging-permit-can-be-created? sijoitus-application)
         (digging-permit-operation? digging-operation)]}
  (let [digging-app (app/do-create-application {:data {:address          address
                                                       :operation        digging-operation
                                                       :propertyId       propertyId
                                                       :propertyIdSource propertyIdSource
                                                       :x                x
                                                       :y                y
                                                       :infoRequest      false
                                                       :messages         []}
                                                :user user
                                                :created created})]
    (-> digging-app
        (copy-applicant-and-payer-information sijoitus-application
                                              organization
                                              user
                                              created)
        (assoc :drawings (:drawings sijoitus-application)))))
