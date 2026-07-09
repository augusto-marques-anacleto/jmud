# jMud

Cliente MUD para Android feito por e para pessoas cegas, com acessibilidade como requisito central.

## Recursos

- Lista de personagens com login automático e comandos pós-conexão
- Triggers (gatilhos) com vários tipos de busca, históricos nomeados e sons
- Temporizadores com escopo por MUD ou por personagem
- Sintetizador de voz integrado (mecanismo, voz, velocidade, tom e volume configuráveis)
- Suporte a MSP (MUD Sound Protocol) com pacotes de sons
- Logs de sessão
- Backup e restauração de todos os dados
- Atualizador integrado

## Download

Baixe o APK mais recente na [página de releases](https://github.com/augusto-marques-anacleto/jmud/releases/latest).

## Compilação

Projeto Android padrão (Kotlin + Jetpack Compose). Abra no Android Studio ou compile com:

```
gradlew assembleDebug
```

O build de release exige uma keystore própria em `keystore/keystore.properties` (não incluída no repositório).
