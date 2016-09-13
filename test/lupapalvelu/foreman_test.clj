(ns lupapalvelu.foreman-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure? unauthorized?]]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.data-schema :as dds]
            [sade.schema-generators :as ssg]))

(testable-privates lupapalvelu.foreman
                   validate-notice-or-application
                   validate-notice-submittable
                   henkilo-invite
                   yritys-invite)

(def foreman-app {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-ilmoitus"
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}
                               :data {}}]
                  :linkPermitData [{:type "lupapistetunnus"
                                    :id "LP-123"}]})

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
