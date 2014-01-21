(ns govalert.notify.mailformat
  (:require [net.cgrand.enlive-html :as html]))

(defn uri-coll [item]
  (if (coll? (:uri item)) (:uri item) (list (:uri item))))

(def email-template (.toURI (java.io.File. "resources/email-template.html")))

(defn format-email [result & [template-source]]
  (html/emit*
   (html/at (html/html-resource (or template-source email-template))
     [:.govalert-search-query] 
      (html/substitute (:q result))
     [:.govalert-city-name]
      (html/substitute (:t result))
     [:.govalert-body-name]
      (html/substitute (:t result))
     [:.govalert-agendas]
      (if-not (:items result)
         identity
        (html/clone-for [item (:items result)]
          [:.govalert-agenda-title]
           (if-not (empty? (:title item))
                   (html/append (:title item)))
          [:.govalert-agenda-title] html/unwrap
          [:.govalert-agenda-date]
           (if-not (empty? (:date item))
                   (html/append (:date item)))
          [:.govalert-agenda-date] html/unwrap
          [:.govalert-agenda-link] 
           (html/clone-for [uri (uri-coll item)] 
                           (html/set-attr :href uri :class nil))
          [:.govalert-agenda-summary]
           (if-not (empty? (:summary item))
                   (html/append
                    (:summary item)))
          [:.govalert-agenda-summary] html/unwrap
          [:.govalert-documents]
           (html/clone-for [item (:docs item)]
              [:.govalert-doc-number]
               (if-not (empty? (:label item))
                       (html/append (:label item)))
              [:.govalert-doc-number] html/unwrap
              [:.govalert-doc-date]
               (if-not (empty? (:date item))
                       (html/append (:date item)))
              [:.govalert-doc-date] html/unwrap
              [:.govalert-doc-title]
                (html/append (or (if-not (empty? (:title item)) (:title item))
                                 "Document"))
              [:.govalert-doc-title] html/unwrap
              [:.govalert-doc-link] 
               (html/clone-for [uri (uri-coll item)] 
                               (html/set-attr :href uri :class nil))
              [:.govalert-doc-summary]
               (if-not (empty? (:summary item))
                       (html/append
                        (:summary item)))
              [:.govalert-doc-summary] html/unwrap)
           [:.govalert-doc-link]
            (html/clone-for [uri (uri-coll item)] 
              (html/set-attr :href uri :class nil)))))))