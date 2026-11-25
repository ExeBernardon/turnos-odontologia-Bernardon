## ADDED Requirements

### Requirement: README de entrega con guion de demo
El `README.md` SHALL permitir que alguien sin contexto levante y muestre el MVP: requisitos, el comando único de arranque desde cero, las URLs, las credenciales del seed (solo ficticias o por referencia a variables de `.env`), cómo pasar a Gmail solo con variables de entorno y un guion de demo que recorra los 6 flujos principales. SHALL NOT contener secretos ni direcciones de email reales.

#### Scenario: Arranque desde cero siguiendo el README
- **WHEN** un integrante sigue el README en una máquina con Docker y una copia de `.env.example` como `.env`
- **THEN** con `docker compose down -v` y `docker compose up --build` el sistema queda accesible en las URLs indicadas, con el odontólogo, las prestaciones y los horarios del seed

#### Scenario: Sin secretos en el README
- **WHEN** se revisa el README versionado
- **THEN** no aparece ninguna contraseña real, contraseña de aplicación, `JWT_SECRET` ni dirección de email real; la cuenta de Gmail de demo figura solo como marcador

### Requirement: Resolución de problemas de correo en el README
El `README.md` SHALL documentar los síntomas conocidos del correo en la demo: si el SMTP configurado no responde, cada acción que envía mail tarda alrededor del timeout de SMTP (unos 5 s) y el turno se crea igual; con Gmail, el envío sincrónico demora la respuesta unos segundos; los mails pueden caer en spam; y el seed del odontólogo solo se aplica sobre una base vacía.

#### Scenario: SMTP inalcanzable
- **WHEN** quien presenta nota que reservar o cancelar tarda unos 5 s y no llegan mails
- **THEN** el README indica revisar la configuración `SPRING_MAIL_*` y que los turnos igual quedaron creados

#### Scenario: Cambio del email del odontólogo del seed
- **WHEN** se cambia `SEED_ODONTOLOGO_EMAIL` en `.env` sobre una base ya inicializada
- **THEN** el README indica que hace falta `docker compose down -v` para que el seed lo aplique
