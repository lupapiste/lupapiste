(ns lupapalvelu.migration.operation-attachment-types-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.migration.operation-attachment-types :as oat]
            [monger.operators :refer :all]))

(def promootio-types #{{:type-group :ennakkoluvat_ja_lausunnot :type-id :lausunto}
                       {:type-group :katselmukset_ja_tarkastukset
                        :type-id    :katselmuksen_tai_tarkastuksen_poytakirja}
                       {:type-group :katselmukset_ja_tarkastukset
                        :type-id    :loppukatselmuksen_poytakirja}
                       {:type-group :muut :type-id :keskustelu}
                       {:type-group :muut :type-id :muu}
                       {:type-group :muut :type-id :paatosote}
                       {:type-group :muut :type-id :paatos}
                       {:type-group :muut :type-id :sopimus}
                       {:type-group :osapuolet :type-id :patevyystodistus}
                       {:type-group :yleiset-alueet :type-id :suunnitelmakartta}
                       {:type-group :yleiset-alueet
                        :type-id    :tilapainen-liikennejarjestelysuunnitelma}
                       {:type-group :yleiset-alueet :type-id :tyoalueen-kuvaus}
                       {:type-group :yleiset-alueet :type-id :valokuva}
                       {:type-group :yleiset-alueet :type-id :valtakirja}})

(def test-organization
  {:operations-attachment-settings
   {:operation-nodes
    {:lannan-levittamisesta-poikkeustilanteessa {:allowed-attachments
                                                 {:mode  "set"
                                                  :types {:ilmoituslomakkeet ["ilmoituslomake"]
                                                          :kartat            ["yleiskartta" "sijaintikartta"]
                                                          :muut              ["muu"]}}}
     :leirintaalueilmoitus                      {:allowed-attachments
                                                 {:mode  "set"
                                                  :types {:hakija             ["valtakirja"]
                                                          :ilmoituslomakkeet  ["ilmoituslomake"]
                                                          :kartat             ["yleiskartta" "sijaintikartta"]
                                                          :laitoksen_tiedot   ["muut_paatokset_sopimukset"]
                                                          :laitoksen_toiminta ["arvio_ymparistoriskeista"
                                                                               "yleiskuvaus_toiminnasta"]
                                                          :laitosalue_sen_ymparisto
                                                          ["tiedot_toiminnan_sijaintipaikasta"
                                                           "ote_asemakaavasta" "ote_yleiskaavasta"
                                                           "selvitys_rajanaapureista"]
                                                          :muut
                                                          ["kiinteiston-omistajan-suostumus-luvan-hakemiseen"
                                                           "muu"]
                                                          :paapiirustus       ["asemapiirros"]}}}}}
   :operations-attachments
   {:ilmoitus-poikkeuksellisesta-tilanteesta [["muut" "muu"]]
    :jatteen-keraystoiminta                  [["muut" "muu"]]
    :koeluontoinen-toiminta                  [["muut" "muu"]]
    :lannan-levittamisesta-poikkeustilanteessa
    [["ilmoituslomakkeet" "ilmoituslomake"] ["kartat" "yleiskartta"]
     ["kartat" "sijaintikartta"]]
    :lannan-varastointi                      [["kartat" "lantapatterin-sijainti"]
                                              ["muut" "muu"]]
    :maa-aineslupa                           [["erityissuunnitelmat" "kivenmurskaamo"] ["muut" "muu"]
                                              ["erityissuunnitelmat" "ottamissuunnitelma"]
                                              ["erityissuunnitelmat"
                                               "kaivannaisjatteen_jatehuoltosuunnitelma"]
                                              ["hakija" "ottamisalueen_omistus_hallintaoikeus"]
                                              ["muut" "selvitys_tieyhteyksista_oikeuksista"]
                                              ["muut"
                                               "vakuus_ottamisen_aloittamiseksi_ennen_luvan_lainvoimaa"]
                                              ["hakija" "valtakirja"] ["muut" "vakuusasiakirja"]
                                              ["muut" "pohjavesitutkimus"]]
    :maa-ainesten-kotitarveotto              [["kartat" "ottamispaikan-sijainti"]
                                              ["muut" "muu"]]
    :meluilmoitus                            [["muut" "muu"]
                                              ["ilmoituslomakkeet" "ilmoituslomake"]]
    :yleinen-ilmoitus
    [["paapiirustus" "asemapiirros"]
     ["ilmoituslomakkeet" "ilmoituslomake"]
     ["paapiirustus" "leikkauspiirros"] ["muut" "muu"]
     ["laitoksen_tiedot" "muut_paatokset_sopimukset"]
     ["kartat" "sijaintikartta"]
     ["laitoksen_tiedot" "voimassa_olevat_ymparistolupa_vesilupa"]
     ["laitoksen_toiminta" "yleiskuvaus_toiminnasta"]
     ["laitoksen_toiminta" "yleisolle_tarkoitettu_tiivistelma"]]}})

(def allowed-typeset #'oat/allowed-typeset)

(deftest allowed-typeset-test
  (is (= promootio-types (allowed-typeset :promootio)))
  (is (= promootio-types (allowed-typeset "promootio")))
  (is (= #{} (allowed-typeset :foobar)))
  (is (= #{} (allowed-typeset nil))))

(def settings-types-by-operation #'oat/settings-types-by-operation)
(def attachment-settings-updates #'oat/attachment-settings-updates)

(deftest operations-attachment-settings-test
  (testing "settings-types-by-operation"
    (is (= {} (settings-types-by-operation {})))
    (is (= {:lannan-levittamisesta-poikkeustilanteessa
            [{:type-id :ilmoituslomake :type-group :ilmoituslomakkeet}
             {:type-id :yleiskartta :type-group :kartat}
             {:type-id :sijaintikartta :type-group :kartat}
             {:type-id :muu :type-group :muut}]
            :leirintaalueilmoitus
            [{:type-id :valtakirja :type-group :hakija}
             {:type-id :ilmoituslomake :type-group :ilmoituslomakkeet}
             {:type-id :yleiskartta :type-group :kartat}
             {:type-id :sijaintikartta :type-group :kartat}
             {:type-id    :muut_paatokset_sopimukset
              :type-group :laitoksen_tiedot}
             {:type-id    :arvio_ymparistoriskeista
              :type-group :laitoksen_toiminta}
             {:type-id    :yleiskuvaus_toiminnasta
              :type-group :laitoksen_toiminta}
             {:type-id    :tiedot_toiminnan_sijaintipaikasta
              :type-group :laitosalue_sen_ymparisto}
             {:type-id    :ote_asemakaavasta
              :type-group :laitosalue_sen_ymparisto}
             {:type-id    :ote_yleiskaavasta
              :type-group :laitosalue_sen_ymparisto}
             {:type-id    :selvitys_rajanaapureista
              :type-group :laitosalue_sen_ymparisto}
             {:type-id    :kiinteiston-omistajan-suostumus-luvan-hakemiseen
              :type-group :muut}
             {:type-id :muu :type-group :muut}
             {:type-id :asemapiirros :type-group :paapiirustus}]}
           (settings-types-by-operation test-organization))))

  (testing "attachment-settings-updates"
    (is (nil? (attachment-settings-updates {})))
    (is (nil? (attachment-settings-updates {:operations-attachment-settings
                                            {:operation-nodes
                                             {:meluilmoitus {:allowed-attachments {:types {:koeluontoinen_toiminta [:kuvaus_toiminnasta]
                                                                                           :laitoksen_toiminta     [:yleiskuvaus_toiminnasta]}}}}}})))
    (is (= {$unset {:operations-attachment-settings.operation-nodes.meluilmoitus.allowed-attachments.types.foo true}
            $pull  {:operations-attachment-settings.operation-nodes.promootio.allowed-attachments.types.muut {$in #{:what :why}}}}
           (attachment-settings-updates {:operations-attachment-settings
                                         {:operation-nodes
                                          {:meluilmoitus {:allowed-attachments {:types {:foo                [:bar :baz]
                                                                                        :laitoksen_toiminta [:yleiskuvaus_toiminnasta]}}}
                                           :promootio    {:allowed-attachments {:types {:muut           [:what :why :muu]
                                                                                        :yleiset-alueet [:valokuva]}}}}}})))
    (is (= {$unset
            {:operations-attachment-settings.operation-nodes.lannan-levittamisesta-poikkeustilanteessa.allowed-attachments.types.kartat            true
             :operations-attachment-settings.operation-nodes.lannan-levittamisesta-poikkeustilanteessa.allowed-attachments.types.ilmoituslomakkeet true
             :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.paapiirustus                           true
             :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.kartat                                 true
             :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.ilmoituslomakkeet                      true
             :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.laitoksen_tiedot                       true}
            $pull
            {:operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.laitosalue_sen_ymparisto
             {$in #{:ote_asemakaavasta :ote_yleiskaavasta}}
             :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.muut
             {$in #{:kiinteiston-omistajan-suostumus-luvan-hakemiseen}}}}
           (attachment-settings-updates test-organization)))))

(def types-by-operation #'oat/types-by-operation)
(def operations-updates #'oat/operations-updates)

(deftest operations-attachments-test
  (testing "types-by-operation"
    (is (= {} (types-by-operation {})))
    (is (= {:hello [{:type-group :group :type-id :id}
                    {:type-group :other :type-id :thing}]}
           (types-by-operation {:operations-attachments {:hello [["group" "id"]
                                                                 ["other" "thing"]]}})))
    (is (= {:ilmoitus-poikkeuksellisesta-tilanteesta
            [{:type-id :muu :type-group :muut}]
            :maa-aineslupa
            [{:type-id :kivenmurskaamo :type-group :erityissuunnitelmat}
             {:type-id :muu :type-group :muut}
             {:type-id :ottamissuunnitelma :type-group :erityissuunnitelmat}
             {:type-id    :kaivannaisjatteen_jatehuoltosuunnitelma
              :type-group :erityissuunnitelmat}
             {:type-id :ottamisalueen_omistus_hallintaoikeus :type-group :hakija}
             {:type-id :selvitys_tieyhteyksista_oikeuksista :type-group :muut}
             {:type-id    :vakuus_ottamisen_aloittamiseksi_ennen_luvan_lainvoimaa
              :type-group :muut}
             {:type-id :valtakirja :type-group :hakija}
             {:type-id :vakuusasiakirja :type-group :muut}
             {:type-id :pohjavesitutkimus :type-group :muut}]
            :lannan-levittamisesta-poikkeustilanteessa
            [{:type-id :ilmoituslomake :type-group :ilmoituslomakkeet}
             {:type-id :yleiskartta :type-group :kartat}
             {:type-id :sijaintikartta :type-group :kartat}]
            :maa-ainesten-kotitarveotto
            [{:type-id :ottamispaikan-sijainti :type-group :kartat}
             {:type-id :muu :type-group :muut}]
            :jatteen-keraystoiminta [{:type-id :muu :type-group :muut}]
            :lannan-varastointi
            [{:type-id :lantapatterin-sijainti :type-group :kartat}
             {:type-id :muu :type-group :muut}]
            :meluilmoitus
            [{:type-id :muu :type-group :muut}
             {:type-id :ilmoituslomake :type-group :ilmoituslomakkeet}]
            :yleinen-ilmoitus
            [{:type-id :asemapiirros :type-group :paapiirustus}
             {:type-id :ilmoituslomake :type-group :ilmoituslomakkeet}
             {:type-id :leikkauspiirros :type-group :paapiirustus}
             {:type-id :muu :type-group :muut}
             {:type-id :muut_paatokset_sopimukset :type-group :laitoksen_tiedot}
             {:type-id :sijaintikartta :type-group :kartat}
             {:type-id    :voimassa_olevat_ymparistolupa_vesilupa
              :type-group :laitoksen_tiedot}
             {:type-id :yleiskuvaus_toiminnasta :type-group :laitoksen_toiminta}
             {:type-id    :yleisolle_tarkoitettu_tiivistelma
              :type-group :laitoksen_toiminta}]
            :koeluontoinen-toiminta [{:type-id :muu :type-group :muut}]}
           (types-by-operation test-organization))))

  (testing "operations-updates"
    (is (nil? (operations-updates {})))
    (is (nil? (operations-updates {:operations-attachments {:promootio [["muut" "muu"]]}})))
    (is (= {$pull {:operations-attachments.promootio {$in #{[:good :bye]
                                                            [:see :ya]}}}}
          (operations-updates {:operations-attachments {:promootio [["muut" "muu"]
                                                                    ["good" "bye"]
                                                                    ["see" "ya"]]}})))
    (is (= {$pull
           {:operations-attachments.maa-aineslupa
            {$in
             #{[:muut :vakuusasiakirja]
               [:muut :pohjavesitutkimus]
               [:hakija :ottamisalueen_omistus_hallintaoikeus]
               [:muut :selvitys_tieyhteyksista_oikeuksista]
               [:muut :vakuus_ottamisen_aloittamiseksi_ennen_luvan_lainvoimaa]}}
            :operations-attachments.lannan-levittamisesta-poikkeustilanteessa
            {$in
             #{[:kartat :sijaintikartta]
               [:ilmoituslomakkeet :ilmoituslomake]
               [:kartat :yleiskartta]}}
            :operations-attachments.maa-ainesten-kotitarveotto
            {$in #{[:kartat :ottamispaikan-sijainti]}}
            :operations-attachments.lannan-varastointi
            {$in #{[:kartat :lantapatterin-sijainti]}}
            :operations-attachments.meluilmoitus
            {$in #{[:ilmoituslomakkeet :ilmoituslomake]}}
            :operations-attachments.yleinen-ilmoitus
            {$in
             #{[:laitoksen_tiedot :muut_paatokset_sopimukset]
               [:paapiirustus :leikkauspiirros]
               [:kartat :sijaintikartta]
               [:laitoksen_tiedot :voimassa_olevat_ymparistolupa_vesilupa]
               [:paapiirustus :asemapiirros]
               [:ilmoituslomakkeet :ilmoituslomake]}}}}
           (operations-updates test-organization)))))

(deftest organization-operation-attachment-updates
  (is (nil? (oat/organization-operation-attachment-updates {})))
  (is (nil? (oat/organization-operation-attachment-updates
              {:operations-attachment-settings
               {:operation-nodes
                {:meluilmoitus {:allowed-attachments {:types {:laitoksen_toiminta [:yleiskuvaus_toiminnasta]}}}
                 :promootio    {:allowed-attachments {:types {:muut           [:muu]
                                                              :yleiset-alueet [:valokuva]}}}}}
               :operations-attachments {:promootio [["muut" "muu"]]}})))
  (is (= {$unset {:operations-attachment-settings.operation-nodes.meluilmoitus.allowed-attachments.types.foo true}
          $pull  {:operations-attachment-settings.operation-nodes.promootio.allowed-attachments.types.muut {$in #{:what :why}}
                  :operations-attachments.promootio                                                        {$in #{[:good :bye] [:see :ya]}}}}
        (oat/organization-operation-attachment-updates
          {:operations-attachment-settings
           {:operation-nodes
            {:meluilmoitus {:allowed-attachments {:types {:foo                [:bar :baz]
                                                          :laitoksen_toiminta [:yleiskuvaus_toiminnasta]}}}
             :promootio    {:allowed-attachments {:types {:muut           [:what :why :muu]
                                                          :yleiset-alueet [:valokuva]}}}}}
           :operations-attachments {:promootio [["muut" "muu"]
                                                ["good" "bye"]
                                                ["see" "ya"]]}})))
  (is (= {$unset
          {:operations-attachment-settings.operation-nodes.lannan-levittamisesta-poikkeustilanteessa.allowed-attachments.types.kartat            true
           :operations-attachment-settings.operation-nodes.lannan-levittamisesta-poikkeustilanteessa.allowed-attachments.types.ilmoituslomakkeet true
           :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.paapiirustus                           true
           :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.kartat                                 true
           :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.ilmoituslomakkeet                      true
           :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.laitoksen_tiedot                       true}
          $pull
          {:operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.laitosalue_sen_ymparisto
           {$in #{:ote_asemakaavasta :ote_yleiskaavasta}}
           :operations-attachment-settings.operation-nodes.leirintaalueilmoitus.allowed-attachments.types.muut
           {$in #{:kiinteiston-omistajan-suostumus-luvan-hakemiseen}}
           :operations-attachments.maa-aineslupa
           {$in
            #{[:muut :vakuusasiakirja]
              [:muut :pohjavesitutkimus]
              [:hakija :ottamisalueen_omistus_hallintaoikeus]
              [:muut :selvitys_tieyhteyksista_oikeuksista]
              [:muut :vakuus_ottamisen_aloittamiseksi_ennen_luvan_lainvoimaa]}}
           :operations-attachments.lannan-levittamisesta-poikkeustilanteessa
           {$in
            #{[:kartat :sijaintikartta]
              [:ilmoituslomakkeet :ilmoituslomake]
              [:kartat :yleiskartta]}}
           :operations-attachments.maa-ainesten-kotitarveotto
           {$in #{[:kartat :ottamispaikan-sijainti]}}
           :operations-attachments.lannan-varastointi
           {$in #{[:kartat :lantapatterin-sijainti]}}
           :operations-attachments.meluilmoitus
           {$in #{[:ilmoituslomakkeet :ilmoituslomake]}}
           :operations-attachments.yleinen-ilmoitus
           {$in
            #{[:laitoksen_tiedot :muut_paatokset_sopimukset]
              [:paapiirustus :leikkauspiirros]
              [:kartat :sijaintikartta]
              [:laitoksen_tiedot :voimassa_olevat_ymparistolupa_vesilupa]
              [:paapiirustus :asemapiirros]
              [:ilmoituslomakkeet :ilmoituslomake]}}}}
         (oat/organization-operation-attachment-updates test-organization))))
