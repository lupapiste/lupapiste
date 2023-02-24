(ns lupapalvelu.migration.new-operation-migration-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.sweet :refer :all]))


(def old-selected-operations-list [:aita
                                   :aloitusoikeus
                                   :jakaminen-tai-yhdistaminen
                                   :asuinrakennus
                                   :auto-katos
                                   :kaivuu
                                   :kortteli-yht-alue-muutos
                                   :jatkoaika
                                   :maalampo
                                   :mainoslaite
                                   :masto-tms
                                   :muu-maisema-toimenpide
                                   :muu-laajentaminen
                                   :muu-tontti-tai-kort-muutos
                                   :muu-rakentaminen
                                   :muu-uusi-rakentaminen
                                   :markatilan-laajentaminen
                                   :paikoutysjarjestus-muutos
                                   :parveke-tai-terassi
                                   :perus-tai-kant-rak-muutos
                                   :puun-kaataminen
                                   :julkisivu-muutos
                                   :jatevesi
                                   :laajentaminen
                                   :purkaminen
                                   :kayttotark-muutos
                                   :suunnittelijan-nimeaminen
                                   :takka-tai-hormi
                                   :tontin-ajoliittyman-muutos
                                   :varasto-tms
                                   :vapaa-ajan-asuinrakennus])

(def new-selected-operations-list [:aita
                                   :aloitusoikeus
                                   :jakaminen-tai-yhdistaminen
                                   :kerrostalo-rivitalo
                                   :pientalo
                                   :auto-katos
                                   :kaivuu
                                   :kortteli-yht-alue-muutos
                                   :raktyo-aloit-loppuunsaat
                                   :maalampo
                                   :mainoslaite
                                   :masto-tms
                                   :muu-maisema-toimenpide
                                   :linjasaneeraus
                                   :muu-tontti-tai-kort-muutos
                                   :muu-rakentaminen
                                   :muu-uusi-rakentaminen
                                   :teollisuusrakennus
                                   :markatilan-laajentaminen
                                   :paikoutysjarjestus-muutos
                                   :parveke-tai-terassi
                                   :perus-tai-kant-rak-muutos
                                   :puun-kaataminen
                                   :rak-valm-tyo
                                   :julkisivu-muutos
                                   :jatevesi
                                   :kerrostalo-rt-laaj
                                   :pientalo-laaj
                                   :vapaa-ajan-rakennus-laaj
                                   :talousrakennus-laaj
                                   :teollisuusrakennus-laaj
                                   :muu-rakennus-laaj
                                   :purkaminen
                                   :kayttotark-muutos
                                   :sisatila-muutos
                                   :suunnittelijan-nimeaminen
                                   :takka-tai-hormi
                                   :tontin-ajoliittyman-muutos
                                   :varasto-tms
                                   :vapaa-ajan-asuinrakennus])



(fact "new ops should replace old ones in selected operations"
  (new-selected-operations old-selected-operations-list) => new-selected-operations-list
  )

(def old-operations-attachments-map {:vapaa-ajan-asuinrakennus [["paapiirustus" "pohjapiirros"] ["hakija" "ote_kauppa_ja_yhdistysrekisterista"] ["muut" "vaestonsuojasuunnitelma"] ["muut" "valaistussuunnitelma"]]
                                     :asuinrakennus [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirros"] ["hakija" "valtakirja"] ["muut" "vaestonsuojasuunnitelma"]]})

(def new-operations-attachments-map {:pientalo [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirros"] ["hakija" "valtakirja"] ["muut" "vaestonsuojasuunnitelma"]]
                                     :vapaa-ajan-asuinrakennus [["paapiirustus" "pohjapiirros"] ["hakija" "ote_kauppa_ja_yhdistysrekisterista"] ["muut" "vaestonsuojasuunnitelma"] ["muut" "valaistussuunnitelma"]]
                                     :asuinrakennus [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirros"] ["hakija" "valtakirja"] ["muut" "vaestonsuojasuunnitelma"]]
                                     :kerrostalo-rivitalo [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirros"] ["hakija" "valtakirja"] ["muut" "vaestonsuojasuunnitelma"]]})

(def old-operations-attachments-map-2 {:jatevesi [[:paapiirustus :asemapiirros]
                                                  [:rakennuspaikka :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma]]
                                       :muu-uusi-rakentaminen [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                               [:paapiirustus :asemapiirros]
                                                               [:paapiirustus :pohjapiirros]
                                                               [:paapiirustus :leikkauspiirros]
                                                               [:paapiirustus :julkisivupiirros]
                                                               [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                               [:muut :muu]]
                                       :muu-tontti-tai-kort-muutos [[:rakennuspaikka :ote_ranta-asemakaavasta]
                                                                    [:paapiirustus :asemapiirros]
                                                                    [:muut :muu]]
                                       :parveke-tai-terassi [[:paapiirustus :asemapiirros]
                                                             [:paapiirustus :pohjapiirros]
                                                             [:paapiirustus :julkisivupiirros]
                                                             [:muut :muu]]
                                       :aita [[:paapiirustus :asemapiirros]
                                              [:muut :muu]
                                              [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :laajentaminen [[:paapiirustus :asemapiirros]
                                                       [:muut :energiataloudellinen_selvitys]
                                                       [:paapiirustus :julkisivupiirros]
                                                       [:paapiirustus :leikkauspiirros]
                                                       [:paapiirustus :pohjapiirros]
                                                       [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :muu-laajentaminen [[:paapiirustus :asemapiirros]
                                                           [:paapiirustus :pohjapiirros]
                                                           [:paapiirustus :leikkauspiirros]
                                                           [:paapiirustus :julkisivupiirros]]
                                       :jakaminen-tai-yhdistaminen [[:muut :muu]
                                                                    [:paapiirustus :pohjapiirros]]
                                       :vapaa-ajan-asuinrakennus [[:paapiirustus :asemapiirros]
                                                                  [:paapiirustus :julkisivupiirros]
                                                                  [:paapiirustus :leikkauspiirros]
                                                                  [:muut :muu]
                                                                  [:paapiirustus :pohjapiirros]
                                                                  [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                                  [:muut :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta]
                                                                  [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]]
                                       :markatilan-laajentaminen [[:paapiirustus :leikkauspiirros]
                                                                  [:paapiirustus :pohjapiirros]
                                                                  [:osapuolet :paa_ja_rakennussuunnittelijan_tiedot]]
                                       :perus-tai-kant-rak-muutos [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                                   [:paapiirustus :asemapiirros]
                                                                   [:paapiirustus :pohjapiirros]
                                                                   [:paapiirustus :leikkauspiirros]
                                                                   [:muut :rakennesuunnitelma]
                                                                   [:muut :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta]]
                                       :kaivuu [[:rakennuspaikka :tonttikartta_tarvittaessa]]
                                       :asuinrakennus [[:paapiirustus :asemapiirros]
                                                       [:muut :energiataloudellinen_selvitys]
                                                       [:paapiirustus :julkisivupiirros]
                                                       [:paapiirustus :leikkauspiirros]
                                                       [:muut :muu]
                                                       [:paapiirustus :pohjapiirros]
                                                       [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                       [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]
                                                       [:muut :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta]]
                                       :masto-tms [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                   [:paapiirustus :asemapiirros]
                                                   [:muut :muu]]
                                       :paikoutysjarjestus-muutos [[:paapiirustus :asemapiirros] [:muut :muu]]
                                       :tontin-ajoliittyman-muutos [[:paapiirustus :asemapiirros]]
                                       :maalampo [[:muut :muu]
                                                  [:rakennuspaikka :tonttikartta_tarvittaessa]]
                                       :muu-maisema-toimenpide [[:muut :muu]]
                                       :suunnittelijan-nimeaminen [[:osapuolet :tutkintotodistus]]
                                       :kayttotark-muutos [[:paapiirustus :asemapiirros]
                                                           [:paapiirustus :pohjapiirros]
                                                           [:paapiirustus :julkisivupiirros]
                                                           [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                           [:muut :energiataloudellinen_selvitys]
                                                           [:muut :muu]]
                                       :takka-tai-hormi [[:paapiirustus :julkisivupiirros]
                                                         [:paapiirustus :leikkauspiirros]
                                                         [:paapiirustus :pohjapiirros]]
                                       :puun-kaataminen [[:rakennuspaikka :tonttikartta_tarvittaessa]
                                                         [:muut :muu]]
                                       :purkaminen [[:paapiirustus :asemapiirros]
                                                    [:muut :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]
                                                    [:rakennuspaikka :tonttikartta_tarvittaessa]
                                                    [:muut :valokuva]]
                                       :varasto-tms [[:paapiirustus :asemapiirros]
                                                     [:paapiirustus :julkisivupiirros]
                                                     [:paapiirustus :leikkauspiirros]
                                                     [:paapiirustus :pohjapiirros]
                                                     [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                     [:muut :muu]]
                                       :muu-rakentaminen [[:paapiirustus :asemapiirros]],
                                       :tyonjohtajan-nimeaminen [[:osapuolet :tutkintotodistus]],
                                       :mainoslaite [[:paapiirustus :asemapiirros]
                                                     [:paapiirustus :julkisivupiirros]
                                                     [:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]]
                                       :kortteli-yht-alue-muutos [[:paapiirustus :asemapiirros]
                                                                  [:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]]
                                       :auto-katos [[:paapiirustus :asemapiirros]
                                                    [:paapiirustus :julkisivupiirros]
                                                    [:paapiirustus :leikkauspiirros]
                                                    [:muut :muu]
                                                    [:paapiirustus :pohjapiirros]
                                                    [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                    [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]]
                                       :julkisivu-muutos [[:paapiirustus :asemapiirros]
                                                          [:paapiirustus :julkisivupiirros]
                                                          [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                          [:muut :julkisivujen_varityssuunnitelma]]})

(def new-operations-attachments-map-2 {:jatevesi [[:paapiirustus :asemapiirros]
                                                  [:rakennuspaikka :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma]]
                                       :muu-uusi-rakentaminen [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                               [:paapiirustus :asemapiirros]
                                                               [:paapiirustus :pohjapiirros]
                                                               [:paapiirustus :leikkauspiirros]
                                                               [:paapiirustus :julkisivupiirros]
                                                               [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                               [:muut :muu]]
                                       :teollisuusrakennus [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                            [:paapiirustus :asemapiirros]
                                                            [:paapiirustus :pohjapiirros]
                                                            [:paapiirustus :leikkauspiirros]
                                                            [:paapiirustus :julkisivupiirros]
                                                            [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                            [:muut :muu]]
                                       :muu-tontti-tai-kort-muutos [[:rakennuspaikka :ote_ranta-asemakaavasta]
                                                                    [:paapiirustus :asemapiirros]
                                                                    [:muut :muu]]
                                       :parveke-tai-terassi [[:paapiirustus :asemapiirros]
                                                             [:paapiirustus :pohjapiirros]
                                                             [:paapiirustus :julkisivupiirros]
                                                             [:muut :muu]]
                                       :aita [[:paapiirustus :asemapiirros]
                                              [:muut :muu]
                                              [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :kerrostalo-rt-laaj [[:paapiirustus :asemapiirros]
                                                            [:muut :energiataloudellinen_selvitys]
                                                            [:paapiirustus :julkisivupiirros]
                                                            [:paapiirustus :leikkauspiirros]
                                                            [:paapiirustus :pohjapiirros]
                                                            [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :pientalo-laaj [[:paapiirustus :asemapiirros]
                                                       [:muut :energiataloudellinen_selvitys]
                                                       [:paapiirustus :julkisivupiirros]
                                                       [:paapiirustus :leikkauspiirros]
                                                       [:paapiirustus :pohjapiirros]
                                                       [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :vapaa-ajan-rakennus-laaj [[:paapiirustus :asemapiirros]
                                                                  [:muut :energiataloudellinen_selvitys]
                                                                  [:paapiirustus :julkisivupiirros]
                                                                  [:paapiirustus :leikkauspiirros]
                                                                  [:paapiirustus :pohjapiirros]
                                                                  [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :talousrakennus-laaj [[:paapiirustus :asemapiirros]
                                                             [:muut :energiataloudellinen_selvitys]
                                                             [:paapiirustus :julkisivupiirros]
                                                             [:paapiirustus :leikkauspiirros]
                                                             [:paapiirustus :pohjapiirros]
                                                             [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :teollisuusrakennus-laaj [[:paapiirustus :asemapiirros]
                                                                 [:muut :energiataloudellinen_selvitys]
                                                                 [:paapiirustus :julkisivupiirros]
                                                                 [:paapiirustus :leikkauspiirros]
                                                                 [:paapiirustus :pohjapiirros]
                                                                 [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :muu-rakennus-laaj [[:paapiirustus :asemapiirros]
                                                           [:muut :energiataloudellinen_selvitys]
                                                           [:paapiirustus :julkisivupiirros]
                                                           [:paapiirustus :leikkauspiirros]
                                                           [:paapiirustus :pohjapiirros]
                                                           [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :laajentaminen [[:paapiirustus :asemapiirros]
                                                       [:muut :energiataloudellinen_selvitys]
                                                       [:paapiirustus :julkisivupiirros]
                                                       [:paapiirustus :leikkauspiirros]
                                                       [:paapiirustus :pohjapiirros]
                                                       [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]]
                                       :muu-laajentaminen [[:paapiirustus :asemapiirros]
                                                           [:paapiirustus :pohjapiirros]
                                                           [:paapiirustus :leikkauspiirros]
                                                           [:paapiirustus :julkisivupiirros]]
                                       :linjasaneeraus [[:paapiirustus :asemapiirros]
                                                        [:paapiirustus :pohjapiirros]
                                                        [:paapiirustus :leikkauspiirros]
                                                        [:paapiirustus :julkisivupiirros]]
                                       :jakaminen-tai-yhdistaminen [[:muut :muu]
                                                                    [:paapiirustus :pohjapiirros]]
                                       :vapaa-ajan-asuinrakennus [[:paapiirustus :asemapiirros]
                                                                  [:paapiirustus :julkisivupiirros]
                                                                  [:paapiirustus :leikkauspiirros]
                                                                  [:muut :muu]
                                                                  [:paapiirustus :pohjapiirros]
                                                                  [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                                  [:muut :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta]
                                                                  [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]]
                                       :markatilan-laajentaminen [[:paapiirustus :leikkauspiirros]
                                                                  [:paapiirustus :pohjapiirros]
                                                                  [:osapuolet :paa_ja_rakennussuunnittelijan_tiedot]]
                                       :perus-tai-kant-rak-muutos [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                                   [:paapiirustus :asemapiirros]
                                                                   [:paapiirustus :pohjapiirros]
                                                                   [:paapiirustus :leikkauspiirros]
                                                                   [:muut :rakennesuunnitelma]
                                                                   [:muut :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta]]
                                       :kaivuu [[:rakennuspaikka :tonttikartta_tarvittaessa]]
                                       :asuinrakennus [[:paapiirustus :asemapiirros]
                                                       [:muut :energiataloudellinen_selvitys]
                                                       [:paapiirustus :julkisivupiirros]
                                                       [:paapiirustus :leikkauspiirros]
                                                       [:muut :muu]
                                                       [:paapiirustus :pohjapiirros]
                                                       [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                       [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]
                                                       [:muut :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta]]
                                       :kerrostalo-rivitalo [[:paapiirustus :asemapiirros]
                                                             [:muut :energiataloudellinen_selvitys]
                                                             [:paapiirustus :julkisivupiirros]
                                                             [:paapiirustus :leikkauspiirros]
                                                             [:muut :muu]
                                                             [:paapiirustus :pohjapiirros]
                                                             [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                             [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]
                                                             [:muut :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta]]
                                       :pientalo [[:paapiirustus :asemapiirros]
                                                  [:muut :energiataloudellinen_selvitys]
                                                  [:paapiirustus :julkisivupiirros]
                                                  [:paapiirustus :leikkauspiirros]
                                                  [:muut :muu]
                                                  [:paapiirustus :pohjapiirros]
                                                  [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                  [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]
                                                  [:muut :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta]]
                                       :masto-tms [[:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]
                                                   [:paapiirustus :asemapiirros]
                                                   [:muut :muu]]
                                       :paikoutysjarjestus-muutos [[:paapiirustus :asemapiirros] [:muut :muu]]
                                       :tontin-ajoliittyman-muutos [[:paapiirustus :asemapiirros]]
                                       :maalampo [[:muut :muu]
                                                  [:rakennuspaikka :tonttikartta_tarvittaessa]]
                                       :muu-maisema-toimenpide [[:muut :muu]]
                                       :suunnittelijan-nimeaminen [[:osapuolet :tutkintotodistus]]
                                       :kayttotark-muutos [[:paapiirustus :asemapiirros]
                                                           [:paapiirustus :pohjapiirros]
                                                           [:paapiirustus :julkisivupiirros]
                                                           [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                           [:muut :energiataloudellinen_selvitys]
                                                           [:muut :muu]]
                                       :sisatila-muutos [[:paapiirustus :asemapiirros]
                                                         [:paapiirustus :pohjapiirros]
                                                         [:paapiirustus :julkisivupiirros]
                                                         [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                         [:muut :energiataloudellinen_selvitys]
                                                         [:muut :muu]]
                                       :takka-tai-hormi [[:paapiirustus :julkisivupiirros]
                                                         [:paapiirustus :leikkauspiirros]
                                                         [:paapiirustus :pohjapiirros]]
                                       :puun-kaataminen [[:rakennuspaikka :tonttikartta_tarvittaessa]
                                                         [:muut :muu]]
                                       :rak-valm-tyo [[:rakennuspaikka :tonttikartta_tarvittaessa]
                                                      [:muut :muu]]
                                       :purkaminen [[:paapiirustus :asemapiirros]
                                                    [:muut :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]
                                                    [:rakennuspaikka :tonttikartta_tarvittaessa]
                                                    [:muut :valokuva]]
                                       :varasto-tms [[:paapiirustus :asemapiirros]
                                                     [:paapiirustus :julkisivupiirros]
                                                     [:paapiirustus :leikkauspiirros]
                                                     [:paapiirustus :pohjapiirros]
                                                     [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                     [:muut :muu]]
                                       :muu-rakentaminen [[:paapiirustus :asemapiirros]],
                                       :tyonjohtajan-nimeaminen [[:osapuolet :tutkintotodistus]],
                                       :mainoslaite [[:paapiirustus :asemapiirros]
                                                     [:paapiirustus :julkisivupiirros]
                                                     [:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]]
                                       :kortteli-yht-alue-muutos [[:paapiirustus :asemapiirros]
                                                                  [:rakennuspaikka :ote_asemakaavasta_jos_asemakaava_alueella]]
                                       :auto-katos [[:paapiirustus :asemapiirros]
                                                    [:paapiirustus :julkisivupiirros]
                                                    [:paapiirustus :leikkauspiirros]
                                                    [:muut :muu]
                                                    [:paapiirustus :pohjapiirros]
                                                    [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                    [:rakennuspaikka :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]]
                                       :julkisivu-muutos [[:paapiirustus :asemapiirros]
                                                          [:paapiirustus :julkisivupiirros]
                                                          [:ennakkoluvat_ja_lausunnot :selvitys_naapurien_kuulemisesta]
                                                          [:muut :julkisivujen_varityssuunnitelma]]})

(fact "new ops' attachments should be copied from old attachments"
  (new-operations-attachments old-operations-attachments-map) => new-operations-attachments-map
  (new-operations-attachments old-operations-attachments-map-2) => new-operations-attachments-map-2)
