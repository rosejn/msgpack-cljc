clean:
	clj -T:build clean

install:
	clj -T:build install

pom.xml:
	clj -Spom

jar:
	clj -T:build jar

deploy: pom.xml jar
	clj -T:build deploy

repl:
	clj -M:dev

tests:
	clj -M:test "$@"

watch:
	clj -M:test watch

