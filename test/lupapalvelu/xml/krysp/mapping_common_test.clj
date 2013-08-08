(ns lupapalvelu.xml.krysp.mapping-common-test
  (:use lupapalvelu.xml.krysp.mapping-common
        clojure.data.xml
        midje.sweet
        lupapalvelu.xml.emit))


(def yksilointitieto [{:tag :yksilointitieto}
                      {:tag :alkuHetki}
                      {:tag :loppuHetki}])

(def result-str "<?xml version='1.0' encoding='UTF-8'?>
<root>
 <yksilointitieto>Uniikki arvo</yksilointitieto>
 <alkuHetki>10.10.2012</alkuHetki>
 <loppuHetki>11.11.2013</loppuHetki>
</root>")

(def result (emit-str (parse-str result-str)))

(fact "data-with-childs, sring comparison"
  (emit-str
    (element-to-xml
      {:root
       {:yksilointitieto "Uniikki arvo"
        :alkuHetki "10.10.2012"
        :loppuHetki "11.11.2013"}}
      {:tag :root :child yksilointitieto})) => result)
