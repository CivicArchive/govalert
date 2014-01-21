(defproject govalert "0.8.0.2"
  :description "Build an index of agendas and their attachments to broadcast alerts of new additions matching search queries"
  :url "http://govalert.me"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.6"] 

                 [compojure "1.1.1"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [ring/ring-devel "1.1.0"]
                 [ring-basic-authentication "1.0.1"]
                 [environ "0.2.1"]

                 [clj-http "0.7.3"]
                 [enlive "1.1.1"]
                 [clj-time "0.6.0"]
                 [clojurewerkz/elastisch "1.2.0"]
                 [org.clojars.ogrim/feedparser-clj "0.4.0"]
                 [url-normalizer "0.5.3-1"]
                 [com.draines/postal "1.10.3"]
                 [digest "1.3.0"]

                 [org.apache.pdfbox/pdfbox "1.7.1"]
                 [org.apache.poi/poi-ooxml "3.10-beta1"]
                 [org.apache.poi/poi-scratchpad "3.10-beta1"]]

  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]
            [lein-swank "1.4.5"]]
  :hooks [environ.leiningen.hooks]
  :profiles {:production {:env {:production true}}})
