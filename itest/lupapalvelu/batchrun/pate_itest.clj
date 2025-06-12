(ns lupapalvelu.batchrun.pate-itest
  (:require [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.batchrun.pate :as pate]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.date :as date]))

(defn test-xml []
  (conv-test/read-xml "dev-resources/krysp/verdict-r.xml"))

(defn with-ids
  [backend-id vrk-id]
  (cond-> (enlive/at (test-xml)
                     [:luvanTunnisteTiedot :LupaTunnus :VRKLupatunnus]
                     (enlive/content vrk-id))
    backend-id (enlive/at [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus]
                          (enlive/content backend-id))))

(defn- check-backend-ids
  [app-id backend-id vrk-id]
  (let [verdict (-> (mongo/by-id :applications app-id [:verdicts])
                    :verdicts first)]
    (if (or backend-id vrk-id)
      (fact "Backend ids are present"
        (:kuntalupatunnus verdict) => backend-id
        (:vrk-lupatunnus verdict) => vrk-id)
      (fact "No verdict"
        verdict => nil))))

(defn backend-id-update [args xml]
  (if xml
    (fact "Run update: new verdict"
      (apply pate/backend-ids-update args) => nil
      (provided
        (krysp/get-application-xml-by-application-id anything)
        => xml))
    (fact "Run update: do nothing"
      (apply pate/backend-ids-update args) => nil
      (provided
        (krysp/get-application-xml-by-application-id anything)
        => nil :times 0))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions
    (facts "Pate backend-ids update"
      (let [{app-id :id} (create-and-submit-local-application pena)
            update-app   #(mongo/update-by-id :applications app-id %)
            rm-verdict   #(update-app {$set {:verdicts []}})
            reset-app    #(update-app {$set   {:organization "753-R"
                                               :sent         12345
                                               :verdicts     []
                                               :state        :verdictGiven}
                                       $unset {:facta-imported true
                                               :infoRequest    true}})]

        (reset-app)
        (check-backend-ids app-id nil nil)
        (fact "Pate not enabled in Sipoo, no updates"
          (pate/backend-ids-update)
          (pate/backend-ids-update "753-R")
          (check-backend-ids app-id nil nil))

        (fact "Enable Pate in Sipoo"
          (command admin :set-organization-scope-pate-value
                   :permitType "R"
                   :municipality "753"
                   :value true) => ok?)

        (facts "Update all"
          (backend-id-update [] (with-ids "id1" "vrk1"))
          (check-backend-ids app-id "id1" "vrk1"))

        (facts "Existing verdicts are not updated"
          (backend-id-update [] nil)
          (backend-id-update ["753-R"] nil)
          (check-backend-ids app-id "id1" "vrk1"))

        (facts "Update Sipoo"
          (rm-verdict)
          (backend-id-update ["753-R"] (with-ids "id2" "vrk2"))
          (check-backend-ids app-id "id2" "vrk2"))

        (facts "Update application"
          (rm-verdict)
          (backend-id-update [app-id] (with-ids "id2" "vrk2"))
          (check-backend-ids app-id "id2" "vrk2"))

        (facts "Application does not match"
          (doseq [m [{:facta-imported true} {:sent nil}
                     {:infoRequest true} {:state :closed}
                     {:organization "123-R"}]]
            (reset-app)
            (update-app {$set m})
            (facts {:midje/description (str "No update: " m)}
              (backend-id-update ["753-R"] nil)
              (backend-id-update [app-id] nil)
              (check-backend-ids app-id nil nil))))

        (facts "Dmcity"
          (facts "Target enabled: no updates"
            (reset-app)
            (command admin :upsert-dmcity-configuration
                     :organizationId "753-R"
                     :configuration {:url      "http://dmcity.example.com"
                                     :username "hello"
                                     :password "world"
                                     :vault    "fortress"
                                     :targets  {:R true}}) => ok?
            (backend-id-update ["753-R"] nil)
            (backend-id-update [app-id] nil)
            (check-backend-ids app-id nil nil))
          (facts "Target not enabled: updates"
            (reset-app)
            (command admin :upsert-dmcity-configuration
                     :organizationId "753-R"
                     :configuration {:url      "http://dmcity.example.com"
                                     :username "hello"
                                     :password "world"
                                     :vault    "fortress"
                                     :targets  {:R false}}) => ok?
            (backend-id-update ["753-R"] (with-ids "behold!" nil))
            (check-backend-ids app-id "behold!" nil)))

        (facts "Not sent, but exported: updates"
          (update-app {$set {:organization "753-R"
                             :verdicts     []
                             :sent         nil
                             :state        :constructionStarted
                             :transfers    [{:hii :hoo}
                                            {:type :exported-to-backing-system}]}})
          (backend-id-update ["753-R"] (with-ids "Rare export" "VRK rare"))
          (check-backend-ids app-id "Rare export" "VRK rare"))

        (facts "Jatkoaika"
          (doseq [op ["jatkoaika"
                      "raktyo-aloit-loppuunsaat"]]
            (reset-app)
            (update-app {$set {:organization          "753-R"
                               :primaryOperation.name op
                               :verdicts              []
                               :verdictDate           (-> (date/now) date/timestamp)
                               :state                 :ready}})
            (backend-id-update ["753-R"] (with-ids "id1" "vrk1"))
            (check-backend-ids app-id "id1" "vrk1")))

        (facts "Jatkoaika (ancient)"
          (doseq [op ["jatkoaika"
                      "raktyo-aloit-loppuunsaat"]]
            (reset-app)
            (update-app {$set {:organization          "753-R"
                               :primaryOperation.name op
                               :verdicts              []
                               :verdictDate           (-> (date/now)
                                                          (date/minus :days 100)
                                                          date/timestamp)
                               :state                 :ready}})
            (backend-id-update ["753-R"] nil)
            (check-backend-ids app-id nil nil)))

        (facts "No backing system, no updates"
          (mongo/update-by-id :organizations "753-R"
                              {$unset {:krysp.R.url true}})
          (reset-app)
          (backend-id-update ["753-R"] nil))

        (facts "YA application"
          (command admin :set-organization-scope-pate-value
                   :permitType "YA"
                   :municipality "753"
                   :value true) => ok?
          (command sipoo-ya :set-krysp-endpoint
                   :organizationId "753-YA"
                   :url (str (server-address) "/dev/krysp")
                   :username "" :password ""
                   :permitType "YA"
                   :version "2.2.1") => ok?
          (reset-app)
          (update-app {$set {:permitType   "YA"
                             :organization "753-YA"}})
          (pate/backend-ids-update "753-YA")
          (check-backend-ids app-id "523" nil))))))
