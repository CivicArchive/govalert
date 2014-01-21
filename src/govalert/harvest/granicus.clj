(ns govalert.harvest.granicus
    ; Interface to granicus content
  (:require [clj-http.client :as client] 
            [feedparser-clj.core :as rss]
            [net.cgrand.enlive-html :as enlive]
            [clj-time.format :as time-format]
            [clj-time.core :as time]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [govalert.harvest.types :as types]
            [govalert.harvest.lib :as lib]
            [url-normalizer.core :as url-normalizer]))

(defn rss-feed [url args]
  (rss/parse-feed (lib/url-str url args)))

(defn agenda-items [url args] 
  (->> (rss-feed url args)
       (:entries)
       (map #(types/make-agenda :title (:title %) :date (:published-date %) :summary (:description %) :uri (:link %)))))

(defmethod types/agendas "granicus"  [agendas]
  (agenda-items (:url agendas) (:args agendas)))

