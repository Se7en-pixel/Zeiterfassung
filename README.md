# Zeiterfassung

Eine einfache Android-App, um Arbeitszeiten zu erfassen: Kommen, Gehen, Pause.

## Was die App kann

- **Kommen / Gehen** mit einem Tipp. Beginn und Ende lassen sich nachträglich korrigieren (z. B. 9:05 auf 9:00).
- **Pause** als Dauer in Minuten (z. B. 9:00 bis 18:00, davon 61 Minuten Pause).
- **Tage direkt nachtragen**, auch als Urlaub, Krank, Feiertag oder Überstundenabbau.
- **Überstunden pro Kalenderwoche** und insgesamt. Tage ohne Eintrag werden als „fehlt“ markiert.
- **Arbeitszeiten pro Wochentag** frei einstellbar (z. B. Mo–Do 9–18 Uhr, Fr 9–12 Uhr). Daraus ergibt sich die Sollzeit.
- **Erinnerungen** zum Arbeitsbeginn („Möchtest du einstempeln?“) und zum Feierabend („Möchtest du ausstempeln?“), mit Knopf direkt in der Benachrichtigung.
- **Widget** für den Startbildschirm mit Kommen/Gehen-Knopf und laufender Arbeitszeit, in der Größe verstellbar.
- **CSV-Export** zum Teilen oder Öffnen in Excel.

Alle Daten bleiben auf dem Handy.

## Installieren

1. Auf dem Android-Handy die neueste Version öffnen:
   https://github.com/Se7en-pixel/zeiterfassung/releases/latest/download/zeiterfassung.apk
2. Die Datei öffnen. Android fragt beim ersten Mal, ob der Browser Apps installieren darf: erlauben.
3. Neue Versionen werden genauso installiert: Android bietet dann „Aktualisieren“ an, die gespeicherten Zeiten bleiben erhalten.
   Die alte App **nicht** vorher deinstallieren, sonst sind die Daten weg.

## Technik

Kotlin mit Jetpack Compose. Bei jeder Änderung an `main` baut GitHub Actions automatisch eine neue APK und
veröffentlicht sie unter „Releases“.

Der Signierschlüssel (`app/zeiterfassung.keystore`) liegt absichtlich im Repository, damit jede Version über die
vorherige installiert werden kann. Für eine private App ist das in Ordnung; installiere die App aber nur über den
Link oben.
