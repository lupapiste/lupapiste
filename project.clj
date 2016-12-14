(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.zip "0.1.1"] ; Note: 0.1.2 breaks lupapalvelu.wfs
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/tools.reader "0.10.0"]
                 [org.clojure/tools.trace "0.7.9"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/core.memoize "0.5.9"]

                 ; Web frameworks
                 [ring "1.5.0" :exclusions [commons-fileupload org.clojure/tools.reader]]
                 [noir "1.3.0" :exclusions [compojure clj-stacktrace org.clojure/tools.macro ring hiccup bultitude]]
                 [compojure "1.1.9" :exclusions [org.clojure/tools.macro]]
                 [metosin/ring-swagger "0.22.12"]
                 [metosin/ring-swagger-ui "2.2.5-0"]

                 ; Namespace finder library
                 [bultitude "0.2.8"] ; noir requires 0.2.0

                 ; MongoDB driver
                 [com.novemberain/monger "3.1.0"]

                 ; Logging
                 [com.taoensso/timbre "4.7.4"]
                 [org.slf4j/slf4j-log4j12 "1.7.21"]

                 ;;Hystrix
                 [com.netflix.hystrix/hystrix-clj "1.5.6"]
                 [com.netflix.hystrix/hystrix-metrics-event-stream "1.5.6"]

                 ; markup processing
                 [enlive "1.1.6"]
                 [endophile "0.1.2" :exclusions [hiccup]]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"]
                 [org.freemarker/freemarker "2.3.23"]

                 ; Encryption and secure hashing
                 [org.jasypt/jasypt "1.9.2"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [crypto-random "1.2.0" :exclusions [commons-codec]]
                 [org.bouncycastle/bcprov-jdk15on "1.55"]
                 [pandect "0.6.0"]

                 ; JSON
                 [cheshire "5.6.3"]

                 ; HTTP client
                 [clj-http "3.3.0" :exclusions [commons-codec]]

                 ; Email client
                 [com.draines/postal "1.11.4" :exclusions [commons-codec/commons-codec]]

                 ; iCalendar
                 [org.mnode.ical4j/ical4j "1.0.7" :exclusions [commons-logging]]

                 ; Apache Commons
                 [commons-fileupload "1.3.2"] ; explicit requirement to catch version upgrades
                 [org.apache.commons/commons-lang3 "3.5"]
                 [commons-io "2.5"]
                 [commons-codec "1.10"]

                 ; Joda time wrapper
                 [clj-time "0.12.0"]

                 ; String case manipulation
                 [camel-snake-kebab "0.4.0"]

                 ; Collection of arrow macros
                 [swiss-arrows "1.0.0"]

                 ; File system lib
                 [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]] ; later version required by pantomime -> tika

                 ; Enhanced try and throw
                 [slingshot "0.12.2"]

                 ; A Clojure(Script) library for declarative data description and validation
                 [prismatic/schema "1.1.3"]
                 [prismatic/schema-generators "0.1.0"]

                 ; MIME type resolution
                 [com.novemberain/pantomime "2.8.0" :exclusions [org.opengis/geoapi org.bouncycastle/bcprov-jdk15on]]

                 ; RSS
                 [clj-rss "0.2.3"]

                 ; Image processing
                 [com.github.jai-imageio/jai-imageio-core "1.3.1"]
                 [com.github.jai-imageio/jai-imageio-jpeg2000 "1.3.0"]
                 [com.google.zxing/javase "2.2"] ; QR codes
                 [com.twelvemonkeys.imageio/imageio-jpeg "3.2.1"]

                 ; MS Office document processing
                 [ontodev/excel "0.2.4" :exclusions [xml-apis org.apache.poi/poi-ooxml]]
                 [org.apache.poi/poi-ooxml "3.15"]
                 [dk.ative/docjure "1.11.0"] ; this also depends on Apache POI v3.14
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.converter.docx.xwpf  "1.0.6"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.itext.extension  "1.0.6" :exclusions [com.lowagie/itext]]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.document.docx  "1.0.6"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.template.freemarker "1.0.6" :exclusions [org.freemarker/freemarker]]

                 ; Apache pdfbox for PDF/A wrapper
                 [org.apache.pdfbox/pdfbox "2.0.3"]
                 [org.apache.pdfbox/xmpbox "2.0.3"]

                 ; JavaScript and CSS compression
                 [com.yahoo.platform.yui/yuicompressor "2.4.8" :exclusions [rhino/js org.mozilla/rhino]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523

                 ;; Geo location libs
                 ; WKT parser
                 [cljts "0.3.0-20150228.035522-2" :exclusions [xerces/xercesImpl]]
                 ; Coordinate conversions, shape file handling etc.
                 [org.geotools/gt-main "12.4"]
                 [org.geotools/gt-shapefile "12.4"]
                 [org.geotools/gt-geojson "12.4"]
                 [org.geotools/gt-referencing "12.4"]
                 [org.geotools/gt-epsg-wkt "12.4"]

                 ;; Lupapiste libraries
                 ; Oskari map (https://github.com/lupapiste/oskari)
                 [lupapiste/oskari "0.9.58"]
                 ; Shared domain code (https://github.com/lupapiste/commons)
                 [lupapiste/commons "0.7.62"]
                 ; Smoke test lib (https://github.com/lupapiste/mongocheck)
                 [lupapiste/mongocheck "0.1.3"]
                 ; iText fork with bug fixes and upgraded dependencies (https://github.com/lupapiste/OpenPDF)
                 [lupapiste/openpdf "1.0.6"]
                 ; Wrapper for clj-pdf for PDF/A document generation
                 [lupapiste/pdfa-generator "1.0.2" :exclusions [org.clojure/tools.reader xalan]]
                 ; JMX-server with socket reuse
                 [lupapiste/jmx-server "0.1.0"]]
  :profiles {:dev {:dependencies [[midje "1.8.3" :exclusions [org.clojure/tools.namespace]]
                                  [ring/ring-mock "0.3.0" :exclusions [ring/ring-codec]]
                                  [com.raspasov/clj-ssh "0.5.12"]
                                  [rhizome "0.2.7"]
                                  [pdfboxing "0.1.10"]]
                   :plugins [[lein-midje "3.2"]
                             [jonase/eastwood "0.2.3" :exclusions [org.clojure/tools.namespace org.clojure/clojure]]
                             [lupapiste/lein-buildid "0.4.2"]
                             [lupapiste/lein-nitpicker "0.5.1"]]
                   :resource-paths ["dev-resources"]
                   :source-paths ["dev-src" "test-utils"]
                   :jvm-opts ["-Djava.awt.headless=true" "-Xmx2G" "-Dfile.encoding=UTF-8"]
                   :eastwood {:continue-on-exception true
                              :source-paths ["src"]
                              :test-paths []}}
             :uberjar  {:main lupapalvelu.main}
             :itest    {:test-paths ^:replace ["itest"]}
             :stest    {:test-paths ^:replace ["stest"]}

             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts ["-Djava.awt.headless=true" "-Xmx1G"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=https://www-dev.lupapiste.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=https://www-test.lupapiste.fi" "-Djava.awt.headless=true"]}}
  :java-source-paths ["java-src"]
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"terms\.html" #"\/email-templates\/" #"proj4" #".debug"]}
  :repositories [["mygrid-repository" {:url "http://www.mygrid.org.uk/maven/repository"
                                       :snapshots false}]
                 ["osgeo" {:url "http://download.osgeo.org/webdav/geotools"}]
                 ["com.levigo.jbig2" {:url "http://jbig2-imageio.googlecode.com/svn/maven-repository"
                                      :snapshots false}]]
  :aliases {"integration" ["with-profile" "dev,itest" ["midje" ":filter" "-ajanvaraus"]]
            "ajanvaraus"  ["with-profile" "dev,itest" ["midje" ":filter" "ajanvaraus"]]
            "stest"       ["with-profile" "dev,stest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," ["midje" ":filter" "-ajanvaraus"]]}
  :aot [lupapalvelu.main clj-time.core]
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :pom-plugins [[org.fusesource.mvnplugins/maven-graph-plugin "1.4"]
                [com.googlecode.maven-overview-plugin/maven-overview-plugin "1.6"]]
  :min-lein-version "2.0.0")
