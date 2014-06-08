(ns lupapalvelu.onnistuu-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :as u]
            [sade.http :as http]
            [lupapalvelu.onnistuu.process :as p]))

(facts "start signing process"
  (let [cname   "Foo Oy"
        y       "FI2341528-4"
        resp    (u/command u/pena :init-sign :company-name cname :y y)
        id      (:id resp)
        process (p/find-sign-process id)]
    id => #"[a-zA-Z0-9]{40}"
    process => (contains {:id      id
                          :stamp   #"[a-zA-Z0-9]{40}"
                          :company {:name cname
                                   :y    y}
                          :status  "created"})
    
    (fact "start-process"
      (let [resp    (http/get (str (u/server-address) "/api/sign/start/" id))
            process (p/find-sign-process id)]
        (-> resp :status) => 200
        (-> process :status) => "start"))
    
    (fact "onnisttu.fi fetches the document"
      (let [resp    (http/get (str (u/server-address) "/api/sign/document/" id))
            process (p/find-sign-process id)]
        (-> resp :status) => 200
        (-> process :status) => "document"))))
