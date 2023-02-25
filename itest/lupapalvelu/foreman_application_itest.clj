(ns lupapalvelu.foreman-application-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.test-util :refer [in-text]]
            [lupapalvelu.verdict :as verdict]
            [midje.sweet :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.core :refer [fail]]
            [sade.date :refer [timestamp]]
            [sade.shared-util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(apply-remote-minimal)

(defn create-foreman-app [apikey authority application-id & [foreman-role]]
  (let [email                (email-for-key apikey)
        {foreman-app-id :id} (command authority :create-foreman-application :id application-id
                                      :taskId ""
                                      :foremanRole (or foreman-role "ei tiedossa")
                                      :foremanEmail email)]
    (fact {:midje/description (str email " approves foreman invite")}
      (command apikey :approve-invite :id foreman-app-id) => ok?)
    (fact {:midje/description (str email " approves foreman invite to original app")}
     (command apikey :approve-invite :id application-id) => ok?)
   (query-application apikey foreman-app-id)))

(defn update-foreman-name [apikey foreman-app-id first-name last-name]
  (let [foreman-app (query-application apikey foreman-app-id)
        doc-id      (:id (domain/get-document-by-name foreman-app "tyonjohtaja-v2"))]
    (fact {:midje/description (str "Foreman name is " first-name " " last-name)}
      (command apikey :update-doc :id foreman-app-id :doc doc-id
               :updates [["henkilotiedot.etunimi" first-name]
                         ["henkilotiedot.sukunimi" last-name]]) => ok?)))

(defn- set-applicant
  [apikey application fname lname email]
  (let [doc-id (:id (domain/get-document-by-name application "hakija-r"))]
    (fact "Set applicant"
      (command apikey :update-doc :id (:id application) :doc doc-id
               :updates (->> [["_selected" "henkilo"]
                              ["henkilo.yhteystiedot.email" email]
                              ["henkilo.henkilotiedot.etunimi" fname]
                              ["henkilo.henkilotiedot.sukunimi" lname]]
                             (filterv #(some? (second %))))) => ok?)))

(defn add-invites [apikey application-id]
  (let [{hakija1 :doc}               (command apikey :create-doc :id application-id :schemaName "hakija-r")
        {hakija-no-auth :doc}        (command apikey :create-doc :id application-id :schemaName "hakija-r")
        {hakija-contact-person :doc} (command apikey :create-doc :id application-id :schemaName "hakija-r")
        {hakija-company :doc}        (command apikey :create-doc :id application-id :schemaName "hakija-r")]
    (fact "add-invites"
          (command apikey :invite-with-role :id application-id :email "foo@example.com" :text "" :documentName ""
                   :documentId "" :path "" :role "writer") => ok?
          (command apikey :update-doc :id application-id :doc hakija1
                   :updates [["henkilo.yhteystiedot.email" "foo@example.com"]]) => ok?
          (command apikey :update-doc :id application-id :doc hakija-no-auth
                   :updates [["henkilo.yhteystiedot.email" "unknown@example.com"]]) => ok?
          (command apikey :update-doc :id application-id :doc hakija-contact-person
                   :updates [["_selected" "yritys"] ["yritys.yhteyshenkilo.yhteystiedot.email" "contact@example.com"]]) => ok?
          (invite-company-and-accept-invitation apikey application-id "solita" kaino) => truthy
          (command apikey :update-doc :id application-id :doc hakija-company
                   :updates [["_selected" "yritys"] ["yritys.companyId" "solita"]]) => ok?)
    {:hakija1 hakija1
     :hakija-no-auth hakija-no-auth
     :hakija-contact-person hakija-contact-person
     :hakija-company hakija-company}))

(facts* "Foreman application"
        (let [fake-application          (create-and-submit-application mikko :operation "pientalo")                              => truthy
              {application-id :id}      (create-and-open-application mikko :operation "kerrostalo-rivitalo")                     => truthy
              application               (query-application mikko application-id)
              _                         (generate-documents! application mikko)
              {foreman-application-id :id
               :as                    foreman-application} (create-foreman-app teppo sonja application-id)
              foreman-link-permit-data  (first (foreman-application :linkPermitData))
              foreman-doc               (domain/get-document-by-name foreman-application "tyonjohtaja-v2")
              _                         (command mikko :add-link-permit :id (:id fake-application) :linkPermitId application-id) => ok?
              application               (query-application mikko application-id)
              link-from-foreman         (some #(when (= "tyonjohtajan-nimeaminen-v2" (:operation %)) %) (application :appsLinkingToUs))
              foreman-applications      (query mikko :foreman-applications :id application-id)                                   => truthy]

          (fact "Has two link permits (pientalo and foreman)"
            (count (:appsLinkingToUs application)) => 2)

          (fact "Initial permit subtype is blank"
            (:permitSubtype foreman-application) => ss/blank?)

          (fact "Auths are correct, foreman get's invited to both applications"
            (->> (:auth foreman-application)
                 (map #(select-keys % [:username :role]))) => (just [{:username "sonja" :role "writer"}
                                                                     {:username "teppo@example.com" :role "foreman"}]
                                                                    :in-any-order)
            (->> (:auth application)
                 (map #(select-keys % [:username :role]))) => (just [{:username "mikko@example.com" :role "writer"}
                                                                     {:username "teppo@example.com" :role "foreman"}]
                                                                    :in-any-order))

          (fact "Foreman can comment both applications"  ; testing foreman-app-context with add-comment command
            (fact "Foreman application "
              (comment-application teppo foreman-application-id) => ok?)
            (fact "Main application"
              (comment-application teppo application-id) => ok?))

          (fact "Foreman application contains link to application"
                (:id foreman-link-permit-data) => application-id)

          (fact "Original application contains summary of foreman application"
                (:id link-from-foreman) => foreman-application-id
                (:state link-from-foreman) => "open"
                (:foreman link-from-foreman) => ""
                (:foremanRole link-from-foreman) => "ei tiedossa")

          (fact "All linked Foreman applications are returned in query"
            (let [applications (:applications foreman-applications)]
                  (count applications) => 1
                  (:id (first applications)) => foreman-application-id))

          (fact "Document data is copied to foreman application"
                (fact "Hankkeen kuvaus"
                  (let [foreman-hankkeen-kuvaus (domain/get-document-by-name foreman-application "hankkeen-kuvaus-minimum")
                        app-hankkeen-kuvaus     (domain/get-document-by-name application "hankkeen-kuvaus")]

                        (get-in app-hankkeen-kuvaus [:data :kuvaus :value]) => (get-in foreman-hankkeen-kuvaus [:data :kuvaus :value])))

                (fact "Tyonjohtaja doc has value from the command"
                      (get-in foreman-doc [:data :kuntaRoolikoodi :value]) => "ei tiedossa")

                (fact "Hakija docs are equal, except the userId"
                  (let [hakija-doc-data         (:henkilo (:data (domain/get-document-by-name application "hakija-r")))
                        foreman-hakija-doc-data (:henkilo (:data (domain/get-document-by-name foreman-application "hakija-tj")))]

                        hakija-doc-data => map?
                        foreman-hakija-doc-data => map?

                        (dissoc foreman-hakija-doc-data :userId) => (dissoc hakija-doc-data :userId))))

          (fact "Foreman name index is updated"
                (command teppo :update-doc :id (:id foreman-application) :doc (:id foreman-doc)
                         :updates [["henkilotiedot.etunimi" "Veijo"]
                                   ["henkilotiedot.sukunimi" "Viranomainen"]
                                   ["kuntaRoolikoodi" "vastaava ty\u00F6njohtaja"]]) => ok?

                (let [application-after-update (query-application teppo (:id foreman-application))]
                  (:foreman foreman-application) => ss/blank?
                  (:foremanRole foreman-application) => ss/blank?
                  (:foreman application-after-update) => "Viranomainen Veijo"
                  (:foremanRole application-after-update) => "vastaava ty\u00F6njohtaja"))

          (fact "Can't submit foreman app because subtype is not selected"
            (get (query teppo :application-submittable :id foreman-application-id) :errors) => (just [(fail :error.foreman.type-not-selected)])
            (command teppo :submit-application :id foreman-application-id) => (partial expected-failure? :error.foreman.type-not-selected))

          (fact "Update subtype to 'tyonjohtaja-hakemus'"
            (command teppo :change-permit-sub-type :id foreman-application-id :permitSubtype "tyonjohtaja-hakemus") => ok?)

          (fact "Can't submit foreman app before original link-permit-app is submitted"
            (query teppo :application-submittable :id foreman-application-id) => (partial expected-failure? :error.not-submittable.foreman-link))

          (fact "Submit link-permit app"
            (command mikko :submit-application :id application-id) => ok?
            (query teppo :application-submittable :id foreman-application-id) => ok?)

          (fact "Update subtype to 'tyonjohtaja-ilmoitus'"
            (command teppo :change-permit-sub-type :id foreman-application-id :permitSubtype "tyonjohtaja-ilmoitus") => ok?)

          (facts "Can't submit foreman notice app if link permit doesn't have verdict"
            (fact "gives error about foreman notice"
              (query teppo :application-submittable :id foreman-application-id) => (partial expected-failure? :error.foreman.notice-not-submittable)
              (command teppo :submit-application :id foreman-application-id) => (partial expected-failure? :error.foreman.notice-not-submittable))
            (command sonja :check-for-verdict :id application-id) => ok?
            (fact "ok after link-permit has verdict"
              (command teppo :submit-application :id foreman-application-id) => ok?))

          (facts "Auths without foremanEmail"               ; tested after verdict, when it's possible to create foreman-app as writer
            (let [{foreman-id :id :as resp} (command mikko :create-foreman-application :id application-id
                                                     :taskId "" :foremanRole "ei tiedossa" :foremanEmail "")
                  orig-app                  (query-application mikko application-id)
                  foreman-app               (query-application mikko foreman-id)]
              resp => ok?
              (fact "original app"
                (->> (:auth orig-app)
                     (map #(select-keys % [:username :role]))) => (just [{:username "mikko@example.com" :role "writer"}
                                                                         {:role "foreman", :username "teppo@example.com"}]
                                                                        :in-any-order))
              (fact "foreman app"
                (->> (:auth foreman-app)
                     (map #(select-keys % [:username :role]))) => (just [{:username "mikko@example.com" :role "writer"}]))))

          ;; delete verdict for next steps
          (let [app         (query-application mikko application-id)
                verdict-id  (-> app :verdicts first :id)
                verdict-id2 (-> app :verdicts second :id)]
            (command sonja :delete-verdict :id application-id :verdict-id verdict-id) => ok?
            (command sonja :delete-verdict :id application-id :verdict-id verdict-id2) => ok?
            (fact "is submitted" (:state (query-application mikko application-id)) => "submitted"))

          (facts "approve foreman"
                 (fact "Can't approve foreman application before actual application"
                       (command sonja :approve-application :id foreman-application-id :lang "fi") => (partial expected-failure? :error.integration.create-message))

                 (fact "can still comment"
                       (comment-application teppo foreman-application-id) => ok?)

                 (fact "After approving actual application, foreman can be approved. No need for verdict"
                       (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
                       (command sonja :approve-application :id application-id :lang "fi") => ok?
                       (command sonja :approve-application :id foreman-application-id :lang "fi") => ok?)

                 (fact "when foreman application is of type 'ilmoitus', after approval its state is acknowledged"
                       (:state (query-application teppo foreman-application-id)) => "acknowledged")

                 (fact "can no longer comment"
                   (comment-application teppo foreman-application-id) => fail?)

                 (letfn [(change-location [apikey]
                           (command apikey :change-location :id foreman-application-id
                                    :address "Urheilutie"
                                    :propertyId "75341600620000"
                                    :x 404459.554
                                    :y 6693624.582))]
                   (fact "applicant cannot change location"
                     (change-location teppo) => fail?)
                   (fact "... but authority can"
                     (change-location sonja) => ok?)))

          (facts "Special foreman/designer verdicts"
            (let [foreman-application     (query-application sonja foreman-application-id)
                  xml-file                (fn [filename] (-> filename io/resource slurp
                                                             (xml/parse-string "utf-8")
                                                             cr/strip-xml-namespaces
                                                             (enlive/at [:paatostieto] (constantly nil))))
                  special            (-> "krysp/verdict-r-foremen.xml"
                                         xml-file
                                         (enlive/at [:poytakirja enlive/any-node] nil)
                                         (enlive/at [:tunnus enlive/any-node]
                                                    (enlive/replace-vars {:application-id (:id application)})))
                  typical            (xml-file "krysp/dev/verdict.xml")
                  ;; LPK-1120: False positives on (at least) some non-special foreman verdicts.
                  old-false-positive (-> "krysp/old-false-positive-special-foreman-verdict.xml"
                                         xml-file
                                         (enlive/at [:tunnus enlive/any-node]
                                                    (enlive/replace-vars {:application-id (:id application)})))
                  normalized         (verdict/verdict-xml-with-foreman-designer-verdicts foreman-application special)
                  poytakirja         (-> normalized (enlive/select [:paatostieto :Paatos :poytakirja :> enlive/any-node]))]
                   (fact "Special verdict"
                         (verdict/special-foreman-designer-verdict? foreman-application special) => truthy)
                   (fact "Typical verdict"
                         (verdict/special-foreman-designer-verdict? foreman-application typical) => falsey)
                   (fact "Old false positive verdict"
                         (verdict/special-foreman-designer-verdict? foreman-application old-false-positive) => falsey)
                   (fact "Paatostieto has replaced the old one"
                          (count (enlive/select normalized [:paatostieto])) => 1)
                   (fact "Paatostieto is in the right place"
                     (let [elems (-> normalized (enlive/select [:RakennusvalvontaAsia :> enlive/any-node]))
                           elems (mapv :tag elems)
                           index (.indexOf elems :paatostieto)]
                           (subvec elems (dec index) (+ index 2))
                           => [:katselmustieto
                               :paatostieto
                               :lisatiedot]))
                   (fact "Poytakirja contents are OK"
                     (reduce (fn [acc m] (assoc acc (:tag m) (-> m :content))) {} poytakirja)
                     => {:liite           [{:attrs nil :content ["kuvaus 1"] :tag :kuvaus}
                                           {:attrs nil
                                            :content ["http://localhost:8000/dev/sample-verdict.pdf"]
                                            :tag :linkkiliitteeseen}]
                         :paatoksentekija ["Pena"]
                         :paatoskoodi     ["hyväksytty"]
                         :paatospvm       ["2015-11-29"]
                         :pykala          ["2"]})
                   (facts "Other paatostieto placements"
                     (let [minispec (enlive/at special
                                               [:paatostieto] nil
                                               [:lisatiedot] nil
                                               [:liitetieto] nil
                                               [:asianTiedot] nil)]
                           (fact "Paatostieto will be last element"
                             (let [norm  (verdict/verdict-xml-with-foreman-designer-verdicts foreman-application minispec)
                                   elems (-> norm (enlive/select [:RakennusvalvontaAsia :> enlive/any-node]))]
                                   (-> elems last :tag) => :paatostieto))
                           (for [e    [:muistiotieto :lisatiedot :liitetieto :kayttotapaus :asianTiedot]
                                 :let [norm (verdict/verdict-xml-with-foreman-designer-verdicts foreman-application minispec)
                                       elems (-> norm (enlive/select [:RakennusvalvontaAsia :> enlive/any-node]))]]
                             (do (fact (str e " is last") (-> elems last :tag) => e)
                                 (fact "Paatostieto is next to last" (->> elems (drop-last 2) first :tag) => :paatostieto)))))))))

(facts "updating other foreman projects to current foreman application"
  (apply-remote-minimal)
  (let [{application1-id :id}         (create-and-open-application mikko :operation "kerrostalo-rivitalo") => truthy
        {foreman-application1-id :id} (create-foreman-app mikko sonja application1-id) => truthy
        {application2-id :id}         (create-and-open-application mikko :operation "kerrostalo-rivitalo") => truthy
        {foreman-application2-id :id} (create-foreman-app mikko sonja application2-id)
        foreman-application1          (query-application mikko foreman-application1-id)
        foreman-application2          (query-application mikko foreman-application2-id)

        foreman-doc1                  (domain/get-document-by-name foreman-application1 "tyonjohtaja-v2")
        foreman-doc2                  (domain/get-document-by-name foreman-application2 "tyonjohtaja-v2")]
    (fact "other project is not updated into the current foreman application because the corresponding foreman application is in a pre-verdict state"
          (command mikko :set-current-user-to-document :id foreman-application1-id :documentId (:id foreman-doc1) :userId mikko-id :path "") => truthy
          (command mikko :set-current-user-to-document :id foreman-application2-id :documentId (:id foreman-doc2) :userId mikko-id :path "") => truthy
          (command mikko :update-foreman-other-applications :id foreman-application2-id :foremanHetu "")

          (let [updated-application (query-application mikko foreman-application2-id)
                updated-foreman-doc (domain/get-document-by-name updated-application "tyonjohtaja-v2")
                project-id (get-in updated-foreman-doc [:data :muutHankkeet :0 :luvanNumero :value])]
            (fact "first project is in other projects document"
                  project-id => "")))

    (fact "other project is updated into current foreman application after the corresponding foreman application is in post-verdict state"
          (command mikko :change-permit-sub-type :id foreman-application1-id :permitSubtype "tyonjohtaja-hakemus") => ok?
          (command mikko :submit-application :id application1-id) => ok?
          (command sonja :check-for-verdict  :id application1-id) => ok?
          (command mikko :submit-application :id foreman-application1-id) => ok?
          (command sonja :check-for-verdict  :id foreman-application1-id) => ok?
          (command mikko :update-foreman-other-applications :id foreman-application2-id) => ok?

          (let [updated-application (query-application mikko foreman-application2-id)
                updated-foreman-doc (domain/get-document-by-name updated-application "tyonjohtaja-v2")
                project-id (get-in updated-foreman-doc [:data :muutHankkeet :0 :luvanNumero :value])]
            (fact "first project is in other projects document"
                  project-id => application1-id)))))

(facts "Link foreman application to task"
  (let [application (create-and-submit-application mikko)
        _ (command sonja :check-for-verdict :id (:id application))
        application (query-application mikko (:id application))
        {foreman-application-id-1 :id} (create-foreman-app mikko sonja (:id application))
        {foreman-application-id-2 :id} (create-foreman-app mikko sonja (:id application))
        tasks (:tasks application)
        foreman-tasks (filter #(= (get-in % [:schema-info :name]) "task-vaadittu-tyonjohtaja") tasks)]

    (fact "link first foreman"
      (command mikko :link-foreman-task :id (:id application) :taskId (:id (first foreman-tasks)) :foremanAppId foreman-application-id-1) => ok?
      (let [app (query-application mikko (:id application))
            updated-tasks (:tasks app)
            updated-foreman-task (first (filter #(= (get-in % [:schema-info :name]) "task-vaadittu-tyonjohtaja") updated-tasks))]
        (get-in updated-foreman-task [:data :asiointitunnus :value]) => foreman-application-id-1))

    (fact "cannot link same foreman to another task"
      (command mikko :link-foreman-task :id (:id application) :taskId (:id (second foreman-tasks)) :foremanAppId foreman-application-id-1) => (partial expected-failure? "error.foreman-already-linked"))

    (fact "linked foreman can be changed on task"
      (command mikko :link-foreman-task :id (:id application) :taskId (:id (first foreman-tasks)) :foremanAppId foreman-application-id-2) => ok?)

    (fact "another foreman can now be linked to first task"
      (command mikko :link-foreman-task :id (:id application) :taskId (:id (second foreman-tasks)) :foremanAppId foreman-application-id-1) => ok?)))

(facts "foreman history"
  (apply-remote-minimal) ; clean mikko before history tests
  (let [{history-base-app-id :id} (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
        _                    (give-legacy-verdict sonja history-base-app-id)
        other-r-app-id       (create-app-id mikko :operation "kerrostalo-rivitalo")
        ya-app-id            (create-app-id mikko :operation "ya-kayttolupa-muu-tyomaakaytto")
        foreman-app-id1      (create-foreman-application history-base-app-id mikko mikko-id "KVV-ty\u00F6njohtaja" "B"); -> should be visible
        foreman-app-id2      (create-foreman-application history-base-app-id mikko mikko-id "KVV-ty\u00F6njohtaja" "A") ; -> should be visible
        foreman-app-id3      (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "B") ; -> should be visible
        foreman-app-id4      (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "B") ; -> should *NOT* be visible
        foreman-app-id5      (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "A") ; -> should be visible
        foreman-app-canceled (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "A") ; -> should NOT be visible

        base-foreman-app-id  (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "B")] ;for calling history

    (command mikko :cancel-application :id foreman-app-canceled :text nil :lang "fi") => ok?

    (facts"reduced"
          (fact "History is empty since all foreman applications are in the pre-verdict state"
                (let [reduced (query sonja :foreman-history :all false :id base-foreman-app-id) => ok?]
                  (-> reduced :projects empty?) => true))
          (finalize-foreman-app mikko sonja foreman-app-id1 true)
          (fact "History is now the same both for reduced and unreduced"
                (query sonja :foreman-history :all false :id foreman-app-id2) => (contains {:ok true :all true})
                (query sonja :foreman-history :all true :id foreman-app-id2) => (contains {:ok true :all true}))
          (finalize-foreman-app mikko sonja foreman-app-id2 false)
          (finalize-foreman-app mikko sonja foreman-app-id3 true)
          (finalize-foreman-app mikko sonja foreman-app-id4 true)
          (finalize-foreman-app mikko sonja foreman-app-id5 true)

          (fact "Appeal one foreman-app"
                (command sonja :change-application-state :id foreman-app-id5 :state :appealed) => ok?)
          (fact "reduced history should contain reduced history (excluding canceled application)"
                (let [reduced-history (query sonja :foreman-history :all false :id base-foreman-app-id)
                      => (contains {:ok true :all false})
                      history-ids (map :foremanAppId (:projects reduced-history))]
                  history-ids => (just [foreman-app-id1 foreman-app-id2 foreman-app-id3 foreman-app-id5] :in-any-order)
                  (some #{foreman-app-id4} history-ids) => nil?))

      (fact "reduced history should depend on the base application"
        (let [reduced-history (query sonja :foreman-history :all false :id foreman-app-id1) => ok?
              history-ids (map :foremanAppId (:projects reduced-history))]
          history-ids =>     (just [foreman-app-id2 foreman-app-id3 foreman-app-id5] :in-any-order)
          history-ids =not=> (has some #{foreman-app-id4 base-foreman-app-id})))

      (fact "Unknown foreman app id"
        (query sonja :foreman-history :all false :id "foobar") => fail?))

    (fact "Should be queriable only with a foreman application"
      (let [resp (query sonja :foreman-history :all true :id history-base-app-id) => fail?]
        (:text resp) => "error.not-foreman-app"))

    (facts "unreduced"
      (fact "unreduced history should not reduce history"
            (let [unreduced-history (query sonja :foreman-history :all true :id base-foreman-app-id)
                  => (contains {:ok true :all true})
              history-ids       (map :foremanAppId (:projects unreduced-history))]
          history-ids => (just [foreman-app-id1 foreman-app-id2 foreman-app-id3 foreman-app-id4 foreman-app-id5] :in-any-order)))

      (fact "Unknown foreman app id"
        (query sonja :foreman-history :all true :id "foobar") => fail?)

      (fact "Should be queriable only with a foreman application"
        (let [resp (query sonja :foreman-history :all false :id history-base-app-id) => fail?]
          (:text resp) => "error.not-foreman-app")))

    (fact "can not link foreman application with a second application"
      (command mikko :add-link-permit :id foreman-app-id3 :linkPermitId other-r-app-id) => fail?)

    (fact "applicant can not remove link permit on foreman application"
      (command mikko :remove-link-permit-by-app-id :id foreman-app-id3 :linkPermitId history-base-app-id) => unauthorized?)

    (fact "can not link foreman application with YA application"
      (fact "Setup: comment for opening application + remove old link"
        (command mikko :add-comment :id foreman-app-id3 :text "please, remove link permit" :target {:type "application"} :roles [] :openApplication true) => ok?
        (command sonja :remove-link-permit-by-app-id :id foreman-app-id3 :linkPermitId history-base-app-id) => ok?)

      (command mikko :add-link-permit :id foreman-app-id3 :linkPermitId ya-app-id) => fail?)

    (fact "can not link foreman applications to each other"
      (fact "Setup: comment for opening application + remove old link"
        (command mikko :add-comment :id foreman-app-id4 :text "please, remove link permit" :target {:type "application"} :roles [] :openApplication true) => ok?
        (command sonja :remove-link-permit-by-app-id :id foreman-app-id4 :linkPermitId history-base-app-id) => ok?)
      (fact "try to link foreman application"
        (command mikko :add-link-permit :id foreman-app-id4 :linkPermitId foreman-app-id5) => fail?))

    (fact "Link permit may be a single paper permit"
      (fact "Setup: comment for opening application + remove old link"
        (command mikko :add-comment :id foreman-app-id5 :text "please, remove link permit" :target {:type "application"} :roles [] :openApplication true) => ok?
        (command sonja :remove-link-permit-by-app-id :id foreman-app-id5 :linkPermitId history-base-app-id) => ok?)
      (fact "1st succeeds"
        (command mikko :add-link-permit :id foreman-app-id5 :linkPermitId "other ID 1") => ok?)
      (fact "2nd fails"
        (command mikko :add-link-permit :id foreman-app-id5 :linkPermitId "other ID 2") => fail?))))

(facts* "Auths and invites"
  (let [apikey pena
        application-id         (create-app-id apikey :operation "kerrostalo-rivitalo")
        _ (add-invites apikey application-id)
        _ (command apikey :submit-application :id application-id)
        _ (give-legacy-verdict sonja application-id)
        has-auth? (fn [email auth]
                        (or (some (partial = email) (map :username auth)) false))]
    (sent-emails) ; clear email box

    (fact "Create foreman application with correct auths"
      (let [{foreman-app-id :id} (command apikey :create-foreman-application :id application-id
                                   :taskId "" :foremanRole "ei tiedossa" :foremanEmail "heppu@example.com") => truthy
            {auth-array :auth} (query-application pena foreman-app-id) => truthy
            {orig-auth :auth}  (query-application pena application-id)]
        (count auth-array) => 4
        (fact "Pena is writer" (:username (some #(when (= (:role %) "writer") %) auth-array)) => "pena")
        (fact "applicant 'foo@example.com' is authed to foreman application"
          (has-auth? "foo@example.com" auth-array) => true)
        (fact "applicant 'unknown@example.com' is not authed to foreman app"
          (has-auth? "unknown@example.com" auth-array) => false)
        (fact "company contact person does not have auth, as it is not authed to original app"
          (has-auth? "contact@example.com" auth-array) => false)
        (fact "company 'solita' is authed" ; id is from minimal 1060155-5
          (has-auth? "1060155-5" auth-array) => true)
        (fact "foreman is authed, also to original application"
          (has-auth? "heppu@example.com" auth-array)
          (has-auth? "heppu@exampe.com" orig-auth))

        (let [emails (sent-emails)]
          (fact "Emails are sent correctly to recipients"
            (let [recipients (map :to emails)]
              ; foreman does not receive separate invitation email for the original application
              recipients => (just ["heppu@example.com"
                                   "foo@example.com"
                                   "Kaino Solita <kaino@solita.fi>"] :in-any-order)))
          (fact "heppu@example.com receives the foreman invite"
            (->> emails
                 (filter #(= (:to %) "heppu@example.com"))
                 first :body :plain)
            => (contains "sinua ollaan nime\u00e4m\u00e4ss\u00e4 ty\u00f6njohtajaksi"))
          (fact "others receive the normal invite"
                (->> emails
                     (remove #(= (:to %) "heppu@example.com"))
                     (map (comp :plain :body)))
                => (has every? (partial re-find #"(Sinut halutaan valtuuttaa)|(haluaa valtuuttaa yrityksenne)"))))))

    (fact "Create foreman application with the applicant as foreman"
          (let [{application-id :id} (create-and-submit-application apikey :operation "kerrostalo-rivitalo") => truthy
                _                    (give-legacy-verdict sonja application-id)
                {foreman-app-id :id} (command apikey :create-foreman-application :id application-id
                                              :taskId "" :foremanRole "ei tiedossa" :foremanEmail "pena@example.com") => truthy
                {auth-array :auth}   (query-application pena foreman-app-id) => truthy
                {orig-auth :auth}    (query-application pena application-id)]
            (fact "Pena is the sole auth and writer on the foreman application"
                  (count auth-array) => 1
                  (:username (some #(when (= (:role %) "writer") %) auth-array)) => "pena")
            (fact "Pena is the sole auth and writer on the original application"
                  (count orig-auth) => 1
                  (:username (some #(when (= (:role %) "writer") %) orig-auth)) => "pena"))))
  (let [apikey pena
        has-auth? (fn [email auth]
                    (or (some (partial = email) (map :username auth)) false))]
    (fact "Contact person is added to new foreman app, when its auth is added to original application"
          (let [{application-id :id} (create-and-submit-application apikey :operation "kerrostalo-rivitalo") => truthy
                {:keys [hakija1]}    (add-invites apikey application-id)
                _ (command apikey :invite-with-role :id application-id :email "contact@example.com" :text "" :documentName ""
                           :documentId "" :path "" :role "writer") => ok?
                _ (command apikey :remove-doc :id application-id :docId hakija1) => ok? ; remove one applicant
                _ (give-legacy-verdict sonja application-id)

                ; This foreman application is created only to cause an invite to the original application
                _  (command apikey :create-foreman-application :id application-id
                            :taskId "" :foremanRole "ei tiedossa" :foremanEmail "heppu@example.com") => truthy

                _ (sent-emails)         ; reset email box
                {foreman-app-id :id} (command apikey :create-foreman-application :id application-id
                                              :taskId "" :foremanRole "ei tiedossa" :foremanEmail "heppu@example.com") => truthy
               {auth-array :auth} (query-application pena foreman-app-id) => truthy]
            (has-auth? "contact@example.com" auth-array) => true
            (has-auth? "foo@example.com" auth-array) => false

            (fact "Emails are correctly sent"
                  (let [recipients (map :to (sent-emails))]
                    (fact "only one email is sent to foreman, as he is already authed to original"
                          (count (filter (partial = "heppu@example.com") recipients)) => 1)
                    recipients => (just ["heppu@example.com"
                                         "contact@example.com"
                                         "Kaino Solita <kaino@solita.fi>"])))))

   (fact "No double-auth, if writer of foreman-application is authed as writer in original application" ;; LPK-1331
         (let [{application-id :id}    (create-and-submit-application apikey :operation "kerrostalo-rivitalo") => truthy
               {writer-applicant :doc} (command apikey :create-doc :id application-id :schemaName "hakija-r")
               ;; Pena fills Teppo's email to applicant document
               _                       (command apikey :update-doc :id application-id :doc writer-applicant
                                                :updates [["henkilo.yhteystiedot.email" "teppo@example.com"]]) => ok?
                                                ;; Pena invites Teppo to application
               _                       (command apikey :invite-with-role :id application-id :email "teppo@example.com" :text "" :documentName ""
                                                :documentId "" :path "" :role "writer")
               _                       (command teppo :approve-invite :id application-id)
               ;; Teppo (with writer role) creates foreman-application
               _                       (give-legacy-verdict sonja application-id)
               {foreman-app-id :id}    (command teppo :create-foreman-application :id application-id
                                                :taskId "" :foremanRole "ei tiedossa" :foremanEmail "heppu@example.com") => truthy
               {auth-array :auth}      (query-application teppo foreman-app-id) => truthy]
           (fact "Teppo is writer" (:username (some #(when (= (:role %) "writer") %) auth-array)) => "teppo@example.com")
           (fact "Teppo is not double authed"
                 (count (filter #(= (:username %) "teppo@example.com") auth-array)) => 1)))))

(facts "foreman rights"
  (let [applicant               pena
        authority               sonja
        foreman                 teppo
        foreman-2               erkki
        foreman-3               sven
        foreman-email           (email-for-key foreman)

        main-application        (create-and-submit-application applicant :operation "kerrostalo-rivitalo")
        application-id          (:id main-application)
        main-attachment-1       (-> main-application :attachments first)

        _                       (set-applicant sonja main-application "Pena" "Panaani" (email-for-key applicant))
        _                       (give-legacy-verdict authority application-id)

        resp                    (command applicant :create-foreman-application
                                     :id application-id
                                     :taskId ""
                                     :foremanRole "ei tiedossa"
                                     :foremanEmail foreman-email)
        resp-2                  (command applicant :create-foreman-application
                                   :id application-id
                                   :taskId ""
                                   :foremanRole "vastaava ty\u00F6njohtaja"
                                   :foremanEmail (email-for-key foreman-2))
        resp-3                  (command applicant :create-foreman-application
                                   :id application-id
                                   :taskId ""
                                   :foremanRole "ei tiedossa"
                                   :foremanEmail (email-for-key foreman-3))

        {foreman-app-id :id}    resp
        {foreman-2-app-id :id}  resp-2
        {foreman-3-app-id :id}  resp-3
        foreman-application     (query-application applicant foreman-app-id)
        foreman-doc             (domain/get-document-by-name foreman-application "tyonjohtaja-v2")
        foreman-applicant-doc   (domain/get-document-by-name foreman-application "hakija-tj")]

    (fact "sanity checks"
      resp => ok?
      main-attachment-1 => map?
      foreman-doc => map?
      (:id foreman-doc) => truthy
      foreman-applicant-doc => map?
      (:id foreman-applicant-doc) => truthy)

    (fact "no comment rights before invites are accepted"
      (comment-application foreman application-id) => unauthorized?
      (comment-application foreman foreman-app-id) => unauthorized?)

    (fact "accept invites"
      (command foreman :approve-invite :id application-id) => ok?
      (command foreman :approve-invite :id foreman-app-id) => ok?)

    (fact "foreman cannot add parties to foreman or post-verdict main application"
      (command foreman :create-doc :id application-id :schemaName "hakija-r")
      => (partial expected-failure? "error.document.post-verdict-addition") ; verdict has been given
      (command foreman :create-doc :id foreman-app-id :schemaName "hakija-tj")
      => (partial expected-failure? "error.foreman"))

    (fact "applicant can add parties"
      (command applicant :create-doc :id foreman-app-id :schemaName "hakija-tj") => ok?)

    (fact "foreman CAN read comments on the main application"
      (query foreman :comments :id application-id) => ok?)

    (fact "foreman can comment on the main application"
      (comment-application foreman application-id) => ok?)

    (fact "foreman CAN read & write comments on the foreman application"
      (query foreman :comments :id foreman-app-id) => ok?
      (comment-application foreman foreman-app-id) => ok?)

    (fact "foreman can invite writers both to the main  and foreman application"
      (command foreman :invite-with-role :id application-id :email "foo@example.com" :text "" :documentName ""
                       :documentId "" :path "" :role "writer") => ok?
      (command foreman :invite-with-role :id foreman-app-id :email "foo@example.com" :text "" :documentName ""
               :documentId "" :path "" :role "writer")
      => ok?)
    (fact "foreman can invite other foremen both to the main and foreman application"
      (command foreman :invite-with-role :id application-id :email "bar@example.com" :text "" :documentName ""
               :documentId "" :path "" :role "foreman") => ok?
      (command foreman :invite-with-role :id foreman-app-id :email "bar@example.com" :text "" :documentName ""
               :documentId "" :path "" :role "foreman")
      => ok?)

    (fact "foreman can invite guest to the main application"
      (command foreman :invite-guest :id application-id :email "baz@example.com" :role "guest") => ok?)

    (fact "foreman CAN invite guest to the foreman application"
      (command foreman :invite-with-role :id foreman-app-id :email "baz@example.com" :text "" :documentName ""
                       :documentId "" :path "" :role "guest") => fail?
    (fact "applicant CAN update applicant document on foreman application"
      (command applicant :update-doc :id foreman-app-id :doc (:id foreman-applicant-doc)
                         :updates [["henkilo.yhteystiedot.email" "foo@example.com"]]) => ok?)
      (command foreman :invite-guest :id foreman-app-id :email "baz@example.com" :role "guest") => ok?)

    (fact "foreman CANNOT update applicant document on foreman application"
      (command foreman :update-doc :id foreman-app-id :doc (:id foreman-applicant-doc)
               :updates [["henkilo.yhteystiedot.email" "foo@example.com"]]) => unauthorized?)

    (fact "both applicant and foreman CAN update foreman document on foreman application"
      (command applicant :update-doc :id foreman-app-id :doc (:id foreman-doc)
                         :updates [["yhteystiedot.email" "foo@example.com"]]) => ok?
      (command foreman :update-doc :id foreman-app-id :doc (:id foreman-doc)
                       :updates [["yhteystiedot.email" "foo2@example.com"]]) => ok?)

    (fact "foreman CANNOT set his own info to applicant document on foreman application"
      (command foreman :set-current-user-to-document :id foreman-app-id :documentId (:id foreman-applicant-doc) :path "") => unauthorized?)

    (fact "foreman CAN set his own info to foreman document on foreman application"
      (command foreman :set-current-user-to-document :id foreman-app-id :documentId (:id foreman-doc) :path "") => ok?)

    (fact "foreman CAN submit the foreman application"
      (fact "Update subtype to 'tyonjohtaja-hakemus'"
        (command foreman :change-permit-sub-type :id foreman-app-id :permitSubtype "tyonjohtaja-hakemus") => ok?)
      (command foreman :submit-application :id foreman-app-id) => ok?)

    (facts "attachments"
      (let [new-main-attachment     (upload-attachment foreman application-id nil true :filename "dev-resources/test-pdf.pdf")
            attachment-by-foreman   (upload-attachment foreman foreman-app-id nil true :filename "dev-resources/test-pdf.pdf")
            attachment-by-applicant (upload-attachment applicant foreman-app-id nil true :filename "dev-resources/test-pdf.pdf")]
       (fact "foreman CAN upload a new attachment to main application" new-main-attachment => ss/not-blank?)
       (fact "foreman CAN upload a new version on main application"
         (upload-attachment foreman application-id {:id new-main-attachment} true) => new-main-attachment)

       (fact "foreman CAN upload a new attachment to foreman application"
         (upload-attachment foreman foreman-app-id nil true) => ss/not-blank?)
       (fact "applicant CAN upload a new attachment to foreman application"
         (upload-attachment applicant foreman-app-id nil true) => ss/not-blank?)

       (fact "foreman CAN copy own attachments to foreman application"
         (command foreman :update-user :firstName "Teppo" :lastName "Nieminen" :architect true) => ok?

         (upload-user-attachment foreman "osapuolet.cv" true)

         (command foreman :copy-user-attachments-to-application :id foreman-app-id) => ok?)

       (fact "foreman can NOT upload a new version to pre-verdict attachment template on main application"
         (upload-attachment foreman application-id main-attachment-1 false) => (:id main-attachment-1))
       (fact "foreman can NOT upload a new version to applicants attachment on foreman application"
         (upload-attachment foreman foreman-app-id {:id attachment-by-applicant} false) => attachment-by-applicant)

       (let [{actions-by-id :actionsById} (query foreman :allowed-actions-for-category :category "attachments" :id foreman-app-id)
             actions-for-foreman-att      (get actions-by-id (keyword attachment-by-foreman))
             actions-for-applicant-att    (get actions-by-id (keyword attachment-by-applicant))
             actions                      [:upload-attachment :delete-attachment :delete-attachment-version :rotate-pdf
                                           :set-attachment-type :set-attachment-meta :set-attachment-visibility]]

         (fact "Foreman can edit own attachment"
           (doseq [action actions]
             (fact {:midje/description (name action)}
               (action actions-for-foreman-att) => ok?)))

         (fact "Foreman can not edit applicant's attachment"
           (doseq [action actions]
             (fact {:midje/description (name action)}
               (action actions-for-applicant-att) =not=> ok?))))

       (fact "Foreman CAN set attachment meta data"
         (command foreman :set-attachment-meta :id foreman-app-id :attachmentId attachment-by-foreman :meta {:contents "kontents"}) => ok?)

       (give-generic-legacy-verdict sonja foreman-app-id
                                    {:fields     {:kuntalupatunnus "888-10-12"
                                                  :verdict-code    "1" ;; Granted
                                                  :verdict-text    "Lorem ipsum"
                                                  :handler         "Decider"
                                                  :anto            (timestamp "21.5.2018")
                                                  :lainvoimainen   (timestamp "30.5.2018")}
                                     :attachment {:state "foremanVerdictGiven"}})

       (fact "Foreman can NOT upload new attachment after verdict is given"
         (upload-attachment foreman foreman-app-id nil false))

       (fact "Authority CAN upload new attachment after verduct is given"
         (upload-attachment sonja foreman-app-id nil true))

       (fact "Foreman can NOT set attachment meta data after verdict is given"
         (command foreman :set-attachment-meta :id foreman-app-id :attachmentId attachment-by-foreman :meta {:contents "kontents2"}) => fail?)

       (fact "Application search"
         (->> (datatables foreman :applications-search :operations ["tyonjohtajan-nimeaminen-v2"])
             :data :applications
             (map #(select-keys % [:state :kuntalupatunnus :verdictDate])))
         => (just [{:state           "foremanVerdictGiven"
                    :kuntalupatunnus "888-10-12"
                    :verdictDate     (timestamp "21.5.2018")}
                   {:state           "draft"
                    :kuntalupatunnus nil}]
                  :in-any-order))))

    (fact "Foreman can NOT cancel the main application"
      (command foreman :cancel-application :id application-id :lang "fi" :text "") => fail?)

    (fact "Foreman can't request own termination when the feature is not enabled"
      (command foreman :request-foreman-termination
               :id              application-id
               :foreman-app-id  (:id foreman-application)
               :foreman-email   foreman-email
               :reason          "")
      => fail?)

    (fact "Applicant can't request termination when the feature is not enabled"
      (command applicant :request-foreman-termination
               :id              application-id
               :foreman-app-id  foreman-3-app-id
               :foreman-email   (email-for-key foreman-3)
               :reason          "")
      => fail?)

    (fact "Authority admin can enable termination requests"
      (command sipoo :set-organization-foreman-termination-request-enabled :organizationId "753-R" :enabled true))

    (fact "Foreman can request own termination"
      (command foreman :request-foreman-termination
               :id              application-id
               :foreman-app-id  (:id foreman-application)
               :foreman-email   foreman-email
               :reason          "")
      => ok?)

    (fact "Foreman can NOT request the termination of another foreman"
      (command foreman-2 :request-foreman-termination
               :id              application-id
               :foreman-app-id  foreman-app-id
               :foreman-email   foreman-email
               :reason          "")
      => fail?)

    (fact "Foreman can NOT confirm termination"
      (command foreman :confirm-foreman-termination
               :id              application-id
               :foreman-app-id  foreman-app-id)
      => fail?)

    (fact "Authority can confirm termination"
      (command authority :confirm-foreman-termination
               :id              application-id
               :foreman-app-id  foreman-app-id)
      => ok?)

    (fact "Authority can terminate foreman without request/confirm phases"
      (command authority :terminate-foreman
               :id              application-id
               :foreman-app-id  foreman-2-app-id
               :reason          "")
      => ok?)

    (fact "Can't request termination for already terminated"
      (command foreman-2 :request-foreman-termination
               :id              application-id
               :foreman-app-id  foreman-2-app-id
               :foreman-email   (email-for-key foreman-2)
               :reason          "")
      => fail?)

    (fact "Applicant can request termination"
      (command applicant :request-foreman-termination
               :id              application-id
               :foreman-app-id  foreman-3-app-id
               :foreman-email   (email-for-key foreman-3)
               :reason          "")
      => ok?)

    (fact "Applicant creates a second application for the same foreman"
      (let [resp          (command applicant :create-foreman-application :id application-id
                                   :taskId "" :foremanRole "ei tiedossa" :foremanEmail foreman-email)
            foreman-app2  (:id resp)]
        resp => ok?
        (command foreman :approve-invite :id foreman-app2) => ok?
        (:state (query-application foreman foreman-app2)) => "draft"

        (fact "Foreman CAN cancel foreman application"
          (command foreman :cancel-application :id foreman-app2 :lang "fi" :text "") => ok?
          (:state (query-application foreman foreman-app2)) => "canceled")
        (fact "Foreman CAN undo his cancellation"
          (command foreman :undo-cancellation :id foreman-app2) => ok?
          (:state (query-application foreman foreman-app2)) => "draft" => "draft")))))

(defn create-foreman-app-and-request-termination
  [apikey foreman authority app-id foreman-role & [submit?]]
  (let [{foreman-app-id :id} (create-foreman-app foreman authority app-id foreman-role)]
    foreman-app-id => truthy
    (when submit?
      (fact "Set subtype and submit"
        (command authority :change-permit-sub-type :id foreman-app-id
                 :permitSubtype "tyonjohtaja-ilmoitus") => ok?
        (command authority :submit-application :id foreman-app-id) => ok?))
    (update-foreman-name foreman foreman-app-id "Super" "Viisori")
    (fact "Request termination"
      (command apikey :request-foreman-termination :id app-id
               :foreman-app-id foreman-app-id
               :foreman-email (email-for-key foreman)
               :reason "Fired!") => ok?)
    (fact "Termination data ok"
      (let [{:keys [foremanTermination
                    submitted]} (query-application apikey foreman-app-id)
            data (cond-> {:state      "requested"
                          :reason     "Fired!"
                          :request-ts truthy
                          :requester  (contains {:id (id-for-key apikey)})}
                   submit? (assoc :started submitted))]
        foremanTermination => (just data)))
    foreman-app-id))

(defn confirm-foreman-termination [apikey app-id foreman-app-id]
  (fact "Confirm termination"
    (command apikey :confirm-foreman-termination :id app-id
             :foreman-app-id foreman-app-id )) => ok?
  (fact "Termination data updated"
    (let [{:keys [foremanTermination
                  submitted]} (query-application apikey foreman-app-id)]
      foremanTermination
      => (just (cond-> {:state "confirmed"
                        :reason "Fired!"
                        :request-ts truthy
                        :requester truthy
                        :ended truthy
                        :confirmer (contains {:id (id-for-key apikey)})}
                 submitted (assoc :started submitted))))))

(defn check-email [app-id address & extras]
  (let [randy (last-email)]
    randy => (contains {:subject (str "Lupapiste: " address ", Sipoo. Työnjohtajan vastuut päätetty.")
                        :to      "randy.random@example.com"})
    (apply in-text (-> randy :body :html
                       (ss/replace "\n" " ")
                       (ss/replace #" \s+" " "))
           (concat ["TJ" "Looksie!" app-id address
                    "Sipoo" (str "/app/fi/applicant#!/application/" app-id
                                 "/tasks")]
                   extras))))

(facts "Foreman termination reason visibility"
  (let [{app-id :id
         :as application} (create-and-submit-application pena
                                                         :propertyId sipoo-property-id
                                                         :address "Worker Way"
                                                         :operation "pientalo")
        _                 (set-applicant pena application "Pena" "Panaani" (email-for "pena"))
        _                 (command sonja :check-for-verdict :id app-id)
        foreman-app-id    (create-foreman-app-and-request-termination pena teppo sonja app-id "työnjohtaja")
        _                 (confirm-foreman-termination sonja app-id foreman-app-id)
        has-reason?       (fn [apikey]
                            (->> (query apikey :foreman-applications :id app-id)
                                 :applications
                                 (util/find-first #(-> % :termination-reason (= "Fired!")))
                                 (some?)))]
    (fact "Applicant can see the reason"
      (has-reason? pena) => true)
    (fact "Authority can see the reason"
      (has-reason? sonja) => true)
    (fact "Foreman cannot see the reason"
      (has-reason? teppo) => false)))

(facts "Foreman termination requests vs. automatic assignments"
  (let [{app-id :id
         :as    application} (create-and-submit-application pena
                                                            :propertyId sipoo-property-id
                                                            :address "Worker Way"
                                                            :operation "pientalo")
        filter-id            (upsert-automatic-assignment-filter
                               sipoo "753-R" {:name     "TJ"
                                              :criteria {:foreman-roles ["vastaava työnjohtaja" "ei tiedossa"]}
                                              :target   {:user-id ronja-id}
                                              :email    {:emails  ["randy.random@example.com"]
                                                         :message "Looksie!"}})]
    app-id => truthy
    filter-id => truthy
    (set-applicant pena application "Pena" "Panaani" (email-for "pena"))
    (fact "Fetch verdict"
      (command sonja :check-for-verdict :id app-id) => ok?)
    (fact "Create foreman application and request termination"
      (let [foreman-app-id (create-foreman-app-and-request-termination pena teppo sonja app-id "vastaava työnjohtaja")]
        (fact "Assignment for Ronja has been created"
          (:assignments (query sonja :assignments-for-application :id app-id))
          => (just [(contains {:description "TJ"
                               :filter-id   filter-id
                               :recipient   (contains {:firstName "Ronja"})
                               :status      "active"
                               :targets     (just [(contains {:group "foremen"
                                                              :id    foreman-app-id})])
                               :trigger     "foreman"})]))
        (fact "Email has been sent to Randy"
          (check-email app-id "Worker Way" "Vastaava työnjohtaja"
                       (i18n/localize :fi :operations.pientalo)
                       "Super Viisori" "Fired!"))
        (fact "... second foreman application and termination"
          (create-foreman-app-and-request-termination pena teppo sonja app-id "työnjohtaja")
          (fact "No new assignment"
            (:assignments (query sonja :assignments-for-application :id app-id))
            => (just [(contains {:description "TJ"
                                 :filter-id   filter-id
                                 :recipient   (contains {:firstName "Ronja"})
                                 :status      "active"
                                 :targets     (just [(contains {:group "foremen"
                                                                :id    foreman-app-id})])
                                 :trigger     "foreman"})])))
        (fact "... third foreman application and termination"
          (let [foreman-app-id3 (create-foreman-app-and-request-termination pena mikko sonja app-id "random" true)]
            (fact "Second target to the assignment"
              (:assignments (query sonja :assignments-for-application :id app-id))
              => (just [(contains {:description "TJ"
                                   :filter-id   filter-id
                                   :recipient   (contains {:firstName "Ronja"})
                                   :status      "active"
                                   :targets     (just [(contains {:group "foremen"
                                                                  :id    foreman-app-id})
                                                       (contains {:group "foremen"
                                                                  :id    foreman-app-id3})]
                                                      :in-any-order)
                                   :trigger     "foreman"})]))
            (fact "Sonja confirms the first termination request"
              (confirm-foreman-termination sonja app-id foreman-app-id))
            (fact "Assignment targets have been updated"
              (:assignments (query sonja :assignments-for-application :id app-id))
              => (just [(contains {:description "TJ"
                                   :filter-id   filter-id
                                   :recipient   (contains {:firstName "Ronja"})
                                   :status      "active"
                                   :targets     (just [(contains {:group "foremen"
                                                                  :id    foreman-app-id3})])
                                   :trigger     "foreman"})]))
            (fact "New automatic assignment filter. New foreman application and termination request"
              (let [filter-id2      (upsert-automatic-assignment-filter sipoo "753-R"
                                                                        {:name     "Boss"
                                                                         :criteria {:foreman-roles ["iv-työnjohtaja"]}})
                    foreman-app-id4 (create-foreman-app-and-request-termination pena pena sonja app-id "iv-työnjohtaja")]
                (fact "New assignment created"
                  (:assignments (query sonja :assignments-for-application :id app-id))
                  => (just [(contains {:description "TJ"
                                       :filter-id   filter-id
                                       :recipient   (contains {:firstName "Ronja"})
                                       :status      "active"
                                       :targets     (just [(contains {:group "foremen"
                                                                      :id    foreman-app-id3})])
                                       :trigger     "foreman"})
                            (contains {:description "Boss"
                                       :filter-id   filter-id2
                                       :recipient   nil
                                       :status      "active"
                                       :targets     (just [(contains {:group "foremen"
                                                                      :id    foreman-app-id4})])
                                       :trigger     "foreman"})]
                           :in-any-order))
                (fact "'Last' confirmation removes the assignment"
                  (confirm-foreman-termination sonja app-id foreman-app-id3)
                  (:assignments (query sonja :assignments-for-application :id app-id))
                  => (just [(contains {:description "Boss"
                                       :filter-id   filter-id2
                                       :recipient   nil
                                       :status      "active"
                                       :targets     (just [(contains {:group "foremen"
                                                                      :id    foreman-app-id4})])
                                       :trigger     "foreman"})]))
                (fact "Disable assignments in Sipoo"
                  (command sipoo :set-organization-assignments
                           :organizationId "753-R"
                           :enabled false) => ok?)
                (fact "No target updates if assignments disabled"
                  (create-foreman-app-and-request-termination pena teppo sonja app-id "vastaava työnjohtaja")
                  (command sipoo :set-organization-assignments
                           :organizationId "753-R"
                           :enabled true) => ok?
                  (:assignments (query sonja :assignments-for-application :id app-id))
                  => (just [(contains {:description "Boss"
                                       :filter-id   filter-id2
                                       :recipient   nil
                                       :status      "active"
                                       :targets     (just [(contains {:group "foremen"
                                                                      :id    foreman-app-id4})])
                                       :trigger     "foreman"})]))))))))))
