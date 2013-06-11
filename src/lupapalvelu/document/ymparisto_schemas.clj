(ns lupapalvelu.document.ymparisto-schemas
  (:use [lupapalvelu.document.schemas]))



(def sijainti (body simple-osoite
                    {:name "karttapiirto" :type :text :max-len 4000}))

(def kesto (body {:name "kesto" :type :group
                     :body [{:name "alku" :type :date}
                            {:name "loppu" :type :date}
                            {:name "kello" :type :group
                             :body [{:name "arkisin" :type :string :size "s"}
                                    {:name "lauantait" :type :string :size "s"}
                                    {:name "pyhat" :type :string :size "s"}]}]}))


(def kesto-mini (body {:name "kesto" :type :group
                     :body [{:name "alku" :type :date}
                            {:name "loppu" :type :date}]}))

(def meluilmoitus (body
                    {:name "rakentaminen" :type :group
                     :body [{:name "melua-aihettava-toiminta" :type :select
                             :body [{:name "louhinta"}
                                    {:name "murskaus"}
                                    {:name "paalutus"}]}
                            {:name "muu-rakentaminen" :type :string :size "m"}
                     {:name "kuvaus" :type :text :max-len 4000}
                     {:name "koneet" :type :text :max-len 4000}]}

                    {:name "tapahtuma" :type :group
                     :body [{:name "nimi" :type :string :size "m"}
                            {:name "ulkoilmakonsertti" :type :checkbox}
                            {:name "kuvaus" :type :text}] }
                    {:name "melu" :type :group
                     :body [{:name "melu10mdBa" :type :string :size "s"}
                            {:name "paivalla" :type :string :size "s"}
                            {:name "yolla" :type :string :size "s"}
                            {:name "mittaus" :type :string :size "m"}]}))

(def pima (body {:name "kuvaus" :type :text :max-len 4000}))

(def ympschemas
  (to-map-by-name [{:info {:name "meluilmoitus"
                           :order 50}
                    :body meluilmoitus}
                   {:info {:name "pima"
                           :order 51}
                    :body pima}
                   {:info {:name "ymp-ilm-kesto-mini"
                           :order 60}
                    :body kesto-mini}
                   {:info {:name "ymp-ilm-kesto"
                           :order 60}
                    :body kesto}

                   ]))



