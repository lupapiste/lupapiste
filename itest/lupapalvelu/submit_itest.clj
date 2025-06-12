(ns lupapalvelu.submit-itest
  "Testing submit-related validation and related checks."
  (:require [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer [$elemMatch $unset]]
            [sade.util :as util]))

(defn application-errors
  "All application validation errors in one list."
  [application]
  (->> application :documents (map :validationErrors) flatten (filter identity)))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Submit-related validation"
    (let [{application-id :id :as application} (create-and-open-application pena
                                                                            :operation "ya-katulupa-vesi-ja-viemarityot"
                                                                            :propertyId sipoo-property-id)
          hakija-doc-id                        (->> application
                                                    :documents
                                                    (util/find-first #(= "hakija-ya"
                                                                         (-> % :schema-info :name)))
                                                    :id)]
      (fact "Document id found"  hakija-doc-id => truthy)

      (fact "Hakija is a person"
        (command pena :update-doc
                 :id application-id
                 :doc hakija-doc-id
                 :updates [["_selected" "henkilo"]]))
      (fact "Generate documents"
        (generate-documents! application pena) => nil
        (provided (tools/dummy-vrk-address) => "Chaoyanggongyuanlu 88"))

      (fact "Update-doc results include :ignore errors"
        (let [{errors :results} (command pena :update-doc
                                         :id application-id
                                         :doc hakija-doc-id
                                         :updates [["_selected" "henkilo"]
                                                   ["yritys.liikeJaYhteisoTunnus" "bad-y"]])]
          (count errors) => 1
          (first errors) => (contains {:ignore true
                                       :path   ["yritys" "liikeJaYhteisoTunnus"]})))

      (fact "Application query results do not include :ignore errors"
        (let [{app :application :as resp} (query pena :application :id application-id)]
          resp => ok?
          (application-errors app) => empty?))
      (fact "Fetch-validation-errors results do not include :ignore errors"
        (let [{errors :results :as resp} (query pena :fetch-validation-errors :id application-id)]
          resp => ok?
          (flatten errors) => empty?))
      (fact "Application is submittable"
        (query pena :application-submittable :id application-id) => ok?)
      (fact "Make hakija-r company"
        (let [{errors :results} (command pena :update-doc
                                         :id application-id
                                         :doc hakija-doc-id
                                         :updates [["_selected" "yritys"]])]
          (count errors) => 1
          (first errors) => (contains {:path ["yritys" "liikeJaYhteisoTunnus"]})
          (first errors) =not=> (contains {:ignore true})))
      (fact "Application query now contains one validation error"
        (let [{app :application :as resp} (query pena :application :id application-id)
              [error]                     (application-errors app)]
          resp => ok?
          error => (contains {:path ["yritys" "liikeJaYhteisoTunnus"]})))
      (fact "Fetch-validation-errors likewise"
        (let [{errors :results :as resp} (query pena :fetch-validation-errors :id application-id)
              [error]                    (flatten errors)]
          resp => ok?
          error => (contains {:path ["yritys" "liikeJaYhteisoTunnus"]})))
      (fact "Application is still submittable"
        (query pena :application-submittable :id application-id) => ok?)
      (fact "Organization requires fully-formed applications"
        (command sipoo-ya :set-organization-app-required-fields-filling-obligatory :organizationId "753-YA"
                 :enabled true))
      (fact "Application is no longer submittable"
        (let [{errors :errors :as resp} (query pena :application-submittable :id application-id)]
          resp => fail?
          (first errors) => (contains {:text "application.requiredDataDesc"})))
      (fact "Application submit fails"
        (command pena :submit-application :id application-id => fail?))
      (fact "After fixing bad-y there are no longer any validation errors"
        (command pena :update-doc
                 :id application-id
                 :doc hakija-doc-id
                 :updates [["yritys.liikeJaYhteisoTunnus" "1234567-1"]])
        => (contains {:ok true :results []}))
      (fact "Application query results do not include any errors"
        (let [{app :application :as resp} (query pena :application :id application-id)]
          resp => ok?
          (application-errors app) => empty?))
      (fact "Fetch-validation-errors results do not include any errors"
        (let [{errors :results :as resp} (query pena :fetch-validation-errors :id application-id)]
          resp => ok?
          (flatten errors) => empty?))
      (fact "Application is still not submittable"
        (let [{errors :errors :as resp} (query pena :application-submittable :id application-id)]
          resp => fail?
          (first errors) => (contains {:text "application.requiredDataDesc"})))
      (fact "Marking the mandatory attachment not needed makes the application finally submittable"
        (command pena :set-attachment-not-needed :id application-id :notNeeded true
                 :attachmentId (-> application :attachments first :id)) => ok?
        (query pena :application-submittable :id application-id) => ok?)
      (fact "Submit application successfully"
        (command pena :submit-application :id application-id) => ok?))))

(defn- make-updates
  [m]
  (-> (util/kw-flatten-map m)
      (update-keys name)))

(def test-person
  {:henkilotiedot {:etunimi  "Etunimi"
                   :hetu     "010106A923S"
                   :sukunimi "Sukunimi"}
   :osoite        {:katu                 "Testikatu 12"
                   :maa                  "FIN"
                   :postinumero          "33720"
                   :postitoimipaikannimi "Tampere"}
   :yhteystiedot  {:email   "testi@lupapiste.fi"
                   :puhelin "0501234567"}})

(def test-case-R-asuinrakennus
  {:name        "R-asuinrakennus"
   :org-admin   sipoo
   :kuntagml    [:R "2.2.2"]
   :applicant   pena
   :org-id      "753-R"
   :operation   "asuinrakennus"
   :property-id sipoo-property-id
   :documents   {"hakija-r"                             {:updates (make-updates
                                                                   {:_selected "henkilo"
                                                                    :henkilo   test-person})}
                 "hankkeen-kuvaus"                      {:updates (make-updates
                                                                   {:kuvaus "tehdään jotain"})}
                 "maksaja"                              {:updates (make-updates
                                                                   {:_selected  "henkilo"
                                                                    :henkilo    test-person
                                                                    :laskuviite "123"})}
                 "paasuunnittelija"                     {:updates (make-updates
                                                                   (merge test-person
                                                                          {:patevyys                          {:koulutusvalinta  "arkkitehti"
                                                                                                               :patevyysluokka   "A"
                                                                                                               :valmistumisvuosi "2020"}
                                                                           :suunnittelutehtavanVaativuusluokka "A"}))}
                 "paatoksen-toimitus-rakval"            {:updates (make-updates
                                                                   {:henkilotiedot {:etunimi  "Etunimi"
                                                                                    :sukunimi "Sukunimi"}
                                                                    :osoite        {:katu                 "Testikatu 12"
                                                                                    :maa                  "FIN"
                                                                                    :postinumero          "33720"
                                                                                    :postitoimipaikannimi "Tampere"}})}
                 "laajennettuRakennusjateselvitys-v2"   {:updates (make-updates
                                                                   {:rakennusJaPurkujate
                                                                    {:vaarallisetJatteet {:0 {:jate-group     {:jate "asbestiJate"}
                                                                                              :maara          "3"
                                                                                              :yksikko        "kg"
                                                                                              :sijoituspaikka "test"}}
                                                                    :muuJate             {:0 {:jate-group     {:jate "lasi"}
                                                                                              :maara          "10"
                                                                                              :sijoituspaikka "test"}}}})}
                 "rakennuspaikka"                       {:updates (make-updates
                                                                   {:hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm "5.12.2020"}
                                                                    :kaavatilanne         "asemakaava"
                                                                    :hallintaperuste      "oma"})}
                 "suunnittelija"                        {:updates (make-updates
                                                                   (merge test-person
                                                                          {:kuntaRoolikoodi                    "kantavien rakenteiden suunnittelija"
                                                                           :patevyys                           {:koulutusvalinta  "kirvesmies"
                                                                                                                :patevyysluokka   "B"
                                                                                                                :valmistumisvuosi "2020"}
                                                                           :suunnittelutehtavanVaativuusluokka "A"}))}
                 "uusiRakennus"                         {:updates (make-updates
                                                                   {:huoneistot           {:0 {:huoneistoTyyppi "asuinhuoneisto"
                                                                                               :keittionTyyppi  "keittio"
                                                                                               :huoneistoala    "1"
                                                                                               :huoneluku       "1"}}
                                                                    :kaytto               {:kayttotarkoitus "011 yhden asunnon talot"
                                                                                           :rakentajaTyyppi "liiketaloudellinen"}
                                                                    :lammitys             {:lammitystapa "suora sähkö"
                                                                                           :lammonlahde  "sähkö"}
                                                                    :mitat                {:kerrosluku                     "1"
                                                                                           :kerrosala                      "7" ; min 7
                                                                                           :kokonaisala                    "7"
                                                                                           :rakennusoikeudellinenKerrosala "7"
                                                                                           :tilavuus                       "15"}
                                                                    :rakenne              {:kantavaRakennusaine "puu"
                                                                                           :julkisivu           "betoni"
                                                                                           :rakentamistapa      "elementti"}
                                                                    :rakennuksenOmistajat {:0 {:omistajalaji "kiinteistö oy"
                                                                                               :henkilo      test-person
                                                                                               :_selected    "henkilo"}}
                                                                    :tunnus               "123"
                                                                    :varusteet            {:sahkoKytkin true}
                                                                    :verkostoliittymat    {:sahkoKytkin true}})}}})

(def test-case-R-asuinrakennus-226
  "With KuntaGML 2.2.6"
  (-> test-case-R-asuinrakennus
      (assoc :name "R-asuinrakennus, KuntaGML 2.2.6"
             :kuntagml [:R "2.2.6"])))

(def test-case-R-asuinrakennus-226-tilavuus
  "With KuntaGML 2.2.6 and `rakennusoikeusValinta` set to `tilavuus`"
  (-> test-case-R-asuinrakennus-226
      (assoc :name "R-asuinrakennus, KuntaGML 2.2.6 + tilavuus")
      (assoc-in [:documents "uusiRakennus" :updates "mitat.rakennusoikeusValinta"] "tilavuus")
      (assoc-in [:documents "uusiRakennus" :updates "mitat.rakennusoikeusKuutiometreina"] "123")))

(def test-case-R-asuinrakennus-226-harjakorkeus
  "With KuntaGML 2.2.6 and `rakennusoikeusValinta` set to `harjakorkeus`"
  (-> test-case-R-asuinrakennus-226
      (assoc :name "R-asuinrakennus, KuntaGML 2.2.6 + harjakorkeus")
      (assoc-in [:documents "uusiRakennus" :updates "mitat.rakennusoikeusValinta"] "harjakorkeus")
      (assoc-in [:documents "uusiRakennus" :updates "mitat.rakennusoikeusKuutiometreina"] "123")
      (assoc-in [:documents "uusiRakennus" :updates "mitat.harjanKorkeusrakennusalalla"] "2.1234")))

(defn- drop-rakennusoikeusValinta
  "New applications automatically include the `rakennusoikeusValinta` field.
  We want to simulate an old application -> remove the field."
  [{:keys [app-id doc-id]}]
  (mongo/update :applications
                {:_id       app-id
                 :documents {$elemMatch {:id doc-id}}}
                {$unset {:documents.$.data.mitat.rakennusoikeusValinta true}}))

(def test-case-R-asuinrakennus-without-rakennusoikeusValinta
  "Try to simulate an old application without `rakennusoikeusValinta`"
  (-> test-case-R-asuinrakennus
      (assoc :name "R-asuinrakennus, no rakennusoikeusValinta")
      (update-in [:documents "uusiRakennus"] assoc :preprocess drop-rakennusoikeusValinta)))

(def test-cases
  [test-case-R-asuinrakennus
   test-case-R-asuinrakennus-226
   test-case-R-asuinrakennus-226-tilavuus
   test-case-R-asuinrakennus-226-harjakorkeus
   test-case-R-asuinrakennus-without-rakennusoikeusValinta])

(defn- set-kuntagml-version
  [{:keys [org-admin org-id permit-type version]}]
  (fact "can get kuntagml config"
    (let [{:keys [krysp] :as resp} (query org-admin :krysp-config :organizationId org-id)
          {:keys [url username password]
           :or   {username ""
                  password ""}
           :as   config}           (get krysp permit-type)]
      (fact "success" resp => ok?)
      (fact "known permit-type" config => map?)
      (fact "can update config"
        (command org-admin :set-krysp-endpoint {:organizationId org-id
                                                :url            url
                                                :username       username
                                                :password       password
                                                :permitType     (name permit-type)
                                                :version        version}) => ok?))))

;; Runs a sequence of test cases,
(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions
    (facts "Submit test cases"
      (doseq [{:keys [org-admin
                      kuntagml
                      applicant
                      org-id
                      operation
                      property-id]
               :as   test-case} test-cases]
        (fact {:midje/description (:name test-case)}
          (fact {:midje/description (str "can enable strict checking for" org-id)}
            (command org-admin :set-organization-app-required-fields-filling-obligatory {:organizationId org-id
                                                                                         :enabled        true})
            => ok?)

          (when-let [[permit-type version] kuntagml]
            (set-kuntagml-version {:org-admin   org-admin
                                   :org-id      org-id
                                   :permit-type permit-type
                                   :version     version}))

          (let [{:keys  [documents]
                 app-id :id :as app} (create-and-open-application applicant
                                                                  :operation operation
                                                                  :propertyId property-id)]

            (fact "Application in expected org"
              (:organization app) => org-id)

            ;; This seems incorrect..
            (fact "Application is submittable even if required fields are not given"
              (query applicant :application-submittable :id app-id) => ok?)

            (fact "check and fill-in documents"
              (let [document->name (fn [doc] (-> doc :schema-info :name))
                    expected-docs  (:documents test-case)
                    actual-by-name (group-by document->name documents)]
                (fact "check initial documents"
                  (let [expected-doc-names (->> expected-docs keys set)
                        actual-doc-names   (->> actual-by-name keys set)]
                    (fact "only a single doc per name"
                      (every? #{1} (mapv (comp count second) actual-by-name)) => truthy)
                    (fact "expected docs match actual docs"
                      actual-doc-names => expected-doc-names)))
                (fact "do some side-effecty preprocessing of the data, if necessary"
                  (doseq [[doc-name doc-data] expected-docs]
                    (let [{doc-id :id :as doc} (first (get actual-by-name doc-name))]
                      (when-let [f (:preprocess doc-data)]
                        (f {:app-id app-id :doc-id doc-id :doc doc})))))
                (fact "fill in documents"
                  (doseq [[doc-name doc-data] expected-docs]
                    (let [{doc-id :id} (first (get actual-by-name doc-name))]
                      (when (seq (:updates doc-data))
                        (fact {:midje/description (str "Can update" doc-name)}
                          (command applicant :update-doc
                                   {:id      app-id
                                    :doc     doc-id
                                    :updates (->> doc-data
                                                  :updates
                                                  (into []))}) => ok?)))))))
            (fact "check errors"
              (let [{:keys [application] :as resp} (query applicant :application :id app-id)
                    documents                      (:documents application)]
                (fact "queried app" resp => ok?)

                (fact "check individual fields"
                  (doseq [doc documents]
                    (fact {:midje/description (format "Doc: %s" (-> doc :schema-info :name))}
                      (let [fields (->> (tree-seq map? vals (:data doc))
                                        ;; Find the parent field, which contains at least one child value
                                        ;; with validation errors. Makes it easier to discover which field was problematic.
                                        (filter (fn [field]
                                                  (when (map? field)
                                                    (some (every-pred map? :validationResult) (vals field))))))]
                        (fact "no validation errors"
                          fields => [])))))

                (fact "check top level errors"
                  (doseq [doc documents]
                    (fact {:midje/description (format "Doc: %s" (-> doc :schema-info :name))}
                      (:validationErrors doc) => [])))

                (fact "check errors API"
                  (let [resp (query applicant :fetch-validation-errors :id app-id)]
                    (fact "ok" resp => ok?)
                    (fact "no errors"
                      (apply concat (:results resp)) => [])))))

            (fact "Application is still submittable"
              (query applicant :application-submittable :id app-id) => ok?)

            (fact "Submit application successfully"
              (command applicant :submit-application :id app-id) => ok?)))))))
