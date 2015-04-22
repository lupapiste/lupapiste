(ns lupapalvelu.prev-permit
  (:require [taoensso.timbre :refer [info]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.application :as application]
            [lupapalvelu.action :as action]
            [lupapalvelu.verdict-api :as verdict-api]
            [lupapalvelu.user :as user]
            [lupapalvelu.authorization-api :as authorization]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.commands :as commands]))

(defn- invite-applicants [{:keys [lang user created application] :as command} applicants]
  {:pre [(every? #(get-in % [:henkilo :sahkopostiosoite]) applicants)]}
  (when (pos? (count applicants))
    (let [emails (->> applicants
                      (map #(get-in % [:henkilo :sahkopostiosoite]))
                      (map user/canonize-email)
                      set)]

      (doseq [email emails]
        ;; action/email-validator returns nil if email was valid
        (when (action/email-validator {:data {:email email}})
          (info "Prev permit application creation, invalid email address received from backing system: " email)
          (fail! :error.email)))

      (dorun
        (->> applicants
             (map-indexed
               (fn [i applicant]
                 (let [applicant-email (get-in applicant [:henkilo :sahkopostiosoite])]

                   ;; Invite applicants
                   (authorization/send-invite!
                     (update-in command [:data] merge {:email        applicant-email
                                                       :text         (i18n/localize lang "invite.default-text")
                                                       :documentName nil
                                                       :documentId   nil
                                                       :path         nil
                                                       :role         "writer"}))
                   (info "Prev permit application creation, invited " applicant-email " to created app " (get-in command [:data :id]))

                   ;; Set applicants' user info to Hakija documents
                   (let [document (if (zero? i)
                                    (domain/get-document-by-name application "hakija")
                                    (commands/do-create-doc (assoc-in command [:data :schemaName] "hakija")))
                         hakija-doc-id (:id document)

                         ;; Not including the id of the invited user into "user-info", so it is not set to personSelector, and validation is thus not done.
                         ;; If user id would be given, the validation would fail since applicants have not yet accepted their invitations
                         ;; (see the check in :personSelector validator in model.clj).
                         user-info {:role "applicant"
                                    :email applicant-email
                                    :username applicant-email
                                    :firstName (get-in applicant [:henkilo :nimi :etunimi])
                                    :lastName (get-in applicant [:henkilo :nimi :sukunimi])
                                    :phone (get-in applicant [:henkilo :puhelin])
                                    :street (get-in applicant [:henkilo :osoite :osoitenimi :teksti])
                                    :zip (get-in applicant [:henkilo :osoite :postinumero])
                                    :city (get-in applicant [:henkilo :osoite :postitoimipaikannimi])
                                    :personId (get-in applicant [:henkilo :henkilotunnus])
                                    :turvakieltokytkin (:turvakieltoKytkin applicant)}]

                     (commands/set-subject-to-document application document user-info "henkilo" created))))))))))

(defn do-create-application-from-previous-permit [command xml app-info location-info]
  (let [{:keys [rakennusvalvontaasianKuvaus vahainenPoikkeaminen hakijat]} app-info
        manual-schema-datas {"hankkeen-kuvaus" (remove empty? (conj []
                                                                    (when-not (ss/blank? rakennusvalvontaasianKuvaus)
                                                                      [["kuvaus"] rakennusvalvontaasianKuvaus])
                                                                    (when-not (ss/blank? vahainenPoikkeaminen)
                                                                      [["poikkeamat"] vahainenPoikkeaminen])))}
        ;; TODO: Property-id structure is about to change -> Fix this municipality logic when it changes.
        municipality (subs (:propertyId location-info) 0 3)
        command (update-in command [:data] merge {:municipality municipality :infoRequest false :messages []} location-info)
        created-application (application/do-create-application command manual-schema-datas)
        ;; TODO: Aseta applicationille viimeisin state? (lupapalvelu.document.canonical-common/application-state-to-krysp-state kaanteisesti)
        ;        created-application (assoc created-application
        ;                              :state (some #(when (= (-> app-info :viimeisin-tila :tila) (val %)) (first %)) lupapalvelu.document.canonical-common/application-state-to-krysp-state))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (merge command (action/application->command created-application))]

    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (application/insert-application created-application)
    (verdict-api/find-verdicts-from-xml command xml)  ;; Get verdicts for the application

    ;; NOTE: at the moment only supporting henkilo-type applicants
    (let [applicants-with-email (filter #(get-in % [:henkilo :sahkopostiosoite]) hakijat)]
      (invite-applicants command applicants-with-email))

    (:id created-application)))