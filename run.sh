#!/bin/sh
# Запуск графической версии (можно передать файл: ./run.sh song.wav)
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
exec java -cp out chordfinder.MainGui "$@"
