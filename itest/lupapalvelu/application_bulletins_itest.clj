(ns lupapalvelu.application-bulletins-itest
  (:require [lupapalvelu.application-bulletin-utils :refer [day-range-ts]]
            [lupapalvelu.application-bulletins :refer [version-elemMatch versions-elemMatch]]
            [lupapalvelu.application-bulletins-itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.util :as util]))

(apply-remote-minimal)

(let [r-app          (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                    :propertyId sipoo-property-id
                                                    :x 406898.625 :y 6684125.375
                                                    :address "Hitantine 108")
      op-description "Kuvausteksti123"
      app-id         (:id r-app)]
  (fact "bulletin-op-description"
    (command sonja :update-app-bulletin-op-description :id (:id r-app) :description op-description) => ok?
    (-> (get-by-id :applications app-id) :body :data) => (contains  {:bulletinOpDescription op-description}))
  (fact "check for backend verdict"
    (command sonja :check-for-verdict :id app-id) => ok?
    (-> (query-application mikko app-id) :verdicts count) => pos?)
  (fact "Bulletin is created automatically"
    (let [bulletins (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1 :sort ""))]
      (count bulletins) => 1
      (-> bulletins first :bulletinState) => "verdictGiven"
      ;TODO ensure bulletin dates looks correct
      (fact "description is set"
        (-> bulletins first :bulletinOpDescription) => op-description)))
  (fact "foreman verdict"
    (let [foreman-app-id (create-foreman-application app-id mikko pena-id "vastaava ty\u00F6njohtaja" "A")
          _              (finalize-foreman-app mikko sonja foreman-app-id true)
          bulletins      (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1 :sort ""))]
      (fact "bulletin was not generated"
        (util/find-by-key :application-id foreman-app-id bulletins) => empty?
        (:bulletins (query mikko :verdict-bulletins :id foreman-app-id)) => empty?)))

  (fact "Bulletin reports for application"
    (let [[{bull-id :id} & _
           :as bulls] (:bulletins (query mikko :verdict-bulletins :id app-id))]
      bulls => (just (just {;; 6.9.2100 (rom verdict.xml)
                            :end-date   (-> {:year 2100 :month 9 :day 6}
                                            date/end-of-day
                                            date/timestamp)
                            :id         bull-id
                            ;; 7.9.2013 (from verdict.xml)
                            :start-date (-> {:year 2013 :month 9 :day 7}
                                            date/zoned-date-time
                                            date/timestamp)}))
      (facts "Bulletin information pdf"
        (pdf-response?
          (raw mikko :bulletin-report-pdf :id app-id
               :bulletinId bull-id
               :lang "fi")
          {"Content-Disposition" (format "filename=\"Julkipanon tiedot %s %s.pdf\""
                                         app-id (date/finnish-date (now) :zero-pad))}))
      (fact "Disable bulletins for Sipoo"
        (command admin :update-organization
                 :permitType "R"
                 :municipality "753"
                 :bulletinsEnabled false) => ok?
        (query mikko :verdict-bulletins :id app-id)
        => (partial expected-failure? "error.bulletins-not-enabled-for-scope")
        (raw mikko :bulletin-report-pdf :id app-id
             :bulletinId bull-id
             :lang "fi")
        => (contains {:status 404
                      :body   "{\"ok\":false,\"text\":\"error.bulletins-not-enabled-for-scope\"}"}))
      (fact "Enable bulletins for Sipoo"
        (command admin :update-organization
                 :permitType "R"
                 :municipality "753"
                 :bulletinsEnabled true) => ok?
        (query mikko :verdict-bulletins :id app-id) => ok?
        (raw mikko :bulletin-report-pdf :id app-id
             :bulletinId bull-id
             :lang "fi") => (contains {:status 200}))
      (fact "Bad bulletin id"
        (raw mikko :bulletin-report-pdf :id app-id
             :bulletinId "bad-id"
             :lang "fi")
        => (contains {:status 404
                      :body   "{\"ok\":false,\"text\":\"error.bulletin.not-found\"}"}))
      (fact "Bad language"
        (raw mikko :bulletin-report-pdf :id app-id
             :bulletinId bull-id
             :lang "bad")
        => (contains {:status 404
                      :body   "{\"ok\":false,\"text\":\"error.illegal-value:schema-validation\"}"}))))

  (fact "Fetch bulletin app-description from backend"
    (command sipoo :update-organization-bulletin-scope :organizationId "753-R"
             :permitType "R"
             :municipality "753"
             :descriptionsFromBackendSystem true) => ok?
    (command sonja :check-for-verdict :id app-id) => ok?)

  (let [bulletins (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1 :sort ""))]
    (fact "description is set according to the backend verdict"
          (-> bulletins first :bulletinOpDescription) => "Uudisrakennuksen ja talousrakennuksen 15 m2 rakentaminen. Yhden huoneiston erillistalo.")))

(facts "Bulletins ends-at correct"
  (apply-remote-minimal)
  (let [app (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                :propertyId sipoo-property-id
                                                :x 406898.625 :y 6684125.375
                                                :address "Hitantine 109")
        app-id (:id app)
        check-resp (command sonja :check-for-verdict :id app-id)
        verdict-app (query-application sonja app-id)]
    (fact "verdict ok"
      check-resp => ok?
      (:state verdict-app) => "verdictGiven")
    (fact "Bulletin is shown on its final day"
      (let [bulletins (datatables pena :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1 :sort "")
            test-bulletin (first (:data bulletins))
            versions (query sonja :bulletin-versions :bulletinId (:id test-bulletin))]
        (count (:data bulletins)) => 1

        (fact "edit ends at"
          (command sonja :save-verdict-given-bulletin
                   :bulletinId (:id test-bulletin)
                   :bulletinVersionId (get-in versions [:bulletin :versions 0 :id])
                   :verdictGivenAt (date/timestamp (.minusDays (date/now) 1))
                   :appealPeriodStartsAt (date/timestamp (date/now))
                   :appealPeriodEndsAt (date/timestamp (.plusDays (date/now) 1))
                   :verdictGivenText "test timestamps logic") => ok?)

        (let [bulletins (:data (datatables pena :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1 :sort ""))]
          (fact "data is still visible"
            (count bulletins) => pos?))))))

(defn make-bulletin [state start-ts & [end-ts]]
  {:bulletinState state
   :versions      [(make-version state start-ts end-ts)]})

(defn query-check [ts limit-ts & states]
  (let [check (just (map name states) :in-any-order)]
    (facts {:midje/description (str (date/finnish-datetime ts) ": " states)}
      (fact version-elemMatch
        (map :bulletinState (mongo/select :application-bulletins (version-elemMatch ts limit-ts)))
        => check)
      (fact versions-elemMatch
        (map :bulletinState (mongo/select :application-bulletins (versions-elemMatch ts limit-ts)))
        => check))))

(defonce db-name (str "test_bulletins_" (now)))

(def hour 3600000)

(facts "mongo queries and dates"
  (mount/start #'mongo/connection)
  (mongo/with-db db-name
    (fixture/apply-fixture "minimal")
    (mongo/insert :application-bulletins (make-bulletin :proclaimed
                                                        (randstamp "1.2.2019")
                                                        (randstamp "1.3.2019")))
    (mongo/insert :application-bulletins (make-bulletin :verdictGiven
                                                        (randstamp "20.2.2019")
                                                        (randstamp "20.3.2019")))
    (mongo/insert :application-bulletins (make-bulletin :final
                                                        (randstamp "10.3.2019")))
    (fact "Three bulletins"
      (mongo/count :application-bulletins) => 3)
    (query-check (randstamp "10.2.2019") 12345 :proclaimed)
    (query-check (first (day-range-ts (randstamp "1.2.2019"))) 12345 :proclaimed)
    (query-check (last (day-range-ts (randstamp "1.3.2019"))) 12345 :proclaimed :verdictGiven)
    (query-check (randstamp "10.3.2019") (randstamp "20.3.2019") :verdictGiven)
    (query-check (first (day-range-ts (randstamp "20.2.2019")))
                 (randstamp "25.2.2019") :proclaimed :verdictGiven)
    (query-check (last (day-range-ts (randstamp "20.3.2019")))
                 (randstamp "30.3.2019") ::verdictGiven)
    (query-check (randstamp "20.3.2019")
                 (randstamp "5.3.2019") :verdictGiven :final)
    (query-check (randstamp "10.3.2019")
                 (randstamp "9.3.2019") :verdictGiven :final)

    (facts "Sorting (id as a tie-breaker)"
      (letfn [(insert [id modified address]
                (mongo/insert :application-bulletins
                              {:id       id
                               :versions [{:modified             modified
                                           :address              address
                                           :municipality         "123"
                                           :bulletinState        :verdictGiven
                                           :verdictGivenAt       (- (now) (* 2 hour))
                                           :appealPeriodStartsAt (- (now) hour)
                                           :appealPeriodEndsAt   (+ (now) hour)}]}))]
        (insert "b" 100 "Road")
        (insert "a" 100 "Avenue")
        (insert "c" 100 "Road")
        (insert "d" 90 "Street")
        (insert "e" 80 "Way")
        (insert "f" 110 "Road")

        (fact "Default is modified descending"
          (->> (local-query pena :application-bulletins :page 0
                            :municipality "" :state "" :searchText ""
                            :sort {})
               :data
               (map (juxt :modified :id)))
          => [[110 "f"] [100 "c"] [100 "b"] [100 "a"] [90 "d"] [80 "e"]])
        (fact "Address ascending"
          (->> (local-query pena :application-bulletins :page 0
                            :municipality "" :state "" :searchText ""
                            :sort {:field "address" :asc 1})
               :data
               (map (juxt :address :id)))
          => [["Avenue" "a"]
              ["Road" "b"]
              ["Road" "c"]
              ["Road" "f"]
              ["Street" "d"]
              ["Way" "e"]])
        (fact "Municipality (tie-breakers only)"
          (->> (local-query pena :application-bulletins :page 0
                            :municipality "" :state "" :searchText ""
                            :sort {:field "municipality"})
               :data
               (map :id)
               (apply str)) => "fedcba"
          (->> (local-query pena :application-bulletins :page 0
                            :municipality "" :state "" :searchText ""
                            :sort {:field "municipality" :asc 1})
               :data
               (map :id)
               (apply str)) => "abcdef")))))
