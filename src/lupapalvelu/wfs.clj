(ns lupapalvelu.wfs)

(defn wfs-query [& e]
  (str "<?xml version='1.0' encoding='UTF-8'?>
        <wfs:GetFeature version='1.1.0'
            xmlns:oso='http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02'
            xmlns:wfs='http://www.opengis.net/wfs'
            xmlns:gml='http://www.opengis.net/gml'
            xmlns:ogc='http://www.opengis.net/ogc'
            xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
            xsi:schemaLocation='http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd'>
          <wfs:Query typeName='oso:Osoitenimi'>"
            (apply str e)
       "  </wfs:Query>
        </wfs:GetFeature>"))

(defn wfs-sort-by
  ([property-name]
    (wfs-sort-by property-name "desc"))
  ([property-name order]
    (str "<ogc:SortBy>
            <ogc:SortProperty>
              <ogc:PropertyName>" property-name "</ogc:PropertyName>
            </ogc:SortProperty>
            <ogc:SortOrder>" (.toUpperCase order) "</ogc:SortOrder>
          </ogc:SortBy>")))

(defn wfs-filter [& e]
  (str "<ogc:Filter>" (apply str e) "</ogc:Filter>"))

(defn wfs-and [& e]
  (str "<ogc:And>" (apply str e) "</ogc:And>"))

(defn wfs-or [& e]
  (str "<ogc:Or>" (apply str e) "</ogc:Or>"))

(defn wfs-property-filter [filter-name property-name property-value]
  (str
    "<ogc:" filter-name " wildCard='*' singleChar='?' escape='!' matchCase='false'>
       <ogc:PropertyName>" property-name "</ogc:PropertyName>
       <ogc:Literal>" property-value "</ogc:Literal>
     </ogc:" filter-name ">"))

(defn wfs-property-is-like [property-name property-value]
  (wfs-property-filter "PropertyIsLike" property-name property-value))

(defn wfs-property-is-equal [property-name property-value]
  (wfs-property-filter "PropertyIsEqualTo" property-name property-value))

(defn wfs-property-is-less [property-name property-value]
  (wfs-property-filter "PropertyIsLessThan" property-name property-value))

(defn wfs-property-is-greater [property-name property-value]
  (wfs-property-filter "PropertyIsGreaterThan" property-name property-value))

(defn wfs-property-is-between [property-name property-lower-value property-upper-value]
  (str
    "<ogc:PropertyIsBetween wildCard='*' singleChar='?' escape='!' matchCase='false'>
       <ogc:PropertyName>" property-name "</ogc:PropertyName>
       <ogc:LowerBoundary>" property-lower-value "</ogc:LowerBoundary>"
    "  <ogc:UpperBoundary>" property-upper-value "</ogc:UpperBoundary>
     </ogc:PropertyIsBetween>"))
