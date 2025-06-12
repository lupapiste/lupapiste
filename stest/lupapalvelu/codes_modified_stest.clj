(ns lupapalvelu.codes-modified-stest
  (:require [clojure.test :refer [deftest is testing]]
            [code-list-querier :as clq]
            [sade.strings :as ss]
            [sade.util :as su]))

(set! *warn-on-reflection* true)

(defn- check-code-list-timestamp
  "Checks if the modified timestamp for a code list matches between repository and fetched data.
   Returns a map with :config-key, :pass?, and optional :message."
  [config-key]
  (let [{:keys [url resource-path]} (get clq/code-list-configs config-key)]
    (try
      (let [repository-data (su/read-edn-resource resource-path)
            repository-ts   (:modified repository-data)
            fetched-data    (clq/fetch-code-list url)
            fetched-ts      (:modified fetched-data)]
        (cond
          (nil? repository-ts)
          {:config-key config-key
           :pass?      false
           :message    "Repository data timestamp not found"}

          (nil? fetched-ts)
          {:config-key config-key
           :pass?      false
           :message    "Fetched data timestamp not found"}

          (not= repository-ts fetched-ts)
          {:config-key config-key
           :pass?      false
           :message    (ss/join "\n" ["Data in koodistot.suomi.fi have been modified"
                                      (str " - repository timestamp: " repository-ts)
                                      (str " - fetched timestamp: " fetched-ts)
                                      (str "Run (code-list-querier/update-code-lists! " config-key ") in REPL and push changes")])}
          :else
          {:config-key config-key
           :pass?      true}))
      (catch Exception e
        {:config-key config-key
         :pass?      false
         :message    (str "Error checking " config-key ": " (.getMessage e))}))))

(deftest modified-timestamp-consistency-test
  (testing "Compare code list modified timestamps in repository to koodistot.suomi.fi"
    (doseq [config-key (keys clq/code-list-configs)]
      (testing (str "Checking " config-key)
        (let [result (check-code-list-timestamp config-key)]
          (is (:pass? result) (:message result)))))))
