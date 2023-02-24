(ns lupapalvelu.notification-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.util :as util]))

(apply-remote-minimal)
(def esimerkki-id "esimerkki")
(last-email) ; inbox zero

(defn check-emails [& recipient-apikeys]
  (if (seq recipient-apikeys)
    (fact "Email recipients"
      (map :to (sent-emails))
      => (just (map (comp contains email-for-key)
                    recipient-apikeys)
               :in-any-order))
    (fact "No emails"
      (sent-emails) => empty?)))

(defn add-to-company [apikey admin? & [accept?]]
  (let [email (email-for-key apikey)
        role  (if admin? "admin" "regular user")]
    (fact {:midje/description (format "Add %s to Esimerkki Oy as %s."
                                      email role)}
      (command erkki :company-invite-user
               :email email
               :admin admin?
               :submit true) => ok?)
    (let [token (token-from-email email)]
      (when-not (false? accept?)
        (fact {:midje/description (str email " accepts invitation.")}
          (http-token-call token)))
      token)))

(facts "Setup company"
  (add-to-company teppo false))

(facts "Subscription"
  (let [{:keys [id]} (create-and-submit-application pena :propertyId sipoo-property-id)
        token        (add-to-company mikko true false)]

    (fact "Application was created" id => truthy)

    (fact "Invite company to the application"
      (command pena :company-invite :id id :company-id "esimerkki") => ok?)

    (fact "Invite Sven to the application"
      (command pena :invite-with-role :id id :email (email-for-key sven) :role "writer"
               :path "" :documentId "" :documentName ""
               :text "Come on, Sven!") => ok?)

    (fact "Clear emails"
      (sent-emails))

    (fact "No emails before accepting the invite"
      (comment-application sonja id false) => ok?
      (check-emails pena))

    (fact "Sven accepts the invitation"
      (command sven :approve-invite :id  id) => ok?)

    (fact "Company accepts invitation"
      (command erkki :approve-invite :id id :invite-type "company") => ok?)

    (fact "Applicant, Sven and company admin get email. Mikko does not."
      (comment-application sonja id false) => ok?
      (check-emails pena erkki sven))

    (fact "Pena cannot unsubscribe for Sven"
      (command pena :toggle-notification-subscription :id id :authId sven-id :subscribe false)
      => fail?)

    (fact "Invalid auth-id is checked"
      (command pena :toggle-notification-subscription :id id :authId "bad" :subscribe false)
      => (partial expected-failure? :error.not-found))

    (fact "Sven can unsubscribe for himself"
      (command sven :toggle-notification-subscription :id id :authId sven-id :subscribe false)
      => ok?)

    (fact "Mikko accepts company invitation and starts receiving emails"
      (http-token-call token)
      (comment-application sonja id false) => ok?
      (check-emails pena erkki mikko))

    (fact "Pena unsubscribes, no more email for him"
      (command pena :toggle-notification-subscription :id id :authId pena-id :subscribe false) => ok?
      (comment-application sonja id false) => ok?
      (check-emails erkki mikko))

    (fact "Teppo cannot unsubscribe company"
      (command teppo :toggle-notification-subscription :id id :authId esimerkki-id :subscribe false)
      => fail?)

    (fact "Erkki unsubscribes for company, no more emails."
      (command erkki :toggle-notification-subscription :id id :authId esimerkki-id :subscribe false)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails))

    (fact "sonja resubscribes pena and company, emails start again"
      (command sonja :toggle-notification-subscription :id id :authId pena-id :subscribe true) => ok?
      (command sonja :toggle-notification-subscription :id id :authId esimerkki-id :subscribe true)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena mikko erkki))

    (fact "sonja unsubscribes pena and company, no more email"
      (command sonja :toggle-notification-subscription :id id :subscribe false :authId pena-id) => ok?
      (command sonja :toggle-notification-subscription :id id :subscribe false :authId esimerkki-id)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails))

    (fact "pena resubscribes, emails start again"
      (command pena :toggle-notification-subscription :id id :authId pena-id :subscribe true) => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena))

    (fact "Mikko resubscribes, emails start again"
      (command mikko :toggle-notification-subscription :id id :authId esimerkki-id :subscribe true)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena erkki mikko))

    (fact "admin disables pena, no more email"
      (command admin :set-user-enabled :email "pena@example.com" :enabled "false") => ok?
      (comment-application sonja id false) => ok?
      (check-emails mikko erkki))

    (fact "Admin locks company, emails not affected"
      (command admin :company-lock
               :company "esimerkki"
               :timestamp (- (now) (* 1000 3600))) => ok?
      (comment-application sonja id false) => ok?
      (check-emails mikko erkki))

    (fact "pena sees mails after being enabled again"
      (command admin :set-user-enabled :email "pena@example.com" :enabled "true") => ok?
      (comment-application sonja id false)
      (check-emails pena mikko erkki))

    (fact "Admin unlocks company, emails not affected"
      (command admin :company-lock
               :company "esimerkki"
               :timestamp 0) => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena mikko erkki))

    (fact "Admin disables mikko, no more email"
      (command admin :set-user-enabled :email "mikko@example.com" :enabled "false") => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena erkki))

    (fact "Application state changes: emails!"
      (command sonja :check-for-verdict :id id) => ok?
      (check-emails pena erkki))

    (fact "Pena is added to company but still receives mails only once"
      (add-to-company pena true)
      (comment-application sonja id false) => ok?
      (check-emails pena erkki))))

(defn- update-party
  "Sets the party data on the given document.
   Nil values are not set (for party types that don't use them)"
  [app-id doc-id fname email graduation field req-class role]
  (command sonja :update-doc :id app-id :doc doc-id
           :updates (->> [["yhteystiedot.email" email]
                          ["yhteystiedot.puhelin" "040-0400400"]
                          ["patevyys.valmistumisvuosi" graduation]
                          ["patevyys.koulutusvalinta" field]
                          ["patevyys.patevyysluokka" req-class]
                          ["suunnittelutehtavanVaativuusluokka" req-class]
                          ["kuntaRoolikoodi" role]
                          ["henkilotiedot.etunimi" fname]
                          ["henkilotiedot.sukunimi" "Rovasti"]
                          ["osoite.katu" "Rovansgatan"]
                          ["osoite.postinumero" "33220"]
                          ["osoite.postitoimipaikannimi" "Rovasti"]
                          ["henkilotiedot.hetu" "131052-308T"]]
                         (filterv #(some? (second %))))))

(defn- get-doc-id-by-name
  [application name]
  (->> (:documents application)
       (util/find-first #(= name (get-in % [:schema-info :name])))
       :id))

(facts "Authority admin authored automatic emails"
  (let [make-partial  #(partial %1 sipoo %2 :organizationId "753-R")
        action-add    (make-partial command :add-organization-automatic-email-template)
        action-save   (make-partial command :save-organization-automatic-email-template-field)
        action-get    (make-partial query :get-organization-automatic-email-templates)
        application   (create-and-submit-application pena :propertyId sipoo-property-id)
        app-id        (:id application)]
    (fact "Admin sets up automatic emails for organization"
      (command admin :set-organization-boolean-attribute
               :organizationId "753-R"
               :enabled true
               :attribute "automatic-emails-enabled") => ok?)
    (fact "Authority admin creates three new email templates"
      (action-add) => ok?
      (action-add) => ok?
      (action-add) => ok?
      (action-add) => ok?)
    (fact "Authority admin fills the email templates"
      (let [template-ids  (mapv :id (:templates (action-get)))
            save-nth      (fn [i field value] (action-save :emailId (nth template-ids i) :field field :value value))]
        (fact "Template A - sent to group 1"
          (save-nth 0 :title "Template A")              => ok?
          (save-nth 0 :contents    "Contents A")              => ok?
          (save-nth 0 :operations  ["kerrostalo-rivitalo"])   => ok?
          (save-nth 0 :states      ["submitted"])             => ok?
          (save-nth 0 :parties     ["vastaava tyonjohtaja"
                                    "erityissuunnittelijat"]) => ok?)
        (fact "Template B - not sent to anyone (missing states)"
          (save-nth 1 :title       "Template B")              => ok?
          (save-nth 1 :contents    "Contents B")              => ok?
          (save-nth 1 :operations  ["kerrostalo-rivitalo"])   => ok?
          (save-nth 1 :states      [])                        => ok?
          (save-nth 1 :parties     ["paasuunnittelija"])      => ok?)
        (fact "Template C - not sent to anyone (missing operations)"
          (save-nth 2 :title       "Template C")              => ok?
          (save-nth 2 :contents    "Contents C")              => ok?
          (save-nth 2 :operations  [])                        => ok?
          (save-nth 2 :states      ["submitted"])             => ok?
          (save-nth 2 :parties     ["paasuunnittelija"])      => ok?)
        (fact "Template D - sent to group 2"
          (save-nth 3 :title       "Template D")              => ok?
          (save-nth 3 :contents    "Contents D")              => ok?
          (save-nth 3 :operations  ["kerrostalo-rivitalo"])   => ok?
          (save-nth 3 :states      ["submitted"])             => ok?
          (save-nth 3 :parties     ["muut tyonjohtajat"
                                    "paasuunnittelija"])      => ok?)))
    (fact "Pena adds parties to the application"
      ;; There are no designers or supervisors in minimal; use random people instead
      (let [app-vtj   (create-foreman-application app-id sonja sonja-id "vastaava ty\u00F6njohtaja" "A")
            app-tj    (create-foreman-application app-id ronja ronja-id "IV-ty\u00F6njohtaja" "A")
            {s :doc}  (command sonja :create-doc :id app-id :schemaName "suunnittelija")
            ps        (get-doc-id-by-name application "paasuunnittelija")
            vtj       (get-doc-id-by-name app-vtj "tyonjohtaja-v2")
            tj        (get-doc-id-by-name app-tj "tyonjohtaja-v2")]
        (update-party app-vtj vtj "Sonja"   (email-for-key sonja)   nil nil nil "vastaava ty\u00F6njohtaja")
        (update-party app-tj  tj  "Ronja"   (email-for-key ronja)   nil nil nil "IV-ty\u00F6njohtaja")
        (update-party app-id  s   "Mikko"   (email-for-key mikko)   "2010" "arkkitehti" "A" nil)
        (update-party app-id  ps  "Veikko"  (email-for-key veikko)  "2009" "kirvesmies" "A" nil)))
    (fact "Pena resubmits the application now that templates and parties are configured"
      (fact "The authority admin emails are sent"
        (command sonja :return-to-draft :id app-id :text "" :lang "fi") => ok?
        (last-email) ; empty the inbox
        (command pena :submit-application :id app-id) => ok?
        (check-emails mikko sonja ronja veikko        ;Authority admin emails
                      pena erkki)))                   ;Lupapiste emails
    (fact "Pena resubmits the application again"
      (fact "The authority admin emails are not sent again"
        (command sonja :return-to-draft :id app-id :text "" :lang "fi") => ok?
        (last-email) ; empty the inbox
        (command pena :submit-application :id app-id) => ok?
        (check-emails pena erkki)))))                 ;Lupapiste emails
