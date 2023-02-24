(ns lupapalvelu.foreman-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure? unauthorized?]]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.foreman-application-util :refer [select-latest-verdict-status] :as foreman-util]
            [lupapalvelu.document.data-schema :as dds]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.schema-generators :as ssg]))

(testable-privates lupapalvelu.foreman
                   validate-notice-or-application
                   validate-notice-submittable
                   henkilo-invite
                   yritys-invite)

(testable-privates lupapalvelu.foreman-application-util
                   filter-foreman-doc-responsibilities)

(def foreman-app {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-ilmoitus"
                  :created 1444444444444
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}
                               :data {}}]
                  :linkPermitData [{:type "lupapistetunnus"
                                    :id "LP-123"}]})
(def project-app {:tasks [{:data {:katselmuksenLaji {:value "loppukatselmus"}
                                  :katselmus {:tila {:value "lopullinen"}
                                              :pitoPvm {:value "12.10.2019"}}}
                           :state "sent"}]})

(facts "Foreman application validation"
  (validate-notice-or-application foreman-app) => nil
  (validate-notice-or-application
    (assoc foreman-app :permitSubtype "")) => (partial expected-failure? :error.foreman.type-not-selected)
  (validate-notice-or-application
    (assoc foreman-app :permitSubtype nil)) => (partial expected-failure? :error.foreman.type-not-selected)
  (validate-notice-or-application
    (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus")) => nil)

(facts "Notice? tests"
  (f/notice? nil) => false
  (f/notice? {}) => false
  (f/notice? foreman-app) => true
  (f/notice? (assoc-in foreman-app [:primaryOperation :name] "other-op")) => false
  (f/notice? (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus")) => false)

(facts "validate if notice is submittable"
  (validate-notice-submittable nil irrelevant) => nil
  (validate-notice-submittable {} irrelevant) => nil
  (fact "Only foreman notice apps are validated"
    (validate-notice-submittable (assoc foreman-app :permitSubtype "hakemus") irrelevant) => nil)
  (fact "Link permit must be procided for validate to run"
    (validate-notice-submittable foreman-app nil) => nil)
  (fact "Validator returns nil if state is post-verdict state"
    (validate-notice-submittable foreman-app {:state "verdictGiven"}) => nil)
  (fact "Validator returns fail! when state is post-verdict state"
    (validate-notice-submittable foreman-app {:state "sent"}) => (partial expected-failure? :error.foreman.notice-not-submittable)))

(facts "Foreman submittable"
  (f/validate-foreman-submittable {:state :draft} nil) => nil
  (f/validate-foreman-submittable {:state :draft} {:state :open}) => (partial expected-failure? :error.not-submittable.foreman-link)
  (f/validate-foreman-submittable {:state :draft} {:state :submitted}) => nil)

(def tj-data-schema               (dds/doc-data-schema "tyonjohtaja-v2"))
(def hankkeen-kuv-min-data-schema (dds/doc-data-schema "hankkeen-kuvaus-minimum"))
(def hankkeen-kuvaus-data-schema  (dds/doc-data-schema "hankkeen-kuvaus"))
(def hakija-tj-data-schema        (dds/doc-data-schema "hakija-tj"))
(def hakija-r-data-schema         (dds/doc-data-schema "hakija-r"))
(def rj-data-schema               (dds/doc-data-schema "rakennusjatesuunnitelma"))

(fact update-foreman-docs
  (let [tj-doc               (ssg/generate tj-data-schema)
        hankkeen-kuvaus      (ssg/generate hankkeen-kuv-min-data-schema)
        hankkeen-kuvaus-orig (assoc-in (ssg/generate hankkeen-kuvaus-data-schema) [:data :kuvaus :value] "Test description")
        hakija-tj            (ssg/generate hakija-tj-data-schema)
        hakija-r1            (ssg/generate hakija-r-data-schema)
        hakija-r2            (ssg/generate hakija-r-data-schema)
        rj-doc               (ssg/generate rj-data-schema)
        foreman-app          (assoc foreman-app :documents [hankkeen-kuvaus rj-doc tj-doc hakija-tj])
        orig-app             {:documents [hankkeen-kuvaus-orig hakija-r1 hakija-r2]}

        result-doc           (f/update-foreman-docs foreman-app orig-app "KVV-tyonjohtaja")]

    (fact "hakija docs are copied from orig application"
      (let [hakija-docs (filter (comp #{:hakija} keyword :subtype :schema-info) (:documents result-doc))]
        (count hakija-docs) => 2
        (set (map #(get-in % [:schema-info :name]) hakija-docs)) => (just "hakija-tj")
        (-> hakija-docs first :data :henkilo (dissoc :userId))  => (-> hakija-r1 :data :henkilo (dissoc :userId))
        (-> hakija-docs second :data :henkilo (dissoc :userId)) => (-> hakija-r2 :data :henkilo (dissoc :userId))))

    (fact "hankkeen kuvaus value is copied from orig app"
      (let [hankkeen-kuvaus-docs (filter (comp #{:hankkeen-kuvaus} keyword :subtype :schema-info) (:documents result-doc))]
        (count hankkeen-kuvaus-docs) => 1
        (-> hankkeen-kuvaus-docs first :schema-info :name) => "hankkeen-kuvaus-minimum"
        (-> hankkeen-kuvaus-docs first :data :kuvaus :value) => "Test description"))

    (fact "foreman role is set"
      (let [foreman-docs (filter (comp #{"tyonjohtaja-v2"} :name :schema-info) (:documents result-doc))]
        (count foreman-docs) => 1
        (-> foreman-docs first :data :kuntaRoolikoodi :value) => "KVV-tyonjohtaja"))

    (fact "other docs are not changed"
      (let [other-docs (remove (comp #{"tyonjohtaja-v2" "hakija-tj" "hankkeen-kuvaus-minimum"} :name :schema-info) (:documents result-doc))]
        (count other-docs) => 1
        (-> other-docs first) => rj-doc))))

(facts "allow-foreman-only-in-foreman-app"
  (let [foreman-app-with-auth (assoc foreman-app :auth [{:id "1" :role "foreman"}])
        non-foreman-app (assoc-in foreman-app-with-auth [:primaryOperation :name] "kerrostalo-rivitalo")
        fm-command {:application foreman-app-with-auth, :user {:id "1"}}
        non-fm-command {:application non-foreman-app, :user {:id "1"}}]

    (fact "meta"
      (f/foreman-app? foreman-app-with-auth) => true
      (f/foreman-app? non-foreman-app) => false)

    (fact "has auth"      (f/allow-foreman-only-in-foreman-app fm-command) => nil)
    (fact "other user ok" (f/allow-foreman-only-in-foreman-app (assoc fm-command :user {:id "2"})) => nil)
    (fact "no access to other kind of application"
      (f/allow-foreman-only-in-foreman-app non-fm-command) => unauthorized?)))

(facts "select-latest-verdict-status"
  (fact "backing system verdict with status"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :status "10"}]}]}]})
    => {:status    "ok"
        :statusLoc :verdict.status.10})
  (fact "one verdict with paatoskoodi"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "29"}]}]}]})
    => {:status    "rejected"
        :statusLoc :verdict.status.29})

  (fact "one verdict with status and paatoskoodi - prefer status"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :status "1" :paatoskoodi "29"}]}]}]})
    => {:status    "ok"
        :statusLoc :verdict.status.1})

  (fact "one verdict without poytakirja"
    (select-latest-verdict-status {:verdicts [{:paatokset []}]})
    => {:status "new"})

  (fact "one verdict with empty poytakirja"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{}]}]}]})
    => {:status "new"})

  (fact "one verdict with multiple paatoskoodi"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}
                                                                          {:paatospvm 3 :paatoskoodi "3"}
                                                                          {:paatospvm 4 :paatoskoodi "4"}
                                                                          {:paatospvm 2 :paatoskoodi "2"}]}]}]})
    => {:status    "ok"
        :statusLoc :verdict.status.4})

  (fact "one verdict with multiple paatos"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}
                                                           {:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                          {:paatospvm 4 :paatoskoodi "4"}]}
                                                           {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}]})
    => {:status    "ok"
        :statusLoc :verdict.status.4})

  (fact "application with multiple verdicts"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}
                                              {:paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                          {:paatospvm 4 :paatoskoodi "4"}]}
                                                           {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}]})
    => {:status    "ok"
        :statusLoc :verdict.status.4})

  (fact "one verdict with future paatoskoodi"
    (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm (- (now) 9999) :paatoskoodi "1"}
                                                                          {:paatospvm (- (now) 999) :paatoskoodi "2"}
                                                                          {:paatospvm (+ (now) 9999) :paatoskoodi "3"}]}]}]})
    => {:status    "ok"
        :statusLoc :verdict.status.3})

  (fact "Pate verdict with verdict-code"
    (select-latest-verdict-status {:pate-verdicts [{:category   "tj"
                                                    :published  {:published 123}
                                                    :data       {:verdict-code "ehdollinen"}}]})
    => {:status    "ok"
        :statusLoc :pate-r.verdict-code.ehdollinen})

  (fact "Pate verdict with numeric, rejected verdict-code"
    (select-latest-verdict-status {:pate-verdicts [{:category   "tj"
                                                    :published  {:published 123}
                                                    :data       {:verdict-code "21"}}]})
    => {:status    "rejected"
        :statusLoc :verdict.status.21})

  (fact "Pate verdict with verdict-code: wrapped"
    (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                    :published {:published 123}
                                                    :data      {:verdict-code {:_value    "ehdollinen"
                                                                               :_modified 123
                                                                               :_user     "hello"}}}]})
    => {:status    "ok"
        :statusLoc :pate-r.verdict-code.ehdollinen})

  (fact "Draft Pate verdict"
    (select-latest-verdict-status {:pate-verdicts [{:category "tj"
                                                    :data     {:verdict-code "ehdollinen"}}]})
    => {:status "new"})

  (fact "Multiple published verdicts: latest is  a Pate verdict"
    (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                    :published {:published 100}
                                                    :data      {:verdict-code "hyvaksytty"}}
                                                   {:category  "tj"
                                                    :published {:published 110}
                                                    :data      {:verdict-code "ehdollinen"}}
                                                   {:category  "tj"
                                                    :published {:published 120}
                                                    :data      {:verdict-code "myonnetty"}}]
                                   :verdicts      [{:modified  105
                                                    :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}
                                                   {:modified  115
                                                    :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                               {:paatospvm 4 :paatoskoodi "4"}]}
                                                                {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}]})
    => {:status "ok" :statusLoc :pate-r.verdict-code.myonnetty})

  (fact "Multiple published verdicts: latest is  a backing system verdict"
    (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                    :published {:published 100}
                                                    :data      {:verdict-code "hyvaksytty"}}
                                                   {:category  "tj"
                                                    :published {:published 110}
                                                    :data      {:verdict-code "ehdollinen"}}
                                                   {:category  "tj"
                                                    :published {:published 120}
                                                    :data      {:verdict-code "myonnetty"}}]
                                   :verdicts      [{:timestamp 125
                                                    :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                               {:paatospvm 4 :paatoskoodi "37"}]}
                                                                {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}
                                                   {:timestamp 105
                                                    :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}]})
    => {:status "rejected" :statusLoc :verdict.status.37})

  (fact "Multiple published verdicts: drafts are ignored"
    (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                    :published {:published 100}
                                                    :data      {:verdict-code "hyvaksytty"}}
                                                   {:category  "tj"
                                                    :published {:published 110}
                                                    :data      {:verdict-code "ehdollinen"}}
                                                   {:category "tj"
                                                    :data     {:verdict-code "myonnetty"}}]
                                   :verdicts      [{:timestamp 125
                                                    :draft     true
                                                    :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                               {:paatospvm 4 :paatoskoodi "37"}]}
                                                                {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}
                                                   {:timestamp 105
                                                    :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}]})
    => {:status "ok" :statusLoc :pate-r.verdict-code.ehdollinen}

    (fact "Multiple published verdicts: state is acknowledged"
      (select-latest-verdict-status {:state         "acknowledged"
                                     :pate-verdicts [{:category  "tj"
                                                      :published {:published 100}
                                                      :data      {:verdict-code "hyvaksytty"}}
                                                     {:category  "tj"
                                                      :published {:published 110}
                                                      :data      {:verdict-code "ehdollinen"}}
                                                     {:category "tj"
                                                      :data     {:verdict-code "myonnetty"}}]
                                     :verdicts        [{:timestamp 125
                                                        :draft     true
                                                        :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                   {:paatospvm 4 :paatoskoodi "37"}]}
                                                                    {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}
                                                       {:timestamp 105
                                                        :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}]})
      => {:status "ok"})))

(facts foreman-app-context
  (fact "foreman in foreman application"
    (-> (f/foreman-app-context {:user {:id "1"}
                                :application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                              :auth [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
        :permissions)
    => #{:foreman-app/test}

    (provided (lupapalvelu.permissions/get-permissions-by-role :foreman-app "foreman") => #{:foreman-app/test}))

  (fact "non-foreman in foreman application"
    (-> (f/foreman-app-context {:user {:id "2"}
                                :application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                              :auth [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role :foreman-app "writer") => #{}))

  (fact "foreman in non-foreman application"
    (-> (f/foreman-app-context {:user {:id "1"}
                                :application {:primaryOperation {:name "kerrostalo-rivitalo"}
                                              :auth [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "user not in application auths"
    (-> (f/foreman-app-context {:user {:id "3"}
                                :application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                              :auth [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "authority user not in application auths"
    (-> (f/foreman-app-context {:user {:id "3" :orgAuthz {:123-T ["authority"]}}
                                :application {:organization "123-T"
                                              :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                              :auth [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "no application in command"
    (-> (f/foreman-app-context {:user {:id "1"}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0)))

(facts "foreman responsibility dates"
  (let [app-new       (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus")
        app-notice    (assoc foreman-app :permitSubtype "tyonjohtaja-ilmoitus"
                             :state "acknowledged"
                             :submitted 5555)
        app-verdict   (assoc app-new :state "foremanVerdictGiven"
                             :pate-verdicts [{:published {:published 1234}
                                              :category  "tj"}])
        app-ok        (assoc-in app-verdict [:pate-verdicts 0 :data :verdict-code] "1")
        app-rejected  (assoc-in app-verdict [:pate-verdicts 0 :data :verdict-code] "evatty")
        app-requested (assoc app-ok :foremanTermination {:started 12345 :ended nil})
        app-confirmed (assoc-in app-requested [:foremanTermination :ended] 67890)]
    (fact "new app has no start or end date"
      (foreman-util/get-foreman-responsibility-timestamps app-new {}) =>       {:started nil
                                                                                :ended   nil})
    (fact "tyonjohtaja-ilmoitus is considered started"
      (foreman-util/get-foreman-responsibility-timestamps app-notice {}) =>    {:started 5555
                                                                                :ended   nil})
    (fact "approved app has start date"
      (foreman-util/get-foreman-responsibility-timestamps app-ok {}) =>        {:started 1234
                                                                                :ended   nil})
    (fact "Verdict date overrides submitted date for notice"
      (foreman-util/get-foreman-responsibility-timestamps
        (assoc app-notice :pate-verdicts [{:published {:published 4321}
                                           :data      {:verdict-code "hyvaksytty"}
                                           :category  "tj"}])
        {})
      => {:started 4321 :ended nil})
    (fact "rejected app has no start or end date"
      (foreman-util/get-foreman-responsibility-timestamps app-rejected {}) =>  {:started nil
                                                                                :ended   nil})
    (fact "app with termination requested has no end date (yet)"
      (foreman-util/get-foreman-responsibility-timestamps app-requested {}) => {:started 12345
                                                                                :ended   nil})
    (fact "terminated app has start and end date"
      (foreman-util/get-foreman-responsibility-timestamps app-confirmed {}) => {:started 12345
                                                                                :ended   67890})
    (fact "approved app whose project app has a final review has ended"
      (foreman-util/get-foreman-responsibility-timestamps app-ok project-app)
      => {:started 1234
          :ended   (date/timestamp "12.10.2019" )})))

(facts "required-roles"
  (let [application  {:tasks [{:schema-info {:name "task-vaadittu-tyonjohtaja"} ; Regular task
                               :data        {:kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"}}}
                              {:schema-info {:name "task-vaadittu-tyonjohtaja"} ; Migrated task
                               :data        {:kuntaRoolikoodi {:value "ty\u00f6njohtaja"}}
                               :migrated-foreman-task true}
                              {:schema-info {:name "task-vaadittu-tyonjohtaja"} ; KVV-foreman (special case)
                               :data        {:kuntaRoolikoodi {:value "kvv-ty\u00f6njohtaja"}}}]}
        foreman-apps [{:id "LP-VTJ" ; Required
                       :documents [{:data {:kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"}}}]}
                      {:id "LP-IVTJ"; Not required
                       :documents [{:data {:kuntaRoolikoodi {:value "iv-ty\u00f6njohtaja"}}}]}]]
    (f/add-required-roles-to-foreman-info application foreman-apps) =>
      [{:lupapiste-role "KVV-ty\u00f6njohtaja"
        :required       true
        :role-name      "KVV-ty\u00f6njohtaja"
        :migrated       false
        :unmet-role     true}
       {:lupapiste-role "ty\u00f6njohtaja"
        :required       true
        :role-name      "ty\u00f6njohtaja"
        :migrated       true
        :unmet-role     true}
       {:documents      [{:data {:kuntaRoolikoodi {:value "iv-ty\u00f6njohtaja"}}}]
        :id             "LP-IVTJ"
        :required       false
        :role-name      "iv-ty\u00f6njohtaja"}
       {:documents      [{:data {:kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"}}}]
        :id             "LP-VTJ"
        :required       true
        :role-name      "vastaava ty\u00f6njohtaja"}]))

(facts "foreman-info-responsible-tasks-filtering"
  (->> [{:data {:kuntaRoolikoodi {:value "IV-ty\u00f6njohtaja"}
                :vastattavatTyotehtavat {:ivLaitoksenAsennustyo {:value true}
                                         :sisapuolinenKvvTyo    {:value true}
                                         :ulkopuolinenKvvTyo    {:value false}
                                         :muuMika               {:value true}
                                         :muuMikaValue          {:value "Janitor work"}}}}
        {:data {:kuntaRoolikoodi {:value "ty\u00f6njohtaja"}
                :vastattavatTyotehtavat {:sisapuolinenKvvTyo    {:value true}
                                         :ulkopuolinenKvvTyo    {:value true}
                                         :linjasaneeraus        {:value true}}}}]
       (mapv filter-foreman-doc-responsibilities)) =>
  [{:data {:kuntaRoolikoodi {:value "IV-ty\u00f6njohtaja"}
           :vastattavatTyotehtavat {:ivLaitoksenAsennustyo {:value true}
                                    :sisapuolinenKvvTyo    {:value false}
                                    :ulkopuolinenKvvTyo    {:value false}
                                    :muuMika               {:value true}
                                    :muuMikaValue          {:value "Janitor work"}}}}
   {:data {:kuntaRoolikoodi {:value "ty\u00f6njohtaja"}
           :vastattavatTyotehtavat {:sisapuolinenKvvTyo    {:value false}
                                    :ulkopuolinenKvvTyo    {:value false}
                                    :linjasaneeraus        {:value true}}}}])

(facts "vastattavat-tyotehtavat"
  (->> [{:data {:vastattavatTyotehtavat {}}}
        {:data {:vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}}}}
        {:data {:vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}
                                         :ulkopuolinenKvvTyo {:value false}
                                         :muuMika            {:value true}
                                         :muuMikaValue       {:value "Janitor work"}}}}
        {:data {:vastattavatTyotehtavat {:muuMika            {:value false}
                                         :muuMikaValue       {:value "Janitor work"}}}}
        {:data {:vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}
                                         :muuMika            {:value true}
                                         :muuMikaValue       {:value ""}}}}]
       (mapv #(f/vastattavat-tyotehtavat % "en"))) =>
  [[]
   ["Internal water supply and drainage work"]
   ["Internal water supply and drainage work" "Janitor work"]
   []
   ["Internal water supply and drainage work"]])
