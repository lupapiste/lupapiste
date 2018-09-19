(ns lupapalvelu.application-ymp-bulletins-itest
  (:require [cheshire.core :as json]
            [lupapalvelu.application-bulletins-itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]))

(apply-remote-minimal)

(create-and-send-application sonja :operation "lannan-varastointi"
                             :propertyId sipoo-property-id
                             :x 406898.625 :y 6684125.375
                             :address "Hitantine 108"
                             :state "sent")

(facts "Check if application is publishable"
  (let [r-app (create-and-submit-application sonja :operation "kerrostalo-rivitalo"
                                             :propertyId sipoo-property-id
                                             :x 406898.625 :y 6684125.375
                                             :address "Hitantine 108")
        ym-app (create-and-submit-application olli :operation "lannan-varastointi"
                                              :propertyId oulu-property-id
                                              :x 430109.3125 :y 7210461.375
                                              :address "Oulu 10")]
    (fact "R permit can not be published"
      (query sonja :ymp-publish-bulletin-enabled :id (:id r-app)) => (partial expected-failure? :error.invalid-permit-type))
    (fact "YM permit can be published"
      (query olli :ymp-publish-bulletin-enabled :id (:id ym-app)) => ok?)))

(facts "Publishing YMP bulletins"
  (let [ts-now                     (now)
        app                        (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                                  :propertyId oulu-property-id
                                                                  :x 430109.3125 :y 7210461.375
                                                                  :address "Oulu 10")
        app-id                     (:id app)
        _                          (upload-file-and-bind pena app-id {:type {:type-group "muut" :type-id "muu"}})                                => true
        _                          (upload-file-and-bind pena app-id {:type {:type-group "kartat" :type-id "jatteen-sijainti"}})                 => true
        _                          (upload-file-and-bind pena app-id {:type {:type-group "jatteen_kerays" :type-id "vastaanottopaikan_tiedot"}}) => true
        {attachments :attachments} (query-application pena app-id)]
    (fact "Pena sets CV not public"
      (command pena :set-attachment-visibility :id app-id :attachmentId (:id (first attachments)) :value "asiakas-ja-viranomainen") => ok?)
    (fact "Pena sets tutkintotodistus only visible to authorities"
      (command pena :set-attachment-visibility :id app-id :attachmentId (:id (second attachments)) :value "viranomainen") => ok?)
    (fact "approve application to 'sent' state"
      (command olli :approve-application :id app-id :lang "fi") => ok?)

    (fact "publishing with wrong id results in error"
      (command olli :move-to-proclaimed
               :id "123"
               :proclamationStartsAt 123
               :proclamationEndsAt 124
               :proclamationText "foo") => (partial expected-failure? :error.application-not-accessible))

    (fact "Bulletin not found before publishing"
      (query pena :bulletin :bulletinId app-id) => (partial expected-failure? :error.bulletin.not-found))

    (fact "Authority can't publish to wrong states"
      (command olli :move-to-verdict-given
               :id app-id
               :verdictGivenAt ts-now
               :appealPeriodStartsAt ts-now
               :appealPeriodEndsAt (+ 1 ts-now)
               :verdictGivenText "foo") => (partial expected-failure? :error.command-illegal-state)
      (command olli :move-to-final
               :id app-id
               :officialAt 123) => (partial expected-failure? :error.command-illegal-state))

    (facts "Authority publishes"
      (fact "Cant publish if start date is after end date"
        (command olli :move-to-proclaimed
                 :id app-id
                 :proclamationStartsAt (util/get-timestamp-from-now :day 2)
                 :proclamationEndsAt (util/get-timestamp-from-now :day 1)
                 :proclamationText "foo") => (partial expected-failure? :error.startdate-before-enddate))
      (fact "Can publish bulletin where proclamation starts tomorrow"
        (command olli :move-to-proclaimed
                 :id app-id
                 :proclamationStartsAt (util/get-timestamp-from-now :day 1)
                 :proclamationEndsAt (util/get-timestamp-from-now :day 2)
                 :proclamationText "foo") => ok?)

      (fact "..but bulletin is not included in query until tomorrow"
        (let [{data :data ok :ok} (datatables pena :application-bulletins :page 1 :searchText "" :municipality nil :state nil :sort nil)]
          ok => true
          (count data) => 0))

      (fact "Bulletin can still be queried" (query pena :bulletin :bulletinId app-id) => ok?)

      (fact "Bulletin is published with immediate proclamation"
        (command olli :move-to-proclaimed
                 :id app-id
                 :proclamationStartsAt ts-now
                 :proclamationEndsAt (util/get-timestamp-from-now :day 1)
                 :proclamationText "foo2") => ok?
        (fact "it is visible in query"
          (->
            (datatables pena :application-bulletins :page 1 :searchText "" :municipality nil :state nil :sort nil)
            :data
            count) => 1
          (query pena :bulletin :bulletinId app-id) => ok?)))

    (fact "Regular user can't publish bulletin"
      (command pena :move-to-proclaimed
               :id app-id
               :proclamationStartsAt 1449153132436
               :proclamationEndsAt 1449153132436
               :proclamationText "foo") => unauthorized?)

    (fact "Not public attachments aren't included in bulletin"
      (let [{bulletin-attachments :attachments} (query-bulletin pena app-id)]
        (count bulletin-attachments) => 1
        (fact "Only energiatodistus"
          (:id (first bulletin-attachments)) => (:id (last attachments))
          (get-in (first bulletin-attachments) [:type :type-id]) => "vastaanottopaikan_tiedot")))

    (facts "States"
      (fact "Can't move to verdictGiven until verdict is given"
        (command olli :move-to-verdict-given
                 :id app-id
                 :verdictGivenAt ts-now
                 :appealPeriodStartsAt ts-now
                 :appealPeriodEndsAt (+ 1 ts-now)
                 :verdictGivenText "foo") => (partial expected-failure? :error.command-illegal-state))
      (fact "Can't move to final until verdict is given"
        (command olli :move-to-final
                 :id app-id
                 :officialAt ts-now) => (partial expected-failure? :error.command-illegal-state))
      (give-generic-legacy-verdict olli app-id {:fields     {:kuntalupatunnus "12330-2016"
                                                             :verdict-code    "2"
                                                             :handler         "Olli Oulu"
                                                             :verdict-text    "Lorem Ipsum"
                                                             :anto            (timestamp "18.9.2018")
                                                             :lainvoimainen   (timestamp "18.10.2018")}
                                                :attachment {:state      "verdictGiven"
                                                             :type-group "muut"}})
      (fact "Cant move to final before bulletin of verdictGiven exists"
        (command olli :move-to-final
                 :id app-id
                 :officialAt ts-now) => (partial expected-failure? :error.bulletin.missing-verdict-given-bulletin)))

    (facts "Publishing verdict given"
      (command olli :move-to-verdict-given
               :id app-id
               :verdictGivenAt ts-now
               :appealPeriodStartsAt ts-now
               :appealPeriodEndsAt (util/get-timestamp-ago :day 2) ; the past
               :verdictGivenText "foo") => (partial expected-failure? :error.startdate-before-enddate)
      (command olli :move-to-verdict-given
               :id app-id
               :verdictGivenAt ts-now
               :appealPeriodStartsAt ts-now
               :appealPeriodEndsAt (util/get-timestamp-from-now :day 2)
               :verdictGivenText "foo") => ok?
      (fact "Can't move twice"
        (command olli :move-to-verdict-given
                 :id app-id
                 :verdictGivenAt ts-now
                 :appealPeriodStartsAt ts-now
                 :appealPeriodEndsAt (util/get-timestamp-from-now :day 2)
                 :verdictGivenText "foo") => (partial expected-failure? :error.bulletin.verdict-given-bulletin-exists)))
    (facts "Moving to final"
      (fact "Can't move to final if appeal date is still acitve"
        (command olli :move-to-final
                 :id app-id
                 :officialAt ts-now) => (partial expected-failure? :error.bulletin.official-before-appeal-period))
      (fact "OK if date same or greater than appealPeriodEndsAt"
        (command olli :move-to-final
                 :id app-id
                 :officialAt (util/get-timestamp-from-now :day 2)) => ok?))))

(facts "Add comment for published bulletin"
  (let [store        (atom {})
        cookie-store (doto (->cookie-store store)
                       (.addCookie test-db-cookie))
        app          (create-and-send-application sonja :operation "lannan-varastointi"
                                                  :propertyId sipoo-property-id
                                                  :x 406898.625 :y 6684125.375
                                                  :address "Hitantine 108"
                                                  :state "sent")
        old-bulletin (create-application-and-bulletin :app app :cookie-store cookie-store)
        bulletin     (create-application-and-bulletin :app app :cookie-store cookie-store)
        _            (vetuma-util/authenticate-to-vetuma! cookie-store)
        files        (->> (json/decode (:body (send-file cookie-store)) true)
                          :files
                          (map #(select-keys % [:fileId :contentType :size :filename])))]

    (fact "unable to add comment for older version"
      (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId old-bulletin) :comment "foobar" :cookie-store cookie-store) => {:ok false :text "error.invalid-version-id"})
    (fact "unable to add empty comment"
      (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId bulletin) :comment "" :cookie-store cookie-store) => {:ok false :text "error.empty-comment"})
    (fact "unable to add comment for unknown bulletin"
      (command sonja :add-bulletin-comment :bulletinId "not-found" :bulletinVersionId (:versionId bulletin) :comment "foobar" :cookie-store cookie-store) => {:ok false :text "error.invalid-bulletin-id"})
    (fact "approve comment for latest version"
      (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId bulletin) :comment "foobar" :cookie-store cookie-store) => ok?)
    (fact "approve comment with attachment"
      (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId bulletin) :comment "foobar with file" :files files :cookie-store cookie-store) => ok?)
    (fact "comment attachment can be downloaded by authorized person"
      (let [resp (raw sonja :download-bulletin-comment-attachment :attachmentId (:fileId (first files)))
            headers (into {}
                          (for [[k v] (:headers resp)]
                            [(keyword k) v]))]
        (:status resp) => 200
        (:Content-Disposition headers) => "attachment;filename=\"sipoon_alueet.zip\""))
    (fact "random person cannot load comment attachment"
      (let [resp (raw pena :download-bulletin-comment-attachment :attachmentId (:fileId (first files)))]
        (:status resp) => 404))))

(clear-collection "application-bulletins")

(def local-db-name (str "test_app-ymp-bulletins-itest_" (now)))

(mongo/connect!)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

(facts* "Querying bulletins"
  (mongo/with-db local-db-name
    (let [oulu-app (create-and-submit-local-application pena :operation "jatteen-keraystoiminta"
                                                  :propertyId oulu-property-id
                                                  :x 430109.3125 :y 7210461.375
                                                  :address "Oulu 10")
          sipoo-app (create-and-submit-local-application pena :operation "lannan-varastointi"
                                                   :propertyId sipoo-property-id
                                                   :x 406898.625 :y 6684125.375
                                                   :address "Hitantine 108")
          _ (local-command olli :approve-application :id (:id oulu-app) :lang "fi") => ok?
          _ (local-command sonja :approve-application :id (:id sipoo-app) :lang "fi") => ok?
          _ (local-command olli :move-to-proclaimed
                     :id (:id oulu-app)
                     :proclamationStartsAt (util/get-timestamp-ago :day 1)
                     :proclamationEndsAt (util/get-timestamp-from-now :day 1)
                     :proclamationText "testi") => ok?
          _ (local-command sonja :move-to-proclaimed
                     :id (:id sipoo-app)
                     :proclamationStartsAt (util/get-timestamp-ago :day 1)
                     :proclamationEndsAt (util/get-timestamp-from-now :day 2)
                     :proclamationText "testi") => ok?
          _ (local-query pena :application-bulletins :page "1"
                        :searchText ""
                        :municipality nil
                        :state nil
                        :sort nil) => (partial expected-failure? :error.illegal-number)
          resp (local-query pena :application-bulletins :page 1 :searchText "" :municipality nil :state nil :sort nil) => ok?]
      (fact "Paging count"
        (:left resp) => -8)
      (fact "Two bulletins is returned"
        (count (:data resp)) => 2)

      (facts "Response data"
        (let [bulletin (query-bulletin local-query pena (:id oulu-app))]
          (keys bulletin) => (just [:id :_applicantIndex :address :applicant :attachments :versionId
                                    :bulletinState :documents :location :modified :municipality
                                    :primaryOperation :propertyId :state :stateSeq :canComment :pate-verdicts
                                    :verdicts :tasks :application-id :verdictData :category :bulletinOpDescription
                                    :proclamationText :proclamationEndsAt :proclamationStartsAt] :in-any-order)
          (fact "attachments only contain specified keys and nothing else"
            (map keys (:attachments bulletin)) => (has every? (just [:id :type :latestVersion :contents :target]))
            (map (comp keys :latestVersion) (:attachments bulletin)) => (has every? (just [:filename :contentType :fileId :size])))
          (fact "bulletin state is 'proclaimed'"
            (:bulletinState bulletin) => "proclaimed")
          (fact "each documents has schema definition"
            (:documents bulletin) => (partial every? :schema))
          (fact "no party documents"
            (:documents bulletin) => (partial every? #(not= (-> % :schema-info :type keyword) :party)))
          (fact "no document metadata in bulletins"
            (:documents bulletin) => (partial not-any? :meta))
          (fact "_applicantIndex is empty"
            (:_applicantIndex bulletin) => [])))

      (facts "Filters"
        (fact "Municipality"
          (->> (local-query pena :application-bulletin-municipalities)
               :municipalities
               (into [])) => (just "564" "753" :in-any-order)
          (let [{data :data} (local-query pena :application-bulletins :page 1 :searchText "" :municipality "753" :state nil :sort nil)]
            (count data) => 1
            (:id (first data)) => (:id sipoo-app)))

        (fact "State"
          (->> (local-query pena :application-bulletin-states)
               :states
               (into [])) => (just ["proclaimed"])
          (let [{data :data} (local-query pena :application-bulletins :page 1 :searchText "" :municipality nil :state "proclaimed" :sort nil)]
            (count data) => 2))

        (fact "Free text search - search term too long"
          (local-query pena :application-bulletins :page 1 :searchText (ss/join (repeat 200 "hitan")) :municipality nil :state nil :sort nil) => fail?)
        (fact "Free text"
          (let [{data :data} (local-query pena :application-bulletins :page 1 :searchText "hitan" :municipality nil :state nil :sort nil)]
            (count data) => 1
            (:id (first data)) => (:id sipoo-app))))

      (facts "verdict given bulletin"
        (let [_ (give-local-legacy-verdict olli (:id oulu-app) :kuntalupatunnus "12330-2016")
              ts (+ 1000 (util/get-timestamp-from-now :day 1))
              resp (local-command olli :move-to-verdict-given
                                 :id (:id oulu-app)
                                 :verdictGivenAt ts
                                 :appealPeriodStartsAt ts
                                 :appealPeriodEndsAt (+ 100000 ts)
                                 :verdictGivenText "foo")]
          (fact "move-to-verdict-given ok"
            resp => ok?)
          (facts "when the verdictGiven bulletin has been published"
            (let [f (partial local-query-with-timestamp (+ 50000 ts))
                  bulletin (query-bulletin f pena (:id oulu-app))]
              (fact "bulletin state is 'verdictGiven'"
                (:bulletinState bulletin) => "verdictGiven")
              (fact "State filter in search still works"
                (into [] (:states (f pena :application-bulletin-states))) => (just ["proclaimed" "verdictGiven"])
                (let [{data :data} (f pena :application-bulletins :page 1 :searchText "" :municipality nil :state "proclaimed" :sort nil)]
                  (count data) => 1) ; This is the proclaimed bulletin for sipoo-app
                (let [{data :data} (f pena :application-bulletins :page 1 :searchText "" :municipality nil :state "verdictGiven" :sort nil)]
                  (count data) => 1))))))

      (facts "Paging"
        (dotimes [_ 20]
          (let [{id :id} (create-and-submit-local-application pena :operation "jatteen-keraystoiminta"
                                                              :propertyId oulu-property-id
                                                              :x 430109.3125 :y 7210461.375
                                                              :address "Oulu 10")]
            (local-command olli :approve-application :id id :lang "fi") => ok?
            (local-command olli :move-to-proclaimed
                     :id id
                     :proclamationStartsAt (now)
                     :proclamationEndsAt (util/get-timestamp-from-now :day 1)
                     :proclamationText "testi")))
        (let [{p1-data :data p1-left :left} (local-query pena :application-bulletins :page 1
                                                         :searchText ""
                                                         :municipality nil
                                                         :state nil
                                                         :sort nil)
              {p2-data :data p2-left :left} (local-query pena :application-bulletins :page 2
                                                         :searchText ""
                                                         :municipality nil
                                                         :state nil
                                                         :sort nil)
              {p3-data :data p3-left :left} (local-query pena :application-bulletins :page 3
                                                         :searchText ""
                                                         :municipality nil
                                                         :state nil
                                                         :sort nil)]
          (fact "page 1"
            (count p1-data) => 10
            p1-left => 12)
          (fact "page 2"
            (count p2-data) => 10
            p2-left => 2)
          (fact "page 3"
            (count p3-data) => 2
            p3-left => -8))))))
