(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.clojure/tools.trace "0.7.8"]
                 [ring "1.2.1"]
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
                 [org.bouncycastle/bcpkix-jdk15on "1.51"]
                 [pandect "0.3.4" :exclusions [org.bouncycastle/bcprov-jdk15on]]
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
                 [ontodev/excel "0.2.3" :exclusions [xml-apis]]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"]
                 [com.yahoo.platform.yui/yuicompressor "2.4.7" :exclusions [rhino/js]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523
                 [fi.sito/oskari "0.9.33"]
                 [slingshot "0.10.3"]
                 [com.google.zxing/javase "2.2"]
                 [prismatic/schema "0.2.4"]
                 [cljts "0.2.0" :exclusions [xerces/xercesImpl]]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [ring-mock "0.1.5"]
                                  [clj-ssh "0.5.7"]]
                   :plugins [[lein-midje "3.1.1"]
                             [lein-buildid "0.2.0"]
                             [lein-nitpicker "0.4.0"]
                             [lein-hgnotes "0.2.0-SNAPSHOT"]]
                   :resource-paths ["dev-resources"]
                   :source-paths ["dev-src" "test-utils"]
                   :jvm-opts ["-Djava.awt.headless=true"
                              "-Xmx1G" "-XX:MaxPermSize=256M"]}
             :uberjar  {:source-paths ["main-src"]
                        :main lupapalvelu.main}
             :itest    {:test-paths ^:replace ["itest"]}
             :stest    {:test-paths ^:replace ["stest"]}
             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts ["-Xmx1G" "-XX:MaxPermSize=256M"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=https://www-dev.lupapiste.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=https://www-test.lupapiste.fi" "-Djava.awt.headless=true"]}}
  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"hgnotes" #"terms\.html" #"\/email-templates\/"]}
  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                    :checksum :ignore}]
                 ["mygrid-repository" {:url "http://www.mygrid.org.uk/maven/repository"
                                       :snapshots false}]]
  :plugin-repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                           :checksum :ignore}]]
  :aliases {"integration" ["with-profile" "dev,itest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," "midje"]}
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :pom-plugins [[org.fusesource.mvnplugins/maven-graph-plugin "1.4"]
                [com.googlecode.maven-overview-plugin/maven-overview-plugin "1.6"]]
  :min-lein-version "2.0.0")
