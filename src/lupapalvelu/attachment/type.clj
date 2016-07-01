(ns lupapalvelu.attachment.type
  (:refer-clojure :exclude [contains?])
  (:require [lupapiste-commons.attachment-types :as attachment-types]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :refer [fn-> fn->>] :as util]
            [sade.strings :as ss]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema AttachmentType
  {:type-id                    sc/Keyword
   :type-group                 sc/Keyword
   (sc/optional-key :metadata) {(sc/optional-key :permitType)     sc/Keyword
                                (sc/optional-key :grouping)       sc/Keyword
                                (sc/optional-key :for-operations) #{(apply sc/enum (keys op/operations))}}})

(def osapuolet attachment-types/osapuolet-v2)

(def- attachment-types-by-permit-type-unevaluated
  {:R    'attachment-types/Rakennusluvat-v2
   :P    'attachment-types/Rakennusluvat-v2
   :YA   'attachment-types/YleistenAlueidenLuvat-v2
   :YI   'attachment-types/Ymparistoilmoitukset
   :YL   'attachment-types/Ymparistolupa
   :YM   'attachment-types/MuutYmparistoluvat
   :VVVL 'attachment-types/Ymparistoilmoitukset
   :MAL  'attachment-types/Maa-ainesluvat
   :MM   'attachment-types/Kiinteistotoimitus
   :KT   'attachment-types/Kiinteistotoimitus})

(defn equals? [& types]
  (boolean (and (:type-id (first types))
                (:type-group (first types))
                (->> (map (juxt (comp keyword :type-id) (comp keyword :type-group)) types)
                     (apply =)))))

(defn contains? [attachment-type-coll attachment-type]
  (boolean (some (partial equals? attachment-type) attachment-type-coll)))

(def- foreman-application-types
  [{:type-id :tutkintotodistus :type-group :osapuolet}
   {:type-id :patevyystodistus :type-group :osapuolet}
   {:type-id :cv               :type-group :osapuolet}
   {:type-id :valtakirja       :type-group :hakija}
   {:type-id :muu              :type-group :muut}])

(defn- for-operations [attachment-type]
  (cond-> #{}
    (contains? foreman-application-types attachment-type) (conj :tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen)))

(def- default-grouping
  {:operation     [{:type-id :pohjapiirustus     :type-group :paapiirustus}
                   {:type-id :leikkauspiirustus  :type-group :paapiirustus}
                   {:type-id :julkisivupiirustus :type-group :paapiirustus}
                   {:type-id :muu_paapiirustus   :type-group :paapiirustus}

                   {:type-id :iv_suunnitelma                       :type-group :erityissuunnitelmat}
                   {:type-id :kvv_suunnitelma                      :type-group :erityissuunnitelmat}
                   {:type-id :rakennesuunnitelma                   :type-group :erityissuunnitelmat}
                   {:type-id :ikkunadetaljit                       :type-group :erityissuunnitelmat}
                   {:type-id :kalliorakentamistekninen_suunnitelma :type-group :erityissuunnitelmat}
                   {:type-id :lammityslaitesuunnitelma             :type-group :erityissuunnitelmat}
                   {:type-id :pohjarakennesuunnitelma              :type-group :erityissuunnitelmat}
                   {:type-id :radontekninen_suunnitelma            :type-group :erityissuunnitelmat}
                   {:type-id :sahkosuunnitelma                     :type-group :erityissuunnitelmat}

                   {:type-id :yhteistilat                            :type-group :selvitykset}
                   {:type-id :energiataloudellinen_selvitys          :type-group :selvitykset}
                   {:type-id :energiatodistus                        :type-group :selvitykset}
                   {:type-id :haittaaineselvitys                     :type-group :selvitykset}
                   {:type-id :kokoontumishuoneisto                   :type-group :selvitykset}
                   {:type-id :kosteudenhallintaselvitys              :type-group :selvitykset}
                   {:type-id :laadunvarmistusselvitys                :type-group :selvitykset}
                   {:type-id :liikkumis_ja_esteettomyysselvitys      :type-group :selvitykset}
                   {:type-id :lomarakennuksen_muutos_asuinrakennukseksi_selvitys_maaraysten_toteutumisesta :type-group :selvitykset}
                   {:type-id :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo :type-group :selvitykset}
                   {:type-id :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta :type-group :selvitykset}
                   {:type-id :rakenteellisen_turvallisuuden_alustava_riskiarvio :type-group :selvitykset}
                   {:type-id :rh_tietolomake                         :type-group :selvitykset}
                   {:type-id :selvitys_kiinteiston_jatehuollon_jarjestamisesta :type-group :selvitykset}
                   {:type-id :selvitys_liittymisesta_ymparoivaan_rakennuskantaan :type-group :selvitykset}
                   {:type-id :selvitys_rakennuksen_kunnosta          :type-group :selvitykset}
                   {:type-id :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo :type-group :selvitykset}
                   {:type-id :selvitys_rakennuksen_terveellisyydesta :type-group :selvitykset}
                   {:type-id :selvitys_rakennuksen_aaniteknisesta_toimivuudesta :type-group :selvitykset}
                   {:type-id :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista :type-group :selvitykset}
                   {:type-id :muu_selvitys                           :type-group :selvitykset}

                   {:type-id :julkisivujen_varityssuunnitelma  :type-group :suunnitelmat}
                   {:type-id :jatevesijarjestelman_suunnitelma :type-group :suunnitelmat}
                   {:type-id :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta :type-group :suunnitelmat}
                   {:type-id :mainoslaitesuunnitelma           :type-group :suunnitelmat}
                   {:type-id :opastesuunnitelma                :type-group :suunnitelmat}
                   {:type-id :piha_tai_istutussuunnitelma      :type-group :suunnitelmat}
                   {:type-id :valaistussuunnitelma             :type-group :suunnitelmat}
                   {:type-id :muu_suunnitelma                  :type-group :suunnitelmat}

                   {:type-id :savunpoistosuunnitelma               :type-group :pelastusviranomaiselle_esitettavat_suunnitelmat}
                   {:type-id :sammutusautomatiikkasuunnitelma      :type-group :pelastusviranomaiselle_esitettavat_suunnitelmat}
                   {:type-id :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta :type-group :pelastusviranomaiselle_esitettavat_suunnitelmat}
                   {:type-id :ilmoitus_vaestonsuojasta             :type-group :pelastusviranomaiselle_esitettavat_suunnitelmat}
                   {:type-id :vaestonsuojasuunnitelma              :type-group :pelastusviranomaiselle_esitettavat_suunnitelmat}
                   {:type-id :muu_pelastusviranomaisen_suunnitelma :type-group :pelastusviranomaiselle_esitettavat_suunnitelmat}

                   {:type-id :rakennuksen_tietomalli_BIM :type-group :tietomallit}]

   :building-site [{:type-id :todistus_hallintaoikeudesta            :type-group :rakennuspaikan_hallinta}
                   {:type-id :ote_yhtiokokouksen_poytakirjasta       :type-group :rakennuspaikan_hallinta}
                   {:type-id :rasitesopimus                          :type-group :rakennuspaikan_hallinta}
                   {:type-id :rasitustodistus                        :type-group :rakennuspaikan_hallinta}
                   {:type-id :todistus_erityisoikeuden_kirjaamisesta :type-group :rakennuspaikan_hallinta}
                   {:type-id :kiinteiston_lohkominen                 :type-group :rakennuspaikan_hallinta}
                   {:type-id :sopimusjaljennos                       :type-group :rakennuspaikan_hallinta}

                   {:type-id :karttaaineisto                            :type-group :rakennuspaikka}
                   {:type-id :ote_alueen_peruskartasta                  :type-group :rakennuspaikka}
                   {:type-id :ote_asemakaavasta_jos_asemakaava_alueella :type-group :rakennuspaikka}
                   {:type-id :ote_kiinteistorekisteristerista           :type-group :rakennuspaikka}
                   {:type-id :ote_ranta-asemakaavasta                   :type-group :rakennuspaikka}
                   {:type-id :ote_yleiskaavasta                         :type-group :rakennuspaikka}
                   {:type-id :perustamistapalausunto                    :type-group :rakennuspaikka}
                   {:type-id :pintavaaitus                              :type-group :rakennuspaikka}
                   {:type-id :rakennusoikeuslaskelma                    :type-group :rakennuspaikka}
                   {:type-id :tonttikartta_tarvittaessa                 :type-group :rakennuspaikka}
                   {:type-id :selvitys_rakennuspaikan_korkeusasemasta   :type-group :rakennuspaikka}

                   {:type-id :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista     :type-group :selvitykset}
                   {:type-id :selvitys_rakennuspaikan_terveellisyydesta                   :type-group :selvitykset}
                   {:type-id :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta :type-group :selvitykset}
                   {:type-id :tarinaselvitys                                              :type-group :selvitykset}

                   {:type-id :hulevesisuunnitelma            :type-group :erityissuunnitelmat}
                   {:type-id :pohjaveden_hallintasuunnitelma :type-group :erityissuunnitelmat}]

   :parties       [{:type-id :osakeyhtion_perustamiskirja                                :type-group :hakija}
                   {:type-id :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta :type-group :hakija}
                   {:type-id :ote_kauppa_ja_yhdistysrekisterista                         :type-group :hakija}
                   {:type-id :valtakirja                                                 :type-group :hakija}

                   {:type-id :cv                    :type-group :osapuolet}
                   {:type-id :patevyystodistus      :type-group :osapuolet}
                   {:type-id :tutkintotodistus      :type-group :osapuolet}
                   {:type-id :suunnittelijan_tiedot :type-group :osapuolet}]})

(def- group-tag-mapping
  {{:type-id :iv_suunnitelma     :type-group :erityissuunnitelmat} :iv_suunnitelma
   {:type-id :kvv_suunnitelma    :type-group :erityissuunnitelmat} :kvv_suunnitelma
   {:type-id :rakennesuunnitelma :type-group :erityissuunnitelmat} :rakennesuunnitelma
   {:type-id :pohjapiirustus     :type-group :paapiirustus}        :paapiirustus
   {:type-id :leikkauspiirustus  :type-group :paapiirustus}        :paapiirustus
   {:type-id :julkisivupiirustus :type-group :paapiirustus}        :paapiirustus
   {:type-id :muu_paapiirustus   :type-group :paapiirustus}        :paapiirustus})

(defn attachment-type
  ([{type-group :type-group type-id :type-id :as attachment-type}]
   (->> (update attachment-type :metadata util/assoc-when
                :grouping       (some-> (util/find-first (fn-> val (contains? attachment-type)) default-grouping) key)
                :for-operations (not-empty (for-operations attachment-type)))
        (util/strip-nils)
        (sc/validate AttachmentType)))
  ([type-group type-id]
   (attachment-type {:type-group (keyword type-group) :type-id (keyword type-id)}))
  ([permit-type type-group type-id]
   (attachment-type {:type-id (keyword type-id) :type-group (keyword type-group) :metadata {:permitType (keyword permit-type)}})))

(defn- ->attachment-type-array [[permit-type attachment-types]]
  (->> (partition 2 attachment-types)
       (mapcat #(map (partial attachment-type permit-type (first %)) (second %)))))

(def attachment-types-by-permit-type
  (->> (eval attachment-types-by-permit-type-unevaluated)
       (map (juxt key ->attachment-type-array))
       (into {})))

(def all-attachment-type-ids
  (->> (mapcat val attachment-types-by-permit-type)
       (map :type-id)
       (set)))

(def all-attachment-type-groups
  (->> (mapcat val attachment-types-by-permit-type)
       (map :type-group)
       (set)))

(defn attachment-type-for-appeal [appeal-type]
  (case (keyword appeal-type)
    :appealVerdict  {:type-group :paatoksenteko
                     :type-id    :paatos}
    :appeal         {:type-group :muutoksenhaku
                     :type-id    :valitus}
    :rectification  {:type-group :muutoksenhaku
                     :type-id    :oikaisuvaatimus}))

(defn- attachment-types-by-operation [operation]
  (let [operation (keyword operation)
        types     (-> operation op/permit-type-of-operation keyword attachment-types-by-permit-type)]
    (or (not-empty (filter #(some-> (get-in % [:metadata :for-operations]) operation) types))
        types)))

(def get-attachment-types-for-operation (memoize attachment-types-by-operation))

(defn get-attachment-types-for-application
  [{:keys [permitType primaryOperation secondaryOperations] :as application}]
  {:pre [application]}
  (->> (cons primaryOperation secondaryOperations)
       (map :name)
       (mapcat get-attachment-types-for-operation)
       (distinct)))

(defn ->grouped-array [attachment-types]
  (->> (group-by :type-group attachment-types)
       (map (juxt key (fn->> val (map :type-id))))))

(defn parse-attachment-type [attachment-type]
  (if-let [match (re-find #"(.+)\.(.+)" (or attachment-type ""))]
    (let [[type-group type-id] (->> match (drop 1) (map keyword))]
      {:type-group type-group :type-id type-id})))

(defn allowed-attachment-types-contain? [allowed-types {:keys [type-group type-id]}]
  (let [type-group (keyword type-group)
        type-id (keyword type-id)]
    (if-let [types (some (fn [[group group-types]] (when (= (keyword group) type-group) group-types)) allowed-types)]
      (some #(= (keyword %) type-id) types))))

(defn allowed-attachment-type-for-application? [attachment-type application]
  {:pre [(map? attachment-type)]}
  (let [allowed-types (or (not-empty (get-attachment-types-for-application application))
                          (-> application :permitType keyword attachment-types-by-permit-type))]
    (contains? allowed-types attachment-type)))

(defn tag-by-type [{type :type :as attachment}]
  (get group-tag-mapping
       (-> (select-keys type [:type-group :type-id])
           (util/convert-values keyword))))

;;
;; Helpers for reporting purposes
;;

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
