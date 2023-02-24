(ns lupapalvelu.event-log-parser
  (:require [clojure.java.io :as io]
            [lupapalvelu.json :as json]
            [sade.strings :as ss]))

(defn parse-events
  "Parses Clojure maps from event log lines"
  [filename]
  (with-open [rdr (io/reader filename)]
    (vec
      (for [^String line (line-seq rdr)
            :let [i (.indexOf line "{")
                  event (subs line i (.length line))]
            :when (not (ss/blank? event))]
        (json/decode event)))))

(defn write [filename lines]
  (with-open [w (io/writer filename :append false)]
    (doseq [^String line lines]
      (.write w line))))

(comment
  (write "mongo_scripts/prod/convertedToApplication-timestamps.js"
    (map #(format "db.applications.update({_id:'%s', convertedToApplication: null}, {$set: {convertedToApplication: NumberLong('%s')}});\n" (get-in % ["data" "id"]) (% "created") )
    (filter #(= "convert-to-application" (% "action")) (parse-events "convert-to-application-all.txt"))))

  (write "mongo_scripts/prod/muutoslupa.json"
    (map (fn [e] (str (json/encode {:_id (e "created") :user (select-keys (get e "user") (map name lupapalvelu.user/summary-keys) ), :sourceAppId (get-in e ["data" "id"])}) \newline))
      (parse-events "muutoslupa.txt"))))
