clean:
	clj -T:build clean

install:
	clj -T:build install

jar:
	clj -T:build jar

deploy: jar
	clj -T:build deploy

repl:
	clj -M:dev

tests:
	clj -M:test "$@"

watch:
	clj -M:test watch

