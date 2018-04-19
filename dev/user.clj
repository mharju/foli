(ns user
  (:require [reloaded.repl :refer [system reset stop]]
            [foli.system :as system]
            [figwheel-sidecar.repl-api :as fw]))

(reloaded.repl/set-init! #'system/create-system)
#_(fw/start-figwheel!)
