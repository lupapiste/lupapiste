(ns lupapalvelu.ya-digging-permit
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.ya :as ya]
            [sade.core :refer [fail]]
            [sade.util :as util]))


;;; Predicate functions

(defn digging-permit-can-be-created? [application]
  (and (ya/sijoittaminen? application)
       (app/verdict-given? application)))

(defn digging-permit-operation? [operation-name]
  (let [{:keys [permit-type subtypes]} (op/get-operation-metadata operation-name)]
    (and (= permit/YA permit-type)
         (boolean (some #(= :tyolupa %) subtypes)))))


;;; Digging operation tree

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


;;; Copying authorizations

(def copied-document-names ["yleiset-alueet-maksaja" "hakija-ya"])

(defn- party-document->auth-invite
  [digging-document sijoitus-document app-id sijoitus-auth inviter timestamp]
  {:pre [(= (doc-tools/doc-type digging-document) :party)
         (= (doc-tools/doc-type sijoitus-document) :party)
         (= (doc-tools/doc-name digging-document)
            (doc-tools/doc-name sijoitus-document))]}

  ;; Find id from sijoitus document since ids cannot be copied to digging document
  (when-let [invited-id (doc-tools/party-doc-selected-id sijoitus-document)]
    (when-let [invited-auth-entry (util/find-first #(= (copy-app/auth-id %)
                                                       invited-id)
                                                   sijoitus-auth)]
      ;; Copy user information from auth instead of from db to be
      ;; consistent between sijoitus and digging permits
      (if (= (:type invited-auth-entry) "company")
        (copy-app/create-company-auth invited-auth-entry inviter)
        (copy-app/create-user-auth invited-auth-entry :writer inviter app-id timestamp nil
                                   (-> digging-document :schema-info :name)
                                   (:id digging-document))))))

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
        invites (->> document-pairs
                     (map (fn [[digging-doc sijoitus-doc]]
                            (party-document->auth-invite digging-doc sijoitus-doc
                                                         (:id digging-app)
                                                         (:auth sijoitus-app)
                                                         user
                                                         timestamp)))
                     (util/distinct-by copy-app/auth-id))]
    ;; Add invites for those who are not already authorized
    (update digging-app :auth
            concat (filter (comp #(and (some? %)
                                       ((copy-app/not-in-auth (:auth digging-app)) %))
                                 copy-app/auth-id)
                           invites))))


;;; Copying documents

(defn- temporary-copy-auth
  "A temporary auth needed for preprocessing copied documents
  correctly. Proper auth can be created only after we have the document
  copies which are added to the authorization invites."
  [digging-app sijoitus-app user timestamp]
  (concat [(first (:auth digging-app))]
          (map #(copy-app/auth->invite %
                                       user
                                       (:id digging-app)
                                       timestamp)
               (:auth sijoitus-app))))

(defn- copy-document [doc-name digging-app sijoitus-app copy-auth organization]
  (-> (domain/get-document-by-name sijoitus-app doc-name)
      (copy-app/preprocess-document (assoc digging-app :auth
                                           copy-auth)
                                    organization nil)))

(defn- copy-applicant-and-payer-documents
  "Copy the personal information from applicant and payer documents,
  with same restrictions as when copying an application."
  [digging-app sijoitus-app user organization timestamp]
  (let [auth-for-copying (temporary-copy-auth digging-app sijoitus-app user timestamp)]
    (update digging-app :documents
            (partial map (fn [document]
                           (if (contains? (set copied-document-names)
                                          (doc-tools/doc-name document))
                             (copy-document (doc-tools/doc-name document)
                                            digging-app
                                            sijoitus-app
                                            auth-for-copying
                                            organization)
                             document))))))

(defn- copy-applicant-and-payer-information
  "Copy the contents of applicant and payer documents from the sijoitus
  application to the digging application, provided that the personal
  information. Add invites to parties in said documents."
  [digging-app sijoitus-app organization user timestamp]
  (-> digging-app
      (copy-applicant-and-payer-documents sijoitus-app user organization timestamp)
      (add-applicant-and-payer-auth sijoitus-app user timestamp)))


;;; Creating a digging permit from sijoituslupa

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
