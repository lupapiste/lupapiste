(ns lupapalvelu.low-carbon-assessment.config)

(def ^:const BUILDING-LIFESPAN-YEARS 50)

(def ^:const climate-report-lammitetty-nettoala-path
  [:rakennuskohteen-osan-tiedot :lammitetty-nettoala])

(def ^:const building-doc-lammitetty-nettoala-path
  [:rakennuskohteen-osa :energiatiedot :lammitetty-nettoala])

(def ^:const building-doc-rakennusluokka-vahahiilisyys-path
  [:rakennusluokka-vahahiilisyys :rakennusluokka-vahahiilisyys])

(def ^:const building-doc-kohteen-laji-path
  [:kaytto :kohteen-laji])

(def ^:const climate-report-required-fields
  [[:laatimistiedot :pvm]
   [:laatimistiedot :laatija]
   [:laatimistiedot :arvioinnissa-kaytetty-ohjelmisto]])

(def ^:const TIMESTAMP-2029
  "2029-01-01T00:00:00.000Z in milliseconds, used to determine which carbon footprint limits apply"
  1861912800000)

(def limits-2026-2028
  "These limits are to be used before 01.01.2029 (< 1861912800000)."
  {:rivitalo                          {:limit 16.0}
   :asuinkerrostalo                   {:limit 16.0}
   :toimistorakennus-ja-terveyskeskus {:limit 20.0}
   :liikerakennus-ym                  {:limit 22.0}
   :majoitusliikerakennus-ym          {:limit 25.0}
   :opetusrakennus-ja-paivakoti       {:limit 20.0}
   :liikuntahalli                     {:limit 21.0}
   :sairaala                          {:limit 29.0}
   :varastorakennus-ym                {:limit 24.0}})

(def limits-2029
  "These limits should be used after 01.01.2029 (> 1861912800000)."
  {:rivitalo                          {:limit 13.0}
   :asuinkerrostalo                   {:limit 12.0}
   :toimistorakennus-ja-terveyskeskus {:limit 17.0}
   :liikerakennus-ym                  {:limit 18.0}
   :majoitusliikerakennus-ym          {:limit 22.0}
   :opetusrakennus-ja-paivakoti       {:limit 16.0}
   :liikuntahalli                     {:limit 18.0}
   :sairaala                          {:limit 28.0}
   :varastorakennus-ym                {:limit 21.0}})

(defn with-epbd-limit
  "EPBD stands for the European Performance of Buildings Directive.
  This value is equal to the carbon footprint over the course of a buildings lifespan (50 years)"
  [{limit :limit}]
  {:limit      limit
   :epbd-limit (* limit BUILDING-LIFESPAN-YEARS)})


