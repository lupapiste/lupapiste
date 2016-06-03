(ns lupapalvelu.document.vesihuolto-schemas
   (:require [lupapalvelu.document.schemas :refer :all]
             [lupapalvelu.document.tools :refer :all]))

(def vesihuolto-rakennuspaikka [{:name "kiinteisto"
                                 :type :group
                                 :body [
                                        {:name "tilanNimi" :type :string :readonly true}
                                        {:name "rekisterointipvm" :type :string :readonly true}
                                        {:name "maapintaala" :type :string :readonly true :unit :hehtaaria}
                                        {:name "vesipintaala" :type :string :readonly true :unit :hehtaaria}]}])

(def vesihuolto-kiinteisto {:info {:name "vesihuolto-kiinteisto"
                                   :approvable true
                                   :removable false
                                   :repeating false
                                   :order 2
                                   :type :location}
                            :body (conj vesihuolto-rakennuspaikka
                                        {:name "kiinteistoonKuuluu" :type :group :repeating true
                                         :body [{:name "rakennuksenTyypi" :type :select :sortBy :displayname
                                                 :body [{:name "El\u00e4insuoja"}
                                                        {:name "Saunarakennus"}
                                                        {:name "Lomarakennus"}
                                                        {:name "Asuinrakennus"}
                                                        ei-tiedossa]}
                                                {:name "rakennusvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size :s}
                                                {:name "vapautus" :type :checkbox}
                                                {:name "kohteenVarustelutaso" :type :group
                                                 :body [{:name "Suihku" :type :checkbox}
                                                        {:name "Tiskiallas" :type :checkbox}
                                                        {:name "Astianpesukone" :type :checkbox}
                                                        {:name "Pyykinpesukone" :type :checkbox}
                                                        {:name "Lamminvesivaraaja" :type :checkbox}
                                                        {:name "Kuivakaymala" :type :checkbox}
                                                        {:name "WC" :type :checkbox}
                                                        ]}]})})

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
    :body [{:name "hulevedet" :type :select :sortBy :displayname :other-key "johdetaanMuualle"
            :body [{:name "ojaan"}
                   {:name "imeytetaan"}]}
           {:name "johdetaanMuualle" :type :string :size :l}]}
   {:info {:name "talousvedet"
           :approvable false
           :order 4
           :removable false
           :repeating false}
    :body [{:name "hankinta" :type :select :sortBy :displayname :other-key "muualta"
            :body [{:name "Vesihuoltolaitoksen vesijohdosta"}
                   {:name "Kiinteist\u00f6n rengaskaivosta"}
                   {:name "Kiinteist\u00f6n porakaivosta"}]}
           {:name "muualta" :type :string :size :l}
           {:name "johdatus" :type :select :sortBy :displayname
            :body [{:name "kannetaan kaivosta tai vesist\u00f6st\u00e4"}
                   {:name "pumpataan kaivosta tai vesist\u00f6st\u00e4"}
                   {:name "johdetaan paineellisena vesijohtoa pitkin rakennukseen"}
                   ei-tiedossa]}
           {:name "riittavyys" :type :select :sortBy :displayname
            :body [{:name "vesi ajoittain v\u00e4hiss\u00e4"}
                   {:name "vesi riitt\u00e4\u00e4 talouden tarpeisiin"}
                   ei-tiedossa
                   ]}]}
   {:info {:name "jatevedet"
           :approvable false
           :order 5
           :removable false
           :repeating false}
    :body [{:name "kuvaus"  :type :text :max-len 4000 :required true :layout :full-width}]}])
