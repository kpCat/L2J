# Encoding checks026

Две отдельные проверки выполнены по всем14 изменённым source/test файлам и всему публикуемому TASK026 package, включая raw captured diagnostics. Source/user-facing hand-written Cyrillic сохранена читаемой; Unicode/XML escaped Cyrillic не добавлялась.

Mojibake scan проверяет полный требуемый набор маркеров и replacement character. Единственный hit — evidence/R4-observer-census-build.log,68 replacement characters в исходном stderr неуспешного overwrite установленного observer026.jar. FileSystemException и native stack сохранены; исправление lock выполнено task-only hash-versioned agent JAR, не product hotfix. Этот raw log неизменяемый отрицательный evidence. Потерянные байты нельзя losslessly decode; guessed Russian message или подмена raw log не выполнялись. Product/test и вручную созданные документы не содержат совпадений. All-text scan имеет pass=false именно из-за raw diagnostics, не маскируется.

Escaped Cyrillic scan отдельно проверяет backslash-u04/u05 и XML numeric x04/x05 с обоими регистрами X. Совпадений нет. Проверки и scope counts сохранены отдельно в FINAL_MOJIBAKE_CHECK.json и FINAL_ESCAPED_CYRILLIC_CHECK.json. Literal regex в технической shell проверке не является пользовательской строкой в исходнике.
