(ns lupapalvelu.onnistuu-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :as u]
            [sade.http :as http]
            [lupapalvelu.onnistuu.process :as p]))

(defn get-process [process-id]
  (:process (u/query u/pena :find-sign-process :processId process-id)))

(defn get-process-status [process-id]
  (:status (get-process process-id)))

(defn init-sign []
  (-> (u/command u/pena :init-sign
                 :companyName "company-name"
                 :companyY    "1234567-8"
                 :firstName   "First"
                 :lastName    "Last"
                 :email       "email"
                 :lang        "fi")
      :processId
      get-process))

(fact "init-sign"
  (init-sign) => (contains {:stamp   #"[a-zA-Z0-9]{40}"
                            :company {:name "company-name"
                                      :y    "1234567-8"}
                            :signer {:first-name   "First"
                                     :last-name    "Last"
                                     :email        "email"
                                     :lang         "fi"}
                            :status  "created"}))

(fact "cancel"
  (let [process-id (:id (init-sign))]
    (u/command u/pena :cancel-sign :processId process-id)
    (get-process-status process-id) => "cancelled"))

(fact "cancel unknown"
  (u/command u/pena :cancel-sign :processId "fooo") => {:ok false, :text "error.unknown"})

(fact "Fetch document"
    (let [process-id (:id (init-sign))]
      (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 200})
      (get-process-status process-id) => "started"))

(fact "Fetch document for unknown process"
  (http/get (str (u/server-address) "/api/sign/document/" "foozaa") :throw-exceptions false) => (contains {:status 404}))

(fact "Can't fetch document on cancelled process"
  (let [process-id (:id (init-sign))]
    (u/command u/pena :cancel-sign :processId process-id)
    (http/get (str (u/server-address) "/api/sign/document/" process-id) :throw-exceptions false) => (contains {:status 400})))
