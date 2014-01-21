(ns govalert.harvest.pdf
  (:require [govalert.harvest.lib :as lib])
  (:import [org.apache.pdfbox.pdmodel PDDocument]
           [org.apache.pdfbox.util PDFTextStripper]))

(defn pdf-output-text [src wr]
  (with-open [ pd (PDDocument/load src)]
    (let [ stripper (PDFTextStripper.)]
      (.writeText stripper pd wr))))

(defn parse-pdf [in] 
  (with-out-str
     (pdf-output-text in *out*)))

(defn page-annotations [page]
   (let [I (.getAnnotations page)
         it (.iterator I)
         rotation (.findRotation page)
         pageheight (if (= rotation 0) (.getHeight (.findMediaBox page)))]
     (doall
       (for [[ix annotation] (map-indexed vector (iterator-seq it))]
                  (if (instance? org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink annotation)
                    (let [pda (.getAction annotation)]
                      (cond
                        (instance? org.apache.pdfbox.pdmodel.interactive.action.type.PDActionURI pda)
                        (let [rect (.getRectangle annotation)
                              [x y width height][(.getLowerLeftX rect)(.getUpperRightY rect)(.getWidth rect)(.getHeight rect)]]
                           (let [uri (.getURI pda)]
                               {:uri uri :region [x y width height] })))))))))

(defn text-for-regions [items page]
  (let [stripper (org.apache.pdfbox.util.PDFTextStripperByArea.)
        rotation (.findRotation page)
        pageheight (if (= rotation 0) (.getHeight (.findMediaBox page)))]
     (doseq [[ix region] (map-indexed vector items)]
                    (if region
                     (let [[x y width height] region
                           awtRect (java.awt.geom.Rectangle2D$Float. (float x) (float (if pageheight (- pageheight y) y)) (float width) (float height))]
                       (.addRegion stripper (str "link_" ix) awtRect))))
      (.extractRegions stripper page)
      (for [name (iterator-seq (.iterator (.getRegions stripper)))]
         (try
           (.getTextForRegion stripper name)
           (catch java.io.IOException e nil)))))


; (combine-links [{:url "a" :label "text1"}{:url "a" :label "text2"}{:url "b" :label "text3"}])

(defn- as-links [annotations page]
  (->
    (map (fn [link text] (assoc (dissoc link :region) :label text))
         annotations
         (text-for-regions (map :region annotations) page))
    (lib/combine-links)))

(defn extract-links [src]
   ;; inspired by http://crystalreportsconsulting.blogspot.com/2011/11/java-code-to-read-hyperlinks-from-pdf.html
   ;; based on side effects on stripper, hence doall's matters!
  (with-open [ pd (PDDocument/load src)]
    (->
     (for [page (iterator-seq (.iterator (.getAllPages (.getDocumentCatalog pd))))]
       (-> (page-annotations page)
           (as-links page)))
     (doall)
     (flatten))))

; ## remember slime fails on unicode...
; (map count (extract-links src))
; (doseq [item (extract-links src)] (println item))

; (extract-links (java.net.URL. "http://docs.sandiego.gov/ccagenda_rules_ogir/Rules%206-22-11%20Corrected Agenda.pdf"))
; (extract-links (java.net.URL. "http://docs.sandiego.gov/ccagenda_rules_ogir/Rules 6-22-11 Corrected Agenda.pdf"))


;; Apache Tika is a content analysis toolkit that detects and extracts metadata and structured text content from various documents:
;; http://fossies.org/dox/apache-tika-1.3-src
;; https://github.com/alexott/clj-tika/blob/master/src/tika.clj

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;





