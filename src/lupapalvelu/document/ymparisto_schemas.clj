(ns lupapalvelu.document.ymparisto-schemas
  (:use [lupapalvelu.document.schemas]))



(def sijainti (body simple-osoite {:name "karttapiirto" :type :text}))



(def ympschemas
  (to-map-by-name [sijainti]))
;sijainti

;aiheuttava toiminta

;kesto


;paastot


;leviaminen

;tiedoittaminen seuranat torjunta

;lisätietodeot

