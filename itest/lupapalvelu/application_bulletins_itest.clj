(ns lupapalvelu.application-bulletins-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.mongo :as mongo]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn- send-file [cookie-store & [filename]]
  (set-anti-csrf! false)
  (let [filename    (or filename "dev-resources/sipoon_alueet.zip")
        uploadfile  (io/file filename)
        uri         (str (server-address) "/api/upload/file")
        resp        (http-post uri
                               {:multipart [{:name "files[]" :content uploadfile}]
                                :throw-exceptions false
                                :cookie-store cookie-store})]
    (set-anti-csrf! true)
    resp))

(when (sade.env/feature? :publish-bulletin)
  (apply-remote-minimal)

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

  (facts* "Add comment for published bulletin"
    (let [store (atom {})
          cookie-store (doto (->cookie-store store)
                         (.addCookie test-db-cookie))
          app (create-and-send-application sonja :operation "lannan-varastointi"
                                             :propertyId sipoo-property-id
                                             :x 406898.625 :y 6684125.375
                                             :address "Hitantine 108"
                                             :state "sent")
          _ (command sonja :publish-bulletin :id (:id app) :cookie-store cookie-store) => ok?
          old-bulletin (:bulletin (query pena :bulletin :bulletinId (:id app) :cookie-store cookie-store))
          _ (command sonja :publish-bulletin :id (:id app) :cookie-store cookie-store)
          bulletin (:bulletin (query pena :bulletin :bulletinId (:id app) :cookie-store cookie-store))
          _ (vetuma-util/authenticate-to-vetuma! cookie-store)
          files (:files (json/decode (:body (send-file cookie-store)) true))]

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
        (let [resp (raw sonja :download-bulletin-comment-attachment :attachmentId (:id (first files)))
              headers (into {}
                        (for [[k v] (:headers resp)]
                          [(keyword k) v]))]
          (:status resp) => 200
          (:Content-Disposition headers) => "attachment;filename=\"sipoon_alueet.zip\""))
      (fact "random person cannot load comment attachment"
        (let [resp (raw pena :download-bulletin-comment-attachment :attachmentId (:id (first files)))]
          (:status resp) => 404))))

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
                                    :primaryOperation :propertyId :state :stateSeq :canComment] :in-any-order)
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
