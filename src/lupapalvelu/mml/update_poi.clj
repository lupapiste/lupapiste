(ns lupapalvelu.mml.update-poi
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [swiss-arrows.core :refer [-<>>]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def default-filename (str "/Volumes/HD2/Users/jarppe/swd/lupa-workspace/mml/" "PNR_2012_01.TXT"))

(defn ->long [v]
  (if (and v (string? v) (re-matches #"\d+" v))
    (Long/parseLong v)
    0))

(def langs {"1" "fi"
            "2" "sv"
            "3" "ps"   ; pohjoissaame
            "4" "is"   ; inarinsaame
            "5" "ks"}) ; koltansaame

(defn parse [line]
  (let [data (s/split line #";")]
    {:id            (get data 30)
     :text          (get data 0)
     :name          (s/lower-case (get data 0))
     :lang          (langs (get data 1) "?")
     :type          (->> (get data 3) ->long (format "%03d"))
     :location {:x  (->long (get data 10))
                :y  (->long (get data 9))}
     :municipality  (->> (get data 11) ->long (format "%03d"))}))

(defn save [record]
  (mc/insert :poi record))

(def validators {:id            (partial re-matches #"\d{8}")
                 :lang          (partial re-matches #"fi|sv|ps|is|ks")
                 :type          (partial re-matches #"\d{3}")
                 :municipality  (partial re-matches #"\d{3}")})

(defn validate-file! [filename]
  (with-open [input (io/reader filename :encoding "ISO-8859-1")]
    (doseq [record (map parse (take 10 (line-seq input)))
            [field valid?] validators]
      (when-not ((fnil valid? "") (field record))
        (throw+ (format "Input file does not look valid: field=%s, value=\"%s\"" (name field) (field record)))))))

(defn process [filename]
  (with-open [input (io/reader filename :encoding "ISO-8859-1")]
    (doseq [[i record] (partition 2 (interleave (map inc (range)) (map parse (line-seq input))))]
      (save record)
      (when (zero? (mod i 10000))
        (println "processed" i "lines...")))))

(defn run [filename]
  (let [start (System/currentTimeMillis)]
    (println "Removing old data (this might take some time)...")
    (mc/drop :poi)
    (mc/ensure-index :poi {:name 1 :lang 1})
    (println (format "Processing file %s..." filename))
    (process filename)
    (println "Done, saved " (mc/count :poi) "records in" (-<>> start (- (System/currentTimeMillis)) double (/ <> 1000.0) (format "%.1f")) "sec")))

(defn connect []
  (m/connect!)
  (m/use-db! "lupapiste"))

(defn -main [& args]
  (let [filename (first args)]
    (try+
      (when (s/blank? filename)
        (throw+ "Must provide a source file name"))
      (when-not (.exists (io/file filename))
        (throw+ (str "Can't find file: " filename)))
      (validate-file! filename)
      (connect)
      (run filename)
      (catch string? message
        (println "Processing failed: " message))
      (catch Object e
        (println "Unexpected error")
        (.printStackTrace e)
        (throw+)))))
