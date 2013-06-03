(ns lupapalvelu.document.yleiset-alueet-schemas
  (:use [lupapalvelu.document.schemas]))

;; TODO: Tämä on vain esimerkki, tee oikea sisältö!

(def yleiset-alueet-kaivuu {:info {:name "yleiset-alueet-kaivuu"
                                   :order 60}
                            :body [{:name "huoneistoTunnus" :type :group
                                    :body [{:name "porras" :type :string :subtype :letter :case :upper :max-len 1 :size "s"}
                                           {:name "huoneistonumero" :type :string :subtype :number :min-len 1 :max-len 3 :size "s"}
                                           {:name "jakokirjain" :type :string :subtype :letter :case :lower :max-len 1 :size "s"}]}
                                   {:name "huoneistonTyyppi"
                                    :type :group
                                    :body [{:name "huoneistoTyyppi" :type :select
                                            :body [{:name "asuinhuoneisto"}
                                                   {:name "toimitila"}
                                                   {:name "ei tiedossa"}]}
                                           {:name "huoneistoala" :type :string :unit "m2" :subtype :number :size "s" :min 1 :max 9999999 :required true}
                                           {:name "huoneluku" :type :string :subtype :number :min 1 :max 99 :required true :size "s"}]}
                                   {:name "keittionTyyppi" :type :select :required true
                                    :body [{:name "keittio"}
                                           {:name "keittokomero"}
                                           {:name "keittotila"}
                                           {:name "tupakeittio"}
                                           {:name "ei tiedossa"}]}
                                   {:name "varusteet"
                                    :type :group
                                    :layout :vertical
                                    :body [{:name "WCKytkin" :type :checkbox}
                                           {:name "ammeTaiSuihkuKytkin" :type :checkbox}
                                           {:name "saunaKytkin" :type :checkbox}
                                           {:name "parvekeTaiTerassiKytkin" :type :checkbox}
                                           {:name "lamminvesiKytkin" :type :checkbox}]}]})

(def yleiset-alueet-schemas
  (to-map-by-name [yleiset-alueet-kaivuu]))