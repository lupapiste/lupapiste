(ns lupapalvelu.xml.krysp.review-reader)

(ns lupapalvelu.xml.krysp.review-reader
  (:require [taoensso.timbre :refer [trace debug info warn error]]
            [lupapalvelu.xml.krysp.common-reader :as common]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.validators :as v]
            [sade.xml :refer [get-text select select1 under has-text xml->edn]]
            [lupapalvelu.document.tools :as tools]
            [sade.util :as util]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.building :as building]))


(defn xml->reviews [xml]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)
        asiat (enlive/select xml-no-ns common/case-elem-selector)]
    (when (pos? (count asiat))
      (println "pos")
      (when (> (count asiat) 1)
        (error "Creating application from previous permit. More than one RakennusvalvontaAsia element were received in the xml message. Count:" (count asiat)))

      (let [asia (first asiat)
            katselmukset (map cr/all-of  (select asia [:katselmustieto :Katselmus]))]
        (println "#katselmukset" (count katselmukset))
        (-> katselmukset
            cr/convert-booleans
            cr/cleanup)))
    ))
