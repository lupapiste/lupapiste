(ns lupapalvelu.application-ymp-bulletins-itest
  (:require [lupapalvelu.application-bulletins-itest-util :refer [create-application-and-bulletin send-file]]
            [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.pate-legacy-itest-util :refer [give-generic-legacy-verdict give-local-legacy-verdict]]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.strings :as ss]))

(set! *warn-on-reflection* true)

(defn now-ts [change-fn]
  (-> (date/now) (change-fn) (date/timestamp)))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  ;; Disable KuntaGML integration, so we don't have to add required fields for every application;
  ;; the KuntaGML generation for YM is already tested in krysp-itest
  (fact "Disable YM KuntaGML integration"
    (command sipoo :set-krysp-endpoint
             :organizationId "753-R"
             :permitType "YM"
             :username ""
             :password ""
             :url ""
             :version "") => ok?)

  (create-and-send-application sonja :operation "lannan-varastointi"
                               :propertyId sipoo-property-id
                               :x 406898.625 :y 6684125.375
                               :address "Hitantine 108"
                               :state "sent")

  (facts "Check if application is publishable"
    (let [r-app  (create-and-submit-application sonja :operation "kerrostalo-rivitalo"
                                                :propertyId sipoo-property-id
                                                :x 406898.625 :y 6684125.375
                                                :address "Hitantine 108")
          ym-app (create-and-submit-application olli :operation "lannan-varastointi"
                                                :propertyId oulu-property-id
                                                :x 430109.3125 :y 7210461.375
                                                :address "Oulu 10")]
      (fact "R permit can not be published"
        (query sonja :ymp-publish-bulletin-enabled :id (:id r-app))
        => unauthorized?)
      (fact "YM permit can be published"
        (query olli :ymp-publish-bulletin-enabled :id (:id ym-app)) => ok?)))

  (facts "Publishing YMP bulletins"
    (let [ts-now                     (now)
          app                        (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                                    :propertyId oulu-property-id
                                                                    :x 430109.3125 :y 7210461.375
                                                                    :address "Oulu 10")
          app-id                     (:id app)
          up-1                       (upload-file-and-bind pena app-id {:type {:type-group "muut" :type-id "muu"}})
          up-2                       (upload-file-and-bind pena app-id {:type {:type-group "kartat" :type-id "jatteen-sijainti"}})
          up-3                       (upload-file-and-bind pena app-id {:type {:type-group "jatteen_kerays" :type-id "vastaanottopaikan_tiedot"}})
          {attachments :attachments} (query-application pena app-id)]
      (fact "upload ok"
        up-1 => truthy
        up-2 => truthy
        up-3 => truthy)
      (fact "Pena sets CV not public"
        (command pena :set-attachment-visibility :id app-id :attachmentId (:id (first attachments)) :value "asiakas-ja-viranomainen") => ok?)
      (fact "Pena sets tutkintotodistus only visible to authorities"
        (command pena :set-attachment-visibility :id app-id :attachmentId (:id (second attachments)) :value "viranomainen") => ok?)
      (fact "approve application to 'sent' state"
        (command olli :approve-application :id app-id :lang "fi") => ok?)

      (fact "publishing with wrong id results in error"
        (command olli :move-to-proclaimed
                 :id "LP-123-2026-10000"
                 :proclamationStartsAt 123
                 :proclamationEndsAt 124
                 :proclamationText "foo") => (partial expected-failure? :error.application-not-accessible))

      (fact "Bulletin not found before publishing"
        (query pena :public-bulletin :bulletinId app-id) => (partial expected-failure? :error.bulletin.not-found))

      (fact "Authority can't publish to wrong states"
        (command olli :move-to-verdict-given
                 :id app-id
                 :verdictGivenAt ts-now
                 :appealPeriodStartsAt ts-now
                 :appealPeriodEndsAt (+ 1 ts-now)
                 :verdictGivenText "foo") => unauthorized?
        (command olli :move-to-final
                 :id app-id
                 :officialAt 123) => unauthorized?)

      (facts "Authority publishes"
        (fact "Cant publish if start date is after end date"
          (command olli :move-to-proclaimed
                   :id app-id
                   :proclamationStartsAt (now-ts #(date/plus % :days 2))
                   :proclamationEndsAt (now-ts #(date/plus % :day))
                   :proclamationText "foo") => (partial expected-failure? :error.startdate-before-enddate))
        (fact "Can publish bulletin where proclamation starts tomorrow"
          (command olli :move-to-proclaimed
                   :id app-id
                   :proclamationStartsAt (now-ts #(date/plus % :day))
                   :proclamationEndsAt (now-ts #(date/plus % :days 2))
                   :proclamationText "foo") => ok?)

        (fact "..but bulletin is not included in query until tomorrow"
          (let [{data :data ok :ok} (datatables pena :application-bulletins :page 1 :searchText "")]
            ok => true
            (count data) => 0))

        (fact "Bulletin query fails for user that are not application authorities"
          (query pena :public-bulletin :bulletinId app-id) => (err :error.bulletin.not-found)
          (query sonja :public-bulletin :bulletinId app-id) => (err :error.bulletin.not-found))

        (fact "Bulletin query succeeds for Olli with a note"
          (-> (query olli :public-bulletin :bulletinId app-id) :bulletin :note)
          => "bulletin.not-active")

        (fact "Bulletin pdf export fails for user that are not application authorities"
          (raw pena :bulletin-pdf-export :bulletinId app-id) => http404?
          (raw sonja :bulletin-pdf-export :bulletinId app-id) => http404?)

        (fact "Bulletin pdf export succeeds for Olli"
          (raw olli :bulletin-pdf-export :bulletinId app-id) => http200?)

        (fact "Bulletin doc download fails for unknown bulletin"
          (raw pena :download-bulletin-doc :bulletinId "not-found") => http404?)

        (fact "Bulletin doc download fails for inaccessible bulletin"
          (raw pena :download-bulletin-doc :bulletinId app-id) => http404?)

        (fact "Bulletin is published with immediate proclamation"
          (command olli :move-to-proclaimed
                   :id app-id
                   :proclamationStartsAt ts-now
                   :proclamationEndsAt (now-ts #(date/plus % :day))
                   :proclamationText "foo2") => ok?
          (fact "it is visible in query"
            (->
              (datatables pena :application-bulletins :page 1 :searchText "")
              :data
              count) => 1
            (facts "No note"
              (fact "Pena"
                (let [{:keys [bulletin]} (query pena :public-bulletin :bulletinId app-id)]
                  bulletin => truthy
                  (:note bulletin) => nil))
              (fact "Olli"
                (let [{:keys [bulletin]} (query olli :public-bulletin :bulletinId app-id)]
                  bulletin => truthy
                  (:note bulletin) => nil))))))

      (facts "Save proclaimed bulletin"
        (let [{:keys [bulletin]} (query olli :public-bulletin :bulletinId app-id)
              version-id         (:versionId bulletin)
              starts-at          (now-ts #(date/minus % :hour))
              ends-at            (now-ts #(date/plus % :days 3))]
          (fact "Can save proclaimed bulletin"
            (command olli :save-proclaimed-bulletin
                     :id app-id
                     :bulletinVersionId version-id
                     :proclamationStartsAt starts-at
                     :proclamationEndsAt ends-at
                     :proclamationText "updated proclaimed text") => ok?
            (let [{saved-bulletin :bulletin} (query olli :public-bulletin :bulletinId app-id)]
              (:proclamationText saved-bulletin) => "updated proclaimed text"
              (:proclamationStartsAt saved-bulletin) => starts-at
              (:proclamationEndsAt saved-bulletin) => ends-at))

          (fact "Invalid date order"
            (command olli :save-proclaimed-bulletin
                     :id app-id
                     :bulletinVersionId version-id
                     :proclamationStartsAt (now-ts #(date/plus % :days 2))
                     :proclamationEndsAt (now-ts #(date/plus % :day))
                     :proclamationText "foo")
            => (partial expected-failure? :error.startdate-before-enddate))

          (fact "Invalid bulletin id"
            (command olli :save-proclaimed-bulletin
                     :id "LP-123-2099-99999"
                     :bulletinVersionId version-id
                     :proclamationStartsAt starts-at
                     :proclamationEndsAt ends-at
                     :proclamationText "foo")
            => (partial expected-failure? :error.invalid-bulletin-id))

          (fact "Regular user cannot save proclaimed bulletin"
            (command pena :save-proclaimed-bulletin
                     :id app-id
                     :bulletinVersionId version-id
                     :proclamationStartsAt starts-at
                     :proclamationEndsAt ends-at
                     :proclamationText "foo") => unauthorized?)))

      (fact "Regular user can't publish bulletin"
        (command pena :move-to-proclaimed
                 :id app-id
                 :proclamationStartsAt 1449153132436
                 :proclamationEndsAt 1449153132436
                 :proclamationText "foo") => unauthorized?)

      (fact "Not public attachments aren't included in bulletin"
        (let [{bulletin-attachments :attachments} (query-bulletin :public-bulletin pena app-id)]
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
                   :verdictGivenText "foo") => unauthorized?)
        (fact "Can't move to final until verdict is given"
          (command olli :move-to-final
                   :id app-id
                   :officialAt ts-now) => unauthorized?))

      (let [verdict-id            (give-generic-legacy-verdict olli app-id
                                                               {:fields     {:kuntalupatunnus "12330-2016"
                                                                             :verdict-code    "2"
                                                                             :handler         "Olli Oulu"
                                                                             :verdict-text    "Lorem Ipsum"
                                                                             :anto            (date/timestamp "18.9.2018")
                                                                             :lainvoimainen   (date/timestamp "18.10.2018")}
                                                                :attachment {:state      "verdictGiven"
                                                                             :type-group "muut"}})
            move-to-verdict-given (fn [] (command olli :move-to-verdict-given
                                                  :id app-id
                                                  :verdictGivenAt ts-now
                                                  :appealPeriodStartsAt ts-now
                                                  :appealPeriodEndsAt (now-ts #(date/plus % :days 2))
                                                  :verdictGivenText "foo"))
            move-to-final         (fn [] (command olli :move-to-final
                                                  :id app-id
                                                  :officialAt (now-ts #(date/plus % :days 2))))]

        (fact "Cant move to final before bulletin of verdictGiven exists"
          (command olli :move-to-final
                   :id app-id
                   :officialAt ts-now) => (partial expected-failure? :error.bulletin.missing-verdict-given-bulletin))

        (facts "Publishing verdict given"
          (command olli :move-to-verdict-given
                   :id app-id
                   :verdictGivenAt ts-now
                   :appealPeriodStartsAt ts-now
                   :appealPeriodEndsAt (now-ts #(date/minus % :days 2)) ; the past
                   :verdictGivenText "foo") => (partial expected-failure? :error.startdate-before-enddate)
          (command olli :move-to-verdict-given
                   :id app-id
                   :verdictGivenAt (now-ts #(date/plus % :day))
                   :appealPeriodStartsAt ts-now
                   :appealPeriodEndsAt (now-ts #(date/plus % :days 2))
                   :verdictGivenText "Appeal starts before verdict")
          => (partial expected-failure? :error.startdate-before-enddate)
          (move-to-verdict-given) => ok?
          (fact "Can't move twice"
            (move-to-verdict-given) => (partial expected-failure? :error.bulletin.verdict-given-bulletin-exists)))
        (facts "Moving to final"
          (fact "Can't move to final if appeal date is still acitve"
            (command olli :move-to-final
                     :id app-id
                     :officialAt ts-now) => (partial expected-failure? :error.bulletin.official-before-appeal-period))
          (fact "OK if date same or greater than appealPeriodEndsAt"
            (move-to-final) => ok?))

        (facts "Removing verdict also removes verdict data from bulletin"
          (command olli :delete-pate-verdict :id app-id :verdict-id verdict-id) => ok?

          (let [bulletin (query-bulletin :public-bulletin olli app-id)]

            (fact "Bulletin state after removing verdict is proclaimed"
              (:bulletinState bulletin) => "proclaimed")

            (facts "Bulletin does not contain verdicts or verdict data after removing verdict"
              (-> bulletin :verdictData seq) => nil
              (-> bulletin :verdicts seq) => nil)

            (fact "Bulletin versions contain only praclaimed states"
              (->> bulletin
                   :versions
                   (remove #(= "proclaimed" (:bulletinState %)))
                   seq) => nil)))

        (give-generic-legacy-verdict olli app-id
                                     {:fields     {:kuntalupatunnus "12330-2016"
                                                   :verdict-code    "2"
                                                   :handler         "Olli Oulu"
                                                   :verdict-text    "Lorem Ipsum"
                                                   :anto            (date/timestamp "18.9.2018")
                                                   :lainvoimainen   (date/timestamp "18.10.2018")}
                                      :attachment {:state      "verdictGiven"
                                                   :type-group "muut"}})
        (move-to-verdict-given)
        (move-to-final))

      (facts "No continuation permit for environmental application"
        (fact "Change state to constructionStarted"
          (command olli :change-application-state :id app-id :state "constructionStarted") => ok?)
        (fact "Continuation permit creation not allowed"
          (command olli :create-continuation-period-permit :id app-id)
          => (err :error.unsupported-permit-type)))

      (facts "Bulletin reports are not supported for YMP bulletins"
        (query olli :verdict-bulletins :id app-id) => unauthorized?
        (raw olli :bulletin-report-pdf :id app-id
             :bulletinId app-id
             :lang "sv") => (contains {:body   "unauthorized"
                                       :status 401}))))

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
        (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId bulletin) :comment "" :cookie-store cookie-store)
        => {:ok false :text "error.missing-parameters" :parameters ["comment"]})
      (fact "unable to add comment for unknown bulletin"
        (command sonja :add-bulletin-comment :bulletinId "not-found" :bulletinVersionId (:versionId bulletin) :comment "foobar" :cookie-store cookie-store)
        => {:ok false :text "error.not-found"})
      (fact "unable to add comment with unlinked file"
        (command sonja :add-bulletin-comment
                 :bulletinId (:id app)
                 :bulletinVersionId (:versionId bulletin)
                 :comment "foobar"
                 :files [(assoc (first files) :fileId "aaaaaaaaaaaaaaaaaaaaaaaa")]
                 :cookie-store cookie-store)
        => (err :error.invalid-files-attached-to-comment))
      (fact "unable to add comment with schema-invalid file payload"
        (command sonja :add-bulletin-comment
                 :bulletinId (:id app)
                 :bulletinVersionId (:versionId bulletin)
                 :comment "foobar"
                 :files [(assoc (first files) :size "bad")]
                 :cookie-store cookie-store)
        => (err :error.illegal-value:schema-validation))
      (fact "approve comment for latest version"
        (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId bulletin) :comment "foobar" :cookie-store cookie-store) => ok?)
      (fact "approve comment with attachment"
        (command sonja :add-bulletin-comment :bulletinId (:id app) :bulletinVersionId (:versionId bulletin) :comment "foobar with file" :files files :cookie-store cookie-store) => ok?)
      (fact "comment attachment can be downloaded by authorized person"
        (let [resp    (raw sonja :download-bulletin-comment-attachment :attachmentId (:fileId (first files)))
              headers (into {}
                            (for [[k v] (:headers resp)]
                              [(keyword k) v]))]
          (:status resp) => 200
          (:Content-Disposition headers) => "attachment;filename=\"sipoon_alueet.zip\""))
      (fact "random person cannot load comment attachment"
        (let [resp (raw pena :download-bulletin-comment-attachment :attachmentId (:fileId (first files)))]
          (:status resp) => 404))))

  (clear-collection "application-bulletins")

  (facts* "Querying bulletins"
    ;; Disable KuntaGML integration, so we don't have to add required fields for every application;
    ;; the KuntaGML generation for YM is already tested in krysp-itest
    (local-command sipoo :set-krysp-endpoint
                   :organizationId "753-R"
                   :permitType "YM"
                   :username ""
                   :password ""
                   :url ""
                   :version "") => ok?

    (let [oulu-app  (create-and-submit-local-application pena :operation "jatteen-keraystoiminta"
                                                         :propertyId oulu-property-id
                                                         :x 430109.3125 :y 7210461.375
                                                         :address "Oulu 10")
          sipoo-app (create-and-submit-local-application pena :operation "lannan-varastointi"
                                                         :propertyId sipoo-property-id
                                                         :x 406898.625 :y 6684125.375
                                                         :address "Hitantine 108")
          _         (local-command olli :approve-application :id (:id oulu-app) :lang "fi")
          =>        ok?
          _         (local-command sonja :approve-application :id (:id sipoo-app) :lang "fi")
          =>        ok?
          _         (local-command olli :move-to-proclaimed
                                   :id (:id oulu-app)
                                   :proclamationStartsAt (now-ts #(date/minus % :day))
                                   :proclamationEndsAt (now-ts #(date/plus % :day))
                                   :proclamationText "testi")
          =>        ok?
          _         (local-command sonja :move-to-proclaimed
                                   :id (:id sipoo-app)
                                   :proclamationStartsAt (now-ts #(date/minus % :day))
                                   :proclamationEndsAt (now-ts #(date/plus % :days 2))
                                   :proclamationText "testi")
          =>        ok?
          _         (local-query pena :application-bulletins :page "bad" :searchText "")
          =>        (err :error.illegal-value:schema-validation)
          resp      (local-query pena :application-bulletins :page 214748365 :searchText "")
          =>        (err :error.illegal-value:schema-validation)
          resp      (local-query pena :application-bulletins :page 1 :searchText "")
          =>        ok?]
      (fact "Paging count"
        (:left resp) => -8)
      (fact "Two bulletins is returned"
        (count (:data resp)) => 2)

      (facts "Response data"
        (let [bulletin (query-bulletin local-query :public-bulletin pena (:id oulu-app))]
          (keys bulletin) => (just [:id :_applicantIndex :address :applicant :attachments :versionId
                                    :bulletinState :documents :location :modified :municipality :permitType
                                    :primaryOperation :propertyId :state :stateSeq :commentDeadline :pate-verdicts
                                    :verdicts :application-id :verdictData :category :bulletinOpDescription
                                    :proclamationText :proclamationEndsAt :proclamationStartsAt :organization :public?
                                    :bulletinVersionCreated :bulletinProcess]
                                   :in-any-order)
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
          (let [{data :data} (local-query pena :application-bulletins :page 1
                                          :searchText "" :municipality "753")]
            (count data) => 1
            (:id (first data)) => (:id sipoo-app)))

        (fact "State"
          (->> (local-query pena :application-bulletin-states)
               :states
               (into [])) => (just ["proclaimed"])
          (let [{data :data} (local-query pena :application-bulletins :page 1 :searchText ""
                                          :state "proclaimed")]
            (count data) => 2))

        (fact "Category"
          (->> (local-query pena :application-bulletin-categories)
               :categories
               (into [])) => (just ["ymp"])
          (let [{data :data} (local-query pena :application-bulletins :page 1
                                          :searchText "" :category "ymp")]
            (count data) => 2)
          (let [{data :data} (local-query pena :application-bulletins :page 1
                                          :searchText "" :category "r")]
            (count data) => 0))

        (fact "Free text search - search term too long"
          (local-query pena :application-bulletins :page 1
                       :searchText (ss/join (repeat 200 "hitan"))) => fail?)
        (fact "Free text"
          (let [{data :data} (local-query pena :application-bulletins :page 1
                                          :searchText "hitan")]
            (count data) => 1
            (:id (first data)) => (:id sipoo-app))))

      (facts "verdict given bulletin"
        ;; YMP application, no kuulutus
        (let [_    (give-local-legacy-verdict olli (:id oulu-app) :YM false :kuntalupatunnus "12330-2016" :julkipano nil)
              ts   (+ 1000 (now-ts #(date/plus % :day)))
              resp (local-command olli :move-to-verdict-given
                                  :id (:id oulu-app)
                                  :verdictGivenAt ts
                                  :appealPeriodStartsAt ts
                                  :appealPeriodEndsAt (+ 100000 ts)
                                  :verdictGivenText "foo")]
          (fact "move-to-verdict-given ok"
            resp => ok?)
          (facts "when the verdictGiven bulletin has been published"
            (let [f        (partial local-query-with-timestamp (+ 50000 ts))
                  bulletin (query-bulletin f :public-bulletin pena (:id oulu-app))]
              (fact "bulletin state is 'verdictGiven'"
                (:bulletinState bulletin) => "verdictGiven")
              (fact "State filter in search still works"
                (into [] (:states (f pena :application-bulletin-states))) => (just ["proclaimed" "verdictGiven"])
                (let [{data :data} (f pena :application-bulletins :page 1 :searchText ""
                                      :state "proclaimed")]
                  (count data) => 1) ; This is the proclaimed bulletin for sipoo-app
                (let [{data :data} (f pena :application-bulletins :page 1 :searchText ""
                                      :state "verdictGiven")]
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
                           :proclamationEndsAt (now-ts #(date/plus % :day))
                           :proclamationText "testi")))
        (let [{p1-data :data p1-left :left} (local-query pena :application-bulletins :page 1
                                                         :searchText "")
              {p2-data :data p2-left :left} (local-query pena :application-bulletins :page 2
                                                         :searchText "")
              {p3-data :data p3-left :left} (local-query pena :application-bulletins :page 3
                                                         :searchText "")]
          (fact "page 1"
            (count p1-data) => 10
            p1-left => 12)
          (fact "page 2"
            (count p2-data) => 10
            p2-left => 2)
          (fact "page 3"
            (count p3-data) => 2
            p3-left => -8)))))

  (facts "bulletin comments"
    (let [{id :id}           (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                            :propertyId oulu-property-id
                                                            :x 430109.3125 :y 7210461.375
                                                            :address "Oulu 10")
          _                  (command olli :approve-application :id id :lang "fi")
          _                  (command olli :move-to-proclaimed
                                      :id id
                                      :proclamationStartsAt (now)
                                      :proclamationEndsAt (now-ts #(date/plus % :day))
                                      :proclamationText "testi")
          {:keys [bulletin]} (query olli :public-bulletin :bulletinId id)]
      (:commentDeadline bulletin) => (-> (date/today) (date/plus :day)
                                         date/end-of-day date/timestamp)
      (command olli :add-manual-bulletin-comment
               :id id
               :bulletinId id
               :bulletinVersionId (:versionId bulletin)
               :comment "Vastustan"
               :contact {:firstName "Teemu"
                         :lastName  "Testaaja"
                         :street    "Striit"
                         :zip       "12345"
                         :city      "Siti"
                         :email     "maili@foo.com"}) => ok?)))
