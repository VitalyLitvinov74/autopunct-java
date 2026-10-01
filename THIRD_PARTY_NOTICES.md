# Библиотеки

Лицензия MIT относится к собственному коду проекта. Зависимости сохраняют свои лицензии и авторские права.

| Компонент | Назначение | Лицензия и исходники |
| --- | --- | --- |
| LanguageTool 6.8, модуль `language-ru` | Готовые русские правила | LGPL-2.1-or-later, https://github.com/languagetool-org/languagetool/tree/v6.8 |
| LibIME2, `717b1901a417667405399cfbf25b25664efcf0e4` | Основа TSF | LGPL-2.1, https://github.com/EasyIME/libIME2/tree/717b1901a417667405399cfbf25b25664efcf0e4 |
| Spring Boot 4.1.1 | Настройка приложения и внедрение зависимостей | Apache-2.0, https://github.com/spring-projects/spring-boot |
| PipelinR 0.11 | Диспетчеризация команд и запросов | MIT, https://github.com/sizovs/PipelinR |
| JNA 5.17.0 | Вызовы Windows API | Apache-2.0 / LGPL-2.1-or-later, https://github.com/java-native-access/jna |
| Jackson 2.22.1 | JSON | Apache-2.0, https://github.com/FasterXML/jackson |
| nlohmann/json 3.12.0 | JSON в C++ | MIT, https://github.com/nlohmann/json |
| Hibernate Validator | Проверка входных сообщений | Apache-2.0, https://github.com/hibernate/hibernate-validator |
| GoogleTest 1.15.2 | Проверки C++ | BSD-3-Clause, https://github.com/google/googletest |
| Eclipse Temurin 17 | Встроенная Java | GPL-2.0 with Classpath Exception, https://github.com/adoptium/temurin17-binaries |

Список транзитивных зависимостей фиксируется Maven. Их оригинальные лицензии и уведомления сохраняются в JAR-файлах.
Встроенная Java содержит каталог `legal`. Установщик дополнительно включает лицензию LibIME2 и nlohmann/json.

LibIME2 подключается из указанной ревизии через CMake. Исходники нашего модуля, CMake и инструкции сборки открыты:
модуль можно пересобрать и перелинковать с изменённой LibIME2. Код самой LibIME2 не изменён.
