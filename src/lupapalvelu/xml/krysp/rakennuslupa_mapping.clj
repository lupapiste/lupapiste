(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  lupapalvelu.xml.emit
     clojure.data.xml
     clojure.java.io
     lupapalvelu.xml.krysp.yhteiset)
  )

  
  	
;RakVal
   (def tunnus [{:tag :tunnus 
               :child [{:tag :valtakunnallinenNumero}
                       {:tag :jarjestysnumero}
                       {:tag :kiinttun}
                       {:tag :rakennusnro}
                       {:tag :aanestysalue}]}])
 
;YHTEISET

 

(def rakennuslupa_to_krysp
  [{
   :tag :Rakennusvalvonta :attr { :xmlns:xs "http://www.w3.org/2001/XMLSchema"
                                 :xmlns:rakval "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
                                 :xmlns:gml "http://www.opengis.net/gml"
                                 :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
                                 :targetNamespace "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
                                 :elementFormDefault "qualified" 
                                 :attributeFormDefault "unqualified" 
                                 :version "2.0.0"}
   :child (conj rakennuspaikka rakennus rakennelma)}
  ]
  )
 


 (print (indent-str (element-to-xml {} [] (first rakennuslupa_to_krysp))))
 
(pprint rakennuslupa_to_krysp) 
			
  
  
  
  
  (with-open [input (java.io.FileInputStream. "C:/temp/krysp_from_logica.xml")]
    (print (.name input))
  
 (def lk (parse-str (slurp "C:/temp/krysp_from_logica.xml")))
    
 (keys lk)
 
 ((:content lk))
  
 
 
 (pprint lk)
  
  
  
  
  