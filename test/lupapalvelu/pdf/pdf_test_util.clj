(ns lupapalvelu.pdf.pdf-test-util
  (:require [lupapalvelu.domain :as domain]))

(def ignored-schemas #{"hankkeen-kuvaus-jatkoaika"
                       "poikkeusasian-rakennuspaikka"
                       "hulevedet"
                       "talousvedet"
                       "ottamismaara"
                       "ottamis-suunnitelman-laatija"
                       "kaupunkikuvatoimenpide"
                       "task-katselmus"
                       "approval-model-with-approvals"
                       "approval-model-without-approvals"})

(def yesterday (- (System/currentTimeMillis) (* 1000 60 60 24)))
(def today (System/currentTimeMillis))

(defn dummy-statement [id name status text]
  {:id id
   :requested 1444802294666
   :given 1444902294666
   :status status
   :text text
   :person {:name name}
   :attachments [{:target {:type "statement"}} {:target {:type "something else"}}]})

(defn dummy-neighbour [id name status message]
  {:propertyId id
   :owner {:type "luonnollinen"
           :name name
           :email nil
           :businessID nil
           :nameOfDeceased nil
           :address
           {:street "Valli & kuja I/X:s Gaatta"
            :city "Helsinki"
            :zip "00100"}}
   :id id
   :status [{:state nil
             :user {:firstName nil :lastName nil}
             :created nil}
            {:state status
             :message message
             :user {:firstName "Sonja" :lastName "Sibbo"}
             :vetuma {:firstName "TESTAA" :lastName "PORTAALIA"}
             :created 1444902294666}]})


(defn dummy-application [map]

  (merge domain/application-skeleton  map)
)