(ns lupapalvelu.conversion-test-util
  (:require [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.itest-util :refer [server-address]]
            [me.raynes.fs :as fs]
            [net.cgrand.enlive-html :as enlive]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(def testfile "dev-resources/krysp/99-0000-13-A.xml")

(defn mk-test-dir []
  (fs/temp-dir "test-conversion-"))

(defn rm-test-dir [dir]
  {:pre [(ss/starts-with (fs/base-name dir) "test-conversion-")]}
  (fs/delete-dir dir))

(defn tmp-edn-file [dir]
  (str dir "/" (fs/temp-name "cfg-" ".edn")))

(defn transform-xml
  "Takes an xml `source` (slurpable) and returns transformed xml string."
  [source & {:keys [backend-id application-id]}]
  (cond-> source
    application-id
    (conv/xml-with-application-id application-id)

    (not application-id)
    slurp

    true
    (ss/replace #"http://localhost:8000" (server-address))

    backend-id
    (-> (xml/parse-string "utf8")
        (enlive/at
          [:rakval:luvanTunnisteTiedot :yht:LupaTunnus :yht:kuntalupatunnus]
          (enlive/content backend-id))
        xml/element-to-string)))

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
  (str (fs/absolute f)))
