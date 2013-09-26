(ns lupapalvelu.document.poikkeamis-canonical)

(defmulti poikkeus-application-to-canonical :permitSubtype)

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application]
  (println "ASSDA")
  )

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application]
  (println "ASSDA!!!!!")
  )



