(ns lupapalvelu.foreman-application-itest
  (:require [midje.sweet :refer :all]
           [clojure.java.io :as io]
           [net.cgrand.enlive-html :as enlive]
           [lupapalvelu.itest-util :refer :all]
           [lupapalvelu.factlet :refer :all]
           [lupapalvelu.domain :as domain]
           [lupapalvelu.verdict :as verdict]
           [sade.common-reader :as cr]
           [sade.strings :as ss]
           [sade.xml :as xml]))

(apply-remote-minimal)

(defn create-foreman-app [apikey authority application-id]
  (let [{foreman-application-id :id} (command authority :create-foreman-application :id application-id
                                              :taskId "" :foremanRole "ei tiedossa" :foremanEmail "")]
   (command authority :invite-with-role
            :id foreman-application-id :email (email-for-key apikey) :text ""
            :documentName "" :documentId "" :path "" :role "writer")
   (command apikey :approve-invite :id foreman-application-id)
   (query-application apikey foreman-application-id)))

(defn add-invites [apikey application-id]
  (let [{hakija1 :doc}               (command apikey :create-doc :id application-id :schemaName "hakija-r")
        {hakija-no-auth :doc}        (command apikey :create-doc :id application-id :schemaName "hakija-r")
        {hakija-contact-person :doc} (command apikey :create-doc :id application-id :schemaName "hakija-r")
        {hakija-company :doc}        (command apikey :create-doc :id application-id :schemaName "hakija-r")]
    (fact "add-invites"
          (command apikey :invite-with-role :id application-id :email "foo@example.com" :text "" :documentName ""
                   :documentId "" :path "" :role "writer") => ok?
          (command apikey :update-doc :id application-id :doc hakija1  :collection "documents"
                   :updates [["henkilo.yhteystiedot.email" "foo@example.com"]]) => ok?
          (command apikey :update-doc :id application-id :doc hakija-no-auth  :collection "documents"
                   :updates [["henkilo.yhteystiedot.email" "unknown@example.com"]]) => ok?
          (command apikey :update-doc :id application-id :doc hakija-contact-person  :collection "documents"
                   :updates [["_selected" "yritys"] ["yritys.yhteyshenkilo.yhteystiedot.email" "contact@example.com"]]) => ok?
          (invite-company-and-accept-invitation apikey application-id "solita") => truthy
          (command apikey :update-doc :id application-id :doc hakija-company  :collection "documents"
                   :updates [["_selected" "yritys"] ["yritys.companyId" "solita"]]) => ok?)
    {:hakija1 hakija1
     :hakija-no-auth hakija-no-auth
     :hakija-contact-person hakija-contact-person
     :hakija-company hakija-company}))

(facts* "Foreman application"
        (let [apikey                       mikko
              email                        (email-for-key apikey)
              {application-id :id}         (create-and-open-application apikey :operation "kerrostalo-rivitalo") => truthy
              application                  (query-application apikey application-id)
              _                            (generate-documents application apikey)
              {foreman-application-id :id
               :as foreman-application}    (create-foreman-app apikey sonja application-id)
              foreman-link-permit-data     (first (foreman-application :linkPermitData))
              foreman-doc                  (domain/get-document-by-name foreman-application "tyonjohtaja-v2")
              application                  (query-application apikey application-id)
              link-to-application          (first (application :appsLinkingToUs))
              foreman-applications         (query apikey :foreman-applications :id application-id) => truthy]

          (fact "Initial permit subtype is blank"
            (:permitSubtype foreman-application) => ss/blank?)

          (fact "Foreman application contains link to application"
                (:id foreman-link-permit-data) => application-id)

          (fact "Original application contains link to foreman application"
                (:id link-to-application) => foreman-application-id)

          (fact "All linked Foreman applications are returned in query"
                (let [applications (:applications foreman-applications)]
                  (count applications) => 1
                  (:id (first applications)) => foreman-application-id))

          (fact "Document data is copied to foreman application"
                (fact "Hankkeen kuvaus"
                      (let [foreman-hankkeen-kuvaus (domain/get-document-by-name foreman-application "hankkeen-kuvaus-minimum")
                            app-hankkeen-kuvaus     (domain/get-document-by-name application "hankkeen-kuvaus-rakennuslupa")]

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
                (command apikey :update-doc :id (:id foreman-application) :doc (:id foreman-doc) :collection "documents"
                         :updates [["henkilotiedot.etunimi" "foo"] ["henkilotiedot.sukunimi" "bar"] ["kuntaRoolikoodi" "erityisalojen ty\u00F6njohtaja"]]) => ok?

                (let [application-after-update (query-application apikey (:id foreman-application))]
                  (:foreman foreman-application) => ss/blank?
                  (:foremanRole foreman-application) => ss/blank?
                  (:foreman application-after-update) => "bar foo"
                  (:foremanRole application-after-update) => "erityisalojen ty\u00F6njohtaja"))

          (fact "Can't submit foreman app because subtype is not selected"
            (command apikey :submit-application :id foreman-application-id) => (partial expected-failure? :error.foreman.type-not-selected))

          (fact "Update subtype to 'tyonjohtaja-ilmoitus'"
                (command apikey :change-permit-sub-type :id foreman-application-id :permitSubtype "tyonjohtaja-ilmoitus") => ok?)

          (fact "Can't submit foreman app before original link-permit-app is submitted"
                (:submittable (query-application apikey foreman-application-id)) => false)

          (fact "Submit link-permit app"
                (command apikey :submit-application :id application-id) => ok?
                (:submittable (query-application apikey foreman-application-id)) => true)

          (facts "Can't submit foreman notice app if link permit doesn't have verdict"
                 (fact "gives error about foreman notice"
                       (command apikey :submit-application :id foreman-application-id) => (partial expected-failure? :error.foreman.notice-not-submittable))
                 (command sonja :check-for-verdict :id application-id) => ok?
                 (fact "ok after link-permit has verdict"
                       (command apikey :submit-application :id foreman-application-id) => ok?))
          (facts "Link foreman application to task"
            (let [apikey                       mikko
                  application (create-and-submit-application apikey)
                  _ (command sonja :check-for-verdict :id (:id application))
                  application (query-application apikey (:id application))
                  {foreman-application-id-1 :id} (create-foreman-app apikey sonja (:id application))
                  {foreman-application-id-2 :id} (create-foreman-app apikey sonja (:id application))
                  tasks (:tasks application)
                  foreman-tasks (filter #(= (get-in % [:schema-info :name]) "task-vaadittu-tyonjohtaja") tasks)]

              (fact "link first foreman"
                (command apikey :link-foreman-task :id (:id application) :taskId (:id (first foreman-tasks)) :foremanAppId foreman-application-id-1) => ok?
                (let [app (query-application apikey (:id application))
                      updated-tasks (:tasks app)
                      updated-foreman-task (first (filter #(= (get-in % [:schema-info :name]) "task-vaadittu-tyonjohtaja") updated-tasks))]
                  (get-in updated-foreman-task [:data :asiointitunnus :value]) => foreman-application-id-1))

              (fact "cannot link same foreman to another task"
                (command apikey :link-foreman-task :id (:id application) :taskId (:id (second foreman-tasks)) :foremanAppId foreman-application-id-1) => (partial expected-failure? "error.foreman-already-linked"))

              (fact "linked foreman can be changed on task"
                (command apikey :link-foreman-task :id (:id application) :taskId (:id (first foreman-tasks)) :foremanAppId foreman-application-id-2) => ok?)

              (fact "another foreman can now be linked to first task"
                (command apikey :link-foreman-task :id (:id application) :taskId (:id (second foreman-tasks)) :foremanAppId foreman-application-id-1) => ok?)))

          ;; delete verdict for next steps
          (let [app (query-application mikko application-id)
                verdict-id (-> app :verdicts first :id)
                verdict-id2 (-> app :verdicts second :id)]
            (command sonja :delete-verdict :id application-id :verdictId verdict-id) => ok?
            (command sonja :delete-verdict :id application-id :verdictId verdict-id2) => ok?
            (fact "is submitted" (:state (query-application mikko application-id)) => "submitted"))

          (facts "approve foreman"
                 (fact "Can't approve foreman application before actual application"
                       (command sonja :approve-application :id foreman-application-id :lang "fi") => (partial expected-failure? "error.link-permit-app-not-in-post-sent-state"))

                 (fact "can still comment"
                       (comment-application apikey foreman-application-id) => ok?)

                 (fact "After approving actual application, foreman can be approved. No need for verdict"
                       (command sonja :approve-application :id application-id :lang "fi") => ok?
                       (command sonja :approve-application :id foreman-application-id :lang "fi") => ok?)

                 (fact "when foreman application is of type 'ilmoitus', after approval its state is acknowledged"
                       (:state (query-application apikey foreman-application-id)) => "acknowledged")

                 (fact "can no longer comment"
                       (comment-application apikey foreman-application-id) => fail?))

          (facts "updating other foreman projects to current foreman application"
                 (let [{application1-id :id}         (create-and-open-application apikey :operation "kerrostalo-rivitalo") => truthy
                       {foreman-application1-id :id} (create-foreman-app apikey sonja application1-id) => truthy
                       {application2-id :id}         (create-and-open-application apikey :operation "kerrostalo-rivitalo") => truthy
                       {foreman-application2-id :id} (create-foreman-app apikey sonja application2-id)
                       foreman-application1          (query-application apikey foreman-application1-id)
                       foreman-application2          (query-application apikey foreman-application2-id)

                       foreman-doc1                  (domain/get-document-by-name foreman-application1 "tyonjohtaja-v2")
                       foreman-doc2                  (domain/get-document-by-name foreman-application2 "tyonjohtaja-v2")]
                   (fact "other project is updated into current foreman application"
                         (command apikey :set-current-user-to-document :id foreman-application1-id :documentId (:id foreman-doc1) :userId mikko-id :path "" :collection "documents" => truthy)
                         (command apikey :set-current-user-to-document :id foreman-application2-id :documentId (:id foreman-doc2) :userId mikko-id :path "" :collection "documents" => truthy)
                         (command apikey :update-foreman-other-applications :id foreman-application2-id :foremanHetu "")

                         (let [updated-application (query-application apikey foreman-application2-id)
                               updated-foreman-doc (domain/get-document-by-name updated-application "tyonjohtaja-v2")
                               project-id (get-in updated-foreman-doc [:data :muutHankkeet :0 :luvanNumero :value])]
                           (fact "first project is in other projects document"
                                 project-id => application1-id)))))

          (facts "Special foreman/designer verdicts"
                 (let [xml-file           (fn [filename] (-> filename io/resource slurp
                                                             (xml/parse-string "utf-8")
                                                             cr/strip-xml-namespaces))
                       special            (-> "krysp/verdict-r-foremen.xml"
                                              xml-file
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
                         => {:paatoskoodi ["hyv\u00e4ksytty"]
                             :paatoksentekija [""]
                             :paatospvm ["2015-11-29"]
                             :liite [{:tag :kuvaus
                                      :attrs nil
                                      :content []}
                                     {:tag :linkkiliitteeseen
                                      :attrs nil
                                      :content ["http://localhost:8000/dev/sample-attachment.txt"]}]})
                   (facts "Other paatostieto placements"
                         (let [minispec (enlive/at special
                                            [:paatostieto] nil
                                            [:lisatiedot] nil
                                            [:liitetieto] nil
                                            [:asianTiedot] nil
                                            [:hankkeenVaativuus] nil)]
                           (fact "Paatostieto will be last element"
                                 (let [norm (verdict/verdict-xml-with-foreman-designer-verdicts foreman-application minispec)
                                       elems (-> norm (enlive/select [:RakennusvalvontaAsia :> enlive/any-node]))]
                                   (-> elems last :tag) => :paatostieto))
                           (for [e [:muistiotieto :lisatiedot :liitetieto :kayttotapaus :asianTiedot :hankkeenVaativuus]
                                 :let [norm (verdict/verdict-xml-with-foreman-designer-verdicts foreman-application minispec)
                                       elems (-> norm (enlive/select [:RakennusvalvontaAsia :> enlive/any-node]))]]
                             (do (fact (str e " is last") (-> elems last :tag) => e)
                                 (fact "Paatostieto is next to last" (->> elems (drop-last 2) first :tag) => :paatostieto)))))))))

(facts "foreman history"
  (apply-remote-minimal) ; clean mikko before history tests
  (let [{history-base-app-id :id} (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
        _                    (give-verdict sonja history-base-app-id :verdictId "321-2016")
        {other-r-app-id :id} (create-app mikko :operation "kerrostalo-rivitalo")
        {ya-app-id :id}      (create-app mikko :operation "ya-kayttolupa-muu-tyomaakaytto")
        foreman-app-id1      (create-foreman-application history-base-app-id mikko mikko-id "KVV-ty\u00F6njohtaja" "B"); -> should be visible
        foreman-app-id2      (create-foreman-application history-base-app-id mikko mikko-id "KVV-ty\u00F6njohtaja" "A") ; -> should be visible
        foreman-app-id3      (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "B") ; -> should be visible
        foreman-app-id4      (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "B") ; -> should *NOT* be visible
        foreman-app-id5      (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "A") ; -> should be visible
        foreman-app-canceled (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "A") ; -> should NOT be visible

        base-foreman-app-id  (create-foreman-application history-base-app-id mikko mikko-id "vastaava ty\u00F6njohtaja" "B")] ;for calling history

    (command mikko :cancel-application :id foreman-app-canceled :text nil :lang "fi") => ok?

    (facts "reduced"
      (fact "reduced history should contain reduced history (excluding canceled application)"
        (let [reduced-history (query sonja :reduced-foreman-history :id base-foreman-app-id) => ok?
              history-ids (map :foremanAppId (:projects reduced-history))]
          history-ids => (just [foreman-app-id1 foreman-app-id2 foreman-app-id3 foreman-app-id5] :in-any-order)
          (some #{foreman-app-id4} history-ids) => nil?))

      (fact "reduced history should depend on the base application"
        (let [reduced-history (query sonja :reduced-foreman-history :id foreman-app-id1) => ok?
              history-ids (map :foremanAppId (:projects reduced-history))]
          history-ids =>     (just [foreman-app-id2 foreman-app-id3 foreman-app-id5] :in-any-order)
          history-ids =not=> (has some #{foreman-app-id4 base-foreman-app-id})))

      (fact "Unknown foreman app id"
        (query sonja :reduced-foreman-history :id "foobar") => fail?))

    (fact "Should be queriable only with a foreman application"
      (let [resp (query sonja :foreman-history :id history-base-app-id) => fail?]
        (:text resp) => "error.not-foreman-app"))

    (facts "unreduced"
      (fact "unreduced history should not reduce history"
        (let [unreduced-history (query sonja :foreman-history :id base-foreman-app-id) => ok?
              history-ids       (map :foremanAppId (:projects unreduced-history))]
          history-ids => (just [foreman-app-id1 foreman-app-id2 foreman-app-id3 foreman-app-id4 foreman-app-id5] :in-any-order)))

      (fact "Unknown foreman app id"
        (query sonja :foreman-history :id "foobar") => fail?)

      (fact "Should be queriable only with a foreman application"
        (let [resp (query sonja :reduced-foreman-history :id history-base-app-id) => fail?]
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
        {application-id :id}         (create-app apikey :operation "kerrostalo-rivitalo") => truthy
        {:keys [hakija1 hakija-no-auth hakija-contact-person hakija-company]} (add-invites apikey application-id)
        _ (command apikey :submit-application :id application-id)
        _ (give-verdict sonja application-id :verdictId "321-2016")

        application            (query-application apikey application-id)

        has-auth? (fn [email auth]
                        (or (some (partial = email) (map :username auth)) false))]
    (sent-emails) ; clear email box

    (fact "Create foreman application with correct auths"
      (let [{foreman-app-id :id} (command apikey :create-foreman-application :id application-id
                                   :taskId "" :foremanRole "ei tiedossa" :foremanEmail "heppu@example.com") => truthy
            {auth-array :auth} (query-application pena foreman-app-id) => truthy
            {orig-auth :auth}  (query-application pena application-id)]
        (count auth-array) => 4
        (fact "Pena is owner" (:username (some #(when (= (:role %) "owner") %) auth-array)) => "pena")
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

        (fact "Emails are sent correctly to recipients"
          (let [emails (sent-emails)
                recipients (map :to emails)]
            recipients => (just ["heppu@example.com" ; foreman is invited to original application also
                                 "heppu@example.com"
                                 "foo@example.com"
                                 "Kaino Solita <kaino@solita.fi>"])))))

    (fact "Create foreman application with the applicant as foreman"
          (let [{application-id :id} (create-and-submit-application apikey :operation "kerrostalo-rivitalo") => truthy
                _                    (give-verdict sonja application-id :verdictId "321-2016")
                {foreman-app-id :id} (command apikey :create-foreman-application :id application-id
                                              :taskId "" :foremanRole "ei tiedossa" :foremanEmail "pena@example.com") => truthy
                {auth-array :auth}   (query-application pena foreman-app-id) => truthy
                {orig-auth :auth}    (query-application pena application-id)]
            (fact "Pena is the sole auth and owner of the foreman application"
                  (count auth-array) => 1
                  (:username (some #(when (= (:role %) "owner") %) auth-array)) => "pena")
            (fact "Pena is the sole auth and owner of the original application"
                  (count orig-auth) => 1
                  (:username (some #(when (= (:role %) "owner") %) orig-auth)) => "pena"))))
  (let [apikey pena
        has-auth? (fn [email auth]
                    (or (some (partial = email) (map :username auth)) false))]
    (fact "Contact person is added to new foreman app, when its auth is added to original application"
          (let [{application-id :id} (create-and-submit-application apikey :operation "kerrostalo-rivitalo") => truthy
                {:keys [hakija1]}    (add-invites apikey application-id)
                _ (command apikey :invite-with-role :id application-id :email "contact@example.com" :text "" :documentName ""
                           :documentId "" :path "" :role "writer") => ok?
                _ (command apikey :remove-doc :id application-id :docId hakija1) => ok? ; remove one applicant
                _ (give-verdict sonja application-id :verdictId "321-2016")

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

   (fact "No double-auth, if owner of foreman-application is authed as writer in original application" ;; LPK-1331
         (let [{application-id :id}    (create-and-submit-application apikey :operation "kerrostalo-rivitalo") => truthy
               {writer-applicant :doc} (command apikey :create-doc :id application-id :schemaName "hakija-r")
               ;; Pena fills Teppo's email to applicant document
               _                       (command apikey :update-doc :id application-id :doc writer-applicant
                                                :collection "documents"
                                                :updates [["henkilo.yhteystiedot.email" "teppo@example.com"]]) => ok?
                                                ;; Pena invites Teppo to application
               _                       (command apikey :invite-with-role :id application-id :email "teppo@example.com" :text "" :documentName ""
                                                :documentId "" :path "" :role "writer")
               _                       (command teppo :approve-invite :id application-id)
               ;; Teppo (with writer role) creates foreman-application
               _                       (give-verdict sonja application-id :verdictId "321-2016")
               {foreman-app-id :id}    (command teppo :create-foreman-application :id application-id
                                                :taskId "" :foremanRole "ei tiedossa" :foremanEmail "heppu@example.com") => truthy
               {auth-array :auth}      (query-application teppo foreman-app-id) => truthy]
           (fact "Teppo is owner" (:username (some #(when (= (:role %) "owner") %) auth-array)) => "teppo@example.com")
           (fact "Teppo is not double authed"
                 (count (filter #(= (:username %) "teppo@example.com") auth-array)) => 1)))))
