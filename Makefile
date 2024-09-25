PORT := 8000

all:
	clojure -T:build uber

.PHONY:	dev
dev:
	clojure -M:dev/reloaded:test:shadow-watch

run:	target/littlereader-standalone.jar
	java -jar $^ $(PORT)
