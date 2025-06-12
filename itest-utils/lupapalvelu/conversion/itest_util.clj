(ns lupapalvelu.conversion.itest-util
  (:require [babashka.fs :as fs]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.itest-system :as test-system]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as common-reader]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(def testfile "dev-resources/krysp/99-0000-13-A.xml")

(defn mk-test-dir []
  (fs/create-temp-dir {:prefix "test-conversion-"}))

(defn test-dir? [dir]
  (ss/starts-with? (fs/file-name dir) "test-conversion-"))

(defn rm-test-dir [dir]
  {:pre [(test-dir? dir)]}
  (fs/delete-tree dir))

(defn tmp-edn-file [dir]
  (->> (format "cfg-%s.edn" (random-uuid))
       (fs/path dir)
       str))

(defn transform-xml
  "Takes an xml `source` (slurpable) and returns transformed xml string."
  [source & {:keys [backend-id application-id permit-type submitted]}]
  (cond-> source
    application-id
    (->> slurp
         (assoc-in {:LP-id       application-id
                    :permit-type (or (some-> permit-type name) "R")}
                   [:kuntagml :xml])
         conv-doc/xml-with-application-id)

    (not application-id)
    slurp

    (or backend-id submitted)
    (-> (xml/parse-string "utf8")
        (cond->
            backend-id (enlive/at
                         [:rakval:luvanTunnisteTiedot :yht:LupaTunnus :yht:kuntalupatunnus]
                         (enlive/content backend-id))
            submitted       (enlive/at
                              [:rakval:RakennusvalvontaAsia :gml:location]
                              (enlive/after {:tag     :rakval:kasittelynTilatieto
                                             :content [{:tag     :yht:Tilamuutos
                                                        :content [{:tag     :yht:pvm
                                                                   :content [(date/xml-date submitted)]}
                                                                  {:tag     :yht:tila
                                                                   :content ["vireillä"]}]}]}))
            true            xml/element-to-string
            ;; Make sure that the year is determined solely by the backend-id.
            (not submitted) (ss/replace #"vireillä" "ei tiedossa")))

    true
    test-system/fix-urls))

(defn write-test-xml [dir target-base-name & opts]
  (let [filename (str dir "/" target-base-name)]
    (spit filename
          (apply transform-xml testfile opts))
    filename))

(defn write-config
  "Writes configuration and returns the filepath."
  [dir & args]
  (let [edn-file (tmp-edn-file dir)]
    (apply conv-cfg/write-configuration edn-file args)
    (when (fs/exists? edn-file)
      edn-file)))

(defn abspath [f]
  (str (fs/absolutize f)))

(defn read-xml
  "Slurps, parses and strips namespaces from given `xml-filename`. Note: slurp supports
  file:// protocol, too."
  [xml-filename]
  (-> (slurp xml-filename)
      (xml/parse-string  "utf8")
      common-reader/strip-xml-namespaces
      test-system/fix-urls))
