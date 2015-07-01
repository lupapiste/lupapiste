(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.clojure/tools.trace "0.7.8"]
                 [commons-fileupload "1.3.1"] ; The latest version - ring requires 1.3
                 [ring "1.3.1"]
                 [noir "1.3.0" :exclusions [compojure clj-stacktrace org.clojure/tools.macro ring hiccup bultitude]]
                 [bultitude "0.2.6"] ; noir requires 0.2.0, midje 1.6 requires 0.2.2
                 [compojure "1.1.9" :exclusions [org.clojure/tools.macro]]
                 [com.novemberain/monger "1.7.0"]
                 [com.taoensso/timbre "2.7.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [enlive "1.1.5"]
                 [org.jasypt/jasypt "1.9.2"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [crypto-random "1.2.0" :exclusions [commons-codec]]
                 [clj-http "0.7.8" :exclusions [commons-codec]]
                 [camel-snake-kebab "0.1.2"]
                 [org.bouncycastle/bcprov-jdk15on "1.46"]
                 [pandect "0.3.0" :exclusions [org.bouncycastle/bcprov-jdk15on]]
                 [clj-time "0.8.0"]
                 [org.apache.commons/commons-lang3 "3.3.2"] ; Already a dependency but required explicitly
                 [commons-io/commons-io "2.4"]
                 [commons-codec/commons-codec "1.9"]
                 [com.lowagie/itext "4.2.1" :exclusions [org.bouncycastle/bctsp-jdk14]]
                 [net.java.dev.jai-imageio/jai-imageio-core-standalone "1.2-pre-dr-b04-2014-09-13"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [endophile "0.1.2" :exclusions [hiccup]]
                 [com.draines/postal "1.11.1" :exclusions [commons-codec/commons-codec]]
                 [swiss-arrows "1.0.0"]
                 [me.raynes/fs "1.4.6"]
                 [ontodev/excel "0.2.3" :exclusions [xml-apis org.apache.poi/poi-ooxml]]
                 [org.apache.poi/poi-ooxml "3.11"]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"]
                 [com.yahoo.platform.yui/yuicompressor "2.4.8" :exclusions [rhino/js]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523
                 [fi.sito/oskari "0.9.44"]
                 [slingshot "0.10.3"]
                 [com.google.zxing/javase "2.2"]
                 [org.geotoolkit/geotk-referencing "3.20-geoapi-3.0"]
                 [prismatic/schema "0.2.4"]
                 [cljts "0.2.0" :exclusions [xerces/xercesImpl]]
                 ; batik-js includes a built-in rhino, which breaks yuicompressor (it too has rhino built in)
                 ; xalan excluded just to avoid bloat, presumably XSLT is not needed
                 [clj-pdf "1.11.21" :exclusions [xalan org.apache.xmlgraphics/batik-js]]
                 [org.freemarker/freemarker  "2.3.22"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.converter.docx.xwpf  "1.0.5"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.itext.extension  "1.0.5"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.document.docx  "1.0.5"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.template.freemarker "1.0.5" :exclusions [org.freemarker/freemarker]]
                 [scss-compiler "0.1.2"]
                 [org.clojure/core.memoize "0.5.7"]
                 [org.apache.pdfbox/pdfbox "1.8.9"]
                 [com.levigo.jbig2/levigo-jbig2-imageio "1.6.3"]
                 [lupapiste/commons "0.5.0"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [ring-mock "0.1.5"]
                                  [clj-ssh "0.5.7"]
                                  [pdfboxing "0.1.5"]]
                   :plugins [[lein-midje "3.1.1"]
                             [lein-buildid "0.2.0"]
                             [lein-nitpicker "0.4.0"]
                             [lein-hgnotes "0.2.0-SNAPSHOT"]
                             [lein-scss-compiler "0.1.4"]]
                   :resource-paths ["dev-resources"]
                   :source-paths ["dev-src" "test-utils"]
                   :jvm-opts ["-Djava.awt.headless=true" "-Xmx2G"]}
             :uberjar  {:main lupapalvelu.main
                        :jar-exclusions [#"gems/.*"]
                        :uberjar-exclusions [#"gems/.*"]}
             :itest    {:test-paths ^:replace ["itest"]}
             :stest    {:test-paths ^:replace ["stest"]}
             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts ["-Djava.awt.headless=true" "-Xmx1G"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=https://www-dev.lupapiste.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=https://www-test.lupapiste.fi" "-Djava.awt.headless=true"]}}
  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"hgnotes" #"terms\.html" #"\/email-templates\/"]}
  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                    :checksum :ignore}]
                 ["mygrid-repository" {:url "http://www.mygrid.org.uk/maven/repository"
                                       :snapshots false}]
                 ["com.levigo.jbig2" {:url "http://jbig2-imageio.googlecode.com/svn/maven-repository"
                      :snapshots false}]]
  :plugin-repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                           :checksum :ignore}]]
  :aliases {"integration" ["with-profile" "dev,itest" "midje"]
            "stest"       ["with-profile" "dev,stest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," "midje"]}
  :aot [lupapalvelu.main]
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :pom-plugins [[org.fusesource.mvnplugins/maven-graph-plugin "1.4"]
                [com.googlecode.maven-overview-plugin/maven-overview-plugin "1.6"]]
  :min-lein-version "2.0.0")
