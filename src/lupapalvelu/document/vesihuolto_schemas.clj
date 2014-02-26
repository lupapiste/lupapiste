(ns lupapalvelu.document.vesihuolto-schemas
   (:require [lupapalvelu.document.schemas :refer :all]
             [lupapalvelu.document.tools :refer :all]))

(def vesihuolto-kiinteisto {:info {:name "vesihuolto-kiinteisto"
                                   :approvable true
                                   :removable false
                                   :repeating false
                                   :order 2}
                            :body (conj (schema-body-without-element-by-name rakennuspaikka
                                                                             "maaraalaTunnus"
                                                                             "rantaKytkin"
                                                                              "hallintaperuste"
                                                                              "kaavanaste")
                                                {:name "kiinteistoonKuuluu" :type :group :repeating true
                                                 :body [{:name "rakennuksenTyypi" :type :select
                                                         :body [{:name "El\u00e4insuoja"}
                                                                {:name "Saunarakennus"}
                                                                {:name "Lomarakennus"}
                                                                {:name "Asuinrakennus"}
                                                                {:name "ei tiedossa"}]}
                                                        {:name "rakennusvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size "s"}
                                                        {:name "vapautus" :type :checkbox}]}
                                                {:name "kohteenVarustelutaso" :type :group
                                                 :body [{:name "Suihku" :type :checkbox}
                                                        {:name "Tiskiallas" :type :checkbox}
                                                        {:name "Astianpesukone" :type :checkbox}
                                                        {:name "Pyykinpesukone" :type :checkbox}
                                                        {:name "L\u00e4mminvesivaraaja" :type :checkbox}
                                                        {:name "Kuivak\u00e4ym\u00e4l\u00e4" :type :checkbox}
                                                        {:name "WC(vesik\u00e4ym\u00e4l\u00e4)" :type :checkbox}
                                                        ]})})




(defschemas
  1
  [vesihuolto-kiinteisto
   {:info {:name "hankkeen-kuvaus-vesihuolto"
           :approvable true
           :order 1}
    :body [kuvaus]}
   {:info {:name "hulevedet"
           :approvable false
           :order 3
           :removable false
           :repeating false
           }
    :body [{:name "hulevedet" :type :select
            :body [{:name "Johdetaan muualle, minne"}
                   {:name "Johdetaan rajaojaan tai muuhun ojaan"}
                   {:name "imeytet\u00e4\u00e4n maaper\u00e4\u00e4n"}]}]}
   {:info {:name "talousvedet"
           :approvable false
           :order 4
           :removable false
           :repeating false}
    :body [{:name "hankinta" :type :select
            :body [{:name "Vesihuoltolaitoksen vesijohdosta"}
                   {:name "Kiinteist\u00f6n rengaskaivosta"}
                   {:name "Kiinteist\u00f6n porakaivosta"}
                   {:name "ei tiedossa"}]}
           {:name "johdatus" :type :select
            :body [{:name "kannetaan kaivosta tai vesist\u00f6st\u00e4"}
                   {:name "pumpataan kaivosta tai vesist\u00f6st\u00e4"}
                   {:name "johdetaan paineellisena vesijohtoa pitkin rakennukseen"}
                   {:name "ei tiedossa"}]}
           {:name "riittavyys" :type :select
            :body [{:name "vesi ajoittain v\u00e4hiss\u00e4"}
                   {:name "vesi riitt\u00e4\u00e4 talouden tarpeisiin"}
                   {:name "ei tiedossa"}
                   ]}]}
   {:info {:name "jatevedet"
           :approvable false
           :order 5
           :removable false
           :repeating false}
    :body [{:name "kuvaus"  :type :text :max-len 4000 :required true :layout :full-width}]}]
  )