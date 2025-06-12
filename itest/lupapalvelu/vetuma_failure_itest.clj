(ns lupapalvelu.vetuma-failure-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [->cookie-store redirects-to with-test-system]]
            [lupapalvelu.vetuma-itest-util :refer :all]
            [midje.sweet :refer :all]))

(def token-query (update-in default-token-query [:query-params] #(merge % {:y "/y-error"
                                                                                   :vtj "/vtj-error"})))

(defn create-base-context []
  (let [params (default-vetuma-params (->cookie-store (atom {})))
        trid (vetuma-init params token-query)]
    [params trid]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Vetuma cancel"
    (let [[params trid] (create-base-context)
          resp          (vetuma-fake-response params {"TRID" trid, "STATUS" "CANCELLED"})]
      (fact "Redirects to cancel URL"
        resp => (partial redirects-to (get-in token-query [:query-params :cancel])))))

  (facts "Vetuma reject"
    (let [[params trid] (create-base-context)
          resp          (vetuma-fake-response params {"TRID" trid, "STATUS" "REJECTED"})]
      (fact "Redirects to error URL"
        resp => (partial redirects-to (get-in token-query [:query-params :error])))))

  (facts "Vetuma VTJ error"
    (let [[params trid] (create-base-context)
          resp          (vetuma-fake-response params {"TRID"      trid
                                                      "STATUS"    "FAILURE"
                                                      "EXTRADATA" "ERROR=Cannot use VTJ.,ERRORCODE=8001"})]
      (fact "Redirects to VTJ error URL"
        resp => (partial redirects-to (get-in token-query [:query-params :vtj])))))

  (facts "Vetuma company"
    (let [[params trid] (create-base-context)
          resp          (vetuma-fake-response params {"TRID"      trid
                                                      "STATUS"    "FAILURE"
                                                      "EXTRADATA" "1060155-5"})]
      (fact "Redirects to company error URL"
        resp => (partial redirects-to (get-in token-query [:query-params :y])))))

  (facts "Vetuma other error"
    (let [[params trid] (create-base-context)
          resp          (vetuma-fake-response params {"TRID" trid, "STATUS" "ERROR"})]
      (fact "Redirects to error URL"
        resp => (partial redirects-to (get-in token-query [:query-params :error]))))))
