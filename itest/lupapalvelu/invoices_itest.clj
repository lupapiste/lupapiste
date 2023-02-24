(ns lupapalvelu.invoices-itest
  (:require [clojurewerkz.money.currencies :refer [EUR]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.invoices :refer [validate-invoice] :as invoices]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.money :as money]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer [err]]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(def runeberg (date/timestamp "5.2.2020"))
(def kalevala (date/timestamp "28.2.2020"))
(def good     (date/timestamp "10.4.2020"))
(def mayday   (date/timestamp "1.5.2020"))

(defn add-organization [id invoicing-enabled?]
  (mongo/insert :organizations {:id    id
                                :scope [{:permitType        "R"
                                         :invoicing-enabled invoicing-enabled?}]}))

(defn auth-user
  "Adds auth authz for the user in orgs."
  [auth apikey & org-ids]
  (-> (util/find-first (util/fn-> :private :apikey (= apikey)) minimal/users)
      (update :orgAuthz (fn [authz]
                          (reduce (fn [acc k]
                                    (update acc (keyword k) conj auth))
                                  authz
                                  org-ids)))
      usr/with-org-auth))

(def biller-user (partial auth-user "biller"))

(defn dummy-submitted-application [apikey]
  (create-and-submit-local-application
    apikey
    :operation "pientalo"
    :x "385770.46" :y "6672188.964"
    :address "Kaivokatu 1"))

(defn new-invoice
  "Create new invoice draft and initialize it with`invoice`. Returns invoice id."
  ([app-id invoice]
   (let [invoice-id (some-> (local-command sonja :new-invoice-draft :id app-id)
                            :invoice :id)]
     (fact {:midje/description (format "New invoice %s for application %s"
                                       invoice-id app-id)}
       invoice-id => truthy
       (when invoice
         (local-command sonja :update-invoice :id app-id
                        :invoice (assoc invoice :id invoice-id)) => ok?))
     invoice-id))
  ([app-id]
   (new-invoice app-id nil)))

(defn invoice->confirmed [draft-invoice]
  (let [{app-id :id}   (dummy-submitted-application pena)
        new-invoice-id (new-invoice app-id draft-invoice)
        new-invoice    (:invoice (local-query sonja :fetch-invoice :id app-id :invoice-id new-invoice-id))]
    (local-command sonja :update-invoice :id app-id :invoice (assoc new-invoice  :state "checked"))
    (local-command sonja :update-invoice :id app-id :invoice (assoc new-invoice  :state "confirmed"))))

(defn invoice->checked [draft-invoice app-id]
  (let [new-invoice-id (new-invoice app-id draft-invoice)
        new-invoice    (:invoice (local-query sonja :fetch-invoice :id app-id :invoice-id new-invoice-id))]
    (local-command sonja :update-invoice :id app-id :invoice (assoc new-invoice  :state "checked"))
    new-invoice-id))

(defn toggle-invoicing
  ([flag permit-type]
   (local-command admin :update-organization
                  :invoicingEnabled flag
                  :municipality "753"
                  :permitType permit-type))
  ([flag]
   (toggle-invoicing flag "R")))

(def invoicing-disabled? (err :error.invoicing-disabled))
(def no-organizations? (err :error.no-organizations))

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :toiminto         "f"
                              :muu-tunniste     "g"})

(env/with-feature-value :invoices true
  (mount/start #'mongo/connection)

  (mongo/with-db test-db-name
    (fixture/apply-fixture "minimal")

    (fact "Invoicing not enabled for 753-R"
      (local-query laura :user-organizations-invoices) => invoicing-disabled?
      (local-query sipoo :organization-price-catalogues
                   :organizationId "753-R") => invoicing-disabled?
      (local-query laura :organizations-transferbatches :organizationId "753-R") => invoicing-disabled?)

    (facts "New invoice"
      (facts "should add an invoice to the db with with all the required fields"
        (let [{app-id :id :as app} (dummy-submitted-application pena)
              maksaja-doc-id       (:id (first (domain/get-documents-by-subtype (:documents app) :maksaja)))
              invoice              {:operations [{:operation-id (get-in app [:primaryOperation :id])
                                                  :name         "linjasaneeraus"
                                                  :invoice-rows [{:text              "Row 1 kpl"
                                                                  :code              "111"
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "kpl"
                                                                  :price-per-unit    10
                                                                  :units             2
                                                                  :discount-percent  0
                                                                  :product-constants dummy-product-constants}
                                                                 {:text              "Row 2 m2"
                                                                  :code              "222"
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "m2"
                                                                  :price-per-unit    20.5
                                                                  :units             15.8
                                                                  :discount-percent  50
                                                                  :product-constants dummy-product-constants}
                                                                 {:text             "Custom row m3"
                                                                  :code             "333"
                                                                  :type             "custom"
                                                                  :unit             "m3"
                                                                  :price-per-unit   20.5
                                                                  :units            15.8
                                                                  :discount-percent 100}]}]}
              _                    (fact "Invoicing not yet enabled"
                                     (local-query luukas :invoices-tab :id app-id)
                                     => invoicing-disabled?
                                     (local-command sonja :new-invoice-draft
                                                    :id app-id) => invoicing-disabled?)
              _                    (fact "Enable invoicing for 753-R"
                                     (toggle-invoicing true) => ok?)

              invoice-id           (new-invoice app-id invoice)
              invoice-in-db        (mongo/by-id "invoices" invoice-id)]

          (fact "valid invoice-in-db"
            (validate-invoice invoice-in-db))

          (fact "No price-catalogue-id"
            (let [history (first (:history invoice-in-db))]
              (:price-catalogue-id invoice-in-db) => nil
              (:price-catalogue-id invoice) => nil
              (:action history) => "create"
              (:state history) => "draft"))

          (facts "If price-catalogue-id is given it must refer to a published catalogue"
            (let [catalog (:draft (local-command sipoo :new-price-catalogue-draft
                                                 :organizationId "753-R"))]
              catalog => truthy
              (fact "No such catalogue"
                (local-command sonja :new-invoice-draft :id app-id
                               :price-catalogue-id "bad-id")
                => (err :error.price-catalogue.not-found))
              (fact "Catalogue is draft"
                (local-command sonja :new-invoice-draft :id app-id
                               :price-catalogue-id (:id catalog))
                => (err :error.price-catalogue.wrong-state))
              (fact "Catalogue is published: success"
                (local-command sipoo :edit-price-catalogue-draft
                               :organizationId "753-R"
                               :price-catalogue-id (:id catalog)
                               :edit {:delete-row (-> catalog :rows first :id)}) => ok?
                (local-command sipoo :publish-price-catalogue
                               :organizationId "753-R"
                               :price-catalogue-id (:id catalog)) => ok?
                (-> (local-command sonja :new-invoice-draft :id app-id
                                   :price-catalogue-id (:id catalog))
                    :invoice :price-catalogue-id)
                => (:id catalog))))

          (fact "with billing info from application when payer is a person"
            (let [updates [["henkilo.henkilotiedot.etunimi" "Pena"]
                           ["henkilo.henkilotiedot.sukunimi" "Panaani"]
                           ["henkilo.henkilotiedot.hetu" "010203-040A"]
                           ["henkilo.osoite.katu" "Paapankuja 12"]
                           ["henkilo.osoite.postinumero" "10203"]
                           ["henkilo.osoite.postitoimipaikannimi" "Piippola"]
                           ["laskuviite" "laskuviite-pena"]]]
              (update-document! pena app-id maksaja-doc-id updates true))
            (let [invoice-id (new-invoice app-id invoice)]
              (mongo/by-id "invoices" invoice-id) => (contains {:entity-name       "Pena Panaani"
                                                                :person-id         "010203-040A"
                                                                :entity-address    "Paapankuja 12 10203 Piippola"
                                                                :billing-reference "laskuviite-pena"})))

          (fact "with billing info from application when payer is a company"
            (let [updates [["_selected" "yritys"]
                           ["yritys.yritysnimi" "Feikki Oy"]
                           ["yritys.liikeJaYhteisoTunnus" "0813000-2"]
                           ["yritys.osoite.katu" "Lumekatu 5"]
                           ["yritys.osoite.postinumero" "12345"]
                           ["yritys.osoite.postitoimipaikannimi" "Huijala"]
                           ["yritys.verkkolaskutustieto.ovtTunnus" "003708130002"]
                           ["laskuviite" "laskuviite-pena-yritys"]
                           ["yritys.yhteyshenkilo.henkilotiedot.etunimi" "Simo"]
                           ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Simonen"]]]
              (update-document! pena app-id maksaja-doc-id updates true))
            (let [invoice-id    (new-invoice app-id invoice)
                  invoice-in-db (mongo/by-id "invoices" invoice-id)]
              invoice-in-db => (contains {:entity-name            "Feikki Oy"
                                          :company-id             "0813000-2"
                                          :entity-address         "Lumekatu 5 12345 Huijala"
                                          :billing-reference      "laskuviite-pena-yritys"
                                          :ovt                    "003708130002"
                                          :company-contact-person "Simo Simonen"})))

          (facts "Invoice worktime"
            (fact "Worktime cannot be set for non-YA application invoices"
              (let [invoice-id (new-invoice app-id)]
                (local-command sonja :update-invoice-worktime :id app-id
                               :invoice-id invoice-id
                               :start-ts runeberg
                               :end-ts kalevala) => {:ok          false
                                                     :permit-type ["YA"]
                                                     :text        "error.invalid-permit-type"}))
            (fact "Worktime is enriched with application `started` and `closed` timestamps"
              (toggle-invoicing true "YA") => ok?
              (let [{ya-app-id :id} (create-and-submit-local-application pena
                                                                         :operation "ya-katulupa-vesi-ja-viemarityot"
                                                                         :propertyId sipoo-property-id)
                    invoice-id      (new-invoice ya-app-id)]
                (mongo/update-by-id :applications ya-app-id {$set {:started runeberg
                                                                   :closed  kalevala}})
                (:invoice (local-query sonja :fetch-invoice :id ya-app-id :invoice-id invoice-id))
                => (contains {:id            invoice-id
                              :work-start-ms runeberg
                              :work-end-ms   kalevala
                              :workdays      {:billable-days 24 :days 24 :free-days 0}})

                (fact "Update invoice worktime"
                  (:workdays (local-command sonja :update-invoice-worktime :id ya-app-id
                                            :invoice-id invoice-id
                                            :start-ts good
                                            :end-ts mayday))
                  => {:billable-days 22 :days 22 :free-days 0}))))

          (fact "Disable invoicing"
            (toggle-invoicing false) => ok?
            (local-command sonja :new-invoice-draft
                           :id app-id) => invoicing-disabled?)

          (fact "Enable invoicing"
            (toggle-invoicing true) => ok?)))

      (fact "reject update where one of the invoice-rows in the request has an unknown unit"
        (let [{app-id :id :as app} (dummy-submitted-application pena)
              invoice              {:operations [{:operation-id (get-in app [:primaryOperation :id])
                                                  :name         "linjasaneeraus"
                                                  :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                  :code              "111"
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "kpl"
                                                                  :price-per-unit    10
                                                                  :units             2
                                                                  :discount-percent  0
                                                                  :product-constants dummy-product-constants}
                                                                 {:text              "Laskurivi2 m3 "
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "UNKNOWN-UNIT"
                                                                  :price-per-unit    20.5
                                                                  :units             15.8
                                                                  :discount-percent  0
                                                                  :product-constants dummy-product-constants}]}]}
              invoice-id           (new-invoice app-id)]
          (local-command sonja :update-invoice :id app-id :invoice (assoc invoice :id invoice-id))
          => fail?))

      (fact "reject update where one of the invoice-rows has a price-per-unit now within allowed limits"
        (let [{app-id :id :as app} (dummy-submitted-application pena)
              invoice              {:operations [{:operation-id (get-in app [:primaryOperation :id])
                                                  :name         "linjasaneeraus"
                                                  :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                  :code              "111"
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "kpl"
                                                                  :price-per-unit    10
                                                                  :units             2
                                                                  :discount-percent  0
                                                                  :product-constants dummy-product-constants}
                                                                 {:text              "Laskurivi2 m3 "
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "m3"
                                                                  :min-unit-price    5
                                                                  :max-unit-price    20
                                                                  :price-per-unit    1 ;; Lower than min-unit-price
                                                                  :units             15.8
                                                                  :discount-percent  0
                                                                  :product-constants dummy-product-constants}]}]}
              invoice-id           (new-invoice app-id)]
          (local-command sonja :update-invoice :id app-id :invoice (assoc invoice :id invoice-id))
          => {:ok false :text "error.unit-price-not-within-allowed-limits"})))

    (fact "delete-invoice command"
      (fact "should delete invoice with state draft"
        (let [{app-id :id} (dummy-submitted-application pena)
              invoice-id   (new-invoice app-id)]
          (mongo/by-id "invoices" invoice-id) => truthy
          (local-command sonja :delete-invoice :id app-id :invoice-id invoice-id) => ok?
          (mongo/by-id "invoices" invoice-id) => nil))

      (fact "should NOT delete invoice with state checked"
        (let [{app-id :id :as app} (dummy-submitted-application pena)
              invoice              {:operations [{:operation-id (get-in app [:primaryOperation :id])
                                                  :name         "linjasaneeraus"
                                                  :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                  :code              "111"
                                                                  :type              "from-price-catalogue"
                                                                  :unit              "kpl"
                                                                  :price-per-unit    10
                                                                  :units             2
                                                                  :discount-percent  0
                                                                  :product-constants dummy-product-constants}]}]}
              invoice-id           (invoice->checked invoice app-id)
              invoice-from-db      (mongo/by-id "invoices" invoice-id)]
          (:state invoice-from-db) => "checked"
          (local-command sonja :delete-invoice :id app-id :invoice-id invoice-id) => fail?
          (mongo/by-id "invoices" invoice-id) => invoice-from-db))

      (fact "should NOT delete invoice when application id is not valid"
        (let [{app-id :id}    (dummy-submitted-application pena)
              invoice-id      (new-invoice app-id)
              invoice-from-db (mongo/by-id "invoices" invoice-id)]
          (local-command sonja :delete-invoice :id "foo-id" :invoice-id invoice-id) => fail?
          (mongo/by-id "invoices" invoice-id) => invoice-from-db))

      (fact "should NOT delete invoice when application id is not the application id for invoice"
        (let [{app-id :id}    (dummy-submitted-application pena)
              {app-id-2 :id}  (dummy-submitted-application pena)
              invoice-id      (new-invoice app-id)
              invoice-from-db (mongo/by-id "invoices" invoice-id)]
          (local-command sonja :delete-invoice :id app-id-2 :invoice-id invoice-id) => fail?
          (mongo/by-id "invoices" invoice-id) => invoice-from-db)))

    (fact "update-invoice command with the role authority should"

      (let [{app-id :id :as app}  (dummy-submitted-application pena)
            invoice               {:operations [{:operation-id (get-in app [:primaryOperation :id])
                                                 :name         "linjasaneeraus"
                                                 :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                 :code              "111"
                                                                 :type              "from-price-catalogue"
                                                                 :unit              "kpl"
                                                                 :price-per-unit    10
                                                                 :units             2
                                                                 :discount-percent  0
                                                                 :product-constants dummy-product-constants}]}]}
            invoice-id            (new-invoice  app-id invoice)
            new-data              {:id         invoice-id
                                   :operations [{:operation-id (get-in app [:primaryOperation :id])
                                                 :name         "sisatila-muutos"
                                                 :invoice-rows [{:text             "Laskurivi1 m2"
                                                                 :type             "custom"
                                                                 :unit             "m2"
                                                                 :price-per-unit   5
                                                                 :units            10
                                                                 :discount-percent 10
                                                                 :sums             {:with-discount    {:currency "EUR"
                                                                                                       :major    45
                                                                                                       :minor    4500
                                                                                                       :text     (money/->currency-text EUR 45.00)}
                                                                                    :without-discount {:currency "EUR"
                                                                                                       :major    50
                                                                                                       :minor    5000
                                                                                                       :text     (money/->currency-text EUR 50.00)}}}]}]}
            update-invoice-result (local-command sonja :update-invoice :id app-id :invoice new-data)
            updated-invoice       (mongo/by-id "invoices" invoice-id)
            history               (:history updated-invoice)
            first-entry           (first history)
            second-entry          (second history)
            third-entry           (nth history 2)]

        (fact "update the operations of an existing invoice"
          update-invoice-result => ok?
          (:operations updated-invoice) => (:operations new-data)
          (count history) => 3
          (:action first-entry) => "create"
          (:action second-entry) => "update"
          (:action third-entry) => "update"))

      (let [{app-id :id :as app} (dummy-submitted-application pena)
            invoice              {:operations [{:operation-id (get-in app [:primaryOperation :id])
                                                :name         "linjasaneeraus"
                                                :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                :code              "123"
                                                                :type              "from-price-catalogue"
                                                                :unit              "kpl"
                                                                :price-per-unit    10
                                                                :units             2
                                                                :discount-percent  0
                                                                :product-constants dummy-product-constants}]}]}
            invoice-id           (new-invoice app-id invoice)]

        (facts "add/update price-catalogue-id if present in the request"
          (fact "Missing catalogue ids are allowed"
            (local-command sonja :update-invoice
                           :id app-id
                           :invoice {:id                 invoice-id
                                     :price-catalogue-id "bad-catalogue-id"})
            => ok?)
          (fact "However, if catalogue is found its organization-id must match the application"
            (mongo/insert :price-catalogues {:id              "vantaa-catalogue-id"
                                             :organization-id "092-R"})
            (local-command sonja :update-invoice
                           :id app-id
                           :invoice {:id                 invoice-id
                                     :price-catalogue-id "vantaa-catalogue-id"})
            => (err :error.price-catalogue.not-available))
          (fact "Good, existing catalogue"
            (mongo/insert :price-catalogues {:id              "good-catalogue-id"
                                             :organization-id "753-R"
                                             :state           "published"
                                             :valid-from      mayday})
            (local-command sonja :update-invoice
                           :id app-id
                           :invoice {:id                 invoice-id
                                     :price-catalogue-id "good-catalogue-id"}) => ok?
            (:price-catalogue-id (mongo/by-id "invoices" invoice-id)) => "good-catalogue-id"))

        (fact "update the state from draft to checked"
          (local-command sonja :update-invoice
                         :id app-id
                         :invoice {:id    invoice-id
                                   :state "checked"}) => ok?
          (:state (mongo/by-id "invoices" invoice-id)) => "checked")

        (fact "update the state from checked to draft"
          (local-command sonja :update-invoice
                         :id app-id
                         :invoice {:id    invoice-id
                                   :state "draft"}) => ok?
          (:state (mongo/by-id "invoices" invoice-id)) => "draft")

        (fact "reject update when it contains an invoice row where price-per-unit not within allowed range"
          (local-command sonja :update-invoice
                         :id app-id
                         :invoice {:operations [{:operation-id "linjasaneeraus"
                                                 :name         "linjasaneeraus"
                                                 :invoice-rows [{:text              "Laskurivi1 kpl"
                                                                 :code              "123"
                                                                 :type              "from-price-catalogue"
                                                                 :unit              "kpl"
                                                                 :min-unit-price    1
                                                                 :max-unit-price    5
                                                                 :price-per-unit    10 ;; Higher than max-unit-price
                                                                 :units             2
                                                                 :discount-percent  0
                                                                 :product-constants dummy-product-constants}]}]})
          => {:ok false :text "error.unit-price-not-within-allowed-limits"})

        (facts "Backend id"
          (facts "Configuration"
            (fact "Backend-id not enabled"
              (local-query sipoo :invoicing-config :organizationId "753-R")
              => {:ok true})
            (fact "Backend-id cannot be configured"
              (local-command sipoo :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:set {:numbers 8}})
              => (err :error.backend-id-not-enabled))
            (fact "Backend code cannot be added to the invoice"
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id invoice-id
                             :code "foo")
              => (err :error.backend-id-not-enabled))
            (fact "Configure invoicing and enable backend-id"
              (local-command admin :update-invoicing-config
                             :org-id "753-R"
                             :invoicing-config {:download?   true
                                                :local-sftp? false
                                                :backend-id? true}) => ok?)
            (fact "Now backend-id can be configured"
              (local-command sipoo :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:set {:numbers 8}}) => ok?)
            (fact "Numbers must be integers in range [1, 10] inclusive"
              (doseq [n [-1 "foo" 0 4.56 11]]
                (fact {:midje/description n}
                  (local-command sipoo :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:set {:numbers n}})
                  => schema-error?)))
            (fact "Upsert: Code and text must be given"
              (doseq [upsert [{} nil {:code "foo"} {:text "bar"}
                              {:code 1 :text 2}  {:code "" :text ""}
                              {:code "Foo" :text "bar" :bad 1}]]
                (local-command sipoo :configure-invoicing-backend-id
                               :organizationId "753-R"
                               :op {:upsert upsert})
                => schema-error?))
            (fact "Upsert: Initial addition of code"
              (let [cfg    (:config (local-command sipoo :configure-invoicing-backend-id
                                                   :organizationId "753-R"
                                                   :op {:upsert {:code " foo "
                                                                 :text " Hello world! "}}))
                    foo-id (-> cfg :codes first :id)]
                foo-id => truthy
                cfg => {:numbers 8
                        :codes   [{:code "foo" :text "Hello world!" :id foo-id}]}
                (fact "Add another code"
                  (local-command sipoo :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:upsert {:code " bar "
                                               :text " Ni hao! "}}) => ok?)
                (fact "Id must match if given"
                  (local-command sipoo :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:upsert {:code "dum"
                                               :id   (mongo/create-id)
                                               :text "Bonjour!"}})
                  => (err :error.id-not-found)
                  (:config (local-command sipoo :configure-invoicing-backend-id
                                          :organizationId "753-R"
                                          :op {:upsert {:code " dum "
                                                        :id   foo-id
                                                        :text " Bonjour! "}}))
                  => (just {:numbers 8
                            :codes   (just {:code "dum"
                                            :id   foo-id
                                            :text "Bonjour!"}
                                           (just {:id   truthy
                                                  :code "bar"
                                                  :text "Ni hao!"}))}))
                (fact "Code must be unique"
                  (local-command sipoo :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:upsert {:code " bar "
                                               :id   foo-id
                                               :text " Reserved! "}})
                  => (err :error.code-reserved)
                  (local-command sipoo :configure-invoicing-backend-id
                                 :organizationId "753-R"
                                 :op {:upsert {:code " dum      "
                                               :text " Reserved! "}})
                  => (err :error.code-reserved))
                (fact "Delete: code id must exist"
                  (doseq [id ["bad-id" nil "  " ""]]
                    (local-command sipoo :configure-invoicing-backend-id
                                   :organizationId "753-R"
                                   :op {:delete {:id id}})
                    => schema-error?
                    (fact "If code is not found, delete just returns the current config"
                      (:config (local-command sipoo :configure-invoicing-backend-id
                                              :organizationId "753-R"
                                              :op {:delete {:id (mongo/create-id)}}))
                      => (just {:numbers 8
                                :codes   (just {:code "dum"
                                                :id   foo-id
                                                :text "Bonjour!"}
                                               (just {:id   truthy
                                                      :code "bar"
                                                      :text "Ni hao!"}))}))))
                (fact "Delete: success"
                  (:config (local-command sipoo :configure-invoicing-backend-id
                                  :organizationId "753-R"
                                  :op {:delete {:id foo-id}}))
                  => (just {:numbers 8
                            :codes   (just [(just {:id   truthy
                                                   :code "bar"
                                                   :text "Ni hao!"})])})))))
          (facts "Set backend code to invoice"
            (fact "Code must exist"
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id invoice-id
                             :code "bad")
              => (err :error.unknown-backend-code))
            (fact "Application must exist"
              (local-command sonja :set-invoice-backend-code
                             :id "bad-id"
                             :invoice-id invoice-id
                             :code "bad")
              => fail?)
            (fact "Invoice must exist"
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id "bad-id"
                             :code "bad")
              => fail?)
            (fact "Success"
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id invoice-id
                             :code "bar")
              => ok?
              (mongo/by-id :invoices invoice-id [:backend-code])
              => {:id           invoice-id
                  :backend-code "bar"})
            (fact "Code can be cleared"
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id invoice-id
                             :code "  ")
              => ok?
              (mongo/by-id :invoices invoice-id [:backend-code])
              => {:id invoice-id})
            (fact "Code cannot be set, if backend id has been set"
              (mongo/update-by-id :invoices invoice-id {$set {:backend-id "XY000123"}})
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id invoice-id
                             :code "bar")
              => (err :error.backend-id-already-defined)))
          (facts "Nothing works if invoicing is not enabled"
            (local-command admin :update-organization
                         :invoicingEnabled false
                         :municipality "753"
                         :permitType "R") => ok?
            (fact "Backend-id cannot be configured"
              (local-command sipoo :configure-invoicing-backend-id
                             :organizationId "753-R"
                             :op {:set {:numbers 8}})
              => invoicing-disabled?)
            (fact "Backend code cannot be added to the invoice"
              (local-command sonja :set-invoice-backend-code
                             :id app-id
                             :invoice-id invoice-id
                             :code "bar")
              => invoicing-disabled?)
            )
          (fact "Enable invoicing and set backend code"
            (local-command admin :update-organization
                           :invoicingEnabled true
                           :municipality "753"
                           :permitType "R")
            => ok?
            (let [{app-id2 :id} (dummy-submitted-application pena)
                  invoice-id2   (new-invoice app-id2 invoice)]
              (fact "Invoice must match the application"
                (local-command sonja :set-invoice-backend-code
                              :id app-id
                              :invoice-id invoice-id2
                              :code "bar")
                => (err :error.not-found))
              (fact "Available codes"
                (:codes (local-query laura :invoicing-backend-id-codes
                                     :id app-id))
                => [{:code "bar" :text "Ni hao!" }])
              (fact "Set backend code successfully"
                (local-command sonja :set-invoice-backend-code
                              :id app-id2
                              :invoice-id invoice-id2
                              :code "bar")
                => ok?
                (mongo/any? :invoices {:_id invoice-id2 :backend-code "bar"})
                => true))))

        (fact "Enable invoicing in Helsinki (091-R)"
          (local-command admin :update-organization
                         :invoicingEnabled true
                         :municipality "091"
                         :permitType "YA") => ok?)

        (fact "Application for Helsinki"
          (let [{hki-app-id :id} (create-and-submit-local-application mikko
                                                                      :operation "ya-kayttolupa-terassit"
                                                                      :propertyId "09141600550007"
                                                                      :address "Fleminginkatu 1")]
            hki-app-id => truthy
            (fact "Helsinki authority cannot update Sipoo invoice"
              (local-command raktark-helsinki :update-invoice :id hki-app-id
                             :invoice {:id          invoice-id
                                       :description "Greetings from Helsinki"})
              => (err :error.unauthorized))

            (fact "Helsinki authority cannot access Sipoo invoice"
              (local-query raktark-helsinki :fetch-invoice :id hki-app-id
                           :invoice-id invoice-id)
              => (err :error.unauthorized))

            (fact "Helsinki authority cannot update Sipoo invoice worktimes"
              (local-command raktark-helsinki :update-invoice-worktime :id hki-app-id
                             :invoice-id invoice-id
                             :start-ts kalevala
                             :end-ts good)
              => (err :error.unauthorized))))


        (fact "Invoice application-id and organization-id cannot be modified"
          (local-command sonja :update-invoice :id app-id
                         :invoice {:id              invoice-id
                                   :application-id  "LP-BAD"
                                   :organization-id "BAD-R"
                                   :description     "Hello"}) => ok?
          (:invoice (local-query sonja :fetch-invoice :id app-id :invoice-id invoice-id))
          => (contains {:organization-id "753-R"
                        :application-id  app-id
                        :description     "Hello"}))))

    (fact "application-invoices query"
      (fact "should return an empty collection of invoices when none found for the application"
        (let [{app-id :id} (dummy-submitted-application pena)]
          (-> (local-query sonja :application-invoices :id app-id)
              :invoices
              count) => 0))

      (fact "should fetch all application invoices"
        (let [{app-a-id :id} (dummy-submitted-application pena)
              {app-b-id :id} (dummy-submitted-application pena)]
          (new-invoice app-a-id) => truthy
          (new-invoice app-a-id) => truthy
          (new-invoice app-b-id) => truthy
          (new-invoice app-b-id) => truthy
          (new-invoice app-b-id) => truthy

          (-> (local-query sonja :application-invoices :id app-a-id)
              :invoices
              count) => 2
          (-> (local-query sonja :application-invoices :id app-b-id)
              :invoices
              count) => 3)))

    (fact "fetch-invoice query"
      (fact "should return invoice when invoice is inserted"
        (let [{app-a-id :id}                (dummy-submitted-application pena)
              inserted-invoice-id           (new-invoice app-a-id)
              {invoice-from-query :invoice} (local-query sonja :fetch-invoice :id app-a-id :invoice-id inserted-invoice-id)]
          (:application-id invoice-from-query) => app-a-id)))

    (fact "application-operations-query"
      (fact "should return vector containing primary operation"
        (let [{app-id :id} (dummy-submitted-application pena)
              operations   (:operations (local-query sonja :application-operations :id app-id))]
          (count operations) => 1
          (-> operations first :name) => "pientalo")))

    (fact "user-organizations-invoices"
      (fact "Invoicing not enabled in any of the user organizations"
        (local-query olli :user-organizations-invoices) => invoicing-disabled?)

      (fact "Invoicing not enabled for the given organization"
        (local-query raktark-jarvenpaa :user-organizations-invoices
                     :organization-id "186-R") => invoicing-disabled?)

      (fact "Invoicing enabled but the user is not biller"
        (local-query sonja :user-organizations-invoices) => no-organizations?)

      (fact "should return invoices for orgs user has the role in"
        (doseq [org-id ["USER-ORG-1" "USER-ORG-2" "FOO-ORG" "BAR-ORG"]
                :let   [{app-id :id} (dummy-submitted-application sonja)
                        invoice-id (new-invoice app-id)]]
          (add-organization org-id true)
          (mongo/update-by-id :invoices invoice-id {$set {:organization-id org-id}}))

        (let [invoices     (:invoices (local-query sonja :user-organizations-invoices))
              invoice-orgs (set (map :organization-id invoices))]
          (count invoices) => 2
          invoice-orgs => #{"USER-ORG-1" "USER-ORG-2"})
        (against-background
          (find-user-from-minimal-by-apikey sonja) => (biller-user sonja :USER-ORG-1 :USER-ORG-2)))


      (fact "with filters"

        (fact "should fail given invalid states value"
          (local-query sonja :user-organizations-invoices :states ["I_AM_NOT_A_VALID_STATE"])
          => {:ok false :text "error.invalid-request"})

        (fact "should fail given invalid from value"
          (local-query sonja :user-organizations-invoices :from "I-AM-NOT-A-TIMESTAMP")
          => {:ok false :text "error.invalid-request"})

        (fact "should fail given invalid limit value"
          (local-query sonja :user-organizations-invoices :limit "I-AM-NOT-AN-INTEGER")
          => {:ok false :text "error.invalid-request"})

        (fact "should return invoices that are in the given state"
          (add-organization "ORG-FOR-STATE-TEST" true)
          (let [org-id       "ORG-FOR-STATE-TEST"
                {app-id :id} (dummy-submitted-application sonja)]
            (new-invoice app-id {:state "checked"})
            (new-invoice app-id)
            (mongo/update-by-id :applications app-id {$set {:organization org-id}})
            (mongo/update-by-query :invoices {:application-id app-id} {$set {:organization-id org-id}})

            (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                   :states ["checked"]))]
              (count invoices) => 1
              (map :state invoices) => ["checked"]))
          (against-background (find-user-from-minimal-by-apikey sonja)
                              => (biller-user sonja "ORG-FOR-STATE-TEST")))

        (fact "should return invoices created between from and until"
          (add-organization "ORG-FOR-FROM-UNTIL-TEST" true)
          (against-background (find-user-from-minimal-by-apikey sonja)
                              => (biller-user sonja "ORG-FOR-FROM-UNTIL-TEST"))
          (doseq [date ["1.1.2000" "1.2.2000" "1.3.2000" "1.4.2000"]
                  :let [updates  {$set {:state   "checked" :organization-id "ORG-FOR-FROM-UNTIL-TEST"
                                        :created (date/timestamp date)}}
                        {app-id :id} (dummy-submitted-application sonja)
                        invoice-id (new-invoice app-id)]]
            (mongo/update-by-id :invoices invoice-id updates))

          (fact "until is not given and"

            (fact "from matches exactly a created timestamp in the db. Matching invoice should be included."
              (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                     :from (date/timestamp "1.2.2000")))]
                (count invoices) => 3
                (set (map :created invoices)) => #{(date/timestamp "1.2.2000")
                                                   (date/timestamp "1.3.2000")
                                                   (date/timestamp "1.4.2000")}))

            (fact "from is between created timestamps of invoices"
              (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                     :from (date/timestamp "10.2.2000")))]
                (count invoices) => 2
                (set (map :created invoices)) => #{(date/timestamp "1.3.2000")
                                                   (date/timestamp "1.4.2000")})))
          (fact "from is not given and"

            (fact "until matches exactly a created timestamp in the db. Matching invoice should be included."
              (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                     :until (date/timestamp "1.2.2000")))]
                (count invoices) => 2
                (set (map :created invoices)) => #{(date/timestamp "1.1.2000")
                                                   (date/timestamp "1.2.2000")}))

            (fact "from is between created timestamps of invoices"
              (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                     :until (date/timestamp "1.8.2000")))]
                (count invoices) => 4
                (set (map :created invoices)) => #{(date/timestamp "1.1.2000")
                                                   (date/timestamp "1.2.2000")
                                                   (date/timestamp "1.3.2000")
                                                   (date/timestamp "1.4.2000")})))

          (fact "when from, until and limit are strings representing the numbers"
            (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                   :from  (str (date/timestamp "1.2.2000"))
                                                   :until (str (date/timestamp "1.4.2000"))
                                                   :limit "2"))]
              (count invoices) => 2
              (set (map :created invoices)) => #{(date/timestamp "1.4.2000")
                                                 (date/timestamp "1.3.2000")})))

        (fact "should return invoices sorted by :created newest first"
          (add-organization "ORG-FOR-SORTING-TEST" true)
          (against-background (find-user-from-minimal-by-apikey sonja)
                              => (biller-user sonja "ORG-FOR-SORTING-TEST"))
          (doseq [date ["1.1.2000" "1.2.2000" "1.3.2000" "1.4.2000"]
                  :let [updates  {$set {:state   "checked" :organization-id "ORG-FOR-SORTING-TEST"
                                        :created (date/timestamp date)}}
                        {app-id :id} (dummy-submitted-application sonja)
                        invoice-id (new-invoice app-id)]]
            (mongo/update-by-id :invoices invoice-id updates))

          (let [invoices (:invoices (local-query sonja :user-organizations-invoices))]
            (count invoices) => 4
            (map :created invoices) => [(date/timestamp "1.4.2000")
                                        (date/timestamp "1.3.2000")
                                        (date/timestamp "1.2.2000")
                                        (date/timestamp "1.1.2000")]))

        (fact "should limit the number of result based on the limit parameter"
          (add-organization "ORG-FOR-LIMIT-TEST" true)
          (against-background (find-user-from-minimal-by-apikey sonja)
                              => (biller-user sonja "ORG-FOR-LIMIT-TEST"))
          (doseq [date ["1.1.2000" "1.2.2000" "1.3.2000" "1.4.2000"]
                  :let [updates  {$set {:state   "checked" :organization-id "ORG-FOR-LIMIT-TEST"
                                        :created (date/timestamp date)}}
                        {app-id :id} (dummy-submitted-application sonja)
                        invoice-id (new-invoice app-id)]]
            (mongo/update-by-id :invoices invoice-id updates))

          (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                 :limit 2))]
            (count invoices) => 2
            (map :created invoices) => [(date/timestamp "1.4.2000")
                                        (date/timestamp "1.3.2000")]))

        (fact "should return 3 the next oldest invoices from the given time (ordered newest first)"
          (add-organization "ORG-FOR-X-OLDER-TEST" true)
          (against-background (find-user-from-minimal-by-apikey sonja)
                              => (biller-user sonja "ORG-FOR-X-OLDER-TEST"))
          (doseq [date ["1.1.2000" "1.2.2000" "1.3.2000" "1.4.2000" "1.5.2000"]
                  :let [updates  {$set {:state   "checked" :organization-id "ORG-FOR-X-OLDER-TEST"
                                        :created (date/timestamp date)}}
                        {app-id :id} (dummy-submitted-application sonja)
                        invoice-id (new-invoice app-id)]]
            (mongo/update-by-id :invoices invoice-id updates))

          (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                 :until (date/timestamp "1.4.2000")
                                                 :limit 3))]
            (count invoices) => 3
            (map :created invoices) => [(date/timestamp "1.4.2000")
                                        (date/timestamp "1.3.2000")
                                        (date/timestamp "1.2.2000")]))

        (fact "when user has given the exact target organization as a parameter"
          (doseq [org-id ["FILTER-ORG-1" "FILTER-ORG-2" "FILTER-ORG-3" "FILTER-ORG-4" "FILTER-ORG-FORBIDDEN"]
                  :let   [updates  {$set {:state   "checked" :organization-id org-id
                                          :created (date/timestamp "1.1.2000")}}
                          {app-id :id} (dummy-submitted-application sonja)
                          invoice-id (new-invoice app-id)]]
            (add-organization org-id true)
            (mongo/update-by-id :invoices invoice-id updates)
            (when (= org-id "FILTER-ORG-1")
              (mongo/update-by-id :invoices (new-invoice app-id) updates)))

          (against-background (find-user-from-minimal-by-apikey sonja)
                              => (biller-user sonja "FILTER-ORG-1" "FILTER-ORG-2" "FILTER-ORG-3"))

          (fact "should return the invoices only for that organization when the user belongs to it"
            (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                                   :organization-id "FILTER-ORG-1"))]
              (count invoices) => 2
              (map :organization-id invoices) => ["FILTER-ORG-1" "FILTER-ORG-1"]))

          (fact "does not return the invoices for AN org if user does NOT belong to it"
            (local-query sonja :user-organizations-invoices
                         :organization-id "FILTER-ORG-FORBIDDEN") => invoicing-disabled?)

          (fact "does not return the invoices for AN org if user is not biller in the org"
            (local-query sonja :user-organizations-invoices
                         :organization-id "FILTER-ORG-FORBIDDEN") => no-organizations?
            (provided (find-user-from-minimal-by-apikey sonja)
                      => (auth-user "authority" sonja "FILTER-ORG-FORBIDDEN")))))

      (fact "should apply all filters together"
        (add-organization "ORG-FOR-ALL-FILTERS-TEST" true)
        (against-background (find-user-from-minimal-by-apikey sonja)
                            => (biller-user sonja "ORG-FOR-ALL-FILTERS-TEST"))
        (doseq [[date state] [["1.1.1999" "draft"] ["1.1.2000" "checked"] ["1.2.2000" "checked"]
                              ["1.2.2000" "draft"] ["1.3.2000" "checked"] ["2.3.2000" "draft"]
                              ["1.4.2000" "checked"] ["4.3.2000" "confirmed"] ["5.4.2000" "draft"]
                              ["1.8.2000" "checked"]]
                :let         [updates  {$set {:state   state :organization-id "ORG-FOR-ALL-FILTERS-TEST"
                                              :created (date/timestamp date)}}
                              {app-id :id} (dummy-submitted-application sonja)
                              invoice-id (new-invoice app-id)]]
          (mongo/update-by-id :invoices invoice-id updates))

        (let [invoices (:invoices (local-query sonja :user-organizations-invoices
                                               :states ["checked" "confirmed"]
                                               :from  (date/timestamp "1.2.2000")
                                               :until (date/timestamp "1.10.2000")
                                               :limit 4))]
          (count invoices) => 4
          (map (juxt :created :state) invoices) => [[(date/timestamp "1.8.2000") "checked"]
                                                    [(date/timestamp "1.4.2000") "checked"]
                                                    [(date/timestamp "4.3.2000") "confirmed"]
                                                    [(date/timestamp "1.3.2000") "checked"]]))

      (fact "should return invoice with"
        (let [{app-id :id} (create-and-submit-local-application pena :address "Kukkuja 7")
              invoice-id   (new-invoice app-id)
              result       (local-query laura :user-organizations-invoices)
              invoices     (:invoices result)
              invoice      (util/find-by-id invoice-id invoices)]

          (fact "organisation data enriched to it"
            (get-in invoice [:enriched-data :organization]) => {:name {:fi "Sipoon rakennusvalvonta"
                                                                       :en "Sipoon rakennusvalvonta"
                                                                       :sv "Sipoon rakennusvalvonta"}})
          (fact "application data enriched to it"
            (get-in invoice [:enriched-data :application]) => {:address "Kukkuja 7"
                                                               :payer   {:payer-type "person"}}))))))
