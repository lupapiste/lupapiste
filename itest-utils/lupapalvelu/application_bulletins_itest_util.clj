(ns lupapalvelu.application-bulletins-itest-util
  (:require [clojure.java.io :as io]
            [lupapalvelu.application-bulletin-utils :as bulletin-utils]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date])
  (:import [org.apache.http.client CookieStore]))

(set! *warn-on-reflection* true)

(defn send-file [cookie-store & [filename]]
  (without-anti-csrf
    (let [filename   (or filename "dev-resources/sipoon_alueet.zip")
          uploadfile (io/file filename)
          uri        (str (server-address) "/api/raw/upload-file")
          resp       (http-post uri
                                {:multipart        [{:name "files[]" :content uploadfile}]
                                 :throw-exceptions false
                                 :cookie-store     cookie-store})]
      resp)))

(defn create-application-and-bulletin [& args]
  (let [args         (if (map? args)
                       args
                       (apply hash-map args))
        cookie-store (if (contains? args :cookie-store)
                       (:cookie-store args)
                       (doto ^CookieStore (->cookie-store (atom {}))
                         (.addCookie test-db-cookie)))
        starts       (-> (date/now) (date/minus :day) (date/timestamp))
        ends         (-> (date/now) (date/plus :day) (date/timestamp))
        app          (if (contains? args :app)
                       (:app args)
                       (create-and-send-application sonja :operation "lannan-varastointi"
                                                    :propertyId sipoo-property-id
                                                    :x 406898.625 :y 6684125.375
                                                    :address "Hitantine 108"
                                                    :state "sent"))
        m2p-resp     (command sonja :move-to-proclaimed
                              :id (:id app)
                              :proclamationStartsAt starts
                              :proclamationEndsAt ends
                              :proclamationText "testi"
                              :cookie-store cookie-store)
        bulletin     (:bulletin (query pena :public-bulletin :bulletinId (:id app) :cookie-store cookie-store))]
    m2p-resp => ok?
    bulletin))

(defn mock-bulletin-item [bulletin-id section start-ts end-ts]
  (bulletin-utils/bulletin-list-item {:id       bulletin-id
                                      :versions [{:appealPeriodStartsAt start-ts
                                                  :appealPeriodEndsAt   end-ts
                                                  :application-id       "LP-000-2000-12345"
                                                  :address              "Testikuja 123"
                                                  :municipality         "Vantaa"
                                                  :propertyId           "0000"
                                                  :verdictData          {:section section}
                                                  :verdicts             [{:paatokset
                                                                          [{:poytakirjat
                                                                            [{:pykala       section
                                                                              :paatoskoodi  "myönnetty"
                                                                              :paatospvm    1234567}]}]}]}]}
                                         :fi))
