(ns lupapalvelu.users-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(defn all-in-role? [role]
  (fn [users]
    (->> users
      (map :role)
      (every? #{role}))))

(defn all-in-organization? [organization]
  (fn [users]
    (->> users
      (map (comp keys :orgAuthz))
      flatten
      (map name)
      (every? #{organization}))))

(facts "defquery users"

  (fact "pena can't list users"
    (query pena :users) => unauthorized?)

  (fact "admin can list users"
    (query admin :users)
      => (contains {:ok true})
    (query admin :users :role "authority")
      => (contains {:ok true :users (all-in-role? "authority")})
    (query admin :users :organization "837-YA")
      => (contains {:ok true :users (all-in-organization? "837-YA")})
    (query admin :users :role "authority" :organization "837-YA")
      => (contains {:ok true :users (every-checker (all-in-role? "authority") (all-in-organization? "837-YA"))})))

