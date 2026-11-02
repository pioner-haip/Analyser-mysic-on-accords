#!/bin/sh
# Сборка: компилирует исходники в папку out/
set -e
mkdir -p out
javac -encoding UTF-8 -d out src/chordfinder/*.java
echo "Готово: out/"
