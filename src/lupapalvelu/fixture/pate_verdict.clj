(ns lupapalvelu.fixture.pate-verdict
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [sade.core :refer :all]
            [schema.core :as sc]
            [lupapalvelu.pate.schemas :as ps]))


(def created (now))


(def users (filter (comp #{"admin" "sonja" "pena" "kaino@solita.fi" "erkki@example.com" "sipoo-r-backend"} :username) minimal/users))

(def verdic-templates-setting
  "Some PATE verdict-templates to help with testing"
  {:templates [{:id        "5a7aff3e5266a1d9c1581956"
                :draft     {:verdict-dates         ["lainvoimainen" "anto" "julkipano"]
                            :bulletinOpDescription ""
                            :giver                 "viranhaltija"
                            :vastaava-tj           true
                            :vastaava-tj-included  true
                            :conditions            {}
                            :language              "fi"
                            :reviews               {:5a7affbf5266a1d9c1581957 {:included true
                                                                               :selected true}
                                                    :5a7affcc5266a1d9c1581958 {:included true
                                                                               :selected true}
                                                    :5a7affe15266a1d9c1581959 {:included true
                                                                               :selected true}}
                            :paatosteksti          "Ver Dict"
                            :upload                true
                            :paloluokka            true}
                :name      "P\u00e4\u00e4t\u00f6spohja"
                :category  "r"
                :modified  created
                :deleted   false
                :published {:published created
                            :data      {:verdict-dates         ["lainvoimainen" "anto" "julkipano"]
                                        :bulletinOpDescription ""
                                        :giver                 "viranhaltija"
                                        :plans                 ["5a85960a809b5a1e454f3233"
                                                                "5a85960a809b5a1e454f3234"]
                                        :vastaava-tj           true
                                        :vastaava-tj-included  true
                                        :language              "fi"
                                        :paatosteksti          "Ver Dict"
                                        :upload                true
                                        :paloluokka            true}
                            :inclusions ["appeal"
                                         "verdict-dates"
                                         "vss-luokka"
                                         "bulletinOpDescription"
                                         "purpose"
                                         "giver"
                                         "complexity"
                                         "plans"
                                         "autopaikat"
                                         "tj"
                                         "tj-included"
                                         "link-to-settings"
                                         "verdict-code"
                                         "complexity-text"
                                         "extra-info"
                                         "iv-tj"
                                         "conditions"
                                         "rights"
                                         "iv-tj-included"
                                         "language"
                                         "erityis-tj-included"
                                         "vv-tj-included"
                                         "reviews"
                                         "paatosteksti"
                                         "vastaava-tj"
                                         "deviations"
                                         "neighbors"
                                         "vastaava-tj-included"
                                         "link-to-settings-no-label"
                                         "vv-tj"
                                         "upload"
                                         "statements"
                                         "erityis-tj"
                                         "add-condition"
                                         "paloluokka"]
                            :settings  {:verdict-code ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
                                        :foremen      ["vastaava-tj"]
                                        :date-deltas  {:julkipano     {:delta 2 :unit "days"}
                                                       :anto          {:delta 0 :unit "days"}
                                                       :muutoksenhaku {:delta 1 :unit "days"}
                                                       :lainvoimainen {:delta 1 :unit "days"}
                                                       :aloitettava   {:delta 1 :unit "years"}
                                                       :voimassa      {:delta 1 :unit "years"}}
                                        :plans        [{:fi       "Suunnitelmat" :sv "Planer" :en "Plans"
                                                        :selected true}
                                                       {:fi       "ErityisSuunnitelmat" :sv "SpecialPlaner" :en "SpecialPlans"
                                                        :selected true}]
                                        :reviews      [{:fi   "Aloituskokous" :sv       "start" :en "start"
                                                        :type "aloituskokous" :selected true}
                                                       {:fi   "Loppukatselmus" :sv       "Loppu" :en "Loppu"
                                                        :type "loppukatselmus" :selected true}
                                                       {:fi   "Katselmus"     :sv       "Syn" :en "Review"
                                                        :type "muu-katselmus" :selected true}]}}}
               {:id       "5a7c4f33d98b0fe901eee1e6"
                :draft    {}
                :name     "P\u00e4\u00e4t\u00f6spohja"
                :category "r"
                :modified created
                :deleted  false}
               {:id "5acc68c953b771ded5d45605",
                :draft {:verdict-dates ["voimassa" "anto"],
                        :language "fi",
                        :giver "viranhaltija",
                        :verdict-code "hyvaksytty",
                        :paatosteksti "\nVerdict given\n",
                        :removed-sections {:appeal true,
                                           :purpose true,
                                           :buildings true,
                                           :complexity true,
                                           :plans true,
                                           :foremen true,
                                           :extra-info true,
                                           :conditions true,
                                           :rights true,
                                           :reviews true,
                                           :deviations true,
                                           :neighbors true,
                                           :statements true},
                        :bulletinOpDescription "\nBulletin\n"},
                :name "Jatkoaika",
                :category "r",
                :modified 1523345647736,
                :deleted false,
                :published {:published 1523345649254,
                            :data {:verdict-dates ["voimassa" "anto"],
                                   :bulletinOpDescription "\nBulletin\n",
                                   :giver "viranhaltija",
                                   :plans [],
                                   :removed-sections {:appeal true,
                                                      :purpose true,
                                                      :buildings true,
                                                      :complexity true,
                                                      :plans true,
                                                      :foremen true,
                                                      :extra-info true,
                                                      :conditions true,
                                                      :rights true,
                                                      :reviews true,
                                                      :deviations true,
                                                      :neighbors true,
                                                      :statements true},
                                   :verdict-code "hyvaksytty",
                                   :language "fi",
                                   :reviews [],
                                   :paatosteksti "\nVerdict given\n"}
                            :inclusions ["verdict-dates"
                                         "bulletinOpDescription"
                                         "giver"
                                         "link-to-settings"
                                         "verdict-code"
                                         "language"
                                         "paatosteksti"
                                         "upload"]
                            :settings {:verdict-code ["evatty"
                                                      "myonnetty"
                                                      "osittain-myonnetty"
                                                      "hyvaksytty"
                                                      "annettu-lausunto"
                                                      "ei-puollettu"],
                                       :foremen ["erityis-tj"],
                                       :date-deltas {:julkipano {:delta 1, :unit "days"},
                                                     :anto {:delta 1, :unit "days"},
                                                     :muutoksenhaku {:delta 1, :unit "days"},
                                                     :lainvoimainen {:delta 1, :unit "days"},
                                                     :aloitettava {:delta 1, :unit "years"},
                                                     :voimassa {:delta 1, :unit "years"}},
                                       :plans [],
                                       :reviews []}}}]
   :settings  {:r {:draft    {:voimassa                 "1"
                              :julkipano                "2"
                              :boardname                "asd"
                              :muutoksenhaku            "1"
                              :anto                     "0"
                              :aloitettava              "1"
                              :verdict-code             ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
                              :lainvoimainen            "1"
                              :lautakunta-muutoksenhaku "2"
                              :reviews                  {:5a7affbf5266a1d9c1581957 {:fi   "Startti" :sv "start" :en "start"
                                                                                    :type "aloituskokous"}
                                                         :5a7affcc5266a1d9c1581958 {:fi   "Loppukatselmus" :sv "Loppu" :en "Loppu"
                                                                                    :type "loppukatselmus"}
                                                         :5a7affe15266a1d9c1581959 {:fi   "Katselmus" :sv "Syn" :en "Review"
                                                                                    :type "muu-katselmus"}}
                              :plans                    {:5a85960a809b5a1e454f3233 {:fi "Suunnitelmat" :sv "Planer" :en "Plans"}
                                                         :5a85960a809b5a1e454f3234 {:fi "ErityisSuunnitelmat" :sv "SpecialPlaner"
                                                                                    :en "SpecialPlans"}}}
                   :modified created}}})

(sc/validate ps/PateSavedVerdictTemplates verdic-templates-setting)

(defn update-sipoo [org]
  (if-not (= "753-R" (:id org))
    org
    (assoc org :verdict-templates verdic-templates-setting
               :pate-enabled true)))

(def organizations (->> minimal/organizations
                        (filter (comp (set (mapcat (comp keys :orgAuthz) users)) keyword :id))
                        (map update-sipoo)))

(deffixture "pate-verdict" {}
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations organizations))
