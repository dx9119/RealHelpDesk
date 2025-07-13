#!/bin/bash

# Проверка наличия pom.xml для уверенности
if [ ! -f "pom.xml" ]; then
  echo "Файл 'pom.xml' не найден. Это не корень Maven-проекта."
  return 1
fi

# Получение свежих изменений
echo "Получение свежих изменений из репозитория..."
git pull || { echo "Ошибка при получении обновлений."; return 1; }

# Проверка, нужно ли выполнять mvn clean
if [ -d "target" ]; then
  echo "Папка 'target' обнаружена. Выполняется 'mvn clean'..."
  mvn clean || { echo "Ошибка при очистке проекта."; return 1; }
fi

# Сборка проекта
echo "Компиляция проекта..."
mvn compile || { echo "Ошибка при компиляции проекта."; return 1; }

echo "Упаковка проекта..."
mvn package || { echo "Ошибка при упаковке проекта."; return 1; }

echo "Проект успешно упакован."

cd target || { echo "Не удалось перейти в папку target."; return 1; }

source ../help-utils/setup-env-vars.sh || { echo "Ошибка при загрузке переменных окружения."; return 1; }

source ../help-utils/gen-selfsigned-keystore.sh || { echo "Ошибка при генерации Keystore."; return 1; }

echo "Рабочая директория JVM: $(pwd)"

JAR_FILE=$(ls realhelpdesk-*.jar | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "JAR-файл 'realhelpdesk-*.jar' не найден в каталоге $(pwd)"
  return 1
fi

echo "Запускается JAR: $JAR_FILE"
java -jar "$JAR_FILE" || { echo "Ошибка при запуске приложения."; return 1; }
echo "========================"
echo "========================"
echo "========================"

