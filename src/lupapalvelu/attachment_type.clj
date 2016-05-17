(ns lupapalvelu.attachment-type
  (:require [lupapiste-commons.attachment-types :as attachment-types]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.tiedonohjaus :as tos]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]))

(def osapuolet attachment-types/osapuolet-v2)

(def- attachment-types-by-permit-type-unevaluated
  (cond-> {:R    'attachment-types/Rakennusluvat
           :P    'attachment-types/Rakennusluvat
           :YA   'attachment-types/YleistenAlueidenLuvat
           :YI   'attachment-types/Ymparistoilmoitukset
           :YL   'attachment-types/Ymparistolupa
           :YM   'attachment-types/MuutYmparistoluvat
           :VVVL 'attachment-types/Ymparistoilmoitukset
           :MAL  'attachment-types/Maa-ainesluvat
           :MM   'attachment-types/Kiinteistotoimitus
           :KT   'attachment-types/Kiinteistotoimitus}
    (env/feature? :updated-attachments) (merge {:R  'attachment-types/Rakennusluvat-v2
                                                :P  'attachment-types/Rakennusluvat-v2
                                                :YA 'attachment-types/YleistenAlueidenLuvat-v2})))

(def- attachment-types-by-permit-type (eval attachment-types-by-permit-type-unevaluated))

(def all-attachment-type-ids
  (->> (vals attachment-types-by-permit-type)
       (apply concat)
       (#(flatten (map second (partition 2 %))))
       (set)))

(def all-attachment-type-groups
  (->> (vals attachment-types-by-permit-type)
       (apply concat)
       (#(map first (partition 2 %)))
       (set)))

(def operation-specific-attachment-types #{{:type-id :pohjapiirros       :type-group :paapiirustus}
                                           {:type-id :leikkauspiirros    :type-group :paapiirustus}
                                           {:type-id :julkisivupiirros   :type-group :paapiirustus}
                                           {:type-id :yhdistelmapiirros  :type-group :paapiirustus}
                                           {:type-id :erityissuunnitelma :type-group :rakentamisen_aikaiset}
                                           {:type-id :energiatodistus    :type-group :muut}
                                           {:type-id :korjausrakentamisen_energiaselvitys :type-group :muut}
                                           {:type-id :rakennuksen_tietomalli_BIM :type-group :muut}
                                           {:type-id :pohjapiirustus     :type-group :paapiirustus}
                                           {:type-id :leikkauspiirustus  :type-group :paapiirustus}
                                           {:type-id :julkisivupiirustus :type-group :paapiirustus}
                                           {:type-id :muu_paapiirustus   :type-group :paapiirustus}
                                           {:type-id :energiatodistus    :type-group :energiatodistus}
                                           {:type-id :rakennuksen_tietomalli_BIM :type-group :tietomallit}})

(defn attachment-type-for-appeal [appeal-type]
  (case (keyword appeal-type)
    :appealVerdict  {:type-group :paatoksenteko
                     :type-id    :paatos}
    :appeal         {:type-group :muutoksenhaku
                     :type-id    :valitus}
    :rectification  {:type-group :muutoksenhaku
                     :type-id    :oikaisuvaatimus}))

(defn get-attachment-types-by-permit-type
  "Returns partitioned list of allowed attachment types or throws exception"
  [permit-type]
  {:pre [permit-type]}
  (if-let [types (get attachment-types-by-permit-type (keyword permit-type))]
    (partition 2 types)
    (fail! (str "unsupported permit-type: " (name permit-type)))))

(defn get-attachment-types-for-application
  [application]
  {:pre [application]}
  (get-attachment-types-by-permit-type (:permitType application)))

(defn default-metadata-for-attachment-type [type {:keys [organization tosFunction verdicts]}]
  (let [metadata (-> (tos/metadata-for-document organization tosFunction type)
                     (tos/update-end-dates verdicts))]
    (if (seq metadata)
      metadata
      {:nakyvyys :julkinen})))

(defn parse-attachment-type [attachment-type]
  (if-let [match (re-find #"(.+)\.(.+)" (or attachment-type ""))]
    (let [[type-group type-id] (->> match (drop 1) (map keyword))]
      {:type-group type-group :type-id type-id})))

(defn allowed-attachment-types-contain? [allowed-types {:keys [type-group type-id]}]
  (let [type-group (keyword type-group)
        type-id (keyword type-id)]
    (if-let [types (some (fn [[group-name group-types]] (if (= (keyword group-name) type-group) group-types)) allowed-types)]
      (some #(= (keyword %) type-id) types))))

;; Helper for reporting purposes
(defn localised-attachments-by-permit-type [permit-type]
  (let [localize-attachment-section
        (fn [lang [title attachment-names]]
          [(i18n/localize lang (ss/join "." ["attachmentType" (name title) "_group_label"]))
           (reduce
            (fn [result attachment-name]
              (let [lockey                    (ss/join "." ["attachmentType" (name title) (name attachment-name)])
                    localized-attachment-name (i18n/localize lang lockey)]
                (conj
                 result
                 (ss/join \tab [(name attachment-name) localized-attachment-name]))))
             []
             attachment-names)])]
    (reduce
     (fn [accu lang]
       (assoc accu (keyword lang)
          (->> (get attachment-types-by-permit-type (keyword permit-type))
               (partition 2)
               (map (partial localize-attachment-section lang))
               vec)))
     {}
     ["fi" "sv"])))

(defn print-attachment-types-by-permit-type []
  (let [permit-types-with-names (into {}
                                      (for [[k v] attachment-types-by-permit-type-unevaluated]
                                        [k (name v)]))]
    (doseq [[permit-type permit-type-name] permit-types-with-names]
      (println permit-type-name)
      (doseq [[group-name types] (:fi (localised-attachments-by-permit-type permit-type))]
        (println "\t" group-name)
        (doseq [type types]
          (println "\t\t" type))))))
