(ns lupapalvelu.neighbor-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(defn- create-app-with-neighbor []
  (let [resp (create-app pena)
        application-id (:id resp)
        resp (command pena :add-comment :id application-id :text "foo" :target "application")
        resp (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n" :street "s" :city "c" :zip "z" :type :person :email "e")
        neighborId (keyword (:neighborId resp))
        resp (query pena :application :id application-id)
        application (:application resp)
        neighbors (:neighbors application)]
    [application neighbors neighborId]))

(defn- find-by-id [neighborId neighbors]
  (some (fn [neighbor] (when (= neighborId (keyword (:neighborId neighbor))) neighbor)) neighbors))

(facts "create app, add neighbor"
  (let [[application neighbors neighborId] (create-app-with-neighbor)
        neighbor (find-by-id neighborId neighbors)]
    (fact (:neighbor neighbor) => {:propertyId "p"
                                   :owner {:name "n"
                                           :address {:street "s" :city "c" :zip "z"}
                                           :type "person"
                                           :email "e"}})
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, update neighbor"
  (let [[application _ neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-update" :id application-id :neighborId neighborId :propertyId "p2" :name "n2" :street "s2" :city "c2" :zip "z2" :type :person :email "e2")
        application (:application (query pena :application :id application-id))
        neighbors (:neighbors application)
        neighbor (neighborId neighbors)]
    (fact (count neighbors) => 1)
    (fact (:neighbor neighbor) => {:propertyId "p2"
                                   :owner {:name "n2"
                                           :address {:street "s2" :city "c2" :zip "z2"}
                                           :type "person"
                                           :email "e2"}})
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, remove neighbor"
  (let [[application _ neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-remove" :id application-id :neighborId neighborId)
        application (:application (query pena :application :id application-id))
        neighbors (:neighbors application)]
    (fact (count neighbors) => 0)))
