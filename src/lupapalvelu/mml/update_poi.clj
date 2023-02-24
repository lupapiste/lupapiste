(ns lupapalvelu.mml.update-poi
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [lupapalvelu.mongo :as mongo]
            [swiss.arrows :refer [-<>>]]
            [slingshot.slingshot :refer [try+ throw+]]))

;;
;; Included types of poi in priority order:
;;

(def poi-types ["540"   ; kunnan nimi, kaupunki
                "550"   ; kunnan nimi, maaseutu
                "560"   ; kylan, kaupunginosan tai kulmakunnan nimi
                "225"   ; liikennealueen nimi
                "350"   ; saaren nimi
                "410"   ; vakaveden nimi
                "420"   ; virtaveden nimi
                "330"   ; suon nimi
                "235"   ; puiston nimi
                "345"   ; niemen nimi
                "325"   ; metsa-alueen nimi
                "200"   ; maa-aineksenottoalueen nimi
                "245"]) ; urheilu- tai virkistysalueen nimi

;;
;;
;;

(defn ->long [v]
  (Long/parseLong v))

(def langs {"1" "fi"
            "2" "sv"})

(def priorities (into {} (zipmap poi-types (range))))

(defn parse [line]
  (let [data     (s/split line #";")
        poi-type (->> (nth data 3) ->long (format "%03d"))
        priority (priorities poi-type)
        lang     (langs (nth data 1))]
    (when (and priority lang)
      {:id            (nth data 30)
       :text          (nth data 0)
       :name          (s/lower-case (nth data 0))
       :lang          lang
       :type          poi-type
       :priority      priority
       :location {:x  (->long (nth data 10))
                  :y  (->long (nth data 9))}
       :municipality  (->> (nth data 11) ->long (format "%03d"))})))

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

(defn process-rows [i c [record & r]]
  (if-not record
    [i c]
    (let [data (parse record)]
      (when data
        (mongo/insert :poi data))
      (when (and (zero? (mod i 10000)) (pos? i))
        (println "processed" i "lines..."))
      (recur (inc i) (if data (inc c) c) r))))

(defn process [filename]
  (with-open [input (io/reader filename :encoding "ISO-8859-1")]
    (process-rows 0 0 (line-seq input))))

(defn run [filename]
  (let [start (System/currentTimeMillis)]
    (println "Removing old data (this might take some time)...")
    (mongo/drop-collection :poi)
    (println (format "Processing file %s..." filename))
    (let [[rows pois] (process filename)
          total (mongo/count :poi)]
      (if (= pois total)
        (do
          (mongo/ensure-index :poi {:name 1 :lang 1 :priority 1})
          (println "Done, processed" rows "rows with" pois "poi's, in" (-<>> start (- (System/currentTimeMillis)) double (/ <> 1000.0) (format "%.1f")) "sec"))
        (println "ERROR: processed" rows "rows with" pois "poi's, but :poi collection has" total "records")))))

(defn -main [& args]
  (let [filename (first args)]
    (try+
      (when (s/blank? filename)
        (throw+ "Must provide a source file name"))
      (when-not (.exists (io/file filename))
        (throw+ (str "Can't find file: " filename)))
      (validate-file! filename)
      (mount/start #'mongo/connection)
      (run filename)
      (catch string? message
        (println "Processing failed: " message))
      (catch Throwable e
        (println "Unexpected error")
        (.printStackTrace e)
        (throw+)))))
