(ns lupapalvelu.organization-itest
  (:require [clojure.set :refer [difference]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as local-org-api]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.proxy-services :as proxy]
            [lupapalvelu.waste-ads :as waste-ads]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.core :as sade :refer [def-]]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [mount.core :as mount]))

(apply-remote-minimal)

(def- sipoo-R-org-id "753-R")
(def- sipoo-YA-org-id "753-YA")
(def- tampere-YA-org-id "837-YA")
(def- oulu-YMP-org-id "564-YMP")

(defn- language-map [langs suffix]
  (into {} (map (juxt identity
                      #(str (name %) " " suffix))
                langs)))

(facts "set-krysp-endpoint"
  (let [uri "http://127.0.0.1:8000/dev/krysp"]
    (fact "pena can't set krysp-url"
      (command pena :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "" :password "" :permitType "R" :version "1") => unauthorized?)

    (fact "sipoo can set working krysp-url"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "" :password "" :permitType "YA" :version "2") => ok?)

    (fact "sipoo can set working krysp-url containing extra spaces"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url (str " " uri " ") :username "" :password "" :permitType "YA" :version "2") => ok?)

   (fact "sipoo can't set incorrect krysp-url"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url "BROKEN_URL" :username "" :password "" :permitType "R"  :version "1") => fail?)))

(facts "set-krysp-endpoint private url"
  (let [uri "http://127.0.0.1:8000/dev/private-krysp"
        non-private "http://127.0.0.1:8000/dev/krysp"]
    (fact "sipoo can not set working krysp-url without credentials"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "" :password "" :permitType "R" :version "2") => fail?)

    (fact "sipoo can not set working krysp-url with incorrect credentials"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "foo" :password "bar" :permitType "R" :version "2") => fail?)

    (fact "sipoo can set working krysp-url with correct credentials"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "pena" :password "pena" :permitType "R" :version "2") => ok?)

    (fact "sipoo can not set working krysp-url with incorrect username and saved password"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "foo" :password "" :permitType "R" :version "2") => fail?)

    (fact "sipoo can set working krysp-url with only username set"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "pena" :password "" :permitType "R" :version "2") => ok?)

    (fact "query krysp config - no credentials set for P endpoint"
          (-> (query sipoo :krysp-config :organizationId sipoo-R-org-id)
              :krysp :P (select-keys [:url :username :password])) => (just {:url anything}))

    (fact "query krysp config - credentials not set for YA endpoint"
      (-> (query sipoo :krysp-config :organizationId sipoo-R-org-id)
          :krysp :YA (select-keys [:url :username :password])) => (just {:url anything}))

    (fact "query krysp config - credentials set for R endpoint - password is not returned"
      (-> (query sipoo :krysp-config :organizationId sipoo-R-org-id)
          :krysp :R (select-keys [:url :username :password])) => (just {:url uri :username "pena"}))

    (fact "changing to uri without username is possible"    ; LPK-3719
      (command sipoo :set-krysp-endpoint  :organizationId sipoo-R-org-id
               :url non-private :username "" :password "" :permitType "R" :version "2") => ok?
      (fact "username has been $unset"
        (-> (query sipoo :krysp-config :organizationId sipoo-R-org-id)
            :krysp :R (select-keys [:url :username :password])) => (just {:url non-private})))
    (fact "and returning back again works"
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "pena" :password "" :permitType "R" :version "2") => fail?
      (command sipoo :set-krysp-endpoint :organizationId sipoo-R-org-id
               :url uri :username "pena" :password "pena" :permitType "R" :version "2") => ok?
      (-> (query sipoo :krysp-config :organizationId sipoo-R-org-id)
          :krysp :R (select-keys [:url :username :password])) => (just {:url uri :username "pena"}))))


(facts* "users-in-same-organizations"
  (let [naantali (apikey-for "rakennustarkastaja@naantali.fi")
        jarvenpaa (apikey-for "rakennustarkastaja@jarvenpaa.fi")
        oulu (apikey-for "olli")

        naantali-user (query naantali :user) => ok?
        jarvenpaa-user (query jarvenpaa :user) => ok?
        oulu-user (query oulu :user) => ok?]

    ; Meta
    (fact "naantali user in naantali & jarvenpaa orgs"
      (->> naantali-user :user :orgAuthz keys) => (just [:529-R :186-R] :in-any-order))
    (fact "jarvenpaa just jarvenpaa"
      (->> jarvenpaa-user :user :orgAuthz keys) => [:186-R])
    (fact "oulu user in oulu & naantali orgs"
      (->> oulu-user :user :orgAuthz keys) => (just [:564-R :529-R :564-YMP] :in-any-order))


    (let [naantali-sees (:users (query naantali :users-in-same-organizations))
          jarvenpaa-sees (:users (query jarvenpaa :users-in-same-organizations))
          oulu-sees (:users (query oulu :users-in-same-organizations))]

      (fact "naantali user sees other users in naantali & jarvenpaa (but not admin)"
        (map :username naantali-sees) =>
        (contains ["rakennustarkastaja@naantali.fi"
                   "lupasihteeri@naantali.fi"
                   "rakennustarkastaja@jarvenpaa.fi"
                   "lupasihteeri@jarvenpaa.fi"
                   "olli"] :in-any-order))

      (fact "jarvenpaa just jarvenpaa users (incl. Mr. Naantali but not admin)"
        (map :username jarvenpaa-sees) =>
        (contains ["rakennustarkastaja@jarvenpaa.fi" "lupasihteeri@jarvenpaa.fi" "rakennustarkastaja@naantali.fi"] :in-any-order))

      (fact "oulu user sees other users in oulu & naantali"
        (map :username oulu-sees) =>
        (contains ["olli" "olli-ya" "rakennustarkastaja@naantali.fi" "lupasihteeri@naantali.fi"] :in-any-order)))))

(fact* "Organization details query works"
 (let [resp  (query pena "organization-details" :municipality "753" :operation "kerrostalo-rivitalo" :lang "fi") => ok?]
   (count (:attachmentsForOp resp )) => pos?
   (count (:links resp)) => pos?))

(fact* "The query /organizations"
  (let [resp (query admin :organizations) => ok?]
    (count (:organizations resp)) => pos?))

(fact "Update organization"
  (let [organization         (first (:organizations (query admin :organizations)))
        orig-scope           (first (:scope organization))
        organization-id      (:id organization)
        resp                 (command admin :update-organization
                               :permitType (:permitType orig-scope)
                               :municipality (:municipality orig-scope)
                               :inforequestEnabled (not (:inforequest-enabled orig-scope))
                               :applicationEnabled (not (:new-application-enabled orig-scope))
                               :openInforequestEnabled (not (:open-inforequest orig-scope))
                               :openInforequestEmail "someone@localhost.localdomain"
                               :opening nil
                               :pateEnabled false
                               :invoicingEnabled false)
        updated-organization (:data (query admin :organization-by-id :organizationId organization-id))
        updated-scope        (local-org-api/resolve-organization-scope (:municipality orig-scope) (:permitType orig-scope) updated-organization)]

    resp => ok?

    (fact "inforequest-enabled" (:inforequest-enabled updated-scope) => (not (:inforequest-enabled orig-scope)))
    (fact "new-application-enabled" (:new-application-enabled updated-scope) => (not (:new-application-enabled orig-scope)))
    (fact "open-inforequest" (:open-inforequest updated-scope) => (not (:open-inforequest orig-scope)))
    (fact "open-inforequest-email" (:open-inforequest-email updated-scope) => "someone@localhost.localdomain")))

(fact "Admin - Add scope"
  (let [organization   (first (:organizations (query admin :organizations)))
        org-id         (:id organization)
        scopes         (:scope organization)
        first-scope    (first scopes)
        new-permitType (first
                         (difference
                           (set (keys (permit/permit-types)))
                           (set (map :permitType scopes))))]
    (fact "Duplicate scope can't be added"
      (command admin :add-scope
               :organization org-id
               :permitType "R" ; Sipoo in minimal
               :municipality "753" ; Sipoo in minimal
               :inforequestEnabled true
               :applicationEnabled true
               :openInforequestEnabled false
               :openInforequestEmail ""
               :opening nil) => (partial expected-failure? :error.organization.duplicate-scope))
    (fact "invalid muni can't be added"
      (command admin :add-scope
               :organization org-id
               :permitType "R"
               :municipality "foobar"
               :inforequestEnabled true
               :applicationEnabled true
               :openInforequestEnabled false
               :openInforequestEmail ""
               :opening nil) => (partial expected-failure? :error.invalid-municipality))
    (fact "Admin can add new scope to organization"
      (command admin :add-scope
               :organization org-id
               :permitType new-permitType
               :municipality (:municipality first-scope) ; Sipoo in minimal
               :inforequestEnabled true
               :applicationEnabled true
               :openInforequestEnabled false
               :openInforequestEmail ""
               :opening nil) => ok?)))

(fact* "Tampere-ya sees (only) YA operations and attachments (LUPA-917, LUPA-1006)"
  (let [resp (query tampere-ya :organization-by-user :organizationId tampere-YA-org-id) => ok?
        tre  (:organization resp)]
    (fact "operations attachments"
      (keys (:operationsAttachments tre)) => [:YA]
      (-> tre :operationsAttachments :YA) => truthy)
    (fact "attachment types"
      (-> resp :operation-attachment-settings :defaults :allowed-attachments keys) => [:YA]
      (-> resp :operation-attachment-settings :defaults :allowed-attachments keys) => truthy)))

(facts "Organization attachment types"
  (facts "753-R: R P YM YI YL MAL VVVL KT MM"
    (let [types (:attachmentTypes (query sipoo :organization-attachment-types :organizationId sipoo-R-org-id))]
      (fact "R P attachment: ote_kiinteistorekisteristerista"
        types => (contains {:type-group "rakennuspaikka"
                            :type-id "ote_kiinteistorekisteristerista"}))
      (fact "YM attachment: selvitys_ymparistonsuojelutoimista"
        types => (contains {:type-group "koeluontoinen_toiminta"
                            :type-id "selvitys_ymparistonsuojelutoimista"}))
      (fact "YI VVVL attachment: kartta-melun-ja-tarinan-leviamisesta"
        types => (contains {:type-group "kartat-ja-piirustukset"
                            :type-id "kartta-melun-ja-tarinan-leviamisesta"}))
      (fact "YL attachment: paastot_ilmaan"
        types => (contains {:type-group "ymparistokuormitus"
                            :type-id "paastot_ilmaan"}))
      (fact "MAL attachment: selvitys-omistus-tai-hallintaoikeudesta"
        types => (contains {:type-group "hakija"
                            :type-id "selvitys-omistus-tai-hallintaoikeudesta"}))
      (fact "KT MM attachment: tilusvaihtosopimus"
        types => (contains {:type-group "kiinteiston_hallinta"
                            :type-id "tilusvaihtosopimus"}))
      (fact "No YA attachment: valokuva"
        types =not=> (contains {:type-group "yleiset-alueet"
                                :type-id "valokuva"}))))
  (facts "753-YA: YA"
    (let [types (:attachmentTypes (query sipoo-ya :organization-attachment-types :organizationId sipoo-YA-org-id))]
      (fact "No R P attachment: ote_kiinteistorekisteristerista"
        types =not=> (contains {:type-group "rakennuspaikka"
                                :type-id "ote_kiinteistorekisteristerista"}))
      (fact "No YM attachment: selvitys_ymparistonsuojelutoimista"
        types =not=> (contains {:type-group "koeluontoinen_toiminta"
                                :type-id "selvitys_ymparistonsuojelutoimista"}))
      (fact "No YI VVVL attachment: kartta-melun-ja-tarinan-leviamisesta"
        types =not=> (contains {:type-group "kartat"
                                :type-id "kartta-melun-ja-tarinan-leviamisesta"}))
      (fact "No YL attachment: paastot_ilmaan"
        types =not=> (contains {:type-group "ymparistokuormitus"
                                :type-id "paastot_ilmaan"}))
      (fact "No MAL attachment: ottamisalueen_omistus_hallintaoikeus"
        types =not=> (contains {:type-group "hakija"
                                :type-id "ottamisalueen_omistus_hallintaoikeus"}))
      (fact "No KT MM attachment: tilusvaihtosopimus"
        types =not> (contains {:type-group "kiinteiston_hallinta"
                               :type-id "tilusvaihtosopimus"}))
      (fact "YA attachment: valokuva"
        types => (contains {:type-group "yleiset-alueet"
                            :type-id "valokuva"})))))

(facts "Selected operations"
  (fact "For an organization which has no selected operations, all operations are returned"
    (let [resp (query sipoo-ya "all-operations-for-organization" :organizationId "753-YA")
          operations (:operations resp)]
      ;; All the YA operations (and only those) are received here.
      (count operations) => 1
      (-> operations first first) => "yleisten-alueiden-luvat"))

  (fact "Set selected operations"
    (command pena "set-organization-selected-operations" :organizationId sipoo-R-org-id
             :operations ["pientalo" "aita"]) => unauthorized?
    (command sipoo "set-organization-selected-operations" :organizationId sipoo-R-org-id
             :operations ["pientalo" "aita"]) => ok?)

  (fact* "Query selected operations"
    (query pena "selected-operations-for-municipality" :municipality "753") => ok?
    (let [resp (query sipoo "selected-operations-for-municipality" :municipality "753")]
      resp => ok?

      ;; Received the two selected R operations plus 4 YA operations.
      (:operations resp) =>  [["Rakentaminen ja purkaminen"
                               [["Uuden rakennuksen rakentaminen"
                                 [["pientalo" "pientalo"]]]
                                ["Rakennelman rakentaminen"
                                 [["Aita" "aita"]]]]]
                              ["yleisten-alueiden-luvat"
                               [["sijoituslupa"
                                 [["pysyvien-maanalaisten-rakenteiden-sijoittaminen"
                                   [["vesi-ja-viemarijohtojen-sijoittaminen" "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"]]]]]
                                ["katulupa" [["kaivaminen-yleisilla-alueilla"
                                              [["vesi-ja-viemarityot" "ya-katulupa-vesi-ja-viemarityot"]]]
                                             ["liikennealueen-rajaaminen-tyokayttoon"
                                              [["nostotyot" "ya-kayttolupa-nostotyot"]
                                               ["vaihtolavat" "ya-kayttolupa-vaihtolavat"]]]]]
                                ["kayttolupa" [["mainokset" "ya-kayttolupa-mainostus-ja-viitoitus"]
                                               ["terassit" "ya-kayttolupa-terassit"]
                                               ["promootio" "promootio"]
                                               ["lyhytaikainen-maanvuokraus" "lyhytaikainen-maanvuokraus"]]]]]]))

  (fact* "Query selected operations"
    (let [id   (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
          resp (query pena "addable-operations" :id id) => ok?]
      (:operations resp) => [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["pientalo" "pientalo"]]] ["Rakennelman rakentaminen" [["Aita" "aita"]]]]]]))

  (fact* "The query 'organization-by-user' correctly returns the selected operations of the organization"
    (let [_ (query pena "organization-by-user" :organizationId sipoo-R-org-id) => unauthorized?
          resp (query sipoo "organization-by-user" :organizationId sipoo-R-org-id) => ok?]
      (get-in resp [:organization :selectedOperations]) => {:R ["aita" "pientalo"]}))

  (fact "An application query correctly returns the 'required fields filling obligatory' and 'kopiolaitos-email' info in the organization meta data"
    (let [app-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
          app    (query-application pena app-id)
          org    (:data (query admin "organization-by-id" :organizationId (:organization app)))
          kopiolaitos-email "kopiolaitos@example.com"
          kopiolaitos-orderer-address "Testikatu 1"
          kopiolaitos-orderer-phone "123"
          kopiolaitos-orderer-email "orderer@example.com"]

      (fact "the 'app-required-fields-filling-obligatory' and 'kopiolaitos-email' flags have not yet been set for organization in db"
        (:app-required-fields-filling-obligatory org) => falsey
        (-> app :organizationMeta :requiredFieldsFillingObligatory) => falsey)

      (command sipoo "set-organization-app-required-fields-filling-obligatory" :organizationId sipoo-R-org-id
               :enabled false) => ok?

      (let [app    (query-application pena app-id)
            org    (:data (query admin "organization-by-id" :organizationId  (:organization app)))
            organizationMeta (:organizationMeta app)]
        (fact "the 'app-required-fields-filling-obligatory' is set to False"
          (:app-required-fields-filling-obligatory org) => false
          (:requiredFieldsFillingObligatory organizationMeta) => false)
        (fact "the 'kopiolaitos-email' is set (from minimal)"
          (:kopiolaitos-email org) => "sipoo@example.com"
          (get-in organizationMeta [:kopiolaitos :kopiolaitosEmail]) => "sipoo@example.com")
        (fact "the 'kopiolaitos-orderer-address' is set (from minimal)"
          (:kopiolaitos-orderer-address org) => "Testikatu 2, 12345 Sipoo"
          (get-in organizationMeta [:kopiolaitos :kopiolaitosOrdererAddress]) => "Testikatu 2, 12345 Sipoo")
        (fact "the 'kopiolaitos-orderer-email' is set (from minimal)"
          (:kopiolaitos-orderer-email org) => "tilaaja@example.com"
          (get-in organizationMeta [:kopiolaitos :kopiolaitosOrdererEmail]) => "tilaaja@example.com")
        (fact "the 'kopiolaitos-orderer-phone' is set (from minimal)"
          (:kopiolaitos-orderer-phone org) => "0501231234"
          (get-in organizationMeta [:kopiolaitos :kopiolaitosOrdererPhone]) => "0501231234"))

      (command sipoo "set-organization-app-required-fields-filling-obligatory" :organizationId sipoo-R-org-id
               :enabled true) => ok?
      (command sipoo "set-kopiolaitos-info" :organizationId sipoo-R-org-id
        :kopiolaitosEmail kopiolaitos-email
        :kopiolaitosOrdererAddress kopiolaitos-orderer-address
        :kopiolaitosOrdererPhone kopiolaitos-orderer-phone
        :kopiolaitosOrdererEmail kopiolaitos-orderer-email) => ok?

      (let [app    (query-application pena app-id)
            org    (:data (query admin "organization-by-id" :organizationId  (:organization app)))
            organizationMeta (:organizationMeta app)]
        (fact "the 'app-required-fields-filling-obligatory' flag is set to true value"
          (:app-required-fields-filling-obligatory org) => true
          (:requiredFieldsFillingObligatory organizationMeta) => true)
        (fact "the 'kopiolaitos-email' flag is set to given email address"
          (:kopiolaitos-email org) => kopiolaitos-email
          (get-in organizationMeta [:kopiolaitos :kopiolaitosEmail]) => kopiolaitos-email)
        (fact "the 'kopiolaitos-orderer-address' flag is set to given address"
          (:kopiolaitos-orderer-address org) => kopiolaitos-orderer-address
          (get-in organizationMeta [:kopiolaitos :kopiolaitosOrdererAddress]) => kopiolaitos-orderer-address)
        (fact "the 'kopiolaitos-orderer-phone' flag is set to given phone address"
          (:kopiolaitos-orderer-phone org) => kopiolaitos-orderer-phone
          (get-in organizationMeta [:kopiolaitos :kopiolaitosOrdererPhone]) => kopiolaitos-orderer-phone)
        (fact "the 'kopiolaitos-orderer-email' flag is set to given email address"
          (:kopiolaitos-orderer-email org) => kopiolaitos-orderer-email
          (get-in organizationMeta [:kopiolaitos :kopiolaitosOrdererEmail]) => kopiolaitos-orderer-email)))))


(let [{:keys [operation-attachment-settings
              organization]} (query sipoo :organization-by-user
                                           :organizationId sipoo-R-org-id)]
  (fact "Only two operations are selected"
    (:selectedOperations organization)
    => (just {:R (just  "pientalo" "aita" :in-any-order)}))
  (fact "organization-by-user includes deprecated operations"
    (:operation-nodes operation-attachment-settings)
    => (contains {:tyonjohtajan-nimeaminen    (contains {:deprecated? true})
                  :tyonjohtajan-nimeaminen-v2 (just {:allowed-attachments truthy
                                                     :default-attachments truthy
                                                     :permit-type          "R"})
                  :lannan-varastointi         (contains {:deprecated? true})
                  :lannan-varastointi-v2      (just {:allowed-attachments truthy
                                                    :default-attachments truthy
                                                    :permit-type          "YM"})})))

(facts "organization-operations-attachments"
  (fact "Invalid operation is rejected"
    (command sipoo :organization-operations-attachments :organizationId sipoo-R-org-id
             :operation "foo" :attachments []) => (partial expected-failure? "error.unknown-operation"))

  (fact "Empty attachments array is ok"
    (command sipoo :organization-operations-attachments :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments []) => ok?)

  (fact "scalar value as attachments parameter is not ok"
    (command sipoo :organization-operations-attachments :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments "") => (partial expected-failure? "error.non-vector-parameters"))

  (fact "Invalid attachment is rejected"
    (command sipoo :organization-operations-attachments :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [["foo" "muu"]]) => (partial expected-failure? "error.unknown-attachment-type"))

  (fact "Valid attachment is ok"
    (command sipoo :organization-operations-attachments :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [["muut" "muu"]]) => ok?))

(facts "set-organization-operations-allowed-attachments"
  (fact "Invalid operation is rejected"
    (command sipoo :set-organization-operations-allowed-attachments :organizationId sipoo-R-org-id
             :operation "ya-kayttolupa-terassit"
             :attachments [] :mode "inherit")
    => (partial expected-failure? "error.unknown-operation"))

  (fact "Empty attachments array is ok"
    (command sipoo :set-organization-operations-allowed-attachments
             :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [] :mode "inherit") => ok?)

  (fact "mode is required and are 'inherit' or 'set'"
    (command sipoo :set-organization-operations-allowed-attachments
             :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [] :mode "inherit") => ok?
    (command sipoo :set-organization-operations-allowed-attachments
             :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [] :mode "set") => ok?
    (command sipoo :set-organization-operations-allowed-attachments
             :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments []) => (partial expected-failure? "error.missing-parameters")
    (command sipoo :set-organization-operations-allowed-attachments
             :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [] :mode "invalid-mode") => (partial expected-failure? "error.unknown-attachment-mode"))

  (fact "scalar value as attachments parameter is not ok"
    (command sipoo :set-organization-operations-allowed-attachments
             :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments "" :mode "inherit") => (partial expected-failure? "error.non-vector-parameters"))

  (fact "Invalid attachment is rejected"
    (command sipoo :set-organization-operations-allowed-attachments :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [["foo" "muu"]] :mode "set") => (partial expected-failure? "error.unknown-attachment-type"))

  (fact "Valid attachment is ok"
    (command sipoo :set-organization-operations-allowed-attachments :organizationId sipoo-R-org-id
             :operation "pientalo" :attachments [["muut" "muu"]] :mode "set") => ok?)

  (fact "Attachments can be added to deprecated and unselected operations"
    (command sipoo :set-organization-operations-allowed-attachments :organizationId sipoo-R-org-id
             :operation "lannan-varastointi"
             :attachments [["hakemukset-ja-ilmoitukset" "ilmoituslomake"]]
             :mode "set") => ok?))

(facts "Archiving features can be set"
  (let [organization  (first (:organizations (query admin :organizations)))
        id (:id organization)]

    (fact "Permanent archive can be enabled"
      (command admin "set-organization-boolean-attribute" :enabled true :organizationId id :attribute "permanent-archive-enabled") => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (:permanent-archive-enabled updated-org) => true))

    (fact "Permanent archive can be disabled"
      (command admin "set-organization-boolean-attribute" :enabled false :organizationId id :attribute "permanent-archive-enabled") => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (:permanent-archive-enabled updated-org) => false))

    (fact "Digitizer tools can be enabled"
      (command admin "set-organization-boolean-attribute" :enabled true :organizationId id :attribute "digitizer-tools-enabled") => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (:digitizer-tools-enabled updated-org) => true))

    (fact "Digitizer tools can be disabled"
      (command admin "set-organization-boolean-attribute" :enabled false :organizationId id :attribute "digitizer-tools-enabled") => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (:digitizer-tools-enabled updated-org) => false))))

(facts "Organization names"
  (let [{names :names :as resp} (query pena :get-organization-names)]
    resp => ok?
    (count names) => pos?
    (-> names :753-R :fi) => "Sipoon rakennusvalvonta"))

(facts organization-names-by-user
  (let [sipoo-R-names {:fi "Sipoon rakennusvalvonta", :sv "Sipoon rakennusvalvonta", :en "Sipoon rakennusvalvonta"}]
    (query pena :organization-names-by-user) => {:ok true, :names {}}
    (query sipoo :organization-names-by-user) => {:ok true, :names {:753-R sipoo-R-names}}
    (query sonja :organization-names-by-user)
    => {:ok true, :names {:753-R         sipoo-R-names
                          :753-YA        {:en "Sipoon yleisten alueiden rakentaminen"
                                          :fi "Sipoon yleisten alueiden rakentaminen"
                                          :sv "Sipoon yleisten alueiden rakentaminen"}
                          :998-R-TESTI-2 sipoo-R-names}}))

(facts "Organization tags"
  (fact "only auth admin can add new tags"
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags []) => ok?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id
             :tags [{:id nil :label " makeja "} {:id nil :label "   nigireja   "}]) => ok?
    (command sonja :save-organization-tags :organizationId sipoo-R-org-id :tags [{:id nil :label "illegal"}] =not=> ok?)
    (command pena :save-organization-tags :organizationId sipoo-R-org-id :tags [{:id nil :label "  makeja  "}] =not=> ok?))
  (fact "tags are trimmed and get ids when saved"
    (:tags (query sipoo :get-organization-tags)) => (just {:753-R (just {:name (just (i18n/localization-schema string?))
                                                                         :tags (just [(just {:id string? :label "makeja"})
                                                                                      (just {:id string? :label "nigireja"})])})}))

  (facts "Blank tags are are not allowed"
    (command sipoo-ya :save-organization-tags :organizationId sipoo-YA-org-id
             :tags [{:id nil :label "one"} {:id nil :label "two"}]) => ok?
    (let [tags   (-> (query sipoo-ya :get-organization-tags) :tags :753-YA :tags)
          one-id (:id (util/find-by-key :label "one" tags))
          two-id (:id (util/find-by-key :label "two" tags))]
      one-id => truthy
      two-id => truthy
      (fact "One -> blank -> error"
        (command sipoo-ya :save-organization-tags :organizationId sipoo-YA-org-id
                 :tags [{:id two-id :label " New two "} {:id one-id :label "  "}])
        => (partial expected-failure? :error.illegal-value:schema-validation)
        (-> (query sipoo-ya :get-organization-tags) :tags :753-YA :tags)
        => tags)))

  (fact "only authority can fetch available tags"
    (query pena :get-organization-tags) =not=> ok?
    (map :label (:tags (:753-R (:tags (query sonja :get-organization-tags))))) => ["makeja" "nigireja"])

  (fact "invalid data is rejected"
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags {}) => fail?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags nil) => fail?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags "tag") => fail?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags [{}]) => fail?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags [{:id "id"}]) => fail?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id :tags [{:label false}]) => fail?
    (command sipoo :save-organization-tags :organizationId sipoo-R-org-id
             :tags [{:id nil :label {:injection true}}]) => fail?)

  (fact "Check tag deletion query"
    (let [id     (create-app-id sonja)
          tag-id (-> (query sonja :get-organization-tags)
                     :tags :753-R :tags first :id)]
      (command sonja :add-application-tags :id id :tags [tag-id]) => ok?

      (fact "when tag is used, application id is returned"
        (let [res (query sipoo :remove-tag-ok :organizationId sipoo-R-org-id :tagId tag-id)]
          res =not=> ok?
          (-> res :applications first :id) => id))

      (fact "when tag is not used in applications, ok is returned"
        (command sonja :add-application-tags :id id :tags []) => ok?
        (query sipoo :remove-tag-ok :organizationId sipoo-R-org-id :tagId tag-id) => ok?)))

  (facts "Reader and commenter can query tags areas"
    (let [tags  (util/dissoc-in (query ronja :get-organization-tags) [:tags (keyword sipoo-YA-org-id)])
          areas (query ronja :get-organization-areas)]
      tags => ok?
      (:tags tags) => not-empty
      areas => ok?
      (:areas areas) => not-empty
      (fact "Reader"
        (query luukas :get-organization-tags) => tags
        (query luukas :get-organization-areas) => areas)
      (fact "Commenter"
        (query kosti :get-organization-tags) => tags
        (query kosti :get-organization-areas) => areas))))

(facts "Organization areas zip file upload"
  (fact "only authorityAdmin can upload"
    (:body (upload-area pena sipoo-R-org-id)) => "unauthorized"
    (:body (upload-area sonja sipoo-R-org-id)) => "unauthorized")

  (fact "text file is not ok (zip required)"
    (:body (decode-response (upload-area sipoo sipoo-R-org-id "dev-resources/test-attachment.txt")))
    => {:ok   false
        :text (i18n/localize :fi :error.illegal-shapefile)})

  (let [resp (upload-area sipoo sipoo-R-org-id)
        body (:body (decode-response resp))]

    (fact "zip file with correct shape file can be uploaded by auth admin"
      resp => http200?
      body => ok?)))

(def local-db-name (str "test_organization_itest_" (sade/now)))

(mount/start #'mongo/connection)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

(facts "Municipality (753) maps"
       (mongo/with-db local-db-name
         (let [url "http://mapserver"]
           (local-org-api/update-organization
            "753-R"
            {$set {:map-layers {:server {:url url}
                                :layers [{:name "asemakaava"
                                          :id "asemakaava-id"
                                          :base true}
                                         {:name "kantakartta"
                                          :id "kantakartta-id"
                                          :base true}
                                         {:name "foo"
                                          :id "foo-id"
                                          :base false}
                                         {:name "bar"
                                          :id "bar-id"
                                          :base false}]}}})
           (local-org-api/update-organization
            "753-YA"
            {$set {:map-layers {:server {:url url}
                                :layers [{:name "asemakaava"
                                          :id "other-asemakaava-id"
                                          :base true}
                                         {:name "kantakartta"
                                          :id "other-kantakartta-id"
                                          :base true}
                                         {:name "Other foo"
                                          :id "foo-id"
                                          :base false}
                                         {:name "kantakartta" ;; not base layer
                                          :id "hii-id"
                                          :base false}]}}})
           (let [layers (proxy/municipality-layers "753")
                 objects (proxy/municipality-layer-objects "753")]
             (fact "Five layers"
                   (count layers) => 5)
             (fact "Only one asemakaava"
                   (->> layers (filter #(= (:name %) "asemakaava")) count) => 1)
             (fact "Only one with foo-id"
                   (->> layers (filter #(= (:id %) "foo-id")) count) => 1)
             (facts "Two layers named kantakartta"
                    (let [kantas (filter #(= "kantakartta" (:name %)) layers)
                          [k1 k2] kantas]
                      (fact "Two layers" (count kantas) => 2)
                      (fact "One of which is not a base layer"
                            (not= (:base k1) (:base k2)) => true)))
             (facts "Layer objects"
                    (defn loc-data [s] (zipmap [:fi :sv :en] (repeat s)))
                    (fact "Every layer object has an unique id"
                          (->> objects (map :id) set count) => 5)
                    (fact "Ids are correctly formatted"
                          (every? #(let [{:keys [id base]} %]
                                     (or (and base (= (name {"asemakaava"  101
                                                             "kantakartta" 102})
                                                      id))
                                         (and (not base) (not (number? id))))) objects))
                    (fact "Bar layer is correct "
                          (let [subtitles (loc-data "")
                                bar-index (->> layers (map-indexed #(assoc %2 :index %1)) (some #(if (= (:id %) "bar-id") (:index %))))]

                            (nth objects bar-index) => {:name        (loc-data "bar")
                                                        :subtitle    subtitles
                                                        :id          (str "Lupapiste-" bar-index)
                                                        :minScale    400000
                                                        :wmsName     "Lupapiste-753-R:bar-id"
                                                        :wmsUrl      "/proxy/kuntawms"})))
             (facts "New map data with different server to 753-YA"
                    (local-org-api/update-organization
                     "753-YA"
                     {$set {:map-layers {:server {:url "http://different"}
                                         :layers [{:name "asemakaava"
                                                   :id   "other-asemakaava-id"
                                                   :base true}
                                                  {:name "kantakartta"
                                                   :id   "other-kantakartta-id"
                                                   :base true}
                                                  {:name "Other foo"
                                                   :id   "foo-id"
                                                   :base false}]}}})
                    (let [layers (proxy/municipality-layers "753")]
                      (fact "Two layers with same ids are allowed if the servers differ"
                            (->> layers (filter #(= (:id %) "foo-id")) count) => 2)
                      (fact "Still only two base layers"
                            (->> layers (filter :base) count) => 2)))
             (facts "Storing passwords"
                    (local-org-api/update-organization-map-server "753-YA"
                                                                  "http://doesnotmatter"
                                                                  "username"
                                                                  "plaintext")
                    (let [org (local-org-api/get-organization "753-YA")
                          {:keys [password crypto-iv]} (-> org :map-layers :server)]
                      (fact "Password is encrypted"
                            (not= password "plaintext") => true)
                      (fact "Password decryption"
                            (local-org-api/decode-credentials password crypto-iv) => "plaintext")
                      (fact "No extra credentials are stored (nil)"
                            (local-org-api/update-organization-map-server "753-YA"
                                                                          "http://stilldoesnotmatter"
                                                                          nil
                                                                          nil)
                            (-> "753-YA" local-org-api/get-organization :map-layers :server
                                (select-keys [:password :crypto-iv])) => {:password nil})
                      (fact "No extra credentials are stored ('')"
                            (local-org-api/update-organization-map-server "753-YA"
                                                                          "http://stilldoesnotmatter"
                                                                          ""
                                                                          "")
                            (-> "753-YA" local-org-api/get-organization :map-layers :server
                                (select-keys [:password :crypto-iv])) => {:password nil})))))))

(doseq [[command-name config-key] [[:set-organization-neighbor-order-email :neighbor-order-emails]
                                   [:set-organization-submit-notification-email :submit-notification-emails]
                                   [:set-organization-inforequest-notification-email :inforequest-notification-emails]]]
  (facts {:midje/description (name command-name)}
    (fact "Emails are not set in fixture"
      (let [resp (query sipoo :organization-by-user :organizationId sipoo-R-org-id)]
        resp => ok?
        (:organization resp) => seq
        (get-in resp [:organization :notifications config-key]) => empty?))

    (fact "One email is set"
      (command sipoo command-name :organizationId sipoo-R-org-id :emails "kirjaamo@sipoo.example.com") => ok?
      (-> (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
          (get-in [:organization :notifications config-key])) => ["kirjaamo@sipoo.example.com"])

    (fact "Three emails are set"
      (command sipoo command-name :organizationId sipoo-R-org-id
               :emails "KIRJAAMO@sipoo.example.com,  sijainen1@sipoo.example.com;sijainen2@sipoo.example.com") => ok?
      (-> (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
          (get-in [:organization :notifications config-key]))
      => ["kirjaamo@sipoo.example.com", "sijainen1@sipoo.example.com", "sijainen2@sipoo.example.com"])

    (fact "Reset email addresses"
      (command sipoo command-name :organizationId sipoo-R-org-id :emails "") => ok?
      (-> (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
          (get-in [:organization :notifications config-key])) => empty?)))

(facts "Suti server details"
  (fact "initially empty"
    (let [initial-org-resp (query sipoo :organization-by-user :organizationId sipoo-R-org-id)]
      initial-org-resp => ok?
      (get-in initial-org-resp [:organization :suti :server]) => empty?))

  (fact "update succeeds"
    (command sipoo :update-suti-server-details :organizationId sipoo-R-org-id
             :url "http://localhost:8000/dev/suti" :username "sipoo" :password "xx") => ok?)

  (fact "is set"
    (let [{:keys [organization] :as org-resp} (query sipoo :organization-by-user :organizationId sipoo-R-org-id)]
      org-resp => ok?
      (get-in organization [:suti :server]) => (contains {:url "http://localhost:8000/dev/suti" :username "sipoo"})

      (fact "password not echoed"
        (get-in organization [:suti :server :password]) => nil))))

(facts "Construction waste feeds"
  (mongo/with-db local-db-name
    (mongo/insert :applications
                  {:_id          "LP-1"
                   :organization "753-R"
                   :municipality "753"
                   :documents    [
                                  {:schema-info {:name "rakennusjateselvitys"}
                                   :data        {:contact            {:name  {:value    "Bob"
                                                                              :modified 1}
                                                                      :phone {:value    "12345"
                                                                              :modified 2}
                                                                      :email {:value    "bob@reboot.tv"
                                                                              :modified 2222}}
                                                 :availableMaterials {:0 {:aines      {:value    "Sora"
                                                                                       :modified 100}
                                                                          :maara      {:value    "2"
                                                                                       :modified 110}
                                                                          :yksikko    {:value    "kg"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Rouheaa"
                                                                                       :modified 100}}}
                                                 }}]})
    (fact "Waste ads: one row"
      (waste-ads/waste-ads "753-R") => '({:modified  2222,
                                          :materials ({:kuvaus     "Rouheaa",
                                                       :saatavilla "17.12.2015",
                                                       :yksikko    "kg", :maara "2", :aines "Sora"}),
                                          :contact   {:email "bob@reboot.tv", :phone "12345", :name "Bob"},
                                          :municipality "753"}))
    (fact "No ads for 753-YA"
      (waste-ads/waste-ads "753-YA") => '())
    (mongo/insert :applications
                  {:_id          "LP-NO-NAME"
                   :organization "753-R"
                   :municipality "753"
                   :documents    [
                                  {:schema-info {:name "rakennusjateselvitys"}
                                   :data        {:contact            {:name  {:value ""}
                                                                      :phone {:value    "12345"
                                                                              :modified 2}
                                                                      :email {:value    "bob@reboot.tv"
                                                                              :modified 2222}}
                                                 :availableMaterials {:0 {:aines      {:value    "Sora"
                                                                                       :modified 100}
                                                                          :maara      {:value    "2"
                                                                                       :modified 110}
                                                                          :yksikko    {:value    "kg"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Rouheaa"
                                                                                       :modified 100}}}
                                                 }}]})
    (mongo/insert :applications
                  {:_id          "LP-NO-PHONE-EMAIL"
                   :organization "753-R"
                   :municipality "753"
                   :documents    [
                                  {:schema-info {:name "rakennusjateselvitys"}
                                   :data        {:contact            {:name  {:value    "Bob"
                                                                              :modified 21}
                                                                      :phone {:value ""}
                                                                      :email {:value ""}}
                                                 :availableMaterials {:0 {:aines      {:value    "Sora"
                                                                                       :modified 100}
                                                                          :maara      {:value    "2"
                                                                                       :modified 110}
                                                                          :yksikko    {:value    "kg"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Rouheaa"
                                                                                       :modified 100}}}
                                                 }}]})
    (mongo/insert :applications
                  {:_id          "LP-NO-AINES"
                   :organization "753-R"
                   :municipality "753"
                   :documents    [
                                  {:schema-info {:name "rakennusjateselvitys"}
                                   :data        {:contact            {:name  {:value    "Bob"
                                                                              :modified 1}
                                                                      :phone {:value    "12345"
                                                                              :modified 2}
                                                                      :email {:value    "bob@reboot.tv"
                                                                              :modified 2222}}
                                                 :availableMaterials {:0 {:aines      {:value ""}
                                                                          :maara      {:value    "2"
                                                                                       :modified 110}
                                                                          :yksikko    {:value    "kg"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Rouheaa"
                                                                                       :modified 100}}}
                                                 }}]})
    (mongo/insert :applications
                  {:_id          "LP-NO-MAARA"
                   :organization "753-R"
                   :municipality "753"
                   :documents    [
                                  {:schema-info {:name "rakennusjateselvitys"}
                                   :data        {:contact            {:name  {:value    "Bob"
                                                                              :modified 1}
                                                                      :phone {:value    "12345"
                                                                              :modified 2}
                                                                      :email {:value    "bob@reboot.tv"
                                                                              :modified 2222}}
                                                 :availableMaterials {:0 {:aines      {:value    "Sora"
                                                                                       :modified 100}
                                                                          :maara      {:value ""}
                                                                          :yksikko    {:value    "kg"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Rouheaa"
                                                                                       :modified 100}}}
                                                 }}]})
    (fact "Ads only include proper information"
      (count (waste-ads/waste-ads "753-R")) => 1)
    (mongo/insert :applications
                  {:_id          "LP-2"
                   :organization "753-R"
                   :municipality "753"
                   :documents    [
                                  {:schema-info {:name "laajennettuRakennusjateselvitys"}
                                   :data        {:contact            {:name  {:value    "Dot"
                                                                              :modified 1}
                                                                      :phone {:value    "12345"
                                                                              :modified 2}
                                                                      :email {:value    "dot@reboot.tv"
                                                                              :modified 2221}}
                                                 :availableMaterials {:0 {:aines      {:value    "Kivi"
                                                                                       :modified 12345}
                                                                          :maara      {:value    "100"
                                                                                       :modified 110}
                                                                          :yksikko    {:value    "tonni"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Rouheaa"
                                                                                       :modified 100}}
                                                                      :1 {:aines      {:value ""}
                                                                          :maara      {:value    "100"
                                                                                       :modified 999999}
                                                                          :yksikko    {:value    "tonni"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "17.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value    "Ignored"
                                                                                       :modified 100}}
                                                                      :2 {:aines      {:value "Puu"}
                                                                          :maara      {:value    "8"
                                                                                       :modified 88}
                                                                          :yksikko    {:value    "m3"
                                                                                       :modified 200}
                                                                          :saatavilla {:value    "20.12.2015"
                                                                                       :modified 170}
                                                                          :kuvaus     {:value ""}}}
                                                 }}]})
    (fact "Two ads"
      (waste-ads/waste-ads "753-R")
      => '({:modified 999999,
            :materials
                      ({:kuvaus "Rouheaa", :saatavilla "17.12.2015", :yksikko "tonni", :maara "100", :aines "Kivi"}
                        {:kuvaus "", :saatavilla "20.12.2015", :yksikko "m3", :maara "8", :aines "Puu"}),
            :contact  {:email "dot@reboot.tv", :phone "12345", :name "Dot"},
            :municipality "753"}
            {:modified  2222,
             :materials ({:kuvaus "Rouheaa", :saatavilla "17.12.2015", :yksikko "kg", :maara "2", :aines "Sora"}),
             :contact   {:email "bob@reboot.tv", :phone "12345", :name "Bob"},
             :municipality "753"}))
    (fact "Ad list size limit"
      (doseq [id (range 110)]
        (mongo/insert :applications
                      {:_id          (str "LP-FILL-" id)
                       :organization "753-R"
                       :municipality "753"
                       :documents    [
                                      {:schema-info {:name "rakennusjateselvitys"}
                                       :data        {:contact            {:name  {:value    "Bob"
                                                                                  :modified 1}
                                                                          :phone {:value    "12345"
                                                                                  :modified 2}
                                                                          :email {:value    "bob@reboot.tv"
                                                                                  :modified 2222}}
                                                     :availableMaterials {:0 {:aines      {:value    "Sora"
                                                                                           :modified 100}
                                                                              :maara      {:value    "2"
                                                                                           :modified 110}
                                                                              :yksikko    {:value    "kg"
                                                                                           :modified 200}
                                                                              :saatavilla {:value    "17.12.2015"
                                                                                           :modified 170}
                                                                              :kuvaus     {:value    "Rouheaa"
                                                                                           :modified 100}}}
                                                     }}]}))
      (count (waste-ads/waste-ads "753-R")) => 100
      (count (waste-ads/waste-ads nil)) => 100
      (count (waste-ads/waste-ads "")) => 100)

    (facts "Validators"
      (fact "Bad format: nil" (local-org-api/valid-feed-format {:data {:fmt nil}})
        => {:ok false, :text "error.invalid-feed-format"})
      (fact "Bad format: ''" (local-org-api/valid-feed-format {:data {:fmt ""}})
        => {:ok false, :text "error.invalid-feed-format"})
      (fact "Bad format: foo" (local-org-api/valid-feed-format {:data {:fmt "foo"}})
        => {:ok false, :text "error.invalid-feed-format"})

      (fact "Good format: rSs" (local-org-api/valid-feed-format {:data {:fmt "rSs"}})
        => nil)
      (fact "Good format: jsON" (local-org-api/valid-feed-format {:data {:fmt "jsON"}})
        => nil)
      (fact "Valid organization 753-R" (local-org-api/valid-org {:data {:org "753-R"}})
        => nil)
      (fact "Valid organization 753-r" (local-org-api/valid-org {:data {:org "753-r"}})
        => nil)
      (fact "Empty organization is valid " (local-org-api/valid-org {:data {}})
        => nil)
      (fact "Invalid organization 888-X" (local-org-api/valid-org {:data {:org "888-X"}})
        => {:ok false, :text "error.organization-not-found"})
      )))



(facts allowed-autologin-ips-for-organization

  (fact "applicant is not authorized"
    (query pena :allowed-autologin-ips-for-organization :org-id "753-R") => unauthorized?)
  (fact "authority is not authorized"
    (query sonja :allowed-autologin-ips-for-organization :org-id "753-R") => unauthorized?)
  (fact "authorityadmin is not authorized"
    (query sipoo :allowed-autologin-ips-for-organization :org-id "753-R") => unauthorized?)
  (fact "admin is authorized"
    (query admin :allowed-autologin-ips-for-organization :org-id "753-R") => ok?)

  (fact "no allowed autologin ips for sipoo is empty"
    (-> (query admin :allowed-autologin-ips-for-organization :org-id "753-R") :ips) => empty?)

  (fact "four allowed autologin ips for porvoo"
    (-> (query admin :allowed-autologin-ips-for-organization :org-id "638-R") :ips count) => 4))

(facts update-allowed-autologin-ips

  (fact "applicant is not authorized"
    (command pena :update-allowed-autologin-ips :org-id "753-R" :ips []) => unauthorized?)
  (fact "authority is not authorized"
    (command sonja :update-allowed-autologin-ips :org-id "753-R" :ips []) => unauthorized?)
  (fact "authorityadmin is not authorized"
    (command sipoo :update-allowed-autologin-ips :org-id "753-R" :ips []) => unauthorized?)
  (fact "admin is authorized"
    (command admin :update-allowed-autologin-ips :org-id "753-R" :ips []) => ok?)

  (fact "autologin ips are updated for sipoo"
    (let [ips (repeatedly 5 #(ssg/generate ssc/IpAddress))]
      (command admin :update-allowed-autologin-ips :org-id "753-R" :ips ips) => ok?
      (-> (query admin :allowed-autologin-ips-for-organization :org-id "753-R") :ips) => ips))

  (fact "there are still four allowed autologin ips for porvoo"
    (-> (query admin :allowed-autologin-ips-for-organization :org-id "638-R") :ips count) => 4)

  (fact "trying to update with invalid ip address"
    (command admin :update-allowed-autologin-ips :org-id "753-R" :ips ["inv.val.id.ip"]) => (partial expected-failure? :error.invalid-ip)))


(facts update-organization-name

  (fact "admin is authorized"
    (command admin :update-organization-name :organizationId "753-R" :name {:fi "modified"}) => ok?)

  (fact "authorityAdmin of the same organization is authorized"
    (command sipoo :update-organization-name :organizationId "753-R" :name {:fi "modified"}) => ok?)

  (fact "authorityAdmin of different organization is not authorized"
    (command sipoo :update-organization-name :organizationId "186-R" :name {:fi "modified"}) => unauthorized?)

  (fact "authority is not authorized"
    (command sonja :update-organization-name :organizationId "753-R" :name {:fi "modified"}) => unauthorized?)

  (fact "applicant is not authorized"
    (command pena :update-organization-name :organizationId "753-R" :name {:fi "modified"}) => unauthorized?)

  (fact "illegal name map"
    (command admin :update-organization-name :organizationId "753-R" :name "modified") => (partial expected-failure? :error.illegal-localization-value))

  (fact "illegal name key"
    (command admin :update-organization-name :organizationId "753-R" :name {:suomi "modified"}) => (partial expected-failure? :error.illegal-localization-value))

  (fact "illegal name value type"
    (command admin :update-organization-name :organizationId "753-R" :name {:fi {}}) => (partial expected-failure? :error.illegal-localization-value))

  (fact "empty name value"
    (command admin :update-organization-name :organizationId "753-R" :name {:fi ""}) => (partial expected-failure? :error.empty-organization-name))

  (facts "organization name is changed only for languages that are specified in command data"
    (let [change-all-map (language-map i18n/supported-langs "modified")]
      (fact "all languages"
        (command admin :update-organization-name :organizationId "753-R" :name change-all-map) => ok?
        (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
                [:organization :name]) => change-all-map)

      (fact "all but one language"
        (let [unchanged (first i18n/supported-langs)
              changed (rest i18n/supported-langs)
              change-map (language-map changed "modified again")
              expected-response (merge (select-keys change-all-map [unchanged])
                                       change-map)]
          (command admin :update-organization-name :organizationId "753-R" :name change-map) => ok?
          (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
                  [:organization :name]) => expected-response)))))


(facts organization-names-by-user
  (let [organization-name-map (select-keys {:fi "Sipoo" :sv "Sibbo" :en "Sipoo"}
                                           i18n/supported-langs)]
    (command admin :update-organization-name :organizationId "753-R"
             :name organization-name-map) => ok?

    (fact "query returns organization names for all languages"
      (query sipoo :organization-name-by-user :organizationId sipoo-R-org-id) => {:ok true :id "753-R"
                                                                                  :name organization-name-map})))

(facts "usage-purposes"
  (query pena :usage-purposes) => {:ok true, :usagePurposes [{:type "applicant"}]}
  (query sipoo :usage-purposes) => {:ok true, :usagePurposes [{:type "authority-admin", :orgId "753-R"}]}
  (query ronja :usage-purposes) => {:ok true, :usagePurposes [{:type "authority"}
                                                              {:type "authority-admin", :orgId "753-R"}
                                                              {:type "authority-admin", :orgId "753-YA"}]})

(def sipoo-handler-roles
  (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id) [:organization :handler-roles]))

(facts upsert-handler-role
  (fact "cannot update with unexisting id"
    (command sipoo :upsert-handler-role :organizationId sipoo-R-org-id
             :roleId "abba1111111111111666acdc"
             :name {:fi "Not found id kasittelija"
                    :sv "Not found id handlaggare"
                    :en "Not found id handler"}) => (partial expected-failure? :error.unknown-handler))

  (fact "cannot update with non-string name"
    (command sipoo :upsert-handler-role :organizationId sipoo-R-org-id
             :roleId (get-in sipoo-handler-roles [1 :id])
             :name {:fi {:handler "non-string kasittelija"}
                    :sv "Updated Swedish handlaggare"
                    :en "Updated English handler"}) => (partial expected-failure? :error.illegal-localization-value))

  (fact "cannot update with missing handler name localization"
    (command sipoo :upsert-handler-role :organizationId sipoo-R-org-id
             :roleId (get-in sipoo-handler-roles [1 :id])
             :name {:fi "Updated Finnish kasittelija"
                    :sv "Updated Swedish handlaggare"
                    :en ""}) => (partial expected-failure? :error.missing-parameters))

  (facts "update existing handler role"
    (command sipoo :upsert-handler-role :organizationId sipoo-R-org-id
             :roleId (get-in sipoo-handler-roles [0 :id])
             :name {:fi "Updated Finnish kasittelija"
                    :sv "Updated Swedish handlaggare"
                    :en "Updated English handler"}) => ok?

    (let [handler-roles (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
                                [:organization :handler-roles])]
      (fact "handler role is updated"
        (first handler-roles) => {:id   (get-in sipoo-handler-roles [0 :id])
                                  :name {:fi "Updated Finnish kasittelija"
                                         :sv "Updated Swedish handlaggare"
                                         :en "Updated English handler"}
                                  :general true})
      (fact "no new handlers added"
        (count handler-roles) => (count sipoo-handler-roles))

      (fact "other handler roles not changed"
        (second handler-roles) => (second sipoo-handler-roles))))

  (facts "insert new handler role"
    (command sipoo :upsert-handler-role :organizationId sipoo-R-org-id
             :name {:fi "New Finnish kasittelija"
                    :sv "New Swedish handlaggare"
                    :en "New English handler"}) => ok?

    (let [handler-roles (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
                                [:organization :handler-roles])]
      (fact "one handler is added"
        (count handler-roles) => (inc (count sipoo-handler-roles)))

      (fact "handler name is set"
        (:name (last handler-roles)) => {:fi "New Finnish kasittelija"
                                         :sv "New Swedish handlaggare"
                                         :en "New English handler"})

      (fact "id is set"
        (:id (last handler-roles)) => ss/not-blank?)

      (fact "other handler-roles not updated"
        (take 2 handler-roles) => [{:id   (get-in sipoo-handler-roles [0 :id])
                                    :name {:fi "Updated Finnish kasittelija"
                                           :sv "Updated Swedish handlaggare"
                                           :en "Updated English handler"}
                                    :general true}
                                   (second sipoo-handler-roles)]))))

(def sipoo-handler-roles
  (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id) [:organization :handler-roles]))

(facts toggle-handler-role
  (fact "cannot disable with unexisting id"
    (command sipoo :toggle-handler-role :organizationId sipoo-R-org-id
             :roleId "abba1111111111111666acdc"
             :enabled false) => (partial expected-failure? :error.unknown-handler))

  (fact "cannot disable with blank id"
    (command sipoo :toggle-handler-role :organizationId sipoo-R-org-id
             :roleId ""
             :enabled false) => (partial expected-failure? :error.missing-parameters))

  (fact "cannot disable general handler role"
        (command sipoo :toggle-handler-role :organizationId sipoo-R-org-id
                 :enabled false
                 :roleId (-> sipoo-handler-roles first :id))
        => (partial expected-failure? :error.illegal-handler-role))

  (fact "disable handler role"
        (command sipoo :toggle-handler-role :organizationId sipoo-R-org-id
                 :enabled false
                 :roleId (-> sipoo-handler-roles second :id))=> ok?

        (let [handler-roles (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
                                    [:organization :handler-roles])]
          (fact "no handler roles added or deleted"
                (count handler-roles) => (count sipoo-handler-roles))

          (fact "second handler is disabled"
                (-> handler-roles second :disabled) => true)

          (fact "second handler is not name and id is not edited"
                (-> handler-roles second (select-keys [:id :name]))
                => (-> sipoo-handler-roles second (select-keys [:id :name])))

          (fact "other handler-roles not updated"
                (first handler-roles) => (first sipoo-handler-roles))))

  (fact "enable handler role"
        (command sipoo :toggle-handler-role :organizationId sipoo-R-org-id
                 :enabled true
                 :roleId (-> sipoo-handler-roles second :id)) => ok?)
  (fact "second handler is now enabled"
        (-> (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id) [:organization :handler-roles])
            second :disabled) => false))

(facts create-organization
  (fact "organization already exists"
    (command admin :create-organization :org-id "753-R" :municipality "666" :name "Manalan rakennusvalvonta" :permit-types ["R" "P"]) => (partial expected-failure? :error.organization-already-exists))

  (fact "duplicate scope"
    (command admin :create-organization :org-id "666-R" :municipality "753" :name "Manalan rakennusvalvonta" :permit-types ["R" "P"]) => (partial expected-failure? :error.organization.duplicate-scope))

  (fact "invalid permit type"
    (command admin :create-organization :org-id "666-R" :municipality "666" :name "Manalan rakennusvalvonta" :permit-types ["G"]) => (partial expected-failure? :error.invalid-permit-type))

  (fact "success"
    (command admin :create-organization :org-id "666-R" :municipality "666" :name "Manalan rakennusvalvonta" :permit-types ["R" "P"]) => ok?)

  (fact "organization created succesfully"
    (let [result (query admin :organization-by-id :organizationId "666-R")
          org    (:data result)]
      (:id org) => "666-R"
      (:name org) => {:en "Manalan rakennusvalvonta" :fi "Manalan rakennusvalvonta" :sv "Manalan rakennusvalvonta"}
      (:scope org) => [{:inforequest-enabled false,
                        :municipality "666",
                        :new-application-enabled false,
                        :open-inforequest false,
                        :open-inforequest-email "",
                        :opening nil, :permitType "R"}
                       {:inforequest-enabled false,
                        :municipality "666",
                        :new-application-enabled false,
                        :open-inforequest false,
                        :open-inforequest-email "",
                        :opening nil,
                        :permitType "P"}])))

(defn update-docstore-info [org-id docStoreInUse docTerminalInUse docDepartmentalInUse
                            documentPrice organizationFee organizationDescription]
  (let [params (util/strip-nils {:org-id                  org-id
                                 :docStoreInUse           docStoreInUse
                                 :docTerminalInUse        docTerminalInUse
                                 :docDepartmentalInUse    docDepartmentalInUse
                                 :pricing                 (when (or documentPrice organizationFee)
                                                            {:price documentPrice
                                                             :fee   organizationFee})
                                 :organizationDescription organizationDescription})
        cmd-fn (partial command admin :update-docstore-info)]
    (->> params (into []) flatten (apply cmd-fn))))

(defn get-docstore-info [org-id]
  (let [result (query admin :organization-by-id :organizationId org-id)]
    (if (ok? result)
      (-> result :data :docstore-info)
      result)))

(facts update-docstore-info

  (fact "calling does not change other organization data"
    (let [org (:data (query admin :organization-by-id :organizationId "753-R"))]
      (update-docstore-info "753-R" true false false 100 20 (i18n/supported-langs-map (constantly "Description"))) => ok?
      (dissoc org :docstore-info)
      => (-> (query admin :organization-by-id :organizationId "753-R")
             :data
             (dissoc :docstore-info))))

  (fact "calling updates organization's docstore info"
    (update-docstore-info "753-R" true false false 100 20
                          {:fi "Kuvaus" :sv "Beskrivning" :en "Description"}) => ok?
    (get-docstore-info "753-R")
    => {:docStoreInUse                      true
        :docTerminalInUse                   false
        :docDepartmentalInUse               false
        :allowedTerminalAttachmentTypes     []
        :allowedDepartmentalAttachmentTypes []
        :documentPrice                      100
        :organizationFee                    20
        :organizationDescription            {:fi "Kuvaus" :sv "Beskrivning" :en "Description"}
        :documentRequest                    {:enabled      false
                                             :email        ""
                                             :instructions {:en "" :fi "" :sv ""}}})
  (fact "Description must have at least Finnish and Swedish"
    (update-docstore-info "753-R" true false false 100 20 {:fi "Kuvaus"})
    => (partial expected-failure? :error.illegal-value:schema-validation)
    (update-docstore-info "753-R" true false false 100 20 {:sv "Hejdå"})
    => (partial expected-failure? :error.illegal-value:schema-validation)
    (update-docstore-info "753-R" true false false 100 20 {:fi "Kuvaus" :sv "Hejdå" :extra "bad"})
    => (partial expected-failure? :error.illegal-value:schema-validation)
    (update-docstore-info "753-R" true false false 100 20 "Very bad")
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (fact "can't set negative document price"
    (update-docstore-info "753-R" true false false -100 20 {:en "Hi" :fi "Moi" :sv "Hej"})
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (fact "can't set negative organization fee"
    (update-docstore-info "753-R" true false false 100 -20 {:en "Hi" :fi "Moi" :sv "Hej"})
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (fact "can't set decimal document price"
    (update-docstore-info "753-R" true true true 1.0 0 {:en "Hi" :fi "Moi" :sv "Hej"})
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (fact "can't set decimal organization fee"
    (update-docstore-info "753-R" true true true 10 1.0 {:en "Hi" :fi "Moi" :sv "Hej"})
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (facts "Organization fee must be less than document price"
    (update-docstore-info "753-R" true true true 100 100 {:fi "Kuvaus" :sv "Beskrivning" :en "Description"})
    => (partial expected-failure? :error.bad-docstore-pricing)
    (update-docstore-info "753-R" true true true 100 120 {:fi "Kuvaus" :sv "Beskrivning" :en "Description"})
    => (partial expected-failure? :error.bad-docstore-pricing))

  (facts "Pricing and descriptions are required only when used"
    (update-docstore-info "186-R" false false false nil nil nil)
    => ok?
    (get-docstore-info "186-R")
    => (contains {:docStoreInUse           false
                  :docTerminalInUse        false
                  :docDepartmentalInUse    false
                  :documentPrice           0
                  :organizationFee         0
                  :organizationDescription {:fi "" :sv "" :en ""}}))
  (facts "Pricing and description updated only when used"
    (update-docstore-info "186-R" false false false 321 123 {:fi "suomeksi" :sv "på svenska"})
    => ok?
    (get-docstore-info "186-R")
    => (contains {:docStoreInUse           false
                  :docTerminalInUse        false
                  :docDepartmentalInUse    false
                  :documentPrice           0
                  :organizationFee         0
                  :organizationDescription {:fi "" :sv "" :en ""}})
    (facts "Terminal enabled"
      (update-docstore-info "186-R" false true false 321 123 {:fi "suomeksi" :sv "på svenska"})
      => ok?
      (fact "Description is optional"
        (update-docstore-info "186-R" false true false 321 123 nil)
        => ok?)
      (get-docstore-info "186-R")
      => (contains {:docStoreInUse           false
                    :docTerminalInUse        true
                    :docDepartmentalInUse    false
                    :documentPrice           0
                    :organizationFee         0
                    :organizationDescription {:fi "suomeksi" :sv "på svenska"}}))
    (fact "Departmental enabled"
      (update-docstore-info "186-R" false false true 321 123
                            {:fi "Virkapääte" :sv "Kontorsterminal" :en "Deparment Terminal"})
      => ok?
      (fact "Description is optional"
        (update-docstore-info "186-R" false false true 321 123 nil)
        => ok?)
      (get-docstore-info "186-R")
      => (contains {:docStoreInUse           false
                    :docTerminalInUse        false
                    :docDepartmentalInUse    true
                    :documentPrice           0
                    :organizationFee         0
                    :organizationDescription {:fi "Virkapääte"
                                              :sv "Kontorsterminal"
                                              :en "Deparment Terminal"}}))
    (facts "Store enabled"
      (update-docstore-info "186-R" true false false nil nil
                            {:fi "Kioski" :sv "Butik" :en "Shop"})
      => (partial expected-failure? :error.missing-docstore-pricing)
      (update-docstore-info "186-R" true false false 321 nil
                            {:fi "Kioski" :sv "Butik" :en "Shop"})
      => (partial expected-failure? :error.illegal-value:schema-validation)
      (update-docstore-info "186-R" true false false nil 123
                            {:fi "Kioski" :sv "Butik" :en "Shop"})
      => (partial expected-failure? :error.illegal-value:schema-validation)
      (update-docstore-info "186-R" true false false 321 123
                            {:fi "Kioski" :sv "Butik" :en "Shop"})
      => ok?
      (get-docstore-info "186-R")
      => (contains {:docStoreInUse           true
                    :docTerminalInUse        false
                    :docDepartmentalInUse    false
                    :documentPrice           321
                    :organizationFee         123
                    :organizationDescription {:fi "Kioski" :sv "Butik" :en "Shop"}})
      (fact "Description is optional"
        (update-docstore-info "186-R" true false false 300 100 nil)
        => ok?)
      (get-docstore-info "186-R")
      => (contains {:docStoreInUse           true
                    :docTerminalInUse        false
                    :docDepartmentalInUse    false
                    :documentPrice           300
                    :organizationFee         100
                    :organizationDescription {:fi "Kioski" :sv "Butik" :en "Shop"}}))))

(facts set-docterminal-attachment-type
  (fact "only authority admin can call"
    (command sonja :set-docterminal-attachment-type :organizationId sipoo-R-org-id
             :attachmentType "osapuolet.cv" :enabled false)
    => (partial expected-failure? :error.unauthorized))

  (fact "cannot set nonsense types"
    (command sipoo :set-docterminal-attachment-type :organizationId sipoo-R-org-id
             :attachmentType "foo" :enabled true)
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (fact "Docterminal not enabled"
    (query sipoo :docterminal-enabled :organizationId sipoo-R-org-id)
    => (partial expected-failure? :error.docterminal-not-enabled))

  (fact "Docdepartmental not enabled"
    (query sipoo :docdepartmental-enabled :organizationId sipoo-R-org-id)
    => (partial expected-failure? :error.docdepartmental-not-enabled))

  (fact "cannot set unless docterminal is enabled"
    (command sipoo :set-docterminal-attachment-type :organizationId sipoo-R-org-id
             :attachmentType "osapuolet.cv" :enabled true)
    => (partial expected-failure? :error.docterminal-not-enabled))

  (fact "calling with proper type adds type to organization's docstore-info"
    (update-docstore-info "753-R" true true true 100 20 {:fi "Kuvaus" :sv "Beskrivning" :en "Description"}) => ok?

    (command sipoo :set-docterminal-attachment-type :organizationId sipoo-R-org-id
             :attachmentType "osapuolet.cv" :enabled true) => ok?
    (-> "753-R" get-docstore-info :allowedTerminalAttachmentTypes) => ["osapuolet.cv"]

    (command sipoo :set-docterminal-attachment-type :organizationId sipoo-R-org-id
             :attachmentType "all" :enabled true) => ok?
    (-> "753-R" get-docstore-info :allowedTerminalAttachmentTypes) => local-org-api/allowed-attachments

    (command sipoo :set-docterminal-attachment-type :organizationId sipoo-R-org-id
             :attachmentType "all" :enabled false) => ok?
    (-> "753-R" get-docstore-info :allowedTerminalAttachmentTypes) => [])

  (fact "Docterminal enabled"
    (query sipoo :docterminal-enabled :organizationId sipoo-R-org-id) => ok?)

  (fact "Docdepartmental enabled"
    (query sipoo :docdepartmental-enabled :organizationId sipoo-R-org-id) => ok?)

  (fact "calling with proper type adds type to organization's departmental attachment types"
    (command sipoo :set-docdepartmental-attachment-type :organizationId sipoo-R-org-id
            :attachmentType "osapuolet.cv" :enabled true) => ok?
    (-> "753-R" get-docstore-info :allowedDepartmentalAttachmentTypes) => ["osapuolet.cv"]

    (command sipoo :set-docdepartmental-attachment-type :organizationId sipoo-R-org-id
            :attachmentType "all" :enabled true) => ok?
    (-> "753-R" get-docstore-info :allowedDepartmentalAttachmentTypes) => local-org-api/allowed-attachments

    (command sipoo :set-docdepartmental-attachment-type :organizationId sipoo-R-org-id
            :attachmentType "all" :enabled false) => ok?
    (-> "753-R" get-docstore-info :allowedDepartmentalAttachmentTypes) => []))

(fact "Document request info"
  (fact "only authority admin can call"
    (command sonja :set-document-request-info :organizationId sipoo-R-org-id
             :enabled false :email "a@b.c" :instructions {:en "Instructions" :fi "Ohjeet" :sv "Anvisningar"})
    => (partial expected-failure? :error.unauthorized))

  (fact "email must be valid"
    (command sipoo :set-document-request-info :organizationId sipoo-R-org-id
             :enabled false :email "not-valid-email" :instructions {:en "Instructions" :fi "Ohjeet" :sv "Anvisningar"})
    => (partial expected-failure? :error.email))

  (fact "Blank or missing email is allowed"
    (command sipoo :set-document-request-info :organizationId sipoo-R-org-id
             :enabled false :email " "
             :instructions {:en "Instructions" :fi "Ohjeet" :sv "Anvisningar"})
    => ok?
    (command sipoo :set-document-request-info :organizationId sipoo-R-org-id
             :enabled false
             :instructions {:en "Instructions" :fi "Ohjeet" :sv "Anvisningar"})
    => ok?)

  (fact "setting works"
    (command sipoo :set-document-request-info :organizationId sipoo-R-org-id
             :enabled true :email "a@b.c"
             :instructions {:en "Instructions" :fi "Ohjeet" :sv "Anvisningar"}) => ok?
    (-> (query sipoo :document-request-info :organizationId sipoo-R-org-id)
        :documentRequest)
    => {:enabled true
        :email "a@b.c"
        :instructions {:en "Instructions" :fi "Ohjeet" :sv "Anvisningar"}}))

(fact "organizations bulletin settings"
  (facts "query setting"
    (fact "sipoo"
      (let [result (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id)]
        result => ok?
        (:bulletin-scopes result)  => [{:permitType "R"
                                            :municipality "753"
                                            :bulletins {:enabled true
                                                        :url "http://localhost:8000/dev/julkipano"
                                                        :notification-email "sonja.sibbo@sipoo.fi"
                                                        :descriptions-from-backend-system false}}]))

    (facts "oulu"
      (query oulu :user-organization-bulletin-settings :organizationId oulu-YMP-org-id)
      => (partial expected-failure? :error.bulletins-not-enabled-for-scope))

    (facts "authority"
      (query sonja :user-organization-bulletin-settings :organizationId sipoo-R-org-id) => unauthorized?))

  (facts "update-organization-bulletin-scope"
    (fact "sipoo R"
      (command sipoo :update-organization-bulletin-scope :organizationId sipoo-R-org-id
               :permitType "R"
               :municipality "753"
               :notificationEmail "pena@example.com") => ok?

      (command sipoo :update-organization-bulletin-scope :organizationId sipoo-R-org-id
               :permitType "R"
               :municipality "753"
               :descriptionsFromBackendSystem "foobar") => (partial expected-failure? :error.invalid-value)

      (command sipoo :update-organization-bulletin-scope :organizationId sipoo-R-org-id
               :permitType "R"
               :municipality "753"
               :descriptionsFromBackendSystem true) => ok?

      (->> (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id)
           :bulletin-scopes
           (map :permitType)) => ["R"])

    (fact "sipoo P"
      (command sipoo :update-organization-bulletin-scope :organizationId sipoo-R-org-id
               :permitType "P"
               :municipality "753"
               :notificationEmail "pena@example.com")
      => (partial expected-failure? :error.bulletins-not-enabled-for-scope)

      (->> (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id)
           :bulletin-scopes
           (map :permitType)) => ["R"]))

  (facts "enable organization bulletins"

    (fact "sipoo P - enable"
      (command admin :update-organization
               :permitType "P"
               :municipality "753"
               :bulletinsEnabled true) => ok?

      (fact "is enabled"

        (:bulletin-scopes (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id))
        => [{:permitType "R"
             :municipality "753"
             :bulletins {:enabled true
                         :url "http://localhost:8000/dev/julkipano"
                         :notification-email "pena@example.com"
                         :descriptions-from-backend-system true}}
            {:permitType "P"
             :municipality "753"
             :bulletins {:enabled true}}]

        (command sipoo :update-organization-bulletin-scope :organizationId sipoo-R-org-id
                 :permitType "P"
                 :municipality "753"
                 :notificationEmail "rane@example.com") => ok?))

    (fact "sipoo P - update url"
      (command admin :update-organization
               :permitType "P"
               :municipality "753"
               :bulletinsUrl "http://foo.my.url") => ok?

      (:bulletin-scopes (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id))
      => [{:permitType "R"
           :municipality "753"
           :bulletins {:enabled true
                       :url "http://localhost:8000/dev/julkipano"
                       :notification-email "pena@example.com"
                       :descriptions-from-backend-system true}}
          {:permitType "P"
           :municipality "753"
           :bulletins {:enabled true
                       :url "http://foo.my.url"
                       :notification-email "rane@example.com"}}])

    (fact "sipoo - update texts"
      (command sipoo :upsert-organization-local-bulletins-text :organizationId sipoo-R-org-id
               :lang "fi" :key "heading1" :value "Sipoo") => {:ok true :valid true}
      (command sipoo :upsert-organization-local-bulletins-text :organizationId sipoo-R-org-id
               :lang "fi" :key "heading2" :value "Rak.valv.julkipanolistat") => {:ok true :valid true}
      (command sipoo :upsert-organization-local-bulletins-text :organizationId sipoo-R-org-id
               :lang "fi" :key "caption" :value "Sipoo") => {:ok true :valid false}
      (command sipoo :upsert-organization-local-bulletins-text :organizationId sipoo-R-org-id
               :lang "fi" :key "caption" :index 0 :value "Sipoo123") => {:ok true :valid true}
      (command sipoo :remove-organization-local-bulletins-caption :organizationId sipoo-R-org-id
               :lang "fi" :index 1) => ok?
      (command sipoo :remove-organization-local-bulletins-caption :organizationId sipoo-R-org-id
               :lang "fi" :index 1) => ok?
      (-> (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id)
          :local-bulletins-page-texts :fi)
      => {:heading1 "Sipoo"
          :heading2 "Rak.valv.julkipanolistat"
          :caption ["Sipoo123"]})

    (fact "sipoo P - disable"
      (command admin :update-organization
               :permitType "P"
               :municipality "753"
               :bulletinsEnabled false) => ok?

      (fact "is disabled"
        (command sipoo :update-organization-bulletin-scope :organizationId sipoo-R-org-id
                 :permitType "P"
                 :municipality "753"
                 :notificationEmail "pena@example.com")
        => (partial expected-failure? :error.bulletins-not-enabled-for-scope)

        (->> (query sipoo :user-organization-bulletin-settings :organizationId sipoo-R-org-id)
             :bulletin-scopes
             (map :permitType)) => ["R"]))))

(facts "update-organization-backend-systems"
  (fact "Empty backend systems parameter is not allowed"
    (command admin :update-organization-backend-systems
             :org-id "564-YMP"
             :backend-systems {})
    => (partial expected-failure? :error.empty-map-parameters)))

(facts "known-organizations? tests"
  (mongo/with-db test-db-name
    (fixture/apply-fixture "minimal")
    (facts
      (local-org-api/known-organizations? []) => truthy
      (local-org-api/known-organizations? nil) => truthy)
    (fact
      (local-org-api/known-organizations? ["297-R" "433-R" "069-R"])
      => truthy)
    (fact
      (local-org-api/known-organizations? ["297-R" "433-R" "zap"])
      => falsey)))

(facts "State change messages can be set"
  (let [organization  (first (:organizations (query admin :organizations)))
        id (:id organization)]

    (fact "Messages enabled"
      (command admin "set-organization-boolean-attribute" :enabled true :organizationId id :attribute "state-change-msg-enabled") => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (:state-change-msg-enabled updated-org) => true))

    (fact "Messages disabled"
      (command admin "set-organization-boolean-attribute" :enabled false :organizationId id :attribute "state-change-msg-enabled") => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (:state-change-msg-enabled updated-org) => false))))

(defn pate-enabled-for-permit-type? [organization permit-type]
  (boolean (some->> (:scope organization)
                    (util/find-by-key :permitType permit-type)
                    :pate
                    :enabled)))

(facts "Pate can be enabled on scope level"
  (let [organization  (first (:organizations (query admin :organizations)))
        id (:id organization)]

    (fact "Enabled for R"
      (command admin "set-organization-scope-pate-value" :permitType "R" :municipality "186" :value true) => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (pate-enabled-for-permit-type? updated-org "R") => true
        (pate-enabled-for-permit-type? updated-org "KT") => false))

    (fact "Disabled for R"
      (command admin "set-organization-scope-pate-value" :permitType "R" :municipality "186" :value false) => ok?
      (let [updated-org (:data (query admin "organization-by-id" :organizationId id))]
        (pate-enabled-for-permit-type? updated-org "R") => false))))

(facts "update-organization"
  (let [ya-scope #(first (:scope (util/find-by-id "753-YA" (:organizations (query admin :organizations)))))]
    (fact "Initial YA scope"
      (ya-scope) => {:opening                 nil
                     :municipality            "753"
                     :permitType              "YA"
                     :open-inforequest-email  ""
                     :inforequest-enabled     true
                     :open-inforequest        false
                     :new-application-enabled true})
    (fact "Mandatory params missing"
      (command admin :update-organization :municipality "753" :pateEnabled true) => fail?
      (command admin :update-organization :permitType "YA" :pateEnabled true) => fail?)
    (fact "Enable Pate and invoicing"
      (command admin :update-organization
               :permitType "YA"
               :municipality "753"
               :pateEnabled true
               :pateSftp false
               :invoicingEnabled true)=> ok?
      (ya-scope) => {:opening                 nil
                     :municipality            "753"
                     :permitType              "YA"
                     :open-inforequest-email  ""
                     :inforequest-enabled     true
                     :open-inforequest        false
                     :new-application-enabled true
                     :pate                    {:enabled true
                                               :sftp    false}
                     :invoicing-enabled       true})
    (fact "Only mandatory params: no changes"
      (command admin :update-organization
               :permitType "YA"
               :municipality "753")=> ok?
      (ya-scope) => {:opening                 nil
                     :municipality            "753"
                     :permitType              "YA"
                     :open-inforequest-email  ""
                     :inforequest-enabled     true
                     :open-inforequest        false
                     :new-application-enabled true
                     :pate                    {:enabled true
                                               :sftp    false}
                     :invoicing-enabled       true})
    (facts "Bad params"
      (fact "Unknown param"
        (command admin :update-organization
                 :permitType "YA"
                 :municipality "753"
                 :hiihoo true) => fail?)
      (fact "Bad types"
        (command admin :update-organization
                 :permitType "YA"
                 :municipality "753"
                 :opening "bad") => fail?
        (command admin :update-organization
                 :permitType "YA"
                 :municipality "753"
                 :pateEnabled nil) => fail?
        (command admin :update-organization
                 :permitType "YA"
                 :municipality "753"
                 :openInforequestEmail 0) => fail?))
    (fact "Opening"
      (ya-scope) => {:opening                 nil
                     :municipality            "753"
                     :permitType              "YA"
                     :open-inforequest-email  ""
                     :inforequest-enabled     true
                     :open-inforequest        false
                     :new-application-enabled true
                     :pate                    {:enabled true
                                               :sftp    false}
                     :invoicing-enabled       true}
            (command admin :update-organization
                     :permitType "YA"
                     :municipality "753"
                     :opening 12345)=> ok?
            (ya-scope) => {:opening                 12345
                           :municipality            "753"
                           :permitType              "YA"
                           :open-inforequest-email  ""
                           :inforequest-enabled     true
                           :open-inforequest        false
                           :new-application-enabled true
                           :pate                    {:enabled true
                                                     :sftp    false}
                           :invoicing-enabled       true}
            (command admin :update-organization
                     :permitType "YA"
                     :municipality "753"
                     :pateSftp true
                     :opening nil
                     :bulletinsUrl "https://bul.leti.ns") => ok?
            (ya-scope) => {:opening                 nil
                           :municipality            "753"
                           :permitType              "YA"
                           :open-inforequest-email  ""
                           :inforequest-enabled     true
                           :open-inforequest        false
                           :new-application-enabled true
                           :pate                    {:enabled true
                                                     :sftp    true}
                           :invoicing-enabled       true
                           :bulletins               {:url "https://bul.leti.ns"}})
    (fact "Email can be null"
      (ya-scope) => (contains {:open-inforequest-email ""})
      (command admin :update-organization
                     :permitType "YA"
                     :municipality "753"
                     :openInforequestEmail nil) => ok?
      (ya-scope) => (contains {:open-inforequest-email nil}))))

(defn check-review-pdf-configuration [result]
  (fact {:midje/description (str "Review PDF configuration: " result)}
    (-> (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
        :organization :review-pdf) => result))

(facts "Review rectification configuration"
  (check-review-pdf-configuration nil)
  (fact "Enable rectification"
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :rectification-enabled true) => ok?
    (check-review-pdf-configuration {:rectification-enabled true}))
  (fact "Add info markup"
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :rectification-info "This _is_ info.") => ok?
    (check-review-pdf-configuration {:rectification-enabled true
                       :rectification-info  "This _is_ info."}))
  (fact "Add contact"
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :contact "Zhe shi wode mingpian") => ok?
    (check-review-pdf-configuration {:rectification-enabled true
                       :rectification-info  "This _is_ info."
                       :contact "Zhe shi wode mingpian"}))
  (fact "Update all three fields"
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :rectification-enabled false
             :rectification-info "Bye bye information!"
             :contact "Mingtian jian!") => ok?
    (check-review-pdf-configuration {:rectification-enabled false
                       :rectification-info  "Bye bye information!"
                       :contact "Mingtian jian!"}))


  (fact "Only organizationId parameter is mandatory"
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id) => ok?
    (check-review-pdf-configuration {:rectification-enabled false
                       :rectification-info  "Bye bye information!"
                       :contact "Mingtian jian!"}))

  (fact "Input validators"
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :bad "bad") => fail?
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :rectification-enabled "true") => fail?
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :rectification-info false) => fail?
    (command sipoo :update-review-pdf-configuration :organizationId sipoo-R-org-id
             :contact 8) => fail?
    (check-review-pdf-configuration {:rectification-enabled false
                       :rectification-info  "Bye bye information!"
                       :contact "Mingtian jian!"})))
