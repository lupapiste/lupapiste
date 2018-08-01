(ns review-analyzer
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.review :refer :all]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [sade.util :as util]
            [sade.strings :as ss]))

(mongo/connect!)
(println "Requiring review-analyzer...")
(defn analyze-app [app xml]
  (let [maaraykset (lupapalvelu.backing-system.krysp.reader/->lupamaaraukset (sade.xml/select xml [:paatostieto :Paatos]))
        reviews    (review-reader/xml->reviews xml true)
        app-reviews (filter #(= "task-katselmus" (get-in % [:schema-info :name])) (:tasks app))
        maaraykset-xml (:maaraykset maaraykset)
        maaraykset-app (filter #(= "task-lupamaarays" (get-in % [:schema-info :name])) (:tasks app))
        tj-xml (:vaadittuTyonjohtajatieto maaraykset)
        tj-app (filter #(= "task-vaadittu-tyonjohtaja" (get-in % [:schema-info :name])) (:tasks app))
        ]
    {:id (:id app)
     :reviews {:count-xml (count reviews)
               :count-app (count app-reviews)
               :xml reviews
               :app app-reviews}
     :maaraykset {:count-xml (count maaraykset-xml)
                  :count-app (count maaraykset-app)
                  :xml maaraykset-xml
                  :app maaraykset-app}
     :tj {:count-xml (count tj-xml)
          :count-app (count tj-app)
          :xml tj-xml
          :app tj-app}}))

(def explainers {:reviews (constantly nil)
                 :tj (constantly nil)})

(defn get-result-for-key [results k]
  (let [{:keys [count-xml count-app] :as data} (get results k)
        explain-fn (get explainers k)]
    (when (> count-app count-xml)
      {:msg (format "count does not match, XML: %d , app: %d" count-xml count-app )
       :type k})))

(defonce get-xml (memoize
                   (fn [org app]
                     (Thread/sleep (* (rand-int 2) 1000))   ; give some time for backend system to cope with requests
                     (lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type-consecutively
                       org
                       "R"
                       [app]))))

(defn run-for-app [org app]
  (let [xml (first (get-xml org app))
        result (analyze-app app xml)
        keyz (keys (dissoc result :id))
        results (remove nil? (map (partial get-result-for-key result) keyz))
        msgs (group-by :type results)]
    (when (seq results)
      (println "-----")
      (println (:id result))
      (doseq [k keyz
              :when (get msgs k)
              m (get msgs k)]
        (println (name k) ":" (:msg m)))
      (println "-----"))))

(def excluded [])

(def gt-ts 1494882060000)
(def lt-ts 1494968340000)

(defn run-analyzer [org-id]
  (let [org (mongo/by-id :organizations org-id)]
    (run! (partial run-for-app org)
          (mongo/snapshot :applications {:_id {$nin excluded}
                                         :organization (:id org)
                                         :tasks {"$elemMatch" {:created {"$gt" gt-ts, "$lt" lt-ts}
                                                               :source.type "background"}}}))))



(defn generate-mongo-clauses-to-file
  "Generates mongo $pull clauses into file, which remove 'wrong' reviews from applications."
  [org]
  (doseq [app (mongo/snapshot :applications
                              {:_id {$nin excluded}
                               :organization (:id org)
                               :tasks {"$elemMatch" {:created {"$gt" gt-ts, "$lt" lt-ts}
                                                     :source.type "background"}}})
          :let [out-f "/tmp/pull_reviews_mongo_updates.js"
                submitted-ts (:submitted app)
                xmls (-> (get-xml org app)
                         first
                         (review-reader/xml->reviews true))
                reviews (filter #(= "task-katselmus" (get-in % [:schema-info :name])) (:tasks app))]
          line (for [app-review (->> reviews
                                     (filter #(= "background" (get-in % [:source :type]))) ; only those from backend
                                     (filter #(and (> (:created %) gt-ts) (< (:created %) lt-ts)))) ; created in target timeframe
                     :let [review-held (get-in app-review [:data :katselmus :pitoPvm :value])
                           similar (some
                                     (fn [xml]
                                       (and
                                         (= (get-in app-review [:data :katselmuksenLaji :value])
                                            (:katselmuksenLaji xml))
                                         (= (get-in app-review [:taskname])
                                            (:tarkastuksenTaiKatselmuksenNimi xml))
                                         (= review-held
                                            (util/to-local-date (:pitoPvm xml)))
                                         (= (get-in app-review [:data :katselmus :tila :value])
                                            (:osittainen xml))
                                         xml))                ; return similar looking review from XML
                                     xmls)
                           review-before-app? (and (not (ss/blank? review-held))
                                                   (< (util/to-millis-from-local-date-string review-held)
                                                      submitted-ts))]
                     :when (or (not similar)                ; remove not in XML
                               review-before-app?)]         ; OR remove if review was held before the application (errorneous)
                 (str
                   "// " (:id app) " | "
                   (get-in app-review [:data :katselmuksenLaji :value]) " - " (get-in app-review [:taskname])
                   (when review-before-app?
                     (str " (pitoPvm ennen hakemusta: " review-held ")") )
                   "\n"
                   (format "db.applications.update({_id:\"%s\", tasks: {$elemMatch: {id:\"%s\", \"source.type\":\"background\"}}},{$pull: {\"tasks\": {\"id\": \"%s\"}}}); "
                           (:id app)
                           (:id app-review)
                           (:id app-review))))]
    (spit out-f (str line "\n") :append true)))
