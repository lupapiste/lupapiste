(ns lupapalvelu.prev-permit
  (:require [taoensso.timbre :refer [debug info]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env]
            [sade.property :as p]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.authorization-api :as authorization]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

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

(defn- get-applicant-type [applicant]
  (-> applicant (select-keys [:henkilo :yritys]) keys first))

(defn- invite-applicants [{:keys [lang user created application] :as command} applicants]

  (let [applicants-with-no-info (remove get-applicant-type applicants)
        applicants (filter get-applicant-type applicants)]

    ;; ensures execution will not throw exception here if hakija in the xml message lacks both "henkilo" and "yritys" fields
    (doall
      (map
        #(info "Prev permit application creation, app id: " (:id application) ", missing hakija information -> not inviting the applicant: " %)
        applicants-with-no-info))

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
                                (domain/get-applicant-document (:documents application))
                                (doc-persistence/do-create-doc! command (operations/get-applicant-doc-schema-name application)))
                     applicant-type (get-applicant-type applicant)
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
                                            :address1 (get-in postiosoite [:osoitenimi :teksti])
                                            :zip (get-in postiosoite [:postinumero])
                                            :po (get-in postiosoite [:postitoimipaikannimi])
                                            :turvakieltokytkin (:turvakieltoKytkin applicant)}))]

                (doc-persistence/set-subject-to-document application document user-info (name applicant-type) created)))))))))

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
        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]

    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (application/insert-application created-application)
    ;; Get verdicts for the application
    (when-let [updates (verdict/find-verdicts-from-xml command xml)]
      (action/update-application command updates))

    (invite-applicants command hakijat)

    (let [fetched-application (mongo/by-id :applications (:id created-application))]
      (mongo/update-by-id :applications (:id fetched-application) (meta-fields/applicant-index-update fetched-application))
      fetched-application)))

(defn- enough-location-info-from-parameters? [{{:keys [x y address propertyId]} :data}]
  (and
    (not (ss/blank? address))
    (not (ss/blank? propertyId))
    (-> x util/->double pos?)
    (-> y util/->double pos?)))

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

(defn fetch-prev-application! [{{:keys [organizationId kuntalupatunnus]} :data :as command}]
  (let [operation         :aiemmalla-luvalla-hakeminen
        permit-type       (operations/permit-type-of-operation operation)
        dummy-application {:id "" :permitType permit-type :organization organizationId}
        xml               (krysp-fetch/get-application-xml-by-backend-id dummy-application kuntalupatunnus)
        app-info          (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        location-info     (get-location-info command app-info)
        organization      (when (:propertyId location-info)
                            (organization/resolve-organization (p/municipality-id-by-property-id (:propertyId location-info)) permit-type))
        validation-result (permit/validate-verdict-xml permit-type xml organization)
        organizations-match?  (= organizationId (:id organization))
        no-proper-applicants? (not-any? get-applicant-type (:hakijat app-info))]
    (cond
      (empty? app-info)            (fail :error.no-previous-permit-found-from-backend)
      (not location-info)          (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
      (not (:propertyId location-info)) (fail :error.previous-permit-no-propertyid)
      (not organizations-match?)   (fail :error.previous-permit-found-from-backend-is-of-different-organization)
      validation-result            validation-result
      :else                        (let [{id :id} (do-create-application-from-previous-permit command operation xml app-info location-info)]
                                     (if no-proper-applicants?
                                       (ok :id id :text :error.no-proper-applicants-found-from-previous-permit)
                                       (ok :id id))))))


(def fix-prev-permit-counter (atom 0))

(defn fix-prev-permit-addresses []
  (when @mongo/connection
    (throw "Mongo already connected, aborting"))
  (try
    (mongo/connect!)
    (reset! fix-prev-permit-counter 0)

    (let [operation :aiemmalla-luvalla-hakeminen
          applications (mongo/select :applications
                                     {"primaryOperation.name" operation}
                                     {:id 1 :permitType 1 :verdicts 1 :organization 1 :address 1})]
      (doseq [{:keys [id verdicts address] :as application} applications]
        (if-let [kuntalupatunnus (get-in verdicts [0 :kuntalupatunnus])]
          (let [xml (krysp-fetch/get-application-xml-by-backend-id application kuntalupatunnus)
                app-info (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
                correct-address (get-in app-info [:rakennuspaikka :address])]
            (if (nil? correct-address)
              (println "No address info in XML for application" id)
              (when-not (= correct-address address)
                (println "old address:" address)
                (println "#_(mongo/update-by-id :applications" id
                       "{$set {:address" correct-address
                       ":title" correct-address "}})")
                (mongo/update-by-id :applications id
                                    {$set {:address correct-address
                                           :title   correct-address}})
                (mongo/update-by-id :submitted-applications id
                                    {$set {:address correct-address
                                           :title   correct-address}})
                (swap! fix-prev-permit-counter inc))))
          (println "No verdict for application" id))))
    (println "fixed" @fix-prev-permit-counter "applications")
    (finally
      (mongo/disconnect!))))

(defn- applicant-field-values [applicant {:keys [name] :as element}]
  (if (contains? applicant :henkilo)
    (case (keyword name)
      :_selected "henkilo"
      :userId nil
      :etunimi (get-in applicant [:henkilo :nimi :etunimi])
      :sukunimi (get-in applicant [:henkilo :nimi :sukunimi])
      :hetu (get-in applicant [:henkilo :henkilotunnus])
      :turvakieltoKytkin (or (:turvakieltoKytkin applicant) false)
      :katu (get-in applicant [:henkilo :osoite :osoitenimi :teksti])
      :postinumero (get-in applicant [:henkilo :osoite :postinumero])
      :postitoimipaikannimi (get-in applicant [:henkilo :osoite :postitoimipaikannimi])
      :puhelin (get-in applicant [:henkilo :puhelin])
      :email (get-in applicant [:henkilo :sahkopostiosoite])
      (tools/default-values element))
    (let [postiosoite (or
                        (get-in applicant [:yritys :postiosoite])
                        (get-in applicant [:yritys :postiosoitetieto :postiosoite]))]
      (case (keyword name)
        :_selected "yritys"
        :companyId nil
        :yritysnimi (get-in applicant [:yritys :nimi])
        :liikeJaYhteisoTunnus (get-in applicant [:yritys :liikeJaYhteisotunnus])
        :katu (get-in postiosoite [:osoitenimi :teksti])
        :postinumero (get-in postiosoite [:postinumero])
        :postitoimipaikannimi (get-in postiosoite [:postitoimipaikannimi])
        :turvakieltoKytkin (or (:turvakieltoKytkin applicant) false)
        :puhelin (get-in applicant [:yritys :puhelin])
        :email (get-in applicant [:yritys :sahkopostiosoite])
        (tools/default-values element)))))

(defn- applicant->applicant-doc [applicant]
  (let [schema         (schema/get-schema 1 "hakija-r")
        default-values (tools/create-document-data schema tools/default-values)
        document       {:id          (mongo/create-id)
                        :created     (now)
                        :schema-info (:info schema)
                        :data        (tools/create-document-data schema (partial applicant-field-values applicant))}
        unset-type     (if (contains? applicant :henkilo) :yritys :henkilo)]
    (assoc-in document [:data unset-type] (unset-type default-values))))

(defn fix-prev-permit-applicants []
  (when @mongo/connection
    (throw "Mongo already connected, aborting"))
  (try
    (mongo/connect!)
    (reset! fix-prev-permit-counter 0)

    (let [operation :aiemmalla-luvalla-hakeminen
          applications (mongo/select :applications
                                     {"primaryOperation.name" operation}
                                     {:id 1 :permitType 1 :verdicts 1 :organization 1 :documents 1})]
      (doseq [{:keys [id verdicts documents] :as application} applications]
        (if-let [kuntalupatunnus (get-in verdicts [0 :kuntalupatunnus])]
          (let [xml               (krysp-fetch/get-application-xml-by-backend-id application kuntalupatunnus)
                app-info          (krysp-reader/get-app-info-from-message xml kuntalupatunnus)]
            (if (seq app-info)
              (let [dummy-command         (action/application->command application)
                    old-applicants        (filter #(= (get-in % [:schema-info :name]) "hakija-r") documents)
                    new-applicants        (map applicant->applicant-doc (:hakijat app-info))]

                ; remove old applicants from application & create applicant doc for each
                (action/update-application dummy-command {$pull {:documents {:id {$in (map :id old-applicants)}}}})
                (action/update-application dummy-command {$pushAll {:documents new-applicants}})
                (println "Updated" id)
                (swap! fix-prev-permit-counter inc))
              (println "No XML for application" id)))
          (println "No verdict for application" id))))
    (println "fixed" @fix-prev-permit-counter "applications")
    (finally
      (mongo/disconnect!))))
