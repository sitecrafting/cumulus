(ns com.sitecrafting.cumulus.cloudinary
  (:require
   [clojure.string :refer [join]]))


(defn- params->url-segments
  "Given a map like {:w 1000 :h 400}, returns a string like 'w_1000,h_400'.
   If no crop strategy is passed, defaults to c_crop.
   Honors any keys passed to it; it is up to the caller to make sure they are valid
   Cloudinary URL parameters. See Cloudinary's documentation about image
   transformations: https://cloudinary.com/documentation/image_transformations"
  [params]
  (let [;; Apply some sensible defaults.
        params (-> params
                   ;; Set a default crop strategy of "c_crop"
                   (assoc :c (:c params "crop")))]
    (join "," (filter seq (map (fn [[k v]]
                                 (when (and v (not= 0 v))
                                   (str (name k) "_" v)))
                               params)))))


;; TODO we may need version if we need to fall back to the full URL.
(defn params->url [{:keys [bucket folder filename transforms #_version]}]
  (let [segments (conj [bucket "image/upload"]
                       (join "/" (map params->url-segments transforms))
                       folder
                       filename)]
    (str "https://res.cloudinary.com/" (join "/" (filter seq segments)))))

(defn crop->url [params]
  (let [manual-crop (select-keys params [:x :y :w :h])
        [w h] (:target-size params)
        scale {:w w :h h :c "scale"}]
    (params->url (merge params {:transforms [manual-crop scale]}))))

(defn scale->url [params]
  (let [scaling-strategy (:c params "lfill")
        crop (merge (select-keys params [:w :h])
                    {:c scaling-strategy})]
    (params->url (merge params {:transforms [crop]}))))


(comment

  (params->url-segments {:w 150 :h 100 :c "scale"})
  (params->url-segments {:w 150 :h 100 :c "lfill"})

  (crop->url {:bucket "sean-dean"
              :folder "sc-test"
              :x 450
              :y 500
              :w 2000
              :h 1650
              :filename "heron.jpg"})
  ;; => "https://res.cloudinary.com/sean-dean/image/upload/x_450,y_500,w_2000,h_1650,c_crop/sc-test/heron.jpg"

  (scale->url {:bucket "sean-dean"
               :folder "sc-test"
               :w 2000
               :h 1650
               :c "lfill"
               :filename "heron.jpg"})
  ;; => "https://res.cloudinary.com/sean-dean/image/upload/w_2000,h_1650,c_lfill/sc-test/heron.jpg"

  (scale->url {:bucket "sean-dean"
               :folder "sc-test"
               :w 2000
               :h 1650
               :c "fit"
               :filename "heron.jpg"})
  ;; => "https://res.cloudinary.com/sean-dean/image/upload/w_2000,h_1650,c_fit/sc-test/heron.jpg"

  ;;
  )