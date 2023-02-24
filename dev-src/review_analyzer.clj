(ns review-analyzer
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review :refer :all]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss])
  (:import [java.io BufferedWriter]))

(defn analyze-app [app xml]
  (let [maaraykset (lupapalvelu.backing-system.krysp.reader/->lupamaaraykset (sade.xml/select xml [:paatostieto :Paatos]))
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

(defn get-result-for-key [results k]
  (let [{:keys [count-xml count-app]} (get results k)]
    (when (> count-app count-xml)
      {:msg (format "count does not match, XML: %d , app: %d" count-xml count-app )
       :type k})))

(defonce get-xml (memoize
                   (fn [org app]
                     (Thread/sleep (* (rand-int 2) 1000))   ; give some time for backend system to cope with requests
                     (batchrun/fetch-reviews-for-organization-permit-type-consecutively
                       org
                       "R"
                       [app]))))

(defn run-for-app [^BufferedWriter writer org app]
  (let [xml (first (get-xml org app))
        result (analyze-app app xml)
        keyz (keys (dissoc result :id))
        results (remove nil? (map (partial get-result-for-key result) keyz))
        msgs (group-by :type results)]
    (when (seq results)
      (println "Analyzed: " (:id result))
      (.write writer (str "---|" (:id result) "|---"))
      (.newLine writer)

      (doseq [k keyz
              :when (get msgs k)
              m (get msgs k)]
        (.write writer (str (name k) ":" (:msg m)))
        (.newLine writer))
      (.newLine writer)
      (println "-----"))))

(def excluded [])

(def gt-ts 1574726400000)
(def lt-ts 1494968340000)

(defn the-data [org-id]
  (mongo/snapshot :applications {;:_id {$nin excluded}
                                 :organization                         org-id
                                 ; paperilupa applications are fetched using backend-id, thus they need different
                                 ; semantis and thus might give false positives for analyzer if not careful..
                                 :primaryOperation.name {$ne "aiemmalla-luvalla-hakeminen"}
                                 ; it might be that :created timestamp gives false results, because erroreous data
                                 ; from KuntaGML might be merged to task which was created eg when verdict was created
                                 ; thus it is more safer to search by modified
                                 :tasks.data.katselmuksenLaji.modified {"$gt" gt-ts}}))

(defn run-analyzer
  "Analyzes applications' reviews for organization ID.
  Fetches KuntaGML from organization, compares and outputs analyzer result to txt file in /tmp.

  Fetched KuntaGML is cached to memory using memoize."
  [org-id]
  (when-let [org (mongo/by-id :organizations org-id)]
    (with-open [report-file (io/writer (str "/tmp/" org-id "_review_analyzer_output.txt"))]
      (run! (partial run-for-app report-file org) (the-data org-id)))))


(def now (sade.core/now))

(defn generate-mongo-clauses-to-file
  "Generates mongo $pull clauses into file, which remove 'wrong' reviews from applications."
  [org-id]
  (when-let [org (mongo/by-id :organizations org-id)]
    (doseq [app  (the-data org-id)
            :let [out-f                   (str "/tmp/" org-id "_review_pull_clauses_" now ".js")
                  submitted-or-created-ts (or (:submitted app) (:created app))
                  xmls                    (-> (get-xml org app)
                                              first
                                              (review-reader/xml->reviews true))
                  reviews                 (->> (:tasks app)
                                               (filter #(#{"task-katselmus" "task-katselmus-backend"}
                                                          (get-in % [:schema-info :name]))))]
            line (for [app-review (->> reviews
                                       (filter #(= "background" (get-in % [:source :type]))) ; only those from backend
                                       #_(filter #(and (> (:created %) gt-ts)
                                                     #_(< (:created %) lt-ts)))) ; created in target timeframe
                       :let [review-held        (get-in app-review [:data :katselmus :pitoPvm :value])
                             similar            (some
                                                  (fn [xml]
                                                    (and
                                                      (= (get-in app-review [:data :katselmuksenLaji :value])
                                                         (:katselmuksenLaji xml))
                                                      (= (get-in app-review [:taskname])
                                                         (:tarkastuksenTaiKatselmuksenNimi xml))
                                                      (= review-held
                                                         (date/finnish-date (:pitoPvm xml) :zero-pad))
                                                      (= (get-in app-review [:data :katselmus :tila :value])
                                                         (:osittainen xml))
                                                      xml)) ; return similar looking review from XML
                                                  xmls)
                             review-before-app? (and (not (ss/blank? review-held))
                                                     (< (date/timestamp review-held)
                                                        submitted-or-created-ts))]
                       :when (or (not similar) ; remove not in XML
                                 review-before-app?)] ; OR remove if review was held before the application (errorneous)
                   (str
                     "// " (:id app) " | "
                     (get-in app-review [:data :katselmuksenLaji :value])
                     " - " (get-in app-review [:taskname])
                     (when-let [external-id (get-in app-review [:data :muuTunnus :value])]
                       (when-not (str/blank? external-id)
                         (str ", Facta ID [" external-id "]")))
                     (when review-before-app?
                       (str " (pitoPvm:" review-held " on ennen hakemusta: " (date/xml-date submitted-or-created-ts) ")"))
                     "\n"
                     (format "db.applications.update({_id:\"%s\", tasks: {$elemMatch: {id:\"%s\", \"source.type\":\"background\"}}},{$pull: {\"tasks\": {\"id\": \"%s\"}}}); "
                             (:id app)
                             (:id app-review)
                             (:id app-review))))]
      (spit out-f (str line "\n") :append true))))
