(ns lupapalvelu.prev-permit
  (:require [taoensso.timbre :refer [debug info]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env]
            [sade.property :as p]
            [lupapalvelu.application :as application]
            [lupapalvelu.action :as action]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.user :as user]
            [lupapalvelu.authorization-api :as authorization]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.commands :as commands]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.organization :as organization]))

(defn- get-applicant-email [applicant]
  (-> (or
        (get-in applicant [:henkilo :sahkopostiosoite])
        (get-in applicant [:yritys :sahkopostiosoite]))
    user/canonize-email
    (#(if-not (action/email-validator {:data {:email %}})  ;; action/email-validator returns nil if email was valid
       %
       (do
         (info "Prev permit application creation, not inviting the invalid email address received from backing system: " %)
         nil)))))

(defn- invite-applicants [{:keys [lang user created application] :as command} applicants]
  (dorun
    (->> applicants
         (map-indexed
           (fn [i applicant]

             ;; only invite applicants who have a valid email address
             (let [applicant-email (get-applicant-email applicant)]

               ;; Invite applicants
               (when-not (ss/blank? applicant-email)
               (authorization/send-invite!
                 (update-in command [:data] merge {:email        applicant-email
                                                   :text         (i18n/localize lang "invite.default-text")
                                                   :documentName nil
                                                   :documentId   nil
                                                   :path         nil
                                                   :role         "writer"}))
                 (info "Prev permit application creation, invited " applicant-email " to created app " (get-in command [:data :id])))

               ;; Set applicants' user info to Hakija documents
               (let [document (if (zero? i)
                                (domain/get-document-by-name application "hakija")
                                (commands/do-create-doc (assoc-in command [:data :schemaName] "hakija")))
                     applicant-type (-> applicant (select-keys [:henkilo :yritys]) keys first)
                     user-info (case applicant-type
                                 ;; Not including here the id of the invited user into "user-info",
                                 ;; so it is not set to personSelector, and validation is thus not done.
                                 ;; If user id would be given, the validation would fail since applicants have not yet accepted their invitations
                                 ;; (see the check in :personSelector validator in model.clj).
                                 :henkilo {:firstName (get-in applicant [:henkilo :nimi :etunimi])
                                           :lastName (get-in applicant [:henkilo :nimi :sukunimi])
                                           :email applicant-email
                                           :phone (get-in applicant [:henkilo :puhelin])
                                           :personId (get-in applicant [:henkilo :henkilotunnus])
                                           :street (get-in applicant [:henkilo :osoite :osoitenimi :teksti])
                                           :zip (get-in applicant [:henkilo :osoite :postinumero])
                                           :city (get-in applicant [:henkilo :osoite :postitoimipaikannimi])
                                           :turvakieltokytkin (:turvakieltoKytkin applicant)}

                                 :yritys (let [;; the postiosoite path changed in krysp 2.1.5, supporting both
                                               postiosoite (or
                                                             (get-in applicant [:yritys :postiosoite])
                                                             (get-in applicant [:yritys :postiosoitetieto :postiosoite]))]
                                           {:name (get-in applicant [:yritys :nimi])
                                            :y (get-in applicant [:yritys :liikeJaYhteisotunnus])
                                            :email applicant-email
                                            :phone (get-in applicant [:yritys :puhelin])
                                            :adress1 (get-in postiosoite [:osoitenimi :teksti])
                                            :zip (get-in postiosoite [:postinumero])
                                            :po (get-in postiosoite [:postitoimipaikannimi])
                                            :turvakieltokytkin (:turvakieltoKytkin applicant)}))]

                 (commands/set-subject-to-document application document user-info (name applicant-type) created))))))))

(defn- do-create-application-from-previous-permit [command operation xml app-info location-info]
  (let [{:keys [rakennusvalvontaasianKuvaus vahainenPoikkeaminen hakijat]} app-info
        manual-schema-datas {"hankkeen-kuvaus" (remove empty? (conj []
                                                                    (when-not (ss/blank? rakennusvalvontaasianKuvaus)
                                                                      [["kuvaus"] rakennusvalvontaasianKuvaus])
                                                                    (when-not (ss/blank? vahainenPoikkeaminen)
                                                                      [["poikkeamat"] vahainenPoikkeaminen])))}
        municipality (p/municipality-id-by-property-id (:propertyId location-info))
        command (update-in command [:data] merge
                  {:operation operation :municipality municipality :infoRequest false :messages []}
                  location-info)
        created-application (application/do-create-application command manual-schema-datas)
        ;; TODO: Aseta applicationille viimeisin state? (lupapalvelu.document.canonical-common/application-state-to-krysp-state kaanteisesti)
        ;        created-application (assoc created-application
        ;                              :state (some #(when (= (-> app-info :viimeisin-tila :tila) (val %)) (first %)) lupapalvelu.document.canonical-common/application-state-to-krysp-state))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]

    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (application/insert-application created-application)
     ;; Get verdicts for the application
    (when-let [updates (verdict/find-verdicts-from-xml command xml)]
      (action/update-application command updates))
    (invite-applicants command hakijat)
    (:id created-application)))

(defn- enough-location-info-from-parameters? [{{:keys [x y address propertyId]} :data}]
  (and
    (not (ss/blank? address)) (not (ss/blank? propertyId))
    (-> x util/->double pos?) (-> y util/->double pos?)))

(defn- get-location-info [{data :data :as command} app-info]
  (when app-info
    (let [rakennuspaikka-exists? (and (:rakennuspaikka app-info)
                                   (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))
          location-info          (cond
                                   rakennuspaikka-exists?                          (:rakennuspaikka app-info)
                                   (enough-location-info-from-parameters? command) (select-keys data [:x :y :address :propertyId]))]
      (when-not rakennuspaikka-exists?
        (info "Prev permit application creation, rakennuspaikkatieto information incomplete:\n " (:rakennuspaikka app-info) "\n"))
      location-info)))

(defn fetch-prev-application! [{{:keys [organizationId kuntalupatunnus]} :data user :user :as command}]
  (let [operation         :aiemmalla-luvalla-hakeminen
        permit-type       (operations/permit-type-of-operation operation)
        dummy-application {:id kuntalupatunnus :permitType permit-type :organization organizationId}
        xml               (krysp-fetch-api/get-application-xml dummy-application :kuntalupatunnus)
        validator-fn      (permit/get-verdict-validator permit-type)
        validation-result (validator-fn xml)
        app-info          (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        location-info     (get-location-info command app-info)
        organizations-match?   (when (seq app-info)
                                 (= organizationId (:id (organization/resolve-organization (:municipality app-info) permit-type))))]

    (cond
      validation-result            validation-result
      (empty? app-info)            (fail :error.no-previous-permit-found-from-backend)
      (not organizations-match?)   (fail :error.previous-permit-found-from-backend-is-of-different-organization)
      (not location-info)          (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
      :else                        (ok :id (do-create-application-from-previous-permit command operation xml app-info location-info)))))
