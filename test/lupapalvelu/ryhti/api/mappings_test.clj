(ns lupapalvelu.ryhti.api.mappings-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]))

(deftest lupamaarayksen-lajit-match
  (let [helper-items (->> helper/lupamaarayksen-lajit
                          (mapv #(conj (:items %) (:group %)))
                          (reduce into #{}))
        ryhti-items  (->> ryhti-mappings/lupamaarayksen-laji
                          keys
                          set)]
    (testing "the helper list should match the ryhti list"
      (is (= helper-items ryhti-items)))))

(deftest attachment-type->asiakirjan-laji-test
  (testing "Lupapiste can represent all Ryhti `asiakirjan laji` types"
    (let [known-types (->> (:R att-type/attachment-types-by-permit-type)
                           (map :type-id)
                           (into #{}))]
      (is (= #{}
             (reduce (fn [missing asiakirja]
                       (if (known-types asiakirja)
                         missing
                         (conj missing asiakirja)))
                     #{}
                     (keys ryhti-mappings/asiakirjan-laji))))))
  (testing "Ryhti can NOT represent all Lupapiste attachment types"
    ;; These will all mapped to `:muu`
    (is (= {:erityissuunnitelmat   #{:hulevesisuunnitelma
                                     :ikkunadetaljit
                                     :iv_suunnitelma
                                     :kalliorakentamistekninen_suunnitelma
                                     :kvv_suunnitelma
                                     :lammityslaitesuunnitelma
                                     :pintatasaussuunnitelma
                                     :pohjarakennesuunnitelma
                                     :pohjaveden_hallintasuunnitelma
                                     :radontekninen_suunnitelma
                                     :rakennesuunnitelma
                                     :sahkosuunnitelma
                                     :tulisija_ja_hormisuunnitelma}
            :muut                  #{:kuulutusasiakirja
                                     :paatoksen_aikaiset_hankkeen_tiedot}
            :osapuolet             #{:suostumus
                                     :tyonjohtajaluettelo}
            :paapiirustus          #{:aitapiirustus
                                     :asemapiirros
                                     :julkisivupiirustus
                                     :leikkauspiirustus
                                     :muu_paapiirustus
                                     :pohjapiirustus}
            :tietomallien_liitteet #{:muu_tietomallin_liite
                                     :tarkastusraportti
                                     :tarkastustulos
                                     :tarkastustulos_bcf}
            :tietomallit           #{:arkkitehtimalli_BIM
                                     :rakennemalli_BIM
                                     :rakennuksen_tietomalli_BIM
                                     :rakennuspaikkamalli_BIM
                                     :talotekniikkamalli_BIM
                                     :ympariston_tietomalli_BIM}}
           (reduce (fn [acc {:keys [type-group type-id]}]
                     (if (ryhti-mappings/asiakirjan-laji type-id)
                       acc
                       (update acc type-group (fnil conj #{}) type-id)))
                   {}
                   (:R att-type/attachment-types-by-permit-type))))))
