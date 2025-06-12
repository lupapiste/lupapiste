(ns lupapalvelu.backing-system.dynasty.writer-itest
  "Note that we mock side-effects, except for mongo"
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.backing-system.dynasty.api.client-proto :as client-proto]
            [lupapalvelu.backing-system.dynasty.message :as message]
            [lupapalvelu.backing-system.dynasty.util :as d-util]
            [lupapalvelu.backing-system.dynasty.writer :as writer]
            [lupapalvelu.itest-util :refer [local-fixture-factory]]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(def server-config {:host         "localhost"
                    :organization "3"
                    :auth         {:host          "google.com"
                                   :client-id     "client"
                                   :client-secret "hunter2"}})

(def app-id "LP-297-2022-90001")
(def app {:id               app-id
          :primaryOperation {:name "yl-olemassa-oleva-toiminta"}
          :attachments      [{:id            "535f5fac0a5a948174ef2592"
                              :latestVersion {:created      #inst "2022-12-21T09:30:00Z"
                                              :filename     "some_file.pdf"
                                              :content-type "application/pdf"
                                              :fileId       "05b3af41-147f-4109-bf97-535f5fac0a5a"}
                              :metadata      {:nakyvyys "julkinen"}
                              :type          {:type-id    "palotekninen_lausunto"
                                              :type-group "pelastusviranomaiselle_esitettavat_suunnitelmat"}}]
          :created          #inst "2022-12-05T09:00:00Z"
          :creator          {:firstName "Pena"
                             :lastName  "Banaani"}
          :documents        [{:data        {:_selected "henkilo"
                                            :henkilo   {:henkilotiedot {:etunimi  {:value "Sonja"}
                                                                        :sukunimi {:value "Sibbo"}}
                                                        :osoite        {:katu                 {:value "Katuosoite 1 a 1"}
                                                                        :maa                  {:value "FIN"}
                                                                        :postinumero          {:value "33456"}
                                                                        :postitoimipaikannimi {:value "Sipoo"}}
                                                        :yhteystiedot  {:email   {:value "sonja.sibbo@sipoo.fi"}
                                                                        :puhelin {:value "03121991"}}}}
                              :id          "133f5fa30a5a94e174ef2592"
                              :schema-info {:subtype :hakija}}]
          :municipality     "297"
          :permitType       "YL"})
(def user {:id       "132"
           :username "foo"})

(use-fixtures :each (local-fixture-factory)
  (fn [test-fn]
    (mongo/insert :applications app)
    (test-fn)))

(deftest write-case-test
  (let [expected-request {:url         "localhost/dynasty.externalinterface.oauth/api/v2/cases"
                          :auth        {:url           "google.com/dynasty.identityserver/connect/token"
                                        :client-id     "client"
                                        :client-secret "hunter2"}
                          :headers     {:organization "3"}
                          :form-params {:tosClass         "11.01.00"
                                        :organisation     {:name "Alueellinen ympäristönsuojelu"}
                                        :metaJSON         "{\"SomeData\":\"foo\"}"
                                        :title            "Ympäristölupahakemus"
                                        :personalData     "Sisältää henkilötietoja"
                                        :caseBase         {:id "IB2"}
                                        :pendingStartDate "2022-12-05T11:00:00"
                                        :caseDate         "2022-12-21T12:00:00"
                                        :owner            "ympäristöjohtaja"}}
        case-id          (rand-int 1000)
        mock-client      (reify client-proto/DynastyClient
                           (-get [_ opts]
                             (throw (ex-info "Unexpected get" opts)))
                           (-post [_ {:keys [form-params] :as req}]
                             (is (= expected-request req))
                             ;; Produce a mock response
                             (-> form-params
                                 (dissoc :metaJSON)
                                 (assoc :id case-id
                                        :schemaId (str (random-uuid))
                                        :caseNumber "1234"
                                        :publicityClassName "foo"
                                        :isPublic true))))]
    (binding [writer/*dynasty-client* mock-client]
      (is (= {:case-id case-id}
             (->> [{::message/message-type ::message/case
                    ::message/payload      {:case-base          {:id "IB2"}
                                            :case-date          "2022-12-21T12:00:00"
                                            :meta-json          {:some-data "foo"}
                                            :organisation       {:name "Alueellinen ympäristönsuojelu"}
                                            :owner              "ympäristöjohtaja"
                                            :pending-start-date "2022-12-05T11:00:00"
                                            :personal-data      "Sisältää henkilötietoja"
                                            :title              "Ympäristölupahakemus"
                                            :tos-class          "11.01.00"}}]
                  (writer/submit-messages! server-config app user))))
      (testing "Case id is persisted"
        (let [app (mongo/by-id :applications app-id)]
          (is (= case-id (d-util/get-case-id app))))))))

(deftest write-document-test
  (let [case-id          (rand-int 1000)
        expected-request {:url       "localhost/dynasty.externalinterface.oauth/api/v2/documents"
                          :auth      {:url           "google.com/dynasty.identityserver/connect/token"
                                      :client-id     "client"
                                      :client-secret "hunter2"}
                          :headers   {:organization "3"}
                          :multipart [{:name      "MetaData"
                                       :content   {:title                 "Ympäristölupahakemus"
                                                   :state                 "Draft"
                                                   :personalData          "Sisältää henkilötietoja"
                                                   :isPublic              true
                                                   :publicityClassName    "Julkinen"
                                                   :caseAttachmentOptions {:caseSelector  {:id case-id}
                                                                           :phaseSelector {:smartAttachOptions {:pathSelector [{:title "Vireilletulo"}]}}
                                                                           :tosTitle      "Hakemus liitteineen (myös yhteislupa)"}
                                                   :metaJSON              {:SomeData "foo"}
                                                   :arrivalType           "0"}
                                       :mime-type "application/json"
                                       :encoding  "UTF-8"}
                                      {:part-name "File"
                                       :mime-type "application/pdf"
                                       :name      "LP-297-2022-90001.pdf"}]}
        document-id      (rand-int 1000)
        mock-client      (reify client-proto/DynastyClient
                           (-get [_ opts]
                             (throw (ex-info "Unexpected get" opts)))
                           (-post [_ req]
                             (testing "upload contains a file"
                               (is (instance? File (get-in req [:multipart 1 :content]))))
                             ;; To make comparison easier, convert from JSON strings to maps
                             (let [req-as-data (-> req
                                                   (update-in [:multipart 0 :content] json/decode keyword)
                                                   (update-in [:multipart 0 :content :metaJSON] json/decode keyword)
                                                   ;; Compare file separately
                                                   (update-in [:multipart 1] dissoc :content))]
                               (is (= expected-request req-as-data))
                               ;; Produce a mock response
                               (-> req-as-data
                                   (get-in [:multipart 0 :content])
                                   (assoc :id document-id
                                          :schemaId (str (random-uuid))
                                          :guid (str (random-uuid))
                                          :type "DOCUMENT")))))]
    (d-util/set-application-case-id! app-id case-id)
    (let [app (mongo/by-id :applications app-id)]
      (binding [writer/*dynasty-client* mock-client
                writer/*generate-pdf*   (fn [& args]
                                          (is (every? some? args)))]
        (is (= {:document-id document-id}
               (->> [{::message/message-type ::message/document
                      ::message/payload      {:title                   "Ympäristölupahakemus"
                                              :state                   "Draft"
                                              :personal-data           "Sisältää henkilötietoja"
                                              :is-public               true
                                              :publicity-class-name    "Julkinen"
                                              :case-attachment-options {:case-selector  {}
                                                                        :phase-selector {:smart-attach-options {:path-selector [{:title "Vireilletulo"}]}}
                                                                        :tos-title      "Hakemus liitteineen (myös yhteislupa)"}
                                              :meta-json               {:some-data "foo"}
                                              :arrival-type            "0"}
                      ::message/file-entry   {:mime-type "application/pdf"
                                              :name      "LP-297-2022-90001.pdf"}}]
                    (writer/submit-messages! server-config app user))))
        (testing "Document id is persisted"
          (let [app (mongo/by-id :applications app-id)]
            (is (= document-id (d-util/get-document-id app)))))))))

(deftest write-contact-test
  (let [case-id          (rand-int 1000)
        expected-request {:url         (format "localhost/dynasty.externalinterface.oauth/api/v2/cases/%s/contacts" case-id)
                          :auth        {:url           "google.com/dynasty.identityserver/connect/token"
                                        :client-id     "client"
                                        :client-secret "hunter2"}
                          :headers     {:organization "3"}
                          :form-params {:channel     "NO_CHANNEL"
                                        :contactInfo {:city          "Sipoo"
                                                      :country       "FIN"
                                                      :email         "sonja.sibbo@sipoo.fi"
                                                      :phone         "03121991"
                                                      :postalAddress "Katuosoite 1 a 1"
                                                      :postalCode    "33456"}
                                        :person      {:firstName "Sonja"
                                                      :lastName  "Sibbo"}
                                        :role        "Vireillepanija"
                                        :type        "Person"}}
        contact-id       (str (random-uuid))
        mock-client      (reify client-proto/DynastyClient
                           (-get [_ opts]
                             (throw (ex-info "Unexpected get" opts)))
                           (-post [_ {:keys [form-params] :as req}]
                             (is (= expected-request req))
                             ;; Produce a mock response
                             (-> form-params
                                 (assoc :guid contact-id))))]
    (d-util/set-application-case-id! app-id case-id)
    (let [app (mongo/by-id :applications app-id)]
      (binding [writer/*dynasty-client* mock-client]
        (is (= {:contact-ids #{contact-id}}
               (->> [{::message/message-type   ::message/contact
                      ::message/lp-document-id "133f5fa30a5a94e174ef2592"
                      ::message/payload        {:channel      "NO_CHANNEL"
                                                :contact-info {:city           "Sipoo"
                                                               :country        "FIN"
                                                               :email          "sonja.sibbo@sipoo.fi"
                                                               :phone          "03121991"
                                                               :postal-address "Katuosoite 1 a 1"
                                                               :postal-code    "33456"}
                                                :person       {:first-name "Sonja"
                                                               :last-name  "Sibbo"}
                                                :role         "Vireillepanija"
                                                :type         "Person"}}]
                    (writer/submit-messages! server-config app user))))
        (testing "Contact id is persisted"
          (let [app (mongo/by-id :applications app-id)]
            (is (= contact-id
                   (d-util/get-applicant-contact-id (-> app :documents first))))))))))

(deftest write-attachment-test
  (let [case-id          (rand-int 1000)
        expected-request {:url       "localhost/dynasty.externalinterface.oauth/api/v2/documents"
                          :auth      {:url           "google.com/dynasty.identityserver/connect/token"
                                      :client-id     "client"
                                      :client-secret "hunter2"}
                          :headers   {:organization "3"}
                          :multipart [{:name      "MetaData"
                                       :content   {:title                 "Palotekninen lausunto"
                                                   :state                 "Draft"
                                                   :personalData          "Sisältää henkilötietoja"
                                                   :isPublic              true
                                                   :publicityClassName    "Julkinen"
                                                   :caseAttachmentOptions {:caseSelector  {:id case-id}
                                                                           :phaseSelector {:smartAttachOptions {:pathSelector [{:title "Vireilletulo"}]}}
                                                                           :tosTitle      "Muutoshakemus liitteineen"}
                                                   :metaJSON              {:PublicTitle "Palotekninen lausunto"
                                                                           :SomeData    "foo"}}
                                       :mime-type "application/json"
                                       :encoding  "UTF-8"}
                                      {:part-name "File"
                                       :mime-type "application/pdf"
                                       :name      "some_file.pdf"}]}
        document-id      (rand-int 1000)
        mock-client      (reify client-proto/DynastyClient
                           (-get [_ opts]
                             (throw (ex-info "Unexpected get" opts)))
                           (-post [_ req]
                             (testing "upload contains a file"
                               (let [f (get-in req [:multipart 1 :content])]
                                 (is (instance? File f))
                                 (is (= "attachment contents" (slurp f)))))

                             ;; To make comparison easier, convert from JSON strings to maps
                             (let [req-as-data (-> req
                                                   (update-in [:multipart 0 :content] json/decode keyword)
                                                   (update-in [:multipart 0 :content :metaJSON] json/decode keyword)
                                                   ;; Compare file separately
                                                   (update-in [:multipart 1] dissoc :content))]
                               (is (= expected-request req-as-data))
                               ;; Produce a mock response
                               (-> req-as-data
                                   (get-in [:multipart 0 :content])
                                   (assoc :id document-id
                                          :schemaId (str (random-uuid))
                                          :guid (str (random-uuid))
                                          :type "DOCUMENT")))))]
    (d-util/set-application-case-id! app-id case-id)
    (let [app (mongo/by-id :applications app-id)]
      (binding [writer/*dynasty-client*   mock-client
                writer/*storage-download* (fn [application file-id]
                                            (is (= app application))
                                            (is (= "05b3af41-147f-4109-bf97-535f5fac0a5a" file-id))
                                            {:content     (fn [] (io/input-stream (.getBytes "attachment contents")))
                                             :contentType "application/pdf"
                                             :filename    "some_file.pdf"})]
        (is (= {:attachment-ids #{document-id}
                :file-ids       #{"05b3af41-147f-4109-bf97-535f5fac0a5a"}}
               (->> [{::message/message-type  ::message/attachment
                      ::message/attachment-id "535f5fac0a5a948174ef2592"
                      ::message/file-id       "05b3af41-147f-4109-bf97-535f5fac0a5a"
                      ::message/payload       {:title                   "Palotekninen lausunto"
                                               :state                   "Draft"
                                               :personal-data           "Sisältää henkilötietoja"
                                               :is-public               true
                                               :publicity-class-name    "Julkinen"
                                               :case-attachment-options {:case-selector  {}
                                                                         :phase-selector {:smart-attach-options {:path-selector [{:title "Vireilletulo"}]}}
                                                                         :tos-title      "Muutoshakemus liitteineen"}
                                               :meta-json               {:public-title "Palotekninen lausunto"
                                                                         :some-data    "foo"}}}]
                    (writer/submit-messages! server-config app user))))
        (testing "Document id is persisted in the attachment"
          (let [app (mongo/by-id :applications app-id)]
            (is (= document-id (d-util/get-attachment-document-id (-> app :attachments first))))))))))
