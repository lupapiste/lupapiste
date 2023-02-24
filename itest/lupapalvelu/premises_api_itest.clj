(ns lupapalvelu.premises-api-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(def file-3-lines "premises-data-3-lines.xlsx")
(def file-1-line "premises-data-1-line.xlsx")
(def file-bad-file "premises-data-bad-file.xlsx")

(defn get-huoneistot-doc [application]
  (->> (:documents application)
       (filter #(= "uusiRakennus" (-> % :schema-info :name)))
       (first)))

(defn upload-premises
  [apikey filename doc app-id]
  (let [uploadfile (io/file (str "dev-resources/" filename))
        uri (str (server-address) "/api/raw/upload-premises-data")
        multipart [{:name "files[]" :content uploadfile}
                   {:name "Content/type" :content "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"}
                   {:name "id" :content app-id}
                   {:name "doc" :content doc}]
        params {:oauth-token      apikey
                :throw-exceptions false
                :multipart        multipart}
        resp (http-post uri params)]
    (decode-response resp)))

(facts "Uploading and downloading huoneistot excel"
  (let [app-id (create-app-id pena) => ok?
        application (query-application pena app-id)
        doc (:id (get-huoneistot-doc application))]

    (fact "Uploading premises returns ok"
      (-> (upload-premises pena file-3-lines doc app-id) :body :ok) => true)

    (let [updated-application (query-application pena app-id)
          updated-doc (get-huoneistot-doc updated-application)
          huoneistot (-> updated-doc :data :huoneistot (dissoc :validationResult))]

      (fact "Application now contains correct huoneisto data"
        (count huoneistot) => 3
        (-> huoneistot :0 :porras :value) => "A"
        (-> huoneistot :1 :porras :value) => "B"
        (-> huoneistot :2 :porras :value) => "C"
        (-> huoneistot :0 :huoneistonumero :value) => "001"
        (-> huoneistot :0 :jakokirjain :value) => nil
        (-> huoneistot :0 :huoneluku :value) => "2"
        (-> huoneistot :0 :keittionTyyppi :value) => "keittotila"
        (-> huoneistot :1 :huoneistoala :value) => "74.5"
        (-> huoneistot :1 :WCKytkin :value) => true
        (-> huoneistot :2 :ammeTaiSuihkuKytkin :value) => true
        (-> huoneistot :2 :parvekeTaiTerassiKytkin :value) => true
        (-> huoneistot :2 :saunaKytkin :value) => true
        (-> huoneistot :2 :lamminvesiKytkin :value) => true)

      (fact "Application now contains information about the uploaded xlsx file"
        (-> updated-application :ifc-data :filename) => file-3-lines)

      (fact "Updating application with bad excel file does not change data"
        (-> (upload-premises pena file-bad-file doc app-id) :body :ok) => false

        (let [updated-application (query-application pena app-id)
              updated-doc (get-huoneistot-doc updated-application)
              huoneistot (-> updated-doc :data :huoneistot (dissoc :validationResult))]

          (-> (query-application pena app-id) :ifc-data :filename) => file-3-lines
          (-> huoneistot :0 :porras :value) => "A"
          (-> huoneistot :1 :porras :value) => "B"
          (-> huoneistot :2 :porras :value) => "C")))

    (fact "Uploading new file with fewer lines returns ok"
      (-> (upload-premises pena file-1-line doc app-id) :body :ok) => true)

    (let [updated-application (query-application pena app-id)
          updated-doc (get-huoneistot-doc updated-application)
          huoneistot (-> updated-doc :data :huoneistot (dissoc :validationResult))]

      (fact "Application now has only one apartment"
        (count huoneistot) => 1)

      (fact "Application has correct premises data"
        (-> updated-application :ifc-data :filename) => file-1-line
        (-> huoneistot :0 :porras :value) => "G"
        (-> huoneistot :0 :huoneistonumero :value) => "007"
        (-> huoneistot :0 :huoneluku :value) => "7"
        (-> huoneistot :0 :keittionTyyppi :value) => "keittio"))

    (fact "Downloading huoneistot-excel"
      (let [resp (raw pena :download-premises-template :application-id app-id :document-id doc :lang "fi")]
        (:status resp) => 200))
    (fact "Only authority can upload in sent state"
      (command pena :submit-application :id app-id) => ok?
      (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin title") => ok?
      (command sonja :approve-application :id app-id :lang "fi") => ok?
      (upload-premises pena file-1-line doc app-id)
      => (contains {:body {:ok false :state "sent" :text "error.command-illegal-state"}})
      (-> (upload-premises sonja file-1-line doc app-id) :body :ok) => true)))
