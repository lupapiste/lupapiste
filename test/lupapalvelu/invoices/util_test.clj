(ns lupapalvelu.invoices.util-test
  (:require [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.invoices.util :as util]
            [midje.sweet :refer :all]))

(defn mk-org
  ([org-id cfg bid-cfg]
   {:id                          org-id
    :invoicing-config            cfg
    :invoicing-backend-id-config bid-cfg})
  ([cfg bid-cfg]
   (mk-org "foo" cfg bid-cfg)))

(facts "backend-codes"
  (util/backend-codes {}) => nil
  (util/backend-codes (delay {})) => nil
  (util/backend-codes (mk-org {:backend-id? false}
                              {:codes [{:bar "foo" :code "A" :text "One"}
                                       {:bar "baz" :code "B" :text "Two"}]}))
  => nil
  (util/backend-codes (mk-org {:backend-id? true}
                              {:codes [{:bar "foo" :code "A" :text "One"}
                                       {:bar "baz" :code "B" :text "Two"}]}))
  => [{:code "A" :text "One"} {:code "B" :text "Two"}]
  (util/backend-codes (delay (mk-org {:backend-id? true}
                                     {:codes [{:bar "foo" :code "A" :text "One"}
                                              {:bar "baz" :code "B" :text "Two"}]})))
  => [{:code "A" :text "One"} {:code "B" :text "Two"}]
  (util/backend-codes (mk-org {}
                              {:codes [{:bar "foo" :code "A" :text "One"}
                                       {:bar "baz" :code "B" :text "Two"}]}))
  => nil
  (util/backend-codes (mk-org {:backend-id? true}
                              {:codes []}))
  => nil)

(against-background
  [(lupapalvelu.mongo/get-next-sequence-value "invoices-foo") => 12]
  (facts "generate-backend-id"
    (let [cfg (mk-org {:backend-id? true}
                      {:codes [{:code "XY" :text "Hello"}]})]
      (util/generate-backend-id cfg "XY") => "XY12"
      (util/generate-backend-id (delay cfg) "XY") => "XY12"
      (util/generate-backend-id (assoc-in cfg [:invoicing-backend-id-config :numbers] 8) "XY")
      => "XY00000012"
      (util/generate-backend-id cfg "AB") => nil
      (provided
        (lupapalvelu.mongo/get-next-sequence-value "invoices-foo") => 12 :times 0)
      (util/generate-backend-id (assoc-in cfg [:invoicing-config :backend-id?] false) "XY")
      => nil
      (util/generate-backend-id {} "XY") => nil
      (util/generate-backend-id nil "XY") => nil)))

(defn make-person [ & kvs]
  (let [{:keys [first last phone email street zip city country hetu turvakielto
                foreign-id]} (apply hash-map kvs)]
    {:henkilotiedot (cond-> {:etunimi                 first
                             :sukunimi                last
                             :turvakieltoKytkin       turvakielto
                             :hetu                    hetu}
                      foreign-id
                      (assoc :not-finnish-hetu true
                             :ulkomainenHenkilotunnus foreign-id))
     :osoite        {:katu                 street
                     :postinumero          zip
                     :postitoimipaikannimi city
                     :maa                  country}
     :yhteystiedot  {:puhelin phone
                     :email   email}}))

(defn make-company [& kvs]
  (let [{:keys [name y first last phone email street zip city country
                netbill ovt operator turvakielto]} (apply hash-map kvs)]
    {:yritysnimi           name
     :liikeJaYhteisoTunnus y
     :osoite               {:katu                 street
                            :postinumero          zip
                            :postitoimipaikannimi city
                            :maa                  country}
     :yhteyshenkilo        {:henkilotiedot {:etunimi           first
                                            :sukunimi          last
                                            :turvakieltoKytkin turvakielto}
                            :yhteystiedot  {:puhelin phone
                                            :email   email}}
     :verkkolaskutustieto  {:verkkolaskuTunnus netbill
                            :ovtTunnus         ovt
                            :valittajaTunnus   operator}}))

(defn make-payer [selected person company reference]
  {:schema-info {:subtype "maksaja"}
   :data        (tools/wrapped {:_selected  selected
                                :henkilo    person
                                :yritys     company
                                :laskuviite reference})})



(facts "application-payer-info"
  (let [base-info      {:email          "" :last-name  ""    :application-payer? true
                        :phone          "" :person-id  ""    :city               ""
                        :first-name     "" :company-id ""    :payer-type         ""
                        :postal-code    "" :protected? false :netbill            ""
                        :entity-name    "" :operator   ""    :billing-reference  ""
                        :entity-address "" :ovt        ""    :country            "FIN"}
        person-filled  (util/application-payer-info
                         {:documents
                          [{}
                           (make-payer "henkilo"
                                       (make-person :first " Piper " :last " Payer "
                                                    :phone " 12345678 " :email " piper@example.com "
                                                    :street " Street 123  " :zip " 89000 "
                                                    :city " Town " :country "USA"
                                                    :hetu "000-78X")
                                       nil
                                       "my-ref")]})
        person-partly  (util/application-payer-info
                         {:documents
                          [{}
                           (make-payer "henkilo"
                                       (make-person :first " Piper "
                                                    :turvakielto true
                                                    :foreign-id "argh"
                                                    :city " Town ")
                                       nil
                                       "my-ref")]})
        person-empty   (util/application-payer-info
                         {:documents [(make-payer "henkilo" nil nil nil)]})
        company-empty  (util/application-payer-info
                         {:documents [(make-payer "yritys" nil nil nil)]})
        company-partly (util/application-payer-info
                         {:documents
                          [{}
                           (make-payer "yritys"
                                       nil
                                       (make-company :name " Widget Ltd " :y " 223344-9 "
                                                     :street "  Money Way 10  "
                                                     :netbill "  hello "
                                                     :email " bb@example.net ")
                                       " company-ref ")]})
        company-filled (util/application-payer-info
                         {:documents
                          [{}
                           (make-payer "yritys"
                                       nil
                                       (make-company :name " Widget Ltd " :y " 223344-9 "
                                                     :street "  Money Way 10  "
                                                     :netbill "  hello " :email " bb@example.net "
                                                     :ovt " ooveetee " :operator "  mybank "
                                                     :phone " 88776655 " :first " Big "
                                                     :last " Boss " :turvakielto "yes"
                                                     :zip " 00100 " :city " Malmö "
                                                     :country "SWE")
                                       " company-ref ")]})]
    (fact "Person: filled"
      (util/person-payer? person-filled) => true
      (util/company-payer? person-filled) => false
      (util/payer-name person-filled) => "Piper Payer"
      (util/payer-address :fi person-filled)
      => "Street 123, 89000 Town, Yhdysvallat (USA)"
      person-filled => (merge base-info
                              {:first-name        "Piper"
                               :last-name         "Payer"
                               :entity-address    "Street 123"
                               :postal-code       "89000"
                               :city              "Town"
                               :country           "USA"
                               :person-id         "000-78X"
                               :payer-type        "person"
                               :phone             "12345678"
                               :email             "piper@example.com"
                               :billing-reference "my-ref"}))
   (fact "Person: partly filled"
     (util/person-payer? person-partly) => true
     (util/company-payer? person-partly) => false
     (util/payer-name person-partly) => "Piper"
     (util/payer-address :fi person-partly) => "Town"
      person-partly
      => (merge base-info
                {:first-name        "Piper"
                 :protected?        true
                 :city              "Town"
                 :person-id         "argh"
                 :payer-type        "person"
                 :billing-reference "my-ref"}))

   (fact "Person: empty"
     (util/person-payer? person-empty) => true
     (util/company-payer? person-empty) => false
     (util/payer-name person-empty) => nil
     (util/payer-address :fi person-empty) => nil
     person-empty
     => (assoc base-info :payer-type "person"))

   (fact "Company: empty"
     (util/person-payer? company-empty) => false
     (util/company-payer? company-empty) => true
     (util/payer-name company-empty) => nil
     company-empty
     => (assoc base-info :payer-type "company"))
   (fact "Company: partly filled"
     (util/person-payer? company-partly) => false
     (util/company-payer? company-partly) => true
     (util/payer-name company-partly) => "Widget Ltd"
     (util/payer-address :fi company-partly) => "Money Way 10"
     company-partly
     => (merge base-info {:payer-type        "company"
                          :entity-name       "Widget Ltd"
                          :company-id        "223344-9"
                          :netbill           "hello"
                          :entity-address    "Money Way 10"
                          :email             "bb@example.net"
                          :billing-reference "company-ref"}))
   (fact "Company: filled"
     (util/person-payer? company-filled) => false
     (util/company-payer? company-filled) => true
     (util/payer-name company-filled) => "Widget Ltd"
     (util/payer-address :sv company-filled)
     => "Money Way 10, 00100 Malmö, Sverige"
     company-filled
     => (merge base-info {:payer-type        "company"
                          :entity-name       "Widget Ltd"
                          :company-id        "223344-9"
                          :netbill           "hello"
                          :entity-address    "Money Way 10"
                          :email             "bb@example.net"
                          :billing-reference "company-ref"
                          :first-name        "Big"
                          :last-name         "Boss"
                          :operator          "mybank"
                          :ovt               "ooveetee"
                          :phone             "88776655"
                          :protected?        true
                          :postal-code       "00100"
                          :city              "Malmö"
                          :country           "SWE"}))))

(facts "person-name"
  (util/person-name {}) => nil
  (util/person-name nil) => nil
  (util/person-name {:first-name "  "
                     :last-name  "  "}) => nil
  (util/person-name {:first-name "  Hello  "}) => "Hello"
  (util/person-name {:first-name "  Hello  "
                     :last-name  " World "}) => "Hello World"
  (util/person-name {:last-name " World "}) => "World")

(facts "resolve-invoicing-config"
  (util/resolve-invoicing-config {:organization {}}) => nil
  (util/resolve-invoicing-config
    {:organization (mk-org {:integration-url ""} nil)})
  => {:integration-url ""}
  (util/resolve-invoicing-config
    {:organization (mk-org {:integration-url "url"
                            :credentials     (bs-sec/encode-credentials "my-user" "hunter2")}
                           nil)})
  => {:integration-url "url"
      :general?        true
      :credentials     {:username "my-user"
                        :password "hunter2"}}
  (util/resolve-invoicing-config
    {:organization (mk-org {:local-sftp? true
                            :download?   true}
                           nil)})
  => {:delivery-type :local-sftp
      :local-sftp?   true
      :download?     true
      :general?      true}
  (util/resolve-invoicing-config
    {:organization (delay (mk-org "666-YA"
                                  {:integration-url  "url"
                                   :local-sftp?      true
                                   :download?        true
                                   :invoicing-system "sap-idoc"}
                                  nil))})
  => {:delivery-type    :local-sftp
      :integration-url  "url"
      :local-sftp?      true
      :invoicing-system "sap-idoc"
      :download?        true
      :general?         true
      :sap?             true}
  (util/resolve-invoicing-config
    {:data {:organizationId "123-R"}})
  => {:sap-excel?       true
      :invoicing-system "sap-excel"}
  (provided
    (#'util/org-by-id "123-R")
    => (mk-org "123-R"
               {:download?        true
                :invoicing-system "sap-excel"}
               nil))
  (util/resolve-invoicing-config "234-R")
  => {:foo              "bar"
      :sap-excel?       true
      :invoicing-system "sap-excel"}
  (provided
    (#'util/org-by-id "234-R")
    => (mk-org "234-R" {:foo              "bar"
                        :invoicing-system "sap-excel"} nil)))

(fact "resolve-invoicing-config - delivery type"
  (:delivery-type (util/resolve-invoicing-config
                    {:organization (mk-org "837-YA" {:integration-url "http://localhost"} nil)}))
  => :http

  (:delivery-type (util/resolve-invoicing-config
                    {:organization (mk-org "837-YA" {:integration-url "sftp://localhost"} nil)}))
  => :sftp-password

  (:delivery-type (util/resolve-invoicing-config
                    {:organization (mk-org "837-YA"
                                           {:integration-url     "sftp://localhost"
                                            :ssh-authentication? true}
                                           nil)}))
  => :sftp-ssh

  (:delivery-type (util/resolve-invoicing-config
                    {:organization (mk-org "837-YA"
                                           {:local-sftp? true}
                                           nil)}))
  => :local-sftp)

(defn failed! [k]
  (throws (re-pattern (name k))))

(facts "validata-credentials"

  (fact "http does not require credentials"
    (let [invoicing-config {:integration-url "http://localhost"}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (assoc invoicing-config
                :general? true
                :delivery-type :http)))

  (fact "local-sftp does not require credentials"
    (let [invoicing-config {:local-sftp? true}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (assoc invoicing-config
                :general? true
                :delivery-type :local-sftp)))

  (fact "sftp-ssh requires username"
    (let [invoicing-config {:integration-url      "sftp://localhost"
                            :ssh-authentication? true}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (failed! :error.invalid-configuration))

    (let [invoicing-config {:integration-url      "sftp://localhost"
                            :ssh-authentication? true
                            :credentials {:username "foo"}}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (assoc invoicing-config
                :general? true
                :delivery-type :sftp-ssh)))

  (fact "sftp-password requires username and password"
    (let [invoicing-config {:integration-url      "sftp://localhost"}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (failed! :error.invalid-configuration))

    (let [invoicing-config {:integration-url      "sftp://localhost"
                            :credentials {:username "foo"}}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (failed! :error.invalid-configuration))

    (let [invoicing-config {:integration-url      "sftp://localhost"
                            :credentials {:password "bar"}}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (failed! :error.invalid-configuration))
    (let [invoicing-config {:integration-url      "sftp://localhost"
                            :credentials {:username "foo"
                                          :password "bar"}}]
      (util/validate-config-credentials!
        (util/resolve-invoicing-config {:organization (mk-org "837-YA" invoicing-config nil)}))
      => (assoc invoicing-config
                :general? true
                :delivery-type :sftp-password))))

(facts "resolve-invoice-payer-fields"
  (util/resolve-invoice-payer-fields {}) => {}
  (util/resolve-invoice-payer-fields nil) => nil
  (util/resolve-invoice-payer-fields
    {:first-name         "Be"
     :last-name          "Gone"
     :application-payer? true}
    {:documents [(make-payer "henkilo"
                             (make-person :last "World"
                                          :email "hello@example.com")
                             nil
                             "the-ref")]})
  => (contains {:application-payer? true
                :first-name         ""
                :last-name          "World"
                :email              "hello@example.com"
                :payer-type         "person"
                :billing-reference  "the-ref"})
  (util/resolve-invoice-payer-fields
    {:first-name         "Be"
     :last-name          "Gone"
     :application-payer? true
     :application-id     "LP-foo"})
  => (contains {:application-payer? true
                :first-name         "Hello"
                :last-name          ""
                :payer-type         "company"
                :entity-name        "Corporation"
                :application-id     "LP-foo"
                :billing-reference  "corp-ref"})
  (provided
    (lupapalvelu.mongo/by-id :applications "LP-foo" [:documents])
    => {:documents [(make-payer "yritys"
                                nil
                                (make-company :first "Hello"
                                              :name "Corporation")
                                "corp-ref")]}))

(facts "validate-invoice-fields"
  (facts "default: not validated"
    (util/validate-invoice-fields {:random "field"}) => {})
  (facts "company-id"
    (util/validate-invoice-fields {:company-id "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "person"
                                   :company-id "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "company"
                                   :company-id "  "})
    => {}
    (util/validate-invoice-fields {:payer-type "company"
                                   :company-id "bad"})
    {:company-id :error.illegal-y-tunnus}
    (util/validate-invoice-fields {:payer-type "company"
                                   :company-id "  4485352-3 "})
    => {:company-id :error.illegal-y-tunnus}
    (util/validate-invoice-fields {:payer-type "company"
                                   :company-id "4485352-3"})
    => {})
  (facts "person-id"
    (util/validate-invoice-fields {:person-id "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "company"
                                   :person-id  "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "company"
                                   :person-id  "  "})
    => {}
    (util/validate-invoice-fields {:payer-type "person"
                                   :person-id  "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "person"
                                   :country    "CAN"
                                   :person-id  "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "company"
                                   :country    "FIN"
                                   :person-id  "not validated"})
    => {}
    (util/validate-invoice-fields {:payer-type "person"
                                   :country    "FIN"
                                   :person-id  "bad"})
    => {:person-id :error.illegal-hetu}
    (util/validate-invoice-fields {:payer-type "person"
                                   :country    "FIN"
                                   :person-id  "  040244-942X "})
    => {:person-id :error.illegal-hetu}
    (util/validate-invoice-fields {:payer-type "person"
                                   :country    "FIN"
                                   :person-id  "040244-942X"})
    => {})
  (facts "postal-code"
    (util/validate-invoice-fields {:postal-code "not validated"})
    => {}
    (util/validate-invoice-fields {:country     "EST"
                                   :postal-code "not validated"})
    => {}
    (util/validate-invoice-fields {:country     "FIN"
                                   :postal-code "1234"})
    => {:postal-code :error.illegal-zip}
    (util/validate-invoice-fields {:country     "FIN"
                                   :postal-code "123456"})
    => {:postal-code :error.illegal-zip}
    (util/validate-invoice-fields {:country     "FIN"
                                   :postal-code " 12345 "})
    => {:postal-code :error.illegal-zip}
    (util/validate-invoice-fields {:country     "FIN"
                                   :postal-code "00040"})
    => {})
  (facts "ovt"
    (util/validate-invoice-fields {:ovt "  "}) => {}
    (util/validate-invoice-fields {:ovt nil}) => {}
    (util/validate-invoice-fields {:ovt "bad"})
    => {:ovt :error.illegal-ovt-tunnus}
    (util/validate-invoice-fields {:ovt "003723415284"})
    => {}
    (util/validate-invoice-fields {:ovt " 003723415284 "})
    => {:ovt :error.illegal-ovt-tunnus})
  (facts "email"
    (util/validate-invoice-fields {:email "  "}) => {}
    (util/validate-invoice-fields {:email nil}) => {}
    (util/validate-invoice-fields {:email "bad"})
    => {:email :error.email}
    (util/validate-invoice-fields {:email "good@example.net"})
    => {}
    (util/validate-invoice-fields {:email " foo@example.com "})
    => {:email :error.email})
  (facts "Multiple errors"
    (util/validate-invoice-fields {:email       "bad"
                                   :ovt         "also bad"
                                   :postal-code "ditto"
                                   :country     "FIN"
                                   :payer-type  "company"
                                   :company-id  "bad company"
                                   :person-id   "difficult person"})
    => {:company-id  :error.illegal-y-tunnus
        :email       :error.email
        :ovt         :error.illegal-ovt-tunnus
        :postal-code :error.illegal-zip})
  (util/validate-invoice-fields {:email       "bad"
                                 :ovt         "also bad"
                                 :postal-code "ditto"
                                 :country     "FIN"
                                 :payer-type  "person"
                                 :company-id  "bad company"
                                 :person-id   "difficult person"})
  => {:person-id   :error.illegal-hetu
      :email       :error.email
      :ovt         :error.illegal-ovt-tunnus
      :postal-code :error.illegal-zip})

(against-background
  [(sade.date/now) => (sade.date/zoned-date-time "5.6.2020")]
  (facts "transfer-batch-filename"
    (util/transfer-batch-filename nil) => "Siirtoerä 2020-06-05.xml"
    (util/transfer-batch-filename {} :excel :sv)
    => "Överföringsposten 2020-06-05.xlsx"
    (util/transfer-batch-filename {:name " Älämölö "})
    => "Siirtoerä Älämölö 2020-06-05.xml"
    (util/transfer-batch-filename {:name " Älämölö "} :xml)
    => "Siirtoerä Älämölö 2020-06-05.xml"
    (util/transfer-batch-filename {:name " Älämölö "} :excel :sv)
    => "Överföringsposten Älämölö 2020-06-05.xlsx"
    (util/transfer-batch-filename nil :bad) => (throws)))

(facts "parse-number"
  (util/parse-number nil) => nil
  (util/parse-number "") => nil
  (util/parse-number "   ") => nil
  (util/parse-number " 12 ") => 12
  (util/parse-number " 12,345 ") => 12.345
  (util/parse-number " 12.345 ") => 12.345
  (util/parse-number " -12 ") => -12
  (util/parse-number " -12,345 ") => -12.345
  (util/parse-number "0") => 0
  (util/parse-number "001") => 1
  (util/parse-number "0xff") => (throws NumberFormatException))

(facts "parse-percentage"
  (util/parse-percentage nil) => nil
  (util/parse-percentage "") => nil
  (util/parse-percentage "  % ") => nil
  (util/parse-percentage " 12 ") => 12
  (util/parse-percentage " 12,345 ") => 12.345
  (util/parse-percentage " 12.345 ") => 12.345
  (util/parse-percentage " -12 ") => -12
  (util/parse-percentage " -12,345 ") => -12.345
  (util/parse-percentage "0") => 0
  (util/parse-percentage "001") => 1
  (util/parse-percentage " 12% ") => 12
  (util/parse-percentage " 12,345 % ") => 12.345
  (util/parse-percentage " 12.345 %") => 12.345
  (util/parse-percentage " -12 %") => -12
  (util/parse-percentage " -12,345% ") => -12.345
  (util/parse-percentage "0%") => 0
  (util/parse-percentage "001 %") => 1
  (util/parse-percentage "0xff") => (throws NumberFormatException)
  (util/parse-percentage "0xff %") => (throws NumberFormatException)
  (util/parse-percentage "%1") => 1
  (util/parse-percentage "15%%") => 15
  (util/parse-percentage "   %%%% 99,999 %%%") => 99.999
  (util/parse-percentage "% % 2") => (throws NumberFormatException)
  (util/parse-percentage "2% %") => (throws NumberFormatException))

(facts "parse-percentage-or-nil"
  (util/parse-percentage-or-nil " 12% ") => 12
  (util/parse-percentage-or-nil " 12,345 % ") => 12.345
  (util/parse-percentage-or-nil " 12.345 %") => 12.345
  (util/parse-percentage-or-nil " -12 %") => -12
  (util/parse-percentage-or-nil " -12,345% ") => -12.345
  (util/parse-percentage-or-nil "0%") => 0
  (util/parse-percentage-or-nil "001 %") => 1
  (util/parse-percentage-or-nil "0xff") => nil
  (util/parse-percentage-or-nil "0xff %") => nil
  (util/parse-percentage-or-nil "%1") => 1
  (util/parse-percentage-or-nil "15%%") => 15)

(facts "safe-bigdec"
  (util/safe-bigdec nil) => nil
  (fact "NaN safe"
    (util/safe-bigdec (/ 0.0 0.0)) => nil)
  (fact "Inf safe"
    (util/safe-bigdec (/ 1.0 0.0)) => nil
    (util/safe-bigdec (/ -1.0 0.0)) => nil)
  (util/safe-bigdec (/ 100 3)) => 33.33333333333333M
  (util/safe-bigdec "") => (throws NumberFormatException)
  (doseq [n [-12.35677 "-11.22" -10 0 10 "11.22" 12.35677]]
    (fact {:midje/description (str n)}
      (util/safe-bigdec n) => (bigdec n))))

(facts "num->cents"
  (util/num->cents nil) => nil
  (util/num->cents 0) => 0
  (util/num->cents 0.0) => 0
  (util/num->cents 12) => 1200
  (util/num->cents 12.345) => 1235
  (util/num->cents 12.3449999) => 1234
  (util/num->cents -0) => 0
  (util/num->cents -0.0) => 0
  (util/num->cents -12) => -1200
  (util/num->cents -12.345) => -1235
  (util/num->cents -12.3449999) => -1234
  (util/num->cents (/ 100 3)) => 3333)

(facts "cents->num"
  (util/cents->num nil) => nil
  (util/cents->num 0) => 0.0
  (util/cents->num 12) => 0.12
  (util/cents->num 1200) => 12.0
  (util/cents->num 1235) => 12.35
  (util/cents->num -0) => 0.0
  (util/cents->num -1200) => -12.0
  (util/cents->num -1235) => -12.35
  (util/cents->num (/ 1000 3)) => 3.33
  (util/cents->num (/ 1000 3.11111)) => 3.21
  (util/cents->num 1234.50) => 12.35)

(facts "scale"
  (util/scale nil) => nil
  (util/scale 20) => 20
  (util/scale 0) => 0
  (util/scale 0.0) => 0.0
  (util/scale 12.35) => 12.35
  (util/scale 12.35499999999) => 12.35
  (util/scale 12.3500000) => 12.35
  (util/scale 12.345) => 12.35
  (util/scale 12.35 1) => 12.4
  (util/scale 12.4499999 1) => 12.4
  (util/scale 12.4 1) => 12.4
  (util/scale 12.40000 1) => 12.4
  (util/scale 12.35 3) => 12.35
  (util/scale 12.3545000 3) => 12.355
  (util/scale 12.35549999 3) => 12.355
  (util/scale 12.35549999 0) => 12

  (util/scale -20) => -20
  (util/scale -0.0) => 0.0
  (util/scale -12.35) => -12.35
  (util/scale -12.35499999999) => -12.35
  (util/scale -12.3500000) => -12.35
  (util/scale -12.345) => -12.35
  (util/scale -12.35 1) => -12.4
  (util/scale -12.4499999 1) => -12.4
  (util/scale -12.4 1) => -12.4
  (util/scale -12.40000 1) => -12.4
  (util/scale -12.35 3) => -12.35
  (util/scale -12.3545000 3) => -12.355
  (util/scale -12.35549999 3) => -12.355
  (util/scale -12.35549999 0) => -12)

(facts "euro-format"
  (util/euro-format nil) => nil
  (util/euro-format 0) => "0,00 €"
  (util/euro-format 1) => "1,00 €"
  (util/euro-format 34) => "34,00 €"
  (util/euro-format 34.2) => "34,20 €"
  (util/euro-format 34.56) => "34,56 €"
  (util/euro-format 34.567) => "34,57 €"
  (util/euro-format 34.56 {:symbol? false}) => "34,56"
  (util/euro-format 3456 {:symbol? false
                          :cents?  true}) => "34,56"
  (util/euro-format 3456 {:symbol? true
                          :cents?  true}) => "34,56 €"
  (util/euro-format 7 {:cents? true}) => "0,07 €"

  (util/euro-format -0) => "0,00 €"
  (util/euro-format -1) => "−1,00 €"
  (util/euro-format -34) => "−34,00 €"
  (util/euro-format -34.2) => "−34,20 €"
  (util/euro-format -34.56) => "−34,56 €"
  (util/euro-format -34.567) => "−34,57 €"
  (util/euro-format -34.56 {:symbol? false}) => "−34,56"
  (util/euro-format -3456 {:symbol? false
                           :cents?  true}) => "−34,56"
  (util/euro-format -3456 {:symbol? true
                           :cents?  true}) => "−34,56 €"
  (util/euro-format -7 {:cents? true}) => "−0,07 €"

  (util/euro-format "bad") => (throws))

(defn extract-vat-check [amount percentage]
  (let [{:keys [taxfree-cents vat-percentage vat-cents]
         :as m} (util/extract-vat amount percentage)]
    (facts {:midje/description (format "Extract VAT: %s %s" amount percentage)}
      (fact "Amounts match"
             (+ taxfree-cents vat-cents) => amount)
      (fact "Percentage matches"
        ;; Roughly due to rounding
        (util/scale (* 0.01 vat-percentage taxfree-cents) 0)
        => (roughly vat-cents 1)
        (util/parse-percentage (str percentage))
        => vat-percentage
        (util/scale (* 100.0 (/ vat-cents taxfree-cents)))
        => (roughly (util/scale vat-percentage) 0.1)))
    m))

(facts "extract-vat"
  (extract-vat-check 12400 24)
  => {:taxfree-cents  10000
      :vat-cents      2400
      :vat-percentage 24}
  (extract-vat-check 22222 "33,333%")
  => {:taxfree-cents  16667
      :vat-cents      5555
      :vat-percentage 33.333}
  (util/extract-vat 3333 0) => nil
  (util/extract-vat 3333 100.1) => nil
  (util/extract-vat -3333 15) => nil
  (util/extract-vat 3333 "bad") => nil
  (extract-vat-check 3333 100)
  => {:taxfree-cents  1666
      :vat-cents      1667
      :vat-percentage 100}
  (extract-vat-check 3332 " 100 % ")
  => {:taxfree-cents  1666
      :vat-cents      1666
      :vat-percentage 100}
  (extract-vat-check 3333 " 0.1 ")
  => {:taxfree-cents 3330 :vat-cents 3 :vat-percentage 0.1}
  (extract-vat-check 3333 " 00,01 ")
  => {:taxfree-cents 3333 :vat-cents 0 :vat-percentage 0.01})

(defn add-vat-check [amount percentage]
  (let [{:keys [with-tax-cents vat-percentage vat-cents]
         :as m} (util/add-vat amount percentage)]
    (facts {:midje/description (format "Add VAT: %s %s" amount percentage)}
      (fact "Amounts match"
             (- with-tax-cents vat-cents) => amount)
      (fact "Percentage matches"
        ;; Roughly due to rounding
        (util/scale (* 0.01 vat-percentage amount) 0)
        => (roughly vat-cents 1)
        (util/parse-percentage (str percentage))
        => vat-percentage
        (util/scale (* 100.0 (/ vat-cents amount)))
        => (roughly (util/scale vat-percentage) 0.1)))
    m))

(facts "add-vat"
  (add-vat-check 10000 24)
  => {:vat-cents 2400 :vat-percentage 24 :with-tax-cents 12400}
  (add-vat-check 22222 "33,333")
  => {:vat-cents 7407, :vat-percentage 33.333, :with-tax-cents 29629}
  (util/add-vat 3333 0) => nil
  (util/add-vat 3333 100.1) => nil
  (util/add-vat -3333 15) => nil
  (util/add-vat 3333 "bad") => nil
  (add-vat-check 3333 100)
  => {:vat-cents 3333 :vat-percentage 100 :with-tax-cents 6666}
  (add-vat-check 3333 "  100.0 %  ")
  => {:vat-cents 3333 :vat-percentage 100.0 :with-tax-cents 6666}
  (add-vat-check 3333 " 0.1 ")
  {:vat-cents 3 :vat-percentage 0.1 :with-tax-cents 3336}
  (add-vat-check 3333 " 00,01 ")
  => {:vat-cents 0 :vat-percentage 0.01 :with-tax-cents 3333})

(let [nfe? (throws NumberFormatException)]
  (facts "xml-decimal-string"
    (facts "Success"
      (util/xml-decimal-string 0) =>  "0.00"
      (util/xml-decimal-string -0) => "0.00"
      (util/xml-decimal-string 1.5) => "1.50"
      (util/xml-decimal-string 2.59) => "2.59"
      (util/xml-decimal-string 34.124999999) => "34.12"
      (util/xml-decimal-string 34.12500000) => "34.13"
      (util/xml-decimal-string -34.124999999) => "-34.12"
      (util/xml-decimal-string "-34.12549") => "-34.13"
      (util/xml-decimal-string "34.124999999") => "34.12")
    (facts "Failure"
      (util/xml-decimal-string nil) => (throws NullPointerException )
      (util/xml-decimal-string "") => nfe?
      (util/xml-decimal-string " ") => nfe?
      (util/xml-decimal-string "bad") => nfe?
      (util/xml-decimal-string "0xab") => nfe?
      (util/xml-decimal-string "1,27") => nfe?
      (util/xml-decimal-string "10 ") => nfe?
      (util/xml-decimal-string "10foo")  => nfe?)))

(facts "calculate-percentage-cents"
  (util/calculate-percentage-cents nil 0) => 0
  (util/calculate-percentage-cents 123 0) => 0
  (util/calculate-percentage-cents 1000 20) => 200
  (util/calculate-percentage-cents -1000 25.67) => -257
  ;; Amount should be integer, but doubles are allowed
  (util/calculate-percentage-cents 7654.32 25.67) => 1965
  (util/calculate-percentage-cents nil 10) => (throws)
  (util/calculate-percentage-cents 1000 -1) => (throws)
  (util/calculate-percentage-cents 1000 101) => (throws)
  (util/calculate-percentage-cents 1000 nil) => (throws)
  ;; Amount should be integer, but strings are parsed, if needed.
  (util/calculate-percentage-cents "1000" 20) => 200
  (util/calculate-percentage-cents 1000 "20") => (throws))

(facts "calculate-discount"
  (fact "Zero discount returns the amount unchanged"
    (doseq [a [0 1 -200 1.23 -2.45]
            :let [result {:amount-cents a :discount-cents 0}]]
      (util/calculate-discount a) => result
      (util/calculate-discount a 0) => result))
  (fact "Proper discounts"
    (util/calculate-discount 0 50) => {:amount-cents 0 :discount-cents 0}
    (util/calculate-discount 1234 45) => {:amount-cents 679 :discount-cents 555}
    (util/calculate-discount -56 12.5) => {:amount-cents -49 :discount-cents -7})
  (fact "Bad discounts"
    (util/calculate-discount "hello" 50) => (throws)
    (util/calculate-discount 1234 -45) => (throws)
    (util/calculate-discount 1234 101) => (throws)))
