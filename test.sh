#!/bin/sh
# Сборка вместе с тестами и запуск проверок анализатора и листа
set -e
cd "$(dirname "$0")"
mkdir -p out-test
javac -encoding UTF-8 -d out-test src/chordfinder/*.java test/chordfinder/*.java
J="java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp out-test"
$J chordfinder.DetectorSelfTest
$J chordfinder.SheetBuilderSelfTest | grep -E "OK|FAIL"
