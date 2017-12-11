(ns lupapalvelu.premises-api-itest
  (:require [midje.sweet :refer :all]
            [sade.core :refer [now fail]]
            [lupapalvelu.itest-util :refer :all]
            [clojure.java.io :as io]))

(apply-remote-minimal)

(def filename "premises-data-example.xlsx")
(def filepath "dev-resources/")
(def file-source (str filepath filename))

(defn get-huoneistot-doc [application]
  (->> (:documents application)
       (filter #(= "uusiRakennus" (-> % :schema-info :name)))
       (first)))

(defn upload-premises
  [apikey filename doc app-id]
  (let [uploadfile (io/file filename)
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

(facts "Uploading excel updates huoneistot"
  (let [app-id (create-app-id pena) => ok?
        application (query-application pena app-id)
        doc (:id (get-huoneistot-doc application))]

    (fact "Uploading premises returns ok"
      (:status (upload-premises pena file-source doc app-id)) => 200)

    (let [updated-application (query-application pena app-id)
          updated-doc (get-huoneistot-doc updated-application)
          huoneisto (-> updated-doc :data :huoneistot :0)]
      (fact "Application now contains correct huoneisto data"
        (-> huoneisto :porras :value) => "A"
        (-> huoneisto :huoneistonumero :value) => "001"
        (-> huoneisto :jakokirjain :vale) => nil
        (-> huoneisto :huoneluku :value) => "2"
        (-> huoneisto :keittionTyyppi :value) => "keittotila"
        (-> huoneisto :huoneistoala :value) => "56"
        (-> huoneisto :WCKytkin :value) => true
        (-> huoneisto :ammeTaiSuihkuKytkin :value) => true
        (-> huoneisto :parvekeTaiTerassiKytkin :value) => true
        (-> huoneisto :saunaKytkin :value) => true
        (-> huoneisto :lamminvesiKytkin :value) => true)

      (fact "Application now contains information about the uploaded xlsx file"
        (-> updated-application :ifc-data :filename) => filename))))


; infinite error testi
