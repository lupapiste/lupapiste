(ns lupapalvelu.mml.update-poi
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [swiss-arrows.core :refer [-<>>]]
            [slingshot.slingshot :refer [try+ throw+]]
            [lupapalvelu.find-address :as find-address]))

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

(def priorities {"540"  1   ; kunnan nimi, kaupunki
                 "550"  2   ; kunnan nimi, maaseutu
                 "560"  3   ; kylan, kaupunginosan tai kulmakunnan nimi

                 "225"  4   ; liikennealueen nimi
                 
                 "350"  5   ; saaren nimi
                 "410"  5   ; vakaveden nimi
                 "420"  5   ; virtaveden nimi
                 "330"  5   ; suon nimi

                 "235"  6   ; puiston nimi
                 "345"  6   ; niemen nimi
                 "325"  6   ; metsa-alueen nimi

                 "200"  7   ; maa-aineksenottoalueen nimi
                 "245"  7}) ; urheilu- tai virkistysalueen nimi

(def included-types (set find-address/poi-types))

(defn parse [line]
  (let [data     (s/split line #";")
        poi-type (->> (get data 3) ->long (format "%03d"))]
    (when (included-types poi-type)
      {:id            (get data 30)
       :text          (get data 0)
       :name          (s/lower-case (get data 0))
       :lang          (langs (get data 1) "?")
       :type          poi-type
       :priority      (get priorities poi-type 100)
       :location {:x  (->long (get data 10))
                  :y  (->long (get data 9))}
       :municipality  (->> (get data 11) ->long (format "%03d"))})))

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
  (if record
    (let [data (parse record)]
      (when data
        (mc/insert :poi data))
      (when (and (zero? (mod i 10000)) (pos? i))
        (println "processed" i "lines..."))
      (recur (inc i) (if data (inc c) c) r))
    [i c]))

(defn process [filename]
  (with-open [input (io/reader filename :encoding "ISO-8859-1")]
    (process-rows 0 0 (line-seq input))))

(defn run [filename]
  (let [start (System/currentTimeMillis)]
    (println "Removing old data (this might take some time)...")
    (mc/drop :poi)
    (mc/ensure-index :poi {:name 1 :lang 1 :type 1 :priority 1})
    (println (format "Processing file %s..." filename))
    (let [[rows pois] (process filename)
          total (mc/count :poi)] 
      (if (= pois total)
        (println "Done, processed" rows "rows with" pois "poi's, in" (-<>> start (- (System/currentTimeMillis)) double (/ <> 1000.0) (format "%.1f")) "sec")
        (println "ERROR: processed" rows "rows with" pois "poi's, but :poi collection has" total "records")))))

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
