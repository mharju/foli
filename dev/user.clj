(ns user
  (:require [reloaded.repl :refer [system reset stop]]
            [foli.system :as system]))

(reloaded.repl/set-init! #'system/create-system)
