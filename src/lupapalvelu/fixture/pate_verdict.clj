(ns lupapalvelu.fixture.pate-verdict
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [sade.core :refer :all]
            [schema.core :as sc]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schemas :as ps]))


(def created (now))


(def users (filter (comp #{"admin" "sonja" "ronja" "pena" "mikko@example.com"
                           "kaino@solita.fi" "erkki@example.com"
                           "sipoo-r-backend" "sipoo" "matti-rest-api-user"}
                         :username)
                   minimal/users))

(defn wrap [v]
  ((metadata/wrapper "person" 12345) v))

(defn plan [fi sv en]
  {:fi (wrap fi) :sv (wrap sv) :en (wrap en)})

(defn review [fi sv en type]
  (assoc (plan fi sv en) :type (wrap type)))

(def verdict-templates-setting-r
  "Some PATE verdict-templates to help with testing building control applications"
  {:templates [{:id        "5a7aff3e5266a1d9c1581956"
                :draft     {:verdict-dates         (wrap ["lainvoimainen" "anto" "julkipano"])
                            :bulletinOpDescription (wrap "")
                            :giver                 (wrap "viranhaltija")
                            :vastaava-tj           (wrap true)
                            :vastaava-tj-included  (wrap true)
                            :conditions            {}
                            :language              (wrap "fi")
                            :reviews               {:5a7affbf5266a1d9c1581957 {:included (wrap true)
                                                                               :selected (wrap true)}
                                                    :5a7affcc5266a1d9c1581958 {:included (wrap true)
                                                                               :selected (wrap true)}
                                                    :5a7affe15266a1d9c1581959 {:included (wrap true)
                                                                               :selected (wrap true)}}
                            :paatosteksti          (wrap "Ver Dict")
                            :upload                (wrap true)
                            :paloluokka            (wrap true)}
                :name      (wrap "P\u00e4\u00e4t\u00f6spohja")
                :category  "r"
                :modified  created
                :deleted   (wrap false)
                :published {:published  (wrap created)
                            :data       {:verdict-dates         ["lainvoimainen" "anto" "julkipano"]
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
                                         "kokoontumistilanHenkilomaara"
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
                            :settings   {:verdict-code ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
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
                :name     (wrap "P\u00e4\u00e4t\u00f6spohja")
                :category "r"
                :modified created
                :deleted  (wrap false)}
               {:id        "5acc68c953b771ded5d45605",
                :draft     {:verdict-dates         (wrap ["voimassa" "anto"]),
                            :language              (wrap "fi"),
                            :giver                 (wrap "viranhaltija"),
                            :verdict-code          (wrap "hyvaksytty"),
                            :paatosteksti          (wrap "\nVerdict given\n"),
                            :removed-sections      {:appeal     (wrap true),
                                                    :purpose    (wrap true),
                                                    :buildings  (wrap true),
                                                    :complexity (wrap true),
                                                    :plans      (wrap true),
                                                    :foremen    (wrap true),
                                                    :extra-info (wrap true),
                                                    :conditions (wrap true),
                                                    :rights     (wrap true),
                                                    :reviews    (wrap true),
                                                    :deviations (wrap true),
                                                    :neighbors  (wrap true),
                                                    :statements (wrap true)},
                            :bulletinOpDescription (wrap "\nBulletin\n")},
                :name      (wrap "Jatkoaika"),
                :category  "r",
                :modified  1523345647736,
                :deleted   (wrap false),
                :published {:published  (wrap 1523345649254),
                            :data       {:verdict-dates         ["voimassa" "anto"],
                                         :bulletinOpDescription "\nBulletin\n",
                                         :giver                 "viranhaltija",
                                         :plans                 [],
                                         :removed-sections      {:appeal     true,
                                                                 :purpose    true,
                                                                 :buildings  true,
                                                                 :complexity true,
                                                                 :plans      true,
                                                                 :foremen    true,
                                                                 :extra-info true,
                                                                 :conditions true,
                                                                 :rights     true,
                                                                 :reviews    true,
                                                                 :deviations true,
                                                                 :neighbors  true,
                                                                 :statements true},
                                         :verdict-code          "hyvaksytty",
                                         :language              "fi",
                                         :reviews               [],
                                         :paatosteksti          "\nVerdict given\n"}
                            :inclusions ["verdict-dates"
                                         "bulletinOpDescription"
                                         "giver"
                                         "link-to-settings"
                                         "verdict-code"
                                         "language"
                                         "paatosteksti"
                                         "upload"]
                            :settings   {:verdict-code ["evatty"
                                                        "myonnetty"
                                                        "osittain-myonnetty"
                                                        "hyvaksytty"
                                                        "annettu-lausunto"
                                                        "ei-puollettu"],
                                         :foremen      ["erityis-tj"],
                                         :date-deltas  {:julkipano     {:delta 1, :unit "days"},
                                                        :anto          {:delta 1, :unit "days"},
                                                        :muutoksenhaku {:delta 1, :unit "days"},
                                                        :lainvoimainen {:delta 1, :unit "days"},
                                                        :aloitettava   {:delta 1, :unit "years"},
                                                        :voimassa      {:delta 1, :unit "years"}},
                                         :plans        [],
                                         :reviews      []}}}
               {:id        "5b0689f8cb66507187fbc18f",
                :draft     {:verdict-dates (wrap ["muutoksenhaku" "lainvoimainen" "anto"]),
                            :language      (wrap "fi"),
                            :giver         (wrap "viranhaltija"),
                            :paatosteksti  (wrap "Paatos annettu\n"),
                            :verdict-code  (wrap "hyvaksytty"),
                            :appeal        (wrap "Ohje muutoksen hakuun.\n"),
                            :upload        (wrap true)},
                :name      (wrap "TJ verdict template"),
                :category  "tj",
                :modified  1527155907891,
                :deleted   (wrap false),
                :published {:published  (wrap 1527155908947),
                            :data       {:appeal        "Ohje muutoksen hakuun.\n",
                                         :verdict-dates ["muutoksenhaku" "lainvoimainen" "anto"],
                                         :giver         "viranhaltija",
                                         :verdict-code  "hyvaksytty",
                                         :language      "fi",
                                         :paatosteksti  "Paatos annettu\n",
                                         :upload        true},
                            :inclusions ["link-to-settings-no-label"
                                         "verdict-dates"
                                         "giver"
                                         "link-to-settings"
                                         "verdict-code"
                                         "language"
                                         "paatosteksti"
                                         "appeal"
                                         "upload"],
                            :settings   {:verdict-code ["myonnetty" "hyvaksytty"],
                                         :date-deltas  {:julkipano     {:delta 0, :unit "days"},
                                                        :anto          {:delta 2, :unit "days"},
                                                        :muutoksenhaku {:delta 2, :unit "days"},
                                                        :lainvoimainen {:delta 2, :unit "days"},
                                                        :aloitettava   {:delta 0, :unit "days"},
                                                        :voimassa      {:delta 0, :unit "days"}},
                                         :plans        [],
                                         :reviews      []}}}
               {:id        "ba7aff3e5266a1d9c1581666"
                :draft     {:verdict-dates         (wrap ["julkipano"])
                            :bulletinOpDescription (wrap "Pate bulletin description")
                            :giver                 (wrap "viranhaltija")
                            :vastaava-tj           (wrap true)
                            :vastaava-tj-included  (wrap true)
                            :conditions            {}
                            :language              (wrap "fi")
                            :reviews               {:5a7affbf5266a1d9c1581957 {:included (wrap true)
                                                                               :selected (wrap true)}
                                                    :5a7affcc5266a1d9c1581958 {:included (wrap true)
                                                                               :selected (wrap true)}
                                                    :5a7affe15266a1d9c1581959 {:included (wrap true)
                                                                               :selected (wrap true)}}
                            :paatosteksti          (wrap "Ver Dict")
                            :upload                (wrap true)
                            :paloluokka            (wrap true)}
                :name      (wrap "P\u00e4\u00e4t\u00f6spohja")
                :category  "r"
                :modified  created
                :deleted   (wrap false)
                :published {:published  (wrap created)
                            :data       {:verdict-dates         ["julkipano"]
                                         :bulletinOpDescription "Pate bulletin description"
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
                                         "kokoontumistilanHenkilomaara"
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
                            :settings   {:verdict-code      ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
                                         :foremen           ["vastaava-tj"]
                                         :date-deltas       {:julkipano     {:delta 2 :unit "days"}
                                                             :anto          {:delta 0 :unit "days"}
                                                             :muutoksenhaku {:delta 1 :unit "days"}
                                                             :lainvoimainen {:delta 1 :unit "days"}
                                                             :aloitettava   {:delta 1 :unit "years"}
                                                             :voimassa      {:delta 1 :unit "years"}}
                                         :plans             [{:fi       "Suunnitelmat" :sv "Planer" :en "Plans"
                                                              :selected true}
                                                             {:fi       "ErityisSuunnitelmat" :sv "SpecialPlaner" :en "SpecialPlans"
                                                              :selected true}]
                                         :reviews           [{:fi   "Aloituskokous" :sv       "start" :en "start"
                                                              :type "aloituskokous" :selected true}
                                                             {:fi   "Loppukatselmus" :sv       "Loppu" :en "Loppu"
                                                              :type "loppukatselmus" :selected true}
                                                             {:fi   "Katselmus"     :sv       "Syn" :en "Review"
                                                              :type "muu-katselmus" :selected true}]
                                         :organization-name "Sipoo Building Control"
                                         :boardname         "The Board"}}}]
   :settings  {:r  {:draft    {:organization-name        "Sipoo Building Control"
                               :voimassa                 (wrap "1")
                               :julkipano                (wrap "2")
                               :boardname                (wrap "asd")
                               :muutoksenhaku            (wrap "1")
                               :anto                     (wrap "0")
                               :aloitettava              (wrap "1")
                               :verdict-code             (wrap ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"])
                               :lainvoimainen            (wrap "1")
                               :lautakunta-muutoksenhaku (wrap "2")
                               :reviews                  {:5a7affbf5266a1d9c1581957 (review "Startti" "start" "start"
                                                                                            "aloituskokous")
                                                          :5a7affcc5266a1d9c1581958 (review "Loppukatselmus" "Slut" "End"
                                                                                            "loppukatselmus")
                                                          :5a7affe15266a1d9c1581959 (review "Katselmus" "Syn" "Review"
                                                                                            "muu-katselmus")}
                               :plans                    {:5a85960a809b5a1e454f3233 (plan "Suunnitelmat" "Planer" "Plans")
                                                          :5a85960a809b5a1e454f3234 (plan "ErityisSuunnitelmat"
                                                                                          "SpecialPlaner" "SpecialPlans")}}
                    :modified created}
               :tj {:draft    {:anto                     (wrap "2")
                               :lainvoimainen            (wrap "2")
                               :muutoksenhaku            (wrap "2")
                               :verdict-code             (wrap ["myonnetty" "hyvaksytty"])
                               :lautakunta-muutoksenhaku (wrap "14")
                               :boardname                (wrap "Lauta ja Kunta")
                               :julkipano                (wrap "1")
                               :aloitettava              (wrap "1")
                               :voimassa                 (wrap "1")}
                    :modified created}}})

(def verdict-templates-setting-ya
  {:templates [{:id        "6131f1db58f49f0bc57493e0"
                :name      (wrap "Päätöspohja")
                :category  "ya"
                :modified  created
                :deleted   (wrap false)
                :draft     {:language      (wrap "fi")
                            :verdict-dates (wrap ["lainvoimainen" "muutoksenhaku"])
                            :giver         (wrap "viranhaltija")}
                :published {:published  created
                            :data       {:verdict-dates ["lainvoimainen" "muutoksenhaku"]
                                         :giver         "viranhaltija"
                                         :language      "fi"}
                            :inclusions ["add-condition"
                                         "appeal"
                                         "bulletinOpDescription"
                                         "conditions"
                                         "giver"
                                         "handler-titles"
                                         "inform-others"
                                         "language"
                                         "paatosteksti"
                                         "plans"
                                         "proposaltext"
                                         "review-info"
                                         "reviews"
                                         "statements"
                                         "subtitle"
                                         "title"
                                         "upload"
                                         "verdict-code"
                                         "verdict-dates"]
                            :settings {:verdict-code ["hyvaksytty" "puollettu" "myonnetty" "evatty" "annettu-lausunto"]
                                       :organization-name "Sipoo"
                                       :date-deltas {:julkipano     {:delta 2 :unit "days"}
                                                     :anto          {:delta 6 :unit "days"}
                                                     :muutoksenhaku {:delta 12 :unit "days"}
                                                     :lainvoimainen {:delta 20 :unit "days"}
                                                     :aloitettava   {:delta 30 :unit "years"}}
                                       :plans []
                                       :reviews []
                                       :handler-titles []}}}]
   :settings {:ya {:draft {:julkipano                (wrap "2")
                           :boardname                (wrap "Lautakunta")
                           :handler-titles           {}
                           :muutoksenhaku            (wrap "12")
                           :anto                     (wrap "6")
                           :plans                    {}
                           :aloitettava              (wrap "30")
                           :organization-name        (wrap "Sipoo")
                           :verdict-code             (wrap ["hyvaksytty" "puollettu" "myonnetty" "evatty"])
                           :lainvoimainen            (wrap "20")
                           :lautakunta-muutoksenhaku (wrap "15")}
                   :modified created}}})

(sc/validate ps/PateSavedVerdictTemplates verdict-templates-setting-r)

(defn update-sipoo [org]
  (case (:id org)
    "753-R" (assoc org
                   :verdict-templates verdict-templates-setting-r
                   :scope (mapv (fn [sco] (assoc-in sco [:pate :enabled] true)) (:scope org))
                   :state-change-msg-enabled true)
    "753-YA" (assoc org
                    :verdict-templates verdict-templates-setting-ya
                    :scope (mapv (fn [sco] (assoc-in sco [:pate :enabled] true)) (:scope org)))
    org))

(def organizations (->> minimal/organizations
                        (filter (comp (set (mapcat (comp keys :orgAuthz) users)) keyword :id))
                        (map update-sipoo)))

(deffixture "pate-verdict" {}
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations organizations))
