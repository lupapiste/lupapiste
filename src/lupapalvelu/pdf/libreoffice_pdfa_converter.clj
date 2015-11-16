(ns lupapalvelu.pdf.libreoffice-pdfa-converter
  (:require [clojure.java.shell :as shell]
            [hiccup.core :as hiccup]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [sade.strings :as ss])
  (:import (java.io File)))

; See commanline parameters: https://help.libreoffice.org/Common/Starting_the_Software_With_Parameters
(defn- libre-office-service [action host port]
  {:pre [(or (= :start action) (= :stop action))]}
  (let [a (if (= action :stop) "--unaccept" "--accept")
        h (if (empty? host) "127.0.0.1" host)
        p (if (empty? port) "2220" port)]
    (shell/sh "libreoffice" "--headless" (str a "=\"socket,host=" h ",port=" port ",tcpNoDelay=1;urp") "--nodefault" "--nofirststartwizard" "--nolockcheck" "--nologo" "--norestore" "--invisible" "&")))

(defn start-libre-office-service [& [port host]]
  "Command to start LibreOffice as service that accepts RPC. NB: not needed unless we are going to run the Libre as service"
  (libre-office-service :start host port))

(defn stop-libre-office-service [& [port host]]
  "Command to stop LibreOffice as service that accepts RPC. NB: not needed unless we are going to run the Libre as service"
  (libre-office-service :stop host port))


(defn libre-command [src dst-dir & connection] (shell/sh "libreoffice" "--headless" "--convert-to" "pdf" "--outdir" dst-dir src))

(defn unoconv-command [type src dst-file & connection]
  {:pre [(or (= :document type) (= :graphics type) (= :presentation type) (= :spreadsheet type))]}
  (shell/sh "/usr/bin/unoconv" "-d" (name type) "-f" "pdf" "-eSelectPdfVersion=1" "-o" dst-file src))


(defn- get-operations [{:keys [primaryOperation secondaryOperations]}]
  (ss/join ", " (map (fn [[op c]] (str (if (> c 1) (str c " \u00D7 ")) op))
                     (frequencies (map :name (remove nil? (conj (seq secondaryOperations) primaryOperation)))))))

(def td-style {:style "border: 1.2px solid black; " :align "left" })
(def p-style {:style "margin-left: 10px;"})

(defn- render [title app data]
  (hiccup/html
    [:html {:lang "fi"}
     [:head [:title "Lupapiste.fi"]]
     [:body
      [:div {:style "margin: 40px;"}]
      [:img {:src (.getAbsolutePath (File. (.toURI (io/resource "public/img/logo-v2-flat.png"))))}]
      [:h1 title]
      [:h2 (:title app)]
      [:hr]
      [:table {:width "100%"}
       [:tbody
        [:tr [:td td-style [:h4 "Asiointikunta"] [:p p-style (:municipality app)]] [:td td-style [:h4 "Hakemuksen vaihe"] [:p p-style (:state app)]]]
        [:tr [:td td-style [:h4 "Kiinteist&ouml;tunnus"] [:p p-style (:propertyId app)]] [:td td-style [:h4 "Hakemus j&aumltetty"] [:p p-style (:created app)]]]
        [:tr [:td td-style [:h4 "Asiointitunnus"] [:p p-style (:id app)]] [:td td-style [:h4 "K&auml;sitteluj&auml;"] [:p p-style (str (get-in app [:authority :lastName]) (get-in app [:authority :firstName]))]]]
        [:tr [:td td-style [:h4 "Hankkeen osoite"] [:p p-style (:address app)]] [:td td-style [:h4 "Hakija"] [:p p-style (clojure.string/join ", " (:_applicantIndex app))]]]
        [:tr [:td (assoc td-style :colspan "2") [:h4 "Toimenpiteet"] [:p p-style (get-operations app)]]]]]]
     [:br]
     [:table {:width "100%"}
      data]]))

(defn- render-statement [stm]
  [:tbody
   [:tr [:th {:colspan "2" :style "font-size: 2em; background-color: lightgrey; padding: 1em; border: 1.2px solid black;"} [:h4 {:style "color: #000000;"} "Lausunto"]]]
   [:tr [:td td-style [:h4 "Lausunnon pyyntop&auml;iv&auml;"] [:p p-style (:requested stm)]] [:td td-style [:h4 "Lausunnon antaja"] [:p p-style (get-in stm [:person :name])]]]
   [:tr [:td td-style [:h4 "Lausunnon antop&auml;iv&auml;"] [:p p-style (:given stm)]] [:td td-style [:h4 "Puoltotieto"] [:p p-style (:status stm)]]]
   [:tr [:td (assoc td-style :colspan "2") [:h4 "Lausuntoteksti"] [:p p-style (:text stm)]]]])

(defn unoconv-render-pdf [src-file dst-file]
  "Converts any statement readable by LibreOffice to PDF/A1A using unoconv command
   Requires:
     1) Libre Office core 4.2 which defaults to PDF/A1A
     2) Libre Office headless
     3) Libre Office Writer
     4) unoconv commandline tool"
  (debug " Redering PDF via " (unoconv-command :document (.getAbsolutePath src-file) (.getAbsolutePath dst-file))))

(defn libre-render-pdf [src-filename dst-dirname]
  "Converts any statement readable by LibreOffice to PDF/A1A calling libreoffice directly
   Requires:
     1) Libre Office core 4.2 which defaults to PDF/A1A
     2) Libre Office headless
     3) Libre Office Writer"
  (debug " Redering PDF via " (libre-command :document src-filename dst-dirname)))


(defn render-statement-pdf [app stm]
  "Converts any statement readable by LibreOffice to PDF/A1A
   Requires:
     1) Libre Office core 4.2 which defaults to PDF/A1A
     2) Libre Office headless
     3) Libre Office Writer
     4) unoconv commandline tool"
  (let [src (File/createTempFile "test-libre-html-" ".html")
        dst (File/createTempFile "test-libre-html-" ".pdf")]
    (debug " Rendering statement HTML to temp file: " (.getAbsolutePath src))
    (io/copy (render "Lausunto" app (render-statement stm)) src)
    (unoconv-render-pdf src dst)
    (.delete src)
    dst))