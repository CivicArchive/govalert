(ns govalert.harvest.html
  (:require [net.cgrand.enlive-html :as enlive]
            [url-normalizer.core :as url-normalizer]
            [clj-http.client :as client]
            [govalert.harvest.types :as types]))

(def rex-datestamp #"(\d{1,2})\/(\d{1,2})\/(\d{2,4})")

(defn html-agendas [agendas]
  (let [url (str (:url agendas) (if (:args agendas) "?") (client/generate-query-string (:args agendas)))]
    (for [element (enlive/select (enlive/html-resource (java.net.URL. url)) 
                               (map keyword (:selector agendas)))]
      (let [label (clojure.string/trim (enlive/text element))
            base (:url agendas)
            url (get-in element [:attrs :href])]
        (if url
          (types/make-agenda
            :uri (url-normalizer/resolve base url)
            :title label
            :date (first (re-find rex-datestamp label))))))))

(defn extract-links [url selector]
  (let [base url]
    (for [element (enlive/select (enlive/html-resource (java.net.URL. url)) (or selector [:a]))]
      (if-let [url (get-in element [:attrs :href])]
        {:uri (str (url-normalizer/resolve base url))
         :label (clojure.string/trim (enlive/text element))}))))

(defn parse-html [in]
  (-> (slurp in)
      (enlive/html-snippet)))

(defn read-text 
  "Extract the text from an html document read from the input stream"
  [in]
  {:post [string?]}
  (->> (parse-html in)
       (map enlive/text)
       (apply str)))




