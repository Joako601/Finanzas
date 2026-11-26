# Política de seguridad

Kren guarda información financiera personal, así que tomamos en serio cualquier
reporte de seguridad.

## Versiones con soporte

| Versión | Soporte |
|---|---|
| 1.0.x | Sí |
| < 1.0 (versiones internas) | No |

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Usa el reporte privado de GitHub:
[Security → Report a vulnerability](https://github.com/Joako601/Finanzas/security/advisories/new).

Incluye:

- La versión (`Perfil → Acerca de`) y la distribución (`privada` o `nube`).
- El modelo del teléfono y la versión de Android.
- Los pasos para reproducirla y qué puede lograr alguien que la aproveche.
- Si aplica, una prueba de concepto **sin datos financieros reales**.

Qué puedes esperar:

- Confirmación de que llegó el reporte en un plazo de 7 días.
- Una evaluación inicial y un plan en un plazo de 30 días.
- Crédito en las notas de la versión que la corrija, si así lo quieres.

## Alcance

Sí entra:

- Fugas de datos de la base local, del respaldo cifrado o de la exportación a CSV.
- Cualquier forma de que la distribución `privada` llegue a la red.
- Saltarse el bloqueo con el PIN del teléfono, el modo discreto o la protección contra
  capturas de pantalla.
- Fallas en el cifrado del respaldo (PBKDF2 + AES-GCM).
- En la distribución `nube`: manejo de la sesión y de los tokens.

No entra:

- Ataques que necesitan un teléfono con root o desbloqueado por el atacante.
- El backend Spring Boot, que vive en otro repositorio.
- Vulnerabilidades de Android o de librerías de terceros sin un impacto demostrado en Kren.

## Medidas actuales

Las decisiones de seguridad están documentadas en [docs/decisiones.md](docs/decisiones.md):
sin permiso `INTERNET` (D-005), base excluida del respaldo de Android (D-006),
dependencias verificadas por SHA-256 (D-010), respaldo cifrado con contraseña (D-018) y
aislamiento de la distribución nube (D-020).
