# SwordChips — Paper 1.21.11

PvP-zwaard-upgrades met maximaal vier verschillende computerchips per Cyber Sword.

## Vereisten
- Paper 1.21.11
- Java 21
- Maven 3.9+

## Bouwen
Open een terminal in deze map en voer uit:
```bash
mvn clean package
```
De plugin-JAR staat daarna in `target/SwordChips-1.0.0.jar`. Zet deze in `plugins/` en herstart Paper.

## Commando's
- `/swordchips give [speler]` — geeft een Cyber Sword
- `/swordchips chip <type> [speler]` — geeft een chip
- `/swordchips reload` — herlaadt config.yml

Chiptypes: `fire`, `frost`, `lightning`, `lifesteal`, `resistance`, `sharpness`, `swiftness`, `poison`, `impact`, `critical`.

## Gebruik
1. Houd de Cyber Sword in je hoofdhand.
2. Rechtermuisklik om het chip-menu te openen.
3. Pak een chip op je cursor en klik op een lege slot.
4. Klik op een gevulde slot om die chip terug te krijgen.

Een zwaard kan maximaal vier verschillende chips bevatten. Dezelfde chip kan niet dubbel worden geplaatst.

## Computerchip-textures (resourcepack)
De map `resourcepack/` bevat item definitions en pixel-art PNG's voor de chips. Zip de **inhoud** van die map (pack.mcmeta moet in de root van de zip staan) en stel die zip in als server resource pack. De plugin zet op chip-items het item-model `swordchips:chip_<type>`.

## Balans
Kansen, cooldowns en waarden staan in `plugins/SwordChips/config.yml`. De standaardwaarden zijn bewust gematigd voor PvP. Test de waarden op een testserver voordat je ze op een live server gebruikt.

## Opmerking
Dit is een broncodeproject; de JAR wordt lokaal gebouwd met Maven tegen de Paper 1.21.11 API.


## Automatisch bouwen zonder Maven op je pc (GitHub Actions)
1. Maak een repository aan op GitHub.
2. Upload de volledige inhoud van deze projectmap naar de repository (inclusief de verborgen `.github`-map).
3. Open in GitHub het tabblad **Actions**.
4. Kies **Build SwordChips** en klik op **Run workflow** (of wacht tot de push-build klaar is).
5. Open de geslaagde workflow-run en download onder **Artifacts** het bestand `SwordChips-Paper-1.21.11`.
6. Pak de artifact-ZIP uit. Daarin staat `SwordChips-1.0.0.jar`, die je bij FalixNodes in `plugins/` uploadt.

GitHub bouwt de plugin in de cloud met Java 21 en Maven; lokaal installeren is niet nodig.
