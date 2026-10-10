# GemInfusion

> Infused gemstones and crafted jewellery for TF-Minecraft.

GemInfusion gives gemstones rolled bonuses and turns them into a resource for equipment customisation and goldsmithing. Players infuse gems at a station, discover their rarity and strength, then use them in compatible sockets or as the centrepiece of crafted jewellery.

## Features

- **Gem infusion** — process batches of gemstones using an infusion token, with station feedback as the infusion runs.
- **Rarity and stat rolls** — gems range from common to legendary and provide bonuses such as health, armour, damage, or damage reduction according to their type.
- **d20 rolls** — like `/roll`, a d20 plus a modifier from the player's base attribute. Intelligence sets where an infused gem's stat lands, and Dexterity sets a craft roll that adjusts the jewellery stat. A natural 20 always gives the best result and a natural 1 the worst.
- **Masterworks** — need a perfect recipe and hits, a Flawless gem (one that rolled the top of its range) and a craft roll of 20 or more, so every Masterwork has the highest stat possible.
- **Socketed equipment** — connects gems with MMOItems sockets and preserves rarity information through socketing and removal.
- **Goldsmithing projects** — craft rings, necklaces, medals, and other pieces from metals and infused gems, or make gem-free items such as the Golden Key.
- **Craftsmanship matters** — recipe accuracy, tool work, project tier, and finishing quality shape how much of the gem's bonus reaches the finished piece.
- **Material recovery** — finished pieces record the metals actually used for compatible recycling.

The same gemstone links discovery and craft: its infusion determines the starting bonus, and the goldsmith's work shapes the jewellery made from it. Legendary infusions can also be announced to other players.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/GemInfusion/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

[Goldsmithing and finishing rules](https://github.com/TF-Minecraft/Docs/blob/main/projects/GemInfusion/goldsmithing.md)

## Tests

With Java 21 and the pinned plugin dependencies installed, run `mvn clean verify`.
Tests use JUnit 5, Mockito, and MockBukkit. Surefire test results are in
`target/surefire-reports/`; JaCoCo HTML and XML reports are in `target/site/jacoco/`.
CI uploads both. Verification requires 100% line, branch, and instruction
coverage of production code, with no coverage exclusions.
Live station interactions, client effects, and the installed MMOItems/MMOCore
integrations require separate in-game checks.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
