(ns lupapalvelu.mock.organization
  (:require [midje.sweet :refer :all]
            [sade.util :refer [map-values key-by mongerify]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.fixture.minimal :as minimal]))

(defn mock-get-org [orgs]
  (let [org-map (key-by :id orgs)]
    (fn [org-id]
      (mongerify (get org-map org-id)))))

(defn mock-get-orgs [orgs]
  (fn [& [query]]
    (assert (not query) "I am just a mock function, I can't handle actual mongo queries")
    (mongerify orgs)))

(defn mock-resolve-orgs [orgs]
  (fn [municipality & [permit-type]]
    (->> orgs
         (filter (fn [org]
                   (some #(= (select-keys % (concat [:municipality] (when permit-type [:permitType])))
                             (merge {:municipality municipality} (when permit-type {:permitType permit-type})))
                         (:scope org))))
         (mongerify))))

(def all-orgs-minimal-by-id (key-by :id minimal/organizations))
(def all-orgs-minimal (vals all-orgs-minimal-by-id))

(def jarvenpaa-r (get all-orgs-minimal-by-id "186-R"))
(def sipoo-r (get all-orgs-minimal-by-id "753-R"))
(def sipoo-ya (get all-orgs-minimal-by-id "753-YA"))
(def kuopio-ya (get all-orgs-minimal-by-id "297-YA"))
(def tampere-r (get all-orgs-minimal-by-id "837-R"))
(def tampere-ya (get all-orgs-minimal-by-id "837-YA"))
(def porvoo-r (get all-orgs-minimal-by-id "638-R"))
(def oulu-r (get all-orgs-minimal-by-id "564-R"))
(def oulu-ya (get all-orgs-minimal-by-id "564-YA"))
(def naantali-r (get all-orgs-minimal-by-id "529-R"))
(def selanne-r (get all-orgs-minimal-by-id "069-R"))
(def loppi-r (get all-orgs-minimal-by-id "433-R"))
(def turku-r (get all-orgs-minimal-by-id "853-R"))
(def kuopio-r (get all-orgs-minimal-by-id "297-R"))
(def helsinki-r (get all-orgs-minimal-by-id "091-R"))
(def oulu-ymp (get all-orgs-minimal-by-id "564-YMP"))
(def sipoo-r-new-application-disabled (get all-orgs-minimal-by-id "997-R-TESTI-1"))
(def sipoo-r-inforequest-disabled (get all-orgs-minimal-by-id "998-R-TESTI-2"))
(def sipoo-r-both-new-application-and-inforequest-disabled
  (get all-orgs-minimal-by-id "999-R-TESTI-3"))

(defmacro with-mocked-orgs [orgs & body]
  `(with-redefs [org/get-organization (mock-get-org ~orgs)
                 org/get-organizations (mock-get-orgs ~orgs)
                 org/resolve-organizations (mock-resolve-orgs ~orgs)]
     ~@body))

(defmacro with-all-mocked-orgs [& body]
  `(with-mocked-orgs minimal/organizations ~@body))
