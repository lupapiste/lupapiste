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

(defn- render [title app data]
  (hiccup/html
    [:html {:lang "fi"}
     [:head [:title "Lupapiste.fi"]]
     [:body
      [:img {:src (.getAbsolutePath (File. (.toURI (io/resource "public/img/logo-v2-flat.png"))))}]
      [:h1 title]
      [:h2 (:title app)]
      [:hr]
      [:div {:style "width: 400px;"}
       [:table {:width "100%", :border "1"}
        [:tbody
         [:tr [:td [:h4 "Asiointikunta"] (:municipality app)] [:td [:h4 "Hakemuksen vaihe"] (:state app)]]
         [:tr [:td [:h4 "Kiinteist&ouml;tunnus"] (:propertyId app)] [:td [:h4 "Hakemus j&aumltetty"] (:created app)]]
         [:tr [:td [:h4 "Asiointitunnus"] (:id app)] [:td [:h4 "K&auml;sitteluj&auml;"] (str (get-in app [:authority :lastName]) (get-in app [:authority :firstName]))]]
         [:tr [:td [:h4 "Hankkeen osoite"] (:address app)] [:td [:h4 "Hakija"] (clojure.string/join ", " (:_applicantIndex app))]]
         [:tr [:td {:colspan "2"} [:h4 "Toimenpiteet"] (get-operations app)]]]]]
      [:div {:style "width: 400px;"}
       [:table {:width "100%", :border "1"}
        data]]]]))

(defn- render-statement [stm]
  [:tbody
   [:tr [:th {:colspan "2" :align "left" :style "font-size: 2em; background-color: lightgrey; padding: 1em;"} [:h4 {:style "color: #000000;"} "Lausunto"]]]
   [:tr [:td [:h4 "Lausunnon pyyntop&auml;iv&auml;"] (:requested stm)] [:td [:h4 "Lausunnon antaja"] (get-in stm [:person :name])]]
   [:tr [:td [:h4 "Lausunnon antop&auml;iv&auml;"] (:given stm)] [:td [:h4 "Puoltotieto"] (:status stm)]]
   [:tr [:td {:colspan "2"} [:h4 "Lausuntoteksti"] (:text stm)]]])

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