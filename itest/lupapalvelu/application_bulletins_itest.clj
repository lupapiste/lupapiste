(ns lupapalvelu.application-bulletins-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.mongo :as mongo]
            [clojure.java.io :as io]))

(when (sade.env/feature? :publish-bulletin)
  (apply-remote-minimal)

  (defn- send-comment [apikey id version-id comment & [filename]]
    (let [filename    (or filename "dev-resources/sipoon_alueet.zip")
          uploadfile  (io/file filename)
          uri         (str (server-address) "/api/raw/add-bulletin-comment")]
      (http-post uri
                 {:headers {"authorization" (str "apikey=" apikey)}
                  :multipart [{:name "bulletin-id" :content id}
                              {:name "bulletin-version-id" :content version-id}
                              {:name "bulletin-comment-field" :content comment}
                              {:name "files[]" :content uploadfile}]
                  :throw-exceptions false})))

  (facts "Publishing bulletins"
    (let [app (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                  :propertyId oulu-property-id
                                                  :x 430109.3125 :y 7210461.375
                                                  :address "Oulu 10")
          app-id (:id app)]
      (fact "approve application to 'sent' state"
        (command olli :approve-application :id app-id :lang "fi") => ok?)

      (fact "publishing with wrong id results in error"
        (command olli :publish-bulletin :id "123") => (partial expected-failure? :error.application-not-accessible))

      (fact "Bulletin not found before publishing"
        (query pena :bulletin :bulletinId app-id) => (partial expected-failure? :error.bulletin.not-found))

      (fact "Authority can publish bulletin"
        (command olli :publish-bulletin :id app-id) => ok?)
      (fact "Regular user can't publish bulletin"
        (command pena :publish-bulletin :id app-id) => fail?)))

  (facts "Add comment for published bulletin"
    (let [app (create-and-submit-application pena :operation "lannan-varastointi"
                                             :propertyId sipoo-property-id
                                             :x 406898.625 :y 6684125.375
                                             :address "Hitantine 108")
          _ (command sonja :publish-bulletin :id (:id app))
          old-bulletin (:bulletin (query pena :bulletin :bulletinId (:id app)))
          _ (command sonja :publish-bulletin :id (:id app))
          bulletin (:bulletin (query pena :bulletin :bulletinId (:id app)))]
      (fact "unable to add comment for older version"
        (:body (decode-response (send-comment sonja-id (:id app) (:versionId old-bulletin) "foobar"))) => {:ok false :text "error.invalid-version-id"})
      (fact "approve comment for latest version"
        (:body (decode-response (send-comment sonja-id (:id app) (:versionId bulletin) "foobar"))) => ok?)))

  (clear-collection "application-bulletins")

  (facts* "Querying bulletins"
    (let [oulu-app (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                  :propertyId oulu-property-id
                                                  :x 430109.3125 :y 7210461.375
                                                  :address "Oulu 10")
          sipoo-app (create-and-submit-application pena :operation "lannan-varastointi"
                                                  :propertyId sipoo-property-id
                                                  :x 406898.625 :y 6684125.375
                                                  :address "Hitantine 108")
          _ (command olli :approve-application :id (:id oulu-app) :lang "fi") => ok?
          _ (command sonja :approve-application :id (:id sipoo-app) :lang "fi") => ok?
          _ (command olli :publish-bulletin :id (:id oulu-app)) => ok?
          _ (command sonja :publish-bulletin :id (:id sipoo-app)) => ok?
          _ (datatables pena :application-bulletins :page "1"
                                                    :searchText ""
                                                    :municipality nil
                                                    :state nil
                                                    :sort nil) => (partial expected-failure? :error.illegal-number)
          resp (datatables pena :application-bulletins :page 1 :searchText "" :municipality nil :state nil :sort nil) => ok?]
      (fact "Two bulletins is returned"
        (count (:data resp)) => 2)

      (facts "Response data"
        (let [bulletin (query-bulletin pena (:id oulu-app))]
          (keys bulletin) => (just [:id :_applicantIndex :address :applicant :attachments :versionId
                                    :bulletinState :documents :location :modified :municipality
                                    :primaryOperation :propertyId :state :stateSeq] :in-any-order)
          (fact "bulletin state is 'proclaimed'"
            (:bulletinState bulletin) => "proclaimed")
          (fact "each documents has schema definition"
            (:documents bulletin) => (partial every? :schema))
          (fact "no party documents"
            (:documents bulletin) => (partial every? #(not= (-> % :schema-info :type keyword) :party)))
          (fact "_applicantIndex"
            (:_applicantIndex bulletin) => (just ["Panaani Pena"]))))

      (facts "Filters"
       (fact "Municipality"
         (:municipalities (query pena :application-bulletin-municipalities)) => (just ["564" "753"] :in-any-order)
         (let [{data :data} (datatables pena :application-bulletins :page 1 :searchText "" :municipality "753" :state nil :sort nil)]
           (count data) => 1
           (:id (first data)) => (:id sipoo-app)))

       (fact "State"
         (:states (query pena :application-bulletin-states)) => (just ["proclaimed"])
         (let [{data :data} (datatables pena :application-bulletins :page 1 :searchText "" :municipality nil :state "proclaimed" :sort nil)]
           (count data) => 2))

       (fact "Free text"
         (let [{data :data} (datatables pena :application-bulletins :page 1 :searchText "hitan" :municipality nil :state nil :sort nil)]
           (count data) => 1
           (:id (first data)) => (:id sipoo-app))))

      (facts "Paging"
       (dotimes [_ 20]
         (let [{id :id} (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                            :propertyId oulu-property-id
                                                            :x 430109.3125 :y 7210461.375
                                                            :address "Oulu 10")]
           (command olli :approve-application :id id :lang "fi") => ok?
           (command olli :publish-bulletin :id id)))
       (let [{p1-data :data p1-left :left} (datatables pena :application-bulletins :page 1
                                                                                   :searchText ""
                                                                                   :municipality nil
                                                                                   :state nil
                                                                                   :sort nil)
             {p2-data :data p2-left :left} (datatables pena :application-bulletins :page 2
                                                                                   :searchText ""
                                                                                   :municipality nil
                                                                                   :state nil
                                                                                   :sort nil)
             {p3-data :data p3-left :left} (datatables pena :application-bulletins :page 3
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
