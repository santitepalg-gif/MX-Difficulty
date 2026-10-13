# Sweet Chaos Difficulty (Forge 1.20.1)

Requiere los mods: Sweet Calamity, Born in Chaos (v1.7.5) y Alebrije Tiger (+ GeckoLib, que ya pide Born in Chaos).

## Compilar
Necesitas JDK 17 e internet:

    ./gradlew build        (Windows: gradlew.bat build)

El .jar queda en build/libs/sweet_chaos_difficulty-1.0.0.jar -> copialo a la carpeta mods (cliente Y servidor).

## Comandos (nivel de operador 2). Todos empiezan APAGADOS; cada uno es un interruptor on/off
- /toggle_alebrije_SweetCalamity : Alebrije Tiger aparece en la dimension sweet_calamity:sweet_lands
- /toggle_krampus_SweetCalamity  : Krampus aparece en sweet_calamity:cloying_fields y NO suelta nada (items ni XP)
- /toggle_rain_SweetCalamity     : la lluvia hace 20 de dano (configurable) a jugadores al aire libre
- /toggle_icy_SweetCalamity      : mobs de sweet_calamity y Krampus solo reciben dano con born_in_chaos_v1:icy_sweetness
- /toggle_status_SweetCalamity   : muestra cuales estan activos
- /aurora start | /aurora stop   : aurora boreal visual (cielo oscuro, aurora y estrellas fugaces)

El estado de los interruptores se guarda en el mundo. Config: config/sweet_chaos_difficulty-common.toml (dano e intervalo de la lluvia).
