(ns lupapalvelu.waste-ads-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.waste-ads :as waste-ads]
            [monger.operators :refer :all]
            [sade.util :refer [fn->>]]
            [lupapalvelu.mongo :as mongo]))

(def model-ad-1
  {:municipality "753",
   :documents    [{:schema-info {:name "hakija-r"}, :data {}}
                  {:schema-info {:name "uusiRakennus"}, :data {}}
                  {:schema-info {:name "hankkeen-kuvaus-rakennuslupa"}, :data {}}
                  {:schema-info {:name "paatoksen-toimitus-rakval"}, :data {}}
                  {:schema-info {:name "maksaja"}, :data {}}
                  {:schema-info {:name "rakennuspaikka"}, :data {}}
                  {:schema-info {:name "paasuunnittelija"}, :data {}}
                  {:schema-info {:name "suunnittelija"}, :data {}}
                  {:schema-info {:name "rakennusjatesuunnitelma"}, :data {}}
                  {:schema-info {:name "rakennusjateselvitys"},
                   :data        {:contact            {:name  {:value "Rolf Rolfsson"},
                                                      :phone {:value "040-9876543"},
                                                      :email {:value "rolf@rolf.fi"}},
                                 :availableMaterials {:0 {:aines      {:value "Kipsi", :modified 1495197973654},
                                                          :maara      {:value "1", :modified 1495197977605},
                                                          :yksikko    {:value "kg", :modified 1495197978381},
                                                          :saatavilla {:value "19.05.2017", :modified 1495197979714},
                                                          :kuvaus     {:value "Kilo kipsi\u00e4", :modified 1495197988284}}}}}],
   :id           "LP-753-2017-90001"})

(def model-ad-2
  {:municipality "753",
   :documents    [{:schema-info {:name "hakija-r"}, :data {}}
                  {:schema-info {:name "uusiRakennus"}, :data {}}
                  {:schema-info {:name "hankkeen-kuvaus-rakennuslupa"}, :data {}}
                  {:schema-info {:name "paatoksen-toimitus-rakval"}, :data {}}
                  {:schema-info {:name "maksaja"}, :data {}}
                  {:schema-info {:name "rakennuspaikka"}, :data {}}
                  {:schema-info {:name "paasuunnittelija"}, :data {}}
                  {:schema-info {:name "suunnittelija"}, :data {}}
                  {:schema-info {:name "rakennusjatesuunnitelma"}, :data {}}
                  {:schema-info {:name "rakennusjateselvitys"},
                   :data        {:contact            {:name  {:value "Alf Alfsson"},
                                                      :phone {:value "050-1234567"},
                                                      :email {:value "alf@alf.fi"}},
                                 :availableMaterials {:0 {:aines      {:value "Muovia", :modified 1495198098835},
                                                          :maara      {:value "1", :modified 1495198100062},
                                                          :yksikko    {:value "m3", :modified 1495198101869},
                                                          :saatavilla {:value "19.05.2017", :modified 1495198104826},
                                                          :kuvaus     {:value "Paljon muovia", :modified 1495198121215}},
                                                      :1 {:aines      {:value "Paperia", :modified 1495198109816},
                                                          :maara      {:value "2", :modified 1495198112230},
                                                          :yksikko    {:value "m2", :modified 1495198114729},
                                                          :saatavilla {:value "19.05.2017", :modified 1495198116376},
                                                          :kuvaus     {:value "Kaksi aukeamaa hesaria", :modified 1495198129745}}}}}],
   :id           "LP-753-2017-90002"})

(def model-ad-3
  {:municipality "753",
   :documents    [{:schema-info {:name "hakija-r"}, :data {}}
                  {:schema-info {:name "uusiRakennus"}, :data {}}
                  {:schema-info {:name "hankkeen-kuvaus-rakennuslupa"}, :data {}}
                  {:schema-info {:name "paatoksen-toimitus-rakval"}, :data {}}
                  {:schema-info {:name "maksaja"}, :data {}}
                  {:schema-info {:name "rakennuspaikka"}, :data {}}
                  {:schema-info {:name "paasuunnittelija"}, :data {}}
                  {:schema-info {:name "suunnittelija"}, :data {}}
                  {:schema-info {:name "rakennusjatesuunnitelma"}, :data {}}
                  {:schema-info {:name "rakennusjateselvitys"},
                   :data        {:contact            {:name  {:value "Rolf Rolfsson"},
                                                      :phone {:value "040-9876543"},
                                                      :email {:value "rolf@rolf.fi"}},
                                 :availableMaterials {}}}],
   :id           "LP-753-2017-90003"})

(def model-ad-4
  {:municipality "753",
   :documents    [{:schema-info {:name "hakija-r"}, :data {}}
                  {:schema-info {:name "uusiRakennus"}, :data {}}
                  {:schema-info {:name "hankkeen-kuvaus-rakennuslupa"}, :data {}}
                  {:schema-info {:name "paatoksen-toimitus-rakval"}, :data {}}
                  {:schema-info {:name "maksaja"}, :data {}}
                  {:schema-info {:name "rakennuspaikka"}, :data {}}
                  {:schema-info {:name "paasuunnittelija"}, :data {}}
                  {:schema-info {:name "suunnittelija"}, :data {}}
                  {:schema-info {:name "rakennusjatesuunnitelma"}, :data {}}
                  {:schema-info {:name "rakennusjateselvitys"},
                   :data        {:contact            {:name  {:value "Rolf Rolfsson"},
                                                      :phone {:value ""},
                                                      :email {:value ""}},
                                 :availableMaterials {:0 {:aines      {:value "Kipsi", :modified 1495197973654},
                                                          :maara      {:value "1", :modified 1495197977605},
                                                          :yksikko    {:value "kg", :modified 1495197978381},
                                                          :saatavilla {:value "19.05.2017", :modified 1495197979714},
                                                          :kuvaus     {:value "Kilo kipsi\u00e4", :modified 1495197988284}}}}}],
   :id           "LP-753-2017-90004"})

(facts "waste-ads returns correct information"
       (fact "correct information in map, contact, materials, modified, municipality"
             (waste-ads/waste-ads "") => [{:contact      {:name "Alf Alfsson", :phone "050-1234567", :email "alf@alf.fi"},
                                           :materials    [{:aines "Muovia", :maara "1", :yksikko "m3", :saatavilla "19.05.2017", :kuvaus "Paljon muovia"}
                                                           {:aines "Paperia", :maara "2", :yksikko "m2", :saatavilla "19.05.2017", :kuvaus "Kaksi aukeamaa hesaria"}],
                                           :modified     1495198129745,
                                           :municipality "753"}
                                          {:contact      {:name "Rolf Rolfsson", :phone "040-9876543", :email "rolf@rolf.fi"},
                                           :materials    [{:aines "Kipsi", :maara "1", :yksikko "kg", :saatavilla "19.05.2017", :kuvaus "Kilo kipsi\u00e4"}],
                                           :modified     1495197988284,
                                           :municipality "753"}])

       (fact "correct feed in finnish"
             (waste-ads/waste-ads "" :rss :fi) => "<?xml version='1.0' encoding='UTF-8'?>\n<rss version='2.0' xmlns:atom='http://www.w3.org/2005/Atom'>\n<channel>\n<atom:link href='' rel='self' type='application/rss+xml'/>\n<title>\nLupapiste:Myyt\u00e4v\u00e4t/lahjoitettavat materiaalit\n</title>\n<link>\n\n</link>\n<description>\n\n</description>\n<generator>\nclj-rss\n</generator>\n<item>\n<title>\nLupapiste\n</title>\n<link>\nhttp://www.lupapiste.fi\n</link>\n<author>\nAlf Alfsson\n</author>\n<description>\n<![CDATA[ <div><span>Alf Alfsson 050-1234567 alf@alf.fi Sipoo</span><table><tr><th>Aines</th><th>M\u00e4\u00e4r\u00e4</th><th>Yksikk\u00f6</th><th>Saatavilla</th><th>Kuvaus</th></tr><tr><td>Muovia</td><td>1</td><td>Kuutiota</td><td>19.05.2017</td><td>Paljon muovia</td></tr><tr><td>Paperia</td><td>2</td><td>Neli\u00f6t\u00e4</td><td>19.05.2017</td><td>Kaksi aukeamaa hesaria</td></tr></table></div> ]]>\n</description>\n</item>\n<item>\n<title>\nLupapiste\n</title>\n<link>\nhttp://www.lupapiste.fi\n</link>\n<author>\nRolf Rolfsson\n</author>\n<description>\n<![CDATA[ <div><span>Rolf Rolfsson 040-9876543 rolf@rolf.fi Sipoo</span><table><tr><th>Aines</th><th>M\u00e4\u00e4r\u00e4</th><th>Yksikk\u00f6</th><th>Saatavilla</th><th>Kuvaus</th></tr><tr><td>Kipsi</td><td>1</td><td>Kilo</td><td>19.05.2017</td><td>Kilo kipsi\u00e4</td></tr></table></div> ]]>\n</description>\n</item>\n</channel>\n</rss>\n")

       (fact "correct feed in swedish"
             (waste-ads/waste-ads "" :rss :sv) => "<?xml version='1.0' encoding='UTF-8'?>\n<rss version='2.0' xmlns:atom='http://www.w3.org/2005/Atom'>\n<channel>\n<atom:link href='' rel='self' type='application/rss+xml'/>\n<title>\nLupapiste:Material som ska s\u00e4ljas/sk\u00e4nkas\n</title>\n<link>\n\n</link>\n<description>\n\n</description>\n<generator>\nclj-rss\n</generator>\n<item>\n<title>\nLupapiste\n</title>\n<link>\nhttp://www.lupapiste.fi\n</link>\n<author>\nAlf Alfsson\n</author>\n<description>\n<![CDATA[ <div><span>Alf Alfsson 050-1234567 alf@alf.fi Sibbo</span><table><tr><th>\u00c4mne</th><th>M\u00e4ngd</th><th>Enhet</th><th>Tillg\u00e4nglig</th><th>Beskrivning</th></tr><tr><td>Muovia</td><td>1</td><td>Kubikmeter</td><td>19.05.2017</td><td>Paljon muovia</td></tr><tr><td>Paperia</td><td>2</td><td>Kvadratmeter</td><td>19.05.2017</td><td>Kaksi aukeamaa hesaria</td></tr></table></div> ]]>\n</description>\n</item>\n<item>\n<title>\nLupapiste\n</title>\n<link>\nhttp://www.lupapiste.fi\n</link>\n<author>\nRolf Rolfsson\n</author>\n<description>\n<![CDATA[ <div><span>Rolf Rolfsson 040-9876543 rolf@rolf.fi Sibbo</span><table><tr><th>\u00c4mne</th><th>M\u00e4ngd</th><th>Enhet</th><th>Tillg\u00e4nglig</th><th>Beskrivning</th></tr><tr><td>Kipsi</td><td>1</td><td>Kilo</td><td>19.05.2017</td><td>Kilo kipsi\u00e4</td></tr></table></div> ]]>\n</description>\n</item>\n</channel>\n</rss>\n")

       (against-background
         (mongo/select anything anything anything) => [model-ad-1 model-ad-2 model-ad-3 model-ad-4]))
