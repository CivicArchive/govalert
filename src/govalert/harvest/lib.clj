(ns govalert.harvest.lib
    (:import (java.net HttpURLConnection))
    (:require [clj-http.client :as client]
              [net.cgrand.enlive-html :as enlive]
              [url-normalizer.core :as url-normalizer]
              [govalert.harvest.types :as types])
    (:import [java.util.concurrent TimeoutException]))

(defn resolve-redirect [initial-url]
  "Follows any redirects to the supplied url and returns the final destination"
  ; original from http://pseudofish.com/following-url-redirects-with-clojure.html
  (let [url (java.net.URL. initial-url)
        conn (.openConnection url)]
     (if (= HttpURLConnection/HTTP_OK (.getResponseCode conn))
       (.. conn getURL toString)
       initial-url)))

(defn url-str [url args]
  (let [has-query (not= (.indexOf url "?") -1)]
    (if args
      (str url (if has-query "&" "?") (client/generate-query-string args))
      url)))

(defn http-get [uri & {:as args}]
  (client/get uri (assoc args :socket-timeout 5000 :conn-timeout 5000)))

(defn get-html [url & args]
  {:pre [string? url]}
  (let [{headers :headers body :body}
        ;; ## use http-get instead!
        (client/get url (assoc args :socket-timeout 5000 :conn-timeout 5000 :as :stream))]        
    (enlive/html-resource body)))

(defmacro with-timeout
  ; https://coderwall.com/p/eli69g
  [msec & body]
  `(let [f# (future (do ~@body))
         v# (gensym)
         result# (deref f# ~msec v#)]
    (if (= v# result#)
      (do
        (future-cancel f#)
        ; (throw (java.util.concurrent/TimeoutException.))
        (throw (Exception.)))
      result#)))

(defn sec [n]
  (* n 1000))

(defn clean-text [s]
  (-> (clojure.string/trim s) 
      (clojure.string/trim-newline)
      (clojure.string/replace #"[\s]+" " ")))

(defn agenda-from-anchor [a base]
  (types/make-agenda
         :uri (if (:href (:attrs a))
                (str (url-normalizer/resolve base (:href (:attrs a)))))
         :title (enlive/text a)))

(defn attachment-from-anchor [a base]
  (types/make-attachment
         :uri (if (:href (:attrs a))
                (str (url-normalizer/resolve base (:href (:attrs a)))))
         :label (enlive/text a)))

(defn combine-links 
  "Combines adjacent links with the same url into one with joined label"
  [coll]
  (for [group (partition-by :url coll)]
    (assoc-in (first group) [:label]
       (clojure.string/join " " (map :label group)))))





